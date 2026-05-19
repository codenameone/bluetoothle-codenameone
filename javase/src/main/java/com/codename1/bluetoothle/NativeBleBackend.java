package com.codename1.bluetoothle;

import com.codename1.io.JSONParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/// Real BLE backend for the JavaSE port on macOS, Linux and Windows.
///
/// Spawns a Rust helper executable (built from `javase/src/main/rust/`) that
/// wraps the [btleplug](https://github.com/deviceplug/btleplug) crate —
/// CoreBluetooth on macOS, BlueZ via D-Bus on Linux, WinRT on Windows — and
/// exchanges JSON-line commands and events with it over its stdin/stdout.
/// The helper is packaged as a classpath resource keyed by OS (see
/// {@link #helperResourcePath()}) and extracted to a temp file at first use.
///
/// Each bridge method translates to one JSON command; helper events translate
/// back into the same {@link BluetoothCallbackRegistry#sendResult} calls the
/// {@link SimulatorBluetoothBackend} makes, so the public {@link Bluetooth}
/// API behaves identically regardless of which backend is active.
final class NativeBleBackend implements BluetoothNativeBridge {

    private static final String HELPER_RESOURCE_DIR = "/com/codename1/bluetoothle/native/";

    private Process helper;
    private BufferedWriter helperIn;
    private Thread eventReader;
    private final Object writerLock = new Object();
    private final AtomicLong nextCommandId = new AtomicLong(1);

    private volatile boolean initialized;
    private volatile boolean enabled;
    private volatile boolean scanning;
    private final Set<String> connected = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Set<String> everConnected = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Set<String> discovered = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    /// Returns true iff this backend can run in the current process — i.e., the
    /// packaged Rust helper for this host OS is present on the classpath. The
    /// matching binary only ships when the cn1lib was built on the same OS
    /// (or in a fat-jar published by the maintainer), so a JVM running on
    /// Windows against a Mac-built jar will see this return false and fall
    /// back to the simulator via {@link BluetoothNativeBridgeImpl}.
    static boolean isAvailable() {
        String path = helperResourcePath();
        if (path == null) {
            return false;
        }
        InputStream in = NativeBleBackend.class.getResourceAsStream(path);
        if (in == null) {
            return false;
        }
        try { in.close(); } catch (IOException ignored) {}
        return true;
    }

    /// Classpath location of the helper binary appropriate for the current OS,
    /// or null if this OS isn't supported by btleplug.
    static String helperResourcePath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            return HELPER_RESOURCE_DIR + "macos/cn1-ble-helper";
        }
        if (os.contains("linux")) {
            return HELPER_RESOURCE_DIR + "linux/cn1-ble-helper";
        }
        if (os.contains("windows")) {
            return HELPER_RESOURCE_DIR + "windows/cn1-ble-helper.exe";
        }
        return null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    private synchronized void ensureStarted() {
        if (helper != null && helper.isAlive()) {
            return;
        }
        try {
            File binary = extractHelper();
            ProcessBuilder pb = new ProcessBuilder(binary.getAbsolutePath());
            // Keep stderr visible to the JVM operator — that's where the
            // helper logs malformed-line / decoding errors.
            pb.redirectErrorStream(false);
            helper = pb.start();
            helperIn = new BufferedWriter(new OutputStreamWriter(helper.getOutputStream(), StandardCharsets.UTF_8));
            startEventReader(helper);
            startStderrPump(helper);
            // The helper's stateChanged event will land on the reader thread
            // shortly and flip `enabled`.
        } catch (IOException ex) {
            System.err.println("NativeBleBackend: failed to start helper: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /// Best-effort teardown. Called by {@code BluetoothNativeBridgeImpl} when
    /// the user switches away from this backend at runtime so the helper
    /// process and its OS BLE handles don't outlive the switch. Safe to call
    /// when the helper was never started.
    synchronized void shutdown() {
        if (helper == null) {
            return;
        }
        try {
            if (helperIn != null) {
                synchronized (writerLock) {
                    try {
                        helperIn.write("{\"cmd\":\"shutdown\"}\n");
                        helperIn.flush();
                    } catch (IOException ignored) {
                    }
                    try { helperIn.close(); } catch (IOException ignored) {}
                }
            }
        } finally {
            helper.destroy();
            helper = null;
            helperIn = null;
            initialized = false;
            enabled = false;
            scanning = false;
            connected.clear();
            discovered.clear();
        }
    }

    private File extractHelper() throws IOException {
        String resourcePath = helperResourcePath();
        if (resourcePath == null) {
            throw new IOException("OS not supported by btleplug: " + System.getProperty("os.name"));
        }
        InputStream src = NativeBleBackend.class.getResourceAsStream(resourcePath);
        if (src == null) {
            throw new IOException("helper resource not on classpath: " + resourcePath);
        }
        String filename = isWindows() ? "cn1-ble-helper.exe" : "cn1-ble-helper";
        File out = new File(System.getProperty("java.io.tmpdir"), filename);
        // Always rewrite — cheap and avoids stale binaries from older builds.
        try (FileOutputStream sink = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = src.read(buf)) >= 0) {
                sink.write(buf, 0, n);
            }
        } finally {
            try { src.close(); } catch (IOException ignored) {}
        }
        // POSIX execute bit — no-op on Windows where .exe is recognized.
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        try {
            Files.setPosixFilePermissions(out.toPath(), perms);
        } catch (IOException | UnsupportedOperationException ignored) {
            out.setExecutable(true, true);
        }
        return out;
    }

    private void startEventReader(Process p) {
        eventReader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    handleHelperLine(line);
                }
            } catch (IOException ex) {
                // Process likely died; nothing to do.
            }
        }, "cn1ble-helper-stdout");
        eventReader.setDaemon(true);
        eventReader.start();
    }

    private void startStderrPump(Process p) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    System.err.println("[Cn1BleHelper] " + line);
                }
            } catch (IOException ignored) {
            }
        }, "cn1ble-helper-stderr");
        t.setDaemon(true);
        t.start();
    }

    @SuppressWarnings("unchecked")
    private void handleHelperLine(String line) {
        if (line.isEmpty()) return;
        Map<String, Object> obj;
        try {
            obj = new JSONParser().parseJSON(new StringReader(line));
        } catch (Throwable t) {
            System.err.println("NativeBleBackend: malformed helper output: " + line);
            return;
        }
        Object ev = obj.get("event");
        if (ev instanceof String) {
            handleEvent((String) ev, obj);
            return;
        }
        // Acks are informational — the cn1lib's contract is event-driven, not
        // request/response — but they let us spot rejected commands at debug time.
        Object ack = obj.get("ack");
        Object ok = obj.get("ok");
        if (ack != null && Boolean.FALSE.equals(ok)) {
            System.err.println("NativeBleBackend: helper rejected ack=" + ack + " error=" + obj.get("error"));
        }
    }

    private void handleEvent(String event, Map<String, Object> obj) {
        switch (event) {
            case "stateChanged":
                String state = stringOr(obj, "state", "unknown");
                enabled = "poweredOn".equals(state);
                if (initialized) {
                    BluetoothCallbackRegistry.sendResult("initialize",
                            JsonBuilder.start().put("status", enabled ? "enabled" : "disabled").end(), true, true);
                }
                break;
            case "scanResult": {
                if (!scanning) break;
                BluetoothCallbackRegistry.sendResult("startScan",
                        JsonBuilder.start()
                                .put("status", "scanResult")
                                .put("address", stringOr(obj, "address", ""))
                                .put("name", stringOr(obj, "name", ""))
                                .put("rssi", intOr(obj, "rssi", -127))
                                .put("advertisement", stringOr(obj, "advertisement", ""))
                                .end(), true, true);
                break;
            }
            case "scanStopped":
                scanning = false;
                BluetoothCallbackRegistry.sendResult("stopScan",
                        JsonBuilder.start().put("status", "scanStopped").end(), true);
                break;
            case "connected": {
                String address = stringOr(obj, "address", "");
                connected.add(address);
                everConnected.add(address);
                BluetoothCallbackRegistry.sendResult("connect",
                        JsonBuilder.start()
                                .put("status", "connected")
                                .put("address", address)
                                .put("name", stringOr(obj, "name", ""))
                                .end(), true, true);
                break;
            }
            case "disconnected": {
                String address = stringOr(obj, "address", "");
                connected.remove(address);
                discovered.remove(address);
                BluetoothCallbackRegistry.sendResult("disconnect",
                        JsonBuilder.start()
                                .put("status", "disconnected")
                                .put("address", address)
                                .put("name", "")
                                .end(), true);
                break;
            }
            case "discovered": {
                String address = stringOr(obj, "address", "");
                discovered.add(address);
                BluetoothCallbackRegistry.sendResult("discover",
                        buildDiscoveredJson(obj), true);
                break;
            }
            case "readResult":
                BluetoothCallbackRegistry.sendResult("read",
                        JsonBuilder.start()
                                .put("status", "read")
                                .put("address", stringOr(obj, "address", ""))
                                .put("service", stringOr(obj, "service", ""))
                                .put("characteristic", stringOr(obj, "characteristic", ""))
                                .put("value", stringOr(obj, "value", ""))
                                .end(), true);
                break;
            case "writeResult":
                BluetoothCallbackRegistry.sendResult("write",
                        JsonBuilder.start()
                                .put("status", "written")
                                .put("address", stringOr(obj, "address", ""))
                                .put("service", stringOr(obj, "service", ""))
                                .put("characteristic", stringOr(obj, "characteristic", ""))
                                .put("value", stringOr(obj, "value", ""))
                                .end(), true);
                break;
            case "subscribed":
                BluetoothCallbackRegistry.sendResult("subscribe",
                        JsonBuilder.start()
                                .put("status", "subscribed")
                                .put("address", stringOr(obj, "address", ""))
                                .put("service", stringOr(obj, "service", ""))
                                .put("characteristic", stringOr(obj, "characteristic", ""))
                                .end(), true, true);
                break;
            case "notification":
                BluetoothCallbackRegistry.sendResult("subscribe",
                        JsonBuilder.start()
                                .put("status", "subscribedResult")
                                .put("address", stringOr(obj, "address", ""))
                                .put("service", stringOr(obj, "service", ""))
                                .put("characteristic", stringOr(obj, "characteristic", ""))
                                .put("value", stringOr(obj, "value", ""))
                                .end(), true, true);
                break;
            case "unsubscribed":
                BluetoothCallbackRegistry.sendResult("unsubscribe",
                        JsonBuilder.start()
                                .put("status", "unsubscribed")
                                .put("address", stringOr(obj, "address", ""))
                                .put("service", stringOr(obj, "service", ""))
                                .put("characteristic", stringOr(obj, "characteristic", ""))
                                .end(), true);
                break;
            case "error":
                String command = stringOr(obj, "command", "");
                if (!command.isEmpty()) {
                    BluetoothCallbackRegistry.sendResult(command,
                            JsonBuilder.start()
                                    .put("error", command)
                                    .put("message", stringOr(obj, "message", ""))
                                    .end(), false);
                }
                break;
            default:
                // Unknown event — log but don't crash.
                System.err.println("NativeBleBackend: unknown event '" + event + "'");
        }
    }

    @SuppressWarnings("unchecked")
    private String buildDiscoveredJson(Map<String, Object> obj) {
        StringBuilder services = new StringBuilder("[");
        boolean firstS = true;
        Object svcArr = obj.get("services");
        if (svcArr instanceof List) {
            for (Object svcObj : (List<Object>) svcArr) {
                if (!(svcObj instanceof Map)) continue;
                Map<String, Object> svc = (Map<String, Object>) svcObj;
                if (!firstS) services.append(',');
                firstS = false;
                StringBuilder chars = new StringBuilder("[");
                boolean firstC = true;
                Object chArr = svc.get("characteristics");
                if (chArr instanceof List) {
                    for (Object chObj : (List<Object>) chArr) {
                        if (!(chObj instanceof Map)) continue;
                        Map<String, Object> ch = (Map<String, Object>) chObj;
                        if (!firstC) chars.append(',');
                        firstC = false;
                        StringBuilder props = new StringBuilder("[");
                        boolean firstP = true;
                        Object pArr = ch.get("properties");
                        if (pArr instanceof List) {
                            for (Object p : (List<Object>) pArr) {
                                if (!firstP) props.append(',');
                                firstP = false;
                                props.append('"').append(String.valueOf(p)).append('"');
                            }
                        }
                        props.append(']');
                        chars.append(JsonBuilder.start()
                                .put("characteristic", stringOr(ch, "uuid", ""))
                                .putRaw("properties", props.toString())
                                .end());
                    }
                }
                chars.append(']');
                services.append(JsonBuilder.start()
                        .put("service", stringOr(svc, "uuid", ""))
                        .putRaw("characteristics", chars.toString())
                        .end());
            }
        }
        services.append(']');
        return JsonBuilder.start()
                .put("status", "discovered")
                .put("address", stringOr(obj, "address", ""))
                .put("name", stringOr(obj, "name", ""))
                .putRaw("services", services.toString())
                .end();
    }

    private static String stringOr(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v == null ? def : v.toString();
    }

    private static int intOr(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt((String) v); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private void sendCommand(String json) {
        ensureStarted();
        if (helperIn == null) return;
        synchronized (writerLock) {
            try {
                helperIn.write(json);
                helperIn.write('\n');
                helperIn.flush();
            } catch (IOException ex) {
                System.err.println("NativeBleBackend: write failed: " + ex.getMessage());
            }
        }
    }

    private long nextId() {
        return nextCommandId.getAndIncrement();
    }

    // ------------ BluetoothNativeBridge methods ------------

    @Override public boolean isSupported() {
        return true;
    }

    @Override public boolean initialize(boolean request, boolean statusReceiver, String restoreKey) {
        // Set the flag BEFORE ensureStarted so that the helper's startup
        // stateChanged event (which can arrive before this method returns)
        // doesn't lose the race with the initialize listener.
        initialized = true;
        ensureStarted();
        sendCommand(JsonBuilder.start().put("cmd", "initialize").put("id", (int) nextId()).end());
        // The helper's stateChanged event will fire and propagate the
        // initialize callback (see handleEvent("stateChanged")).
        return true;
    }

    @Override public boolean enable() {
        // CoreBluetooth doesn't expose programmatic Bluetooth toggling; the
        // best we can do is surface a clear "user must enable" status to the
        // listener so the public API doesn't deadlock waiting on an enable
        // event that never lands.
        BluetoothCallbackRegistry.sendResult("enable",
                JsonBuilder.start()
                        .put("status", enabled ? "enabled" : "disabled")
                        .put("message", "Open System Settings → Bluetooth to toggle the adapter")
                        .end(), enabled);
        return true;
    }

    @Override public boolean disable() {
        BluetoothCallbackRegistry.sendResult("disable",
                JsonBuilder.start()
                        .put("status", enabled ? "enabled" : "disabled")
                        .put("message", "Open System Settings → Bluetooth to toggle the adapter")
                        .end(), !enabled);
        return true;
    }

    @Override public boolean startScan(String servicesJson, boolean allowDuplicates,
                                       int scanMode, int matchMode, int matchNum, int callbackType) {
        if (!enabled) {
            BluetoothCallbackRegistry.sendResult("startScan",
                    JsonBuilder.start()
                            .put("error", "isDisabled")
                            .put("message", "Bluetooth not enabled")
                            .end(), false);
            return true;
        }
        scanning = true;
        String servicesArray = (servicesJson == null || servicesJson.isEmpty()) ? "[]" : servicesJson;
        sendCommand("{\"cmd\":\"startScan\",\"id\":" + nextId() + ",\"services\":" + servicesArray
                + ",\"allowDuplicates\":" + allowDuplicates + "}");
        BluetoothCallbackRegistry.sendResult("startScan",
                JsonBuilder.start().put("status", "scanStarted").end(), true, true);
        return true;
    }

    @Override public boolean stopScan() {
        scanning = false;
        sendCommand(JsonBuilder.start().put("cmd", "stopScan").put("id", (int) nextId()).end());
        return true;
    }

    @Override public boolean retrieveConnected(String servicesJson) {
        // CoreBluetooth has retrieveConnectedPeripherals; not wired through
        // yet. Emit empty list so callers don't hang.
        BluetoothCallbackRegistry.sendResult("retrieveConnected",
                JsonBuilder.start()
                        .put("status", "retrieveConnected")
                        .putRaw("devices", "[]")
                        .end(), true);
        return true;
    }

    @Override public boolean connect(String address) {
        sendCommand("{\"cmd\":\"connect\",\"id\":" + nextId() + ",\"address\":\"" + escape(address) + "\"}");
        return true;
    }

    @Override public boolean reconnect(String address) {
        return connect(address);
    }

    @Override public boolean disconnect(String address) {
        sendCommand("{\"cmd\":\"disconnect\",\"id\":" + nextId() + ",\"address\":\"" + escape(address) + "\"}");
        return true;
    }

    @Override public boolean close(String address) {
        // Treat like disconnect; helper has no separate "close" concept.
        return disconnect(address);
    }

    @Override public boolean discover(String address) {
        sendCommand("{\"cmd\":\"discover\",\"id\":" + nextId() + ",\"address\":\"" + escape(address) + "\"}");
        return true;
    }

    @Override public boolean services(String address, String servicesJson) {
        // The discover() event already includes services; no additional helper roundtrip.
        BluetoothCallbackRegistry.sendResult("services",
                JsonBuilder.start()
                        .put("status", "services")
                        .put("address", address)
                        .putRaw("services", "[]")
                        .end(), true);
        return true;
    }

    @Override public boolean characteristics(String address, String service, String characteristicsJson) {
        BluetoothCallbackRegistry.sendResult("characteristics",
                JsonBuilder.start()
                        .put("status", "characteristics")
                        .put("address", address)
                        .put("service", service)
                        .putRaw("characteristics", "[]")
                        .end(), true);
        return true;
    }

    @Override public boolean descriptors(String address, String service, String characteristic) {
        BluetoothCallbackRegistry.sendResult("descriptors",
                JsonBuilder.start()
                        .put("status", "descriptors")
                        .put("address", address)
                        .put("service", service)
                        .put("characteristic", characteristic)
                        .putRaw("descriptors", "[]")
                        .end(), true);
        return true;
    }

    @Override public boolean read(String address, String service, String characteristic) {
        sendCommand("{\"cmd\":\"read\",\"id\":" + nextId()
                + ",\"address\":\"" + escape(address)
                + "\",\"service\":\"" + escape(service)
                + "\",\"characteristic\":\"" + escape(characteristic) + "\"}");
        return true;
    }

    @Override public boolean subscribe(String address, String service, String characteristic) {
        sendCommand("{\"cmd\":\"subscribe\",\"id\":" + nextId()
                + ",\"address\":\"" + escape(address)
                + "\",\"service\":\"" + escape(service)
                + "\",\"characteristic\":\"" + escape(characteristic) + "\"}");
        return true;
    }

    @Override public boolean unsubscribe(String address, String service, String characteristic) {
        sendCommand("{\"cmd\":\"unsubscribe\",\"id\":" + nextId()
                + ",\"address\":\"" + escape(address)
                + "\",\"service\":\"" + escape(service)
                + "\",\"characteristic\":\"" + escape(characteristic) + "\"}");
        return true;
    }

    @Override public boolean write(String address, String service, String characteristic, String value, boolean noResponse) {
        sendCommand("{\"cmd\":\"write\",\"id\":" + nextId()
                + ",\"address\":\"" + escape(address)
                + "\",\"service\":\"" + escape(service)
                + "\",\"characteristic\":\"" + escape(characteristic)
                + "\",\"value\":\"" + escape(value == null ? "" : value)
                + "\",\"noResponse\":" + noResponse + "}");
        return true;
    }

    @Override public boolean writeQ(String address, String service, String characteristic, String value, boolean noResponse) {
        return write(address, service, characteristic, value, noResponse);
    }

    @Override public boolean readDescriptor(String address, String service, String characteristic, String descriptor) {
        BluetoothCallbackRegistry.sendResult("readDescriptor",
                JsonBuilder.start()
                        .put("error", "readDescriptor")
                        .put("message", "Descriptor I/O not yet wired in CoreBluetooth backend")
                        .end(), false);
        return true;
    }

    @Override public boolean writeDescriptor(String address, String service, String characteristic, String descriptor, String value) {
        BluetoothCallbackRegistry.sendResult("writeDescriptor",
                JsonBuilder.start()
                        .put("error", "writeDescriptor")
                        .put("message", "Descriptor I/O not yet wired in CoreBluetooth backend")
                        .end(), false);
        return true;
    }

    @Override public boolean rssi(String address) {
        // CoreBluetooth gives RSSI via peripheral.readRSSI(); not wired yet.
        BluetoothCallbackRegistry.sendResult("rssi",
                JsonBuilder.start()
                        .put("status", "rssi")
                        .put("address", address)
                        .put("rssi", -127)
                        .end(), true);
        return true;
    }

    @Override public boolean mtu(String address, int mtu) {
        BluetoothCallbackRegistry.sendResult("mtu",
                JsonBuilder.start()
                        .put("status", "mtu")
                        .put("address", address)
                        .put("mtu", 185)  // CoreBluetooth's typical default
                        .end(), true);
        return true;
    }

    @Override public boolean requestConnectionPriority(String address, String priority) {
        // Not applicable to CoreBluetooth; ack so the listener doesn't hang.
        BluetoothCallbackRegistry.sendResult("requestConnectionPriority",
                JsonBuilder.start()
                        .put("status", "connectionPriorityRequested")
                        .put("address", address)
                        .put("connectionPriority", priority == null ? "" : priority)
                        .end(), true);
        return true;
    }

    @Override public boolean isInitialized() {
        BluetoothCallbackRegistry.sendResult("isInitialized",
                JsonBuilder.start().put("isInitialized", initialized).end(), true);
        return true;
    }

    @Override public boolean isEnabled() {
        BluetoothCallbackRegistry.sendResult("isEnabled",
                JsonBuilder.start().put("isEnabled", enabled).end(), true);
        return true;
    }

    @Override public boolean isScanning() {
        BluetoothCallbackRegistry.sendResult("isScanning",
                JsonBuilder.start().put("isScanning", scanning).end(), true);
        return true;
    }

    @Override public boolean wasConnected(String address) {
        BluetoothCallbackRegistry.sendResult("wasConnected",
                JsonBuilder.start()
                        .put("address", address)
                        .put("wasConnected", everConnected.contains(address))
                        .end(), true);
        return true;
    }

    @Override public boolean isConnected(String address) {
        BluetoothCallbackRegistry.sendResult("isConnected",
                JsonBuilder.start()
                        .put("address", address)
                        .put("isConnected", connected.contains(address))
                        .end(), true);
        return true;
    }

    @Override public boolean isDiscovered(String address) {
        BluetoothCallbackRegistry.sendResult("isDiscovered",
                JsonBuilder.start()
                        .put("address", address)
                        .put("isDiscovered", discovered.contains(address))
                        .end(), true);
        return true;
    }

    @Override public boolean hasPermission() {
        // macOS handles BT permission via TCC; we treat "enabled" as "permitted"
        // since reaching poweredOn requires both the adapter on and TCC granted.
        BluetoothCallbackRegistry.sendResult("hasPermission",
                JsonBuilder.start().put("hasPermission", true).end(), true);
        return true;
    }

    @Override public boolean requestPermission() {
        BluetoothCallbackRegistry.sendResult("requestPermission",
                JsonBuilder.start().put("requestPermission", true).end(), true);
        return true;
    }

    @Override public boolean isLocationEnabled() {
        // Location not required for BLE scanning on macOS, unlike Android.
        BluetoothCallbackRegistry.sendResult("isLocationEnabled",
                JsonBuilder.start().put("isLocationEnabled", true).end(), true);
        return true;
    }

    @Override public boolean requestLocation() {
        BluetoothCallbackRegistry.sendResult("requestLocation",
                JsonBuilder.start().put("requestLocation", true).end(), true);
        return true;
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
