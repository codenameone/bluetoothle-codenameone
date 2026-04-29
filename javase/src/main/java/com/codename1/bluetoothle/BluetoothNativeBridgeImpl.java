package com.codename1.bluetoothle;

import com.codename1.ui.Display;

import java.util.List;
import java.util.Map;

/// JavaSE / Codename One simulator implementation of the BLE native bridge.
///
/// Backed by [BluetoothSimulator]: a scriptable in-memory virtual peripheral
/// stack. This lets the public [Bluetooth] API run end-to-end inside the CN1
/// simulator and CN1 UnitTest suite, with the same callback payload shapes
/// the Android and iOS bridges produce.
///
/// All bridge methods return true (action accepted for dispatch) and deliver
/// real success/error payloads asynchronously through
/// [BluetoothCallbackRegistry.sendResult] — exactly the contract the iOS and
/// Android implementations honor.
public class BluetoothNativeBridgeImpl implements BluetoothNativeBridge {

    private boolean bluetoothUsageDescriptionChecked;

    @Override
    public boolean isSupported() {
        installBuildHints();
        return true;
    }

    @Override
    public boolean initialize(boolean request, boolean statusReceiver, String restoreKey) {
        installBuildHints();
        SimulatorState s = BluetoothSimulator.state();
        s.setInitialized(true);
        if (request && !s.isEnabled()) {
            s.setEnabled(true);
        }
        final String status = s.isEnabled() ? "enabled" : "disabled";
        // keepCallback=true so subsequent state-change broadcasts (eg
        // simulator-driven enable/disable) can deliver to the same listener.
        s.runSync(() -> BluetoothCallbackRegistry.sendResult("initialize",
                JsonBuilder.start().put("status", status).end(), true, true));
        return true;
    }

    @Override
    public boolean enable() {
        SimulatorState s = BluetoothSimulator.state();
        s.setEnabled(true);
        s.runSync(() -> BluetoothCallbackRegistry.sendResult("enable",
                JsonBuilder.start().put("status", "enabled").end(), true));
        return true;
    }

    @Override
    public boolean disable() {
        SimulatorState s = BluetoothSimulator.state();
        s.setEnabled(false);
        s.runSync(() -> BluetoothCallbackRegistry.sendResult("disable",
                JsonBuilder.start().put("status", "disabled").end(), true));
        return true;
    }

    @Override
    public boolean startScan(String servicesJson, boolean allowDuplicates, int scanMode, int matchMode, int matchNum, int callbackType) {
        SimulatorState s = BluetoothSimulator.state();
        if (failIfPresent("startScan")) return true;
        if (!s.isEnabled()) {
            return errorAsync("startScan", "isDisabled", "Bluetooth not enabled");
        }
        s.setScanning(true);
        s.schedule(() -> BluetoothCallbackRegistry.sendResult("startScan",
                JsonBuilder.start().put("status", "scanStarted").end(), true, true));
        int delay = 0;
        for (SimulatedPeripheral p : s.snapshotPeripherals()) {
            final int extra = delay;
            s.scheduleAfter(extra, () -> {
                if (!s.isScanning()) return;
                String json = JsonBuilder.start()
                        .put("status", "scanResult")
                        .put("address", p.getAddress())
                        .put("name", p.getName())
                        .put("rssi", p.getRssi())
                        .put("advertisement", Base64Util.encode(p.getAdvertisementData() == null ? new byte[0] : p.getAdvertisementData()))
                        .end();
                BluetoothCallbackRegistry.sendResult("startScan", json, true, true);
            });
            delay += 5;
        }
        return true;
    }

    @Override
    public boolean stopScan() {
        SimulatorState s = BluetoothSimulator.state();
        s.setScanning(false);
        s.schedule(() -> BluetoothCallbackRegistry.sendResult("stopScan",
                JsonBuilder.start().put("status", "scanStopped").end(), true));
        return true;
    }

    @Override
    public boolean retrieveConnected(String servicesJson) {
        SimulatorState s = BluetoothSimulator.state();
        s.schedule(() -> {
            StringBuilder arr = new StringBuilder("[");
            boolean firstP = true;
            for (SimulatedPeripheral p : s.snapshotPeripherals()) {
                SimulatorState.ConnectionState cs = s.connectionFor(p.getAddress(), false);
                if (cs == null || !cs.connected) continue;
                if (!firstP) arr.append(',');
                firstP = false;
                arr.append(JsonBuilder.start()
                        .put("address", p.getAddress())
                        .put("name", p.getName())
                        .end());
            }
            arr.append(']');
            String json = JsonBuilder.start()
                    .put("status", "retrieveConnected")
                    .putRaw("devices", arr.toString())
                    .end();
            BluetoothCallbackRegistry.sendResult("retrieveConnected", json, true);
        });
        return true;
    }

    @Override
    public boolean connect(String address) {
        SimulatorState s = BluetoothSimulator.state();
        if (failIfPresent("connect")) return true;
        if (!s.isEnabled()) return errorAsync("connect", "isDisabled", "Bluetooth not enabled");
        SimulatedPeripheral p = s.getPeripheral(address);
        if (p == null) return errorAsync("connect", "neverConnected", "No such device: " + address);
        SimulatorState.ConnectionState cs = s.connectionFor(address, true);
        if (cs.connected) return errorAsync("connect", "isNotDisconnected", "Device isn't disconnected");
        cs.connected = true;
        cs.wasConnected = true;
        s.schedule(() -> BluetoothCallbackRegistry.sendResult("connect",
                JsonBuilder.start()
                        .put("status", "connected")
                        .put("address", p.getAddress())
                        .put("name", p.getName())
                        .end(), true, true));
        return true;
    }

    @Override
    public boolean reconnect(String address) {
        SimulatorState s = BluetoothSimulator.state();
        SimulatedPeripheral p = s.getPeripheral(address);
        if (p == null) return errorAsync("reconnect", "neverConnected", "Never connected to device");
        SimulatorState.ConnectionState cs = s.connectionFor(address, true);
        if (!cs.wasConnected) return errorAsync("reconnect", "neverConnected", "Never connected to device");
        cs.connected = true;
        s.schedule(() -> BluetoothCallbackRegistry.sendResult("reconnect",
                JsonBuilder.start()
                        .put("status", "connected")
                        .put("address", p.getAddress())
                        .put("name", p.getName())
                        .end(), true, true));
        return true;
    }

    @Override
    public boolean disconnect(String address) {
        SimulatorState s = BluetoothSimulator.state();
        SimulatedPeripheral p = s.getPeripheral(address);
        SimulatorState.ConnectionState cs = s.connectionFor(address, false);
        if (cs == null || !cs.connected) {
            return errorAsync("disconnect", "isDisconnected", "Device is disconnected");
        }
        cs.connected = false;
        cs.subscriptions.clear();
        s.schedule(() -> BluetoothCallbackRegistry.sendResult("disconnect",
                JsonBuilder.start()
                        .put("status", "disconnected")
                        .put("address", address)
                        .put("name", p == null ? "" : p.getName())
                        .end(), true));
        return true;
    }

    @Override
    public boolean close(String address) {
        SimulatorState s = BluetoothSimulator.state();
        SimulatorState.ConnectionState cs = s.connectionFor(address, false);
        if (cs != null) {
            cs.connected = false;
            cs.discovered = false;
            cs.subscriptions.clear();
        }
        s.schedule(() -> BluetoothCallbackRegistry.sendResult("close",
                JsonBuilder.start()
                        .put("status", "closed")
                        .put("address", address)
                        .end(), true));
        return true;
    }

    @Override
    public boolean discover(String address) {
        SimulatorState s = BluetoothSimulator.state();
        SimulatedPeripheral p = s.getPeripheral(address);
        SimulatorState.ConnectionState cs = s.connectionFor(address, false);
        if (p == null || cs == null || !cs.connected) {
            return errorAsync("discover", "isDisconnected", "Device is disconnected");
        }
        cs.discovered = true;
        s.schedule(() -> BluetoothCallbackRegistry.sendResult("discover",
                buildDiscoveredJson(p), true));
        return true;
    }

    @Override
    public boolean services(String address, String servicesJson) {
        SimulatorState s = BluetoothSimulator.state();
        SimulatedPeripheral p = s.getPeripheral(address);
        if (p == null) return errorAsync("services", "services", "Unknown device");
        s.schedule(() -> {
            StringBuilder arr = new StringBuilder("[");
            boolean firstS = true;
            for (SimulatedService svc : p.getServices()) {
                if (!firstS) arr.append(',');
                firstS = false;
                arr.append('"').append(svc.getUuid()).append('"');
            }
            arr.append(']');
            String json = JsonBuilder.start()
                    .put("status", "services")
                    .put("address", address)
                    .putRaw("services", arr.toString())
                    .end();
            BluetoothCallbackRegistry.sendResult("services", json, true);
        });
        return true;
    }

    @Override
    public boolean characteristics(String address, String service, String characteristicsJson) {
        SimulatorState s = BluetoothSimulator.state();
        SimulatedPeripheral p = s.getPeripheral(address);
        SimulatedService svc = p == null ? null : p.findService(service);
        if (svc == null) return errorAsync("characteristics", "characteristics", "Unknown service");
        s.schedule(() -> BluetoothCallbackRegistry.sendResult("characteristics",
                JsonBuilder.start()
                        .put("status", "characteristics")
                        .put("address", address)
                        .put("service", service)
                        .putRaw("characteristics", buildCharacteristicsArray(svc))
                        .end(), true));
        return true;
    }

    @Override
    public boolean descriptors(String address, String service, String characteristic) {
        SimulatorState s = BluetoothSimulator.state();
        SimulatedPeripheral p = s.getPeripheral(address);
        SimulatedService svc = p == null ? null : p.findService(service);
        SimulatedCharacteristic ch = svc == null ? null : svc.findCharacteristic(characteristic);
        if (ch == null) return errorAsync("descriptors", "descriptors", "Unknown characteristic");
        s.schedule(() -> {
            StringBuilder arr = new StringBuilder("[");
            boolean firstD = true;
            for (Map.Entry<String, byte[]> e : ch.getDescriptors().entrySet()) {
                if (!firstD) arr.append(',');
                firstD = false;
                arr.append('"').append(e.getKey()).append('"');
            }
            arr.append(']');
            BluetoothCallbackRegistry.sendResult("descriptors",
                    JsonBuilder.start()
                            .put("status", "descriptors")
                            .put("address", address)
                            .put("service", service)
                            .put("characteristic", characteristic)
                            .putRaw("descriptors", arr.toString())
                            .end(), true);
        });
        return true;
    }

    @Override
    public boolean read(String address, String service, String characteristic) {
        SimulatorState s = BluetoothSimulator.state();
        if (failIfPresent("read")) return true;
        SimulatedCharacteristic ch = lookupCharacteristic(address, service, characteristic);
        if (ch == null) return errorAsync("read", "read", "Unknown characteristic");
        byte[] value = ch.getValue();
        s.schedule(() -> BluetoothCallbackRegistry.sendResult("read",
                JsonBuilder.start()
                        .put("status", "read")
                        .put("address", address)
                        .put("service", service)
                        .put("characteristic", characteristic)
                        .put("value", Base64Util.encode(value == null ? new byte[0] : value))
                        .end(), true));
        return true;
    }

    @Override
    public boolean subscribe(String address, String service, String characteristic) {
        SimulatorState s = BluetoothSimulator.state();
        if (failIfPresent("subscribe")) return true;
        SimulatedCharacteristic ch = lookupCharacteristic(address, service, characteristic);
        if (ch == null) return errorAsync("subscribe", "subscribe", "Unknown characteristic");
        SimulatorState.ConnectionState cs = s.connectionFor(address, true);
        cs.subscriptions.add(SimulatorState.subscriptionKey(service, characteristic));
        s.schedule(() -> BluetoothCallbackRegistry.sendResult("subscribe",
                JsonBuilder.start()
                        .put("status", "subscribed")
                        .put("address", address)
                        .put("service", service)
                        .put("characteristic", characteristic)
                        .end(), true, true));
        return true;
    }

    @Override
    public boolean unsubscribe(String address, String service, String characteristic) {
        SimulatorState s = BluetoothSimulator.state();
        SimulatorState.ConnectionState cs = s.connectionFor(address, false);
        if (cs != null) {
            cs.subscriptions.remove(SimulatorState.subscriptionKey(service, characteristic));
        }
        s.schedule(() -> BluetoothCallbackRegistry.sendResult("unsubscribe",
                JsonBuilder.start()
                        .put("status", "unsubscribed")
                        .put("address", address)
                        .put("service", service)
                        .put("characteristic", characteristic)
                        .end(), true));
        return true;
    }

    @Override
    public boolean write(String address, String service, String characteristic, String value, boolean noResponse) {
        SimulatorState s = BluetoothSimulator.state();
        if (failIfPresent("write")) return true;
        SimulatedCharacteristic ch = lookupCharacteristic(address, service, characteristic);
        if (ch == null) return errorAsync("write", "write", "Unknown characteristic");
        ch.setValueInternal(Base64Util.decode(value));
        s.schedule(() -> BluetoothCallbackRegistry.sendResult("write",
                JsonBuilder.start()
                        .put("status", "written")
                        .put("address", address)
                        .put("service", service)
                        .put("characteristic", characteristic)
                        .put("value", value == null ? "" : value)
                        .end(), true));
        return true;
    }

    @Override
    public boolean writeQ(String address, String service, String characteristic, String value, boolean noResponse) {
        return write(address, service, characteristic, value, noResponse);
    }

    @Override
    public boolean readDescriptor(String address, String service, String characteristic, String descriptor) {
        SimulatorState s = BluetoothSimulator.state();
        SimulatedCharacteristic ch = lookupCharacteristic(address, service, characteristic);
        if (ch == null || !ch.hasDescriptor(descriptor)) {
            return errorAsync("readDescriptor", "readDescriptor", "Unknown descriptor");
        }
        byte[] dv = ch.getDescriptorValue(descriptor);
        s.schedule(() -> BluetoothCallbackRegistry.sendResult("readDescriptor",
                JsonBuilder.start()
                        .put("status", "readDescriptor")
                        .put("address", address)
                        .put("service", service)
                        .put("characteristic", characteristic)
                        .put("descriptor", descriptor)
                        .put("value", Base64Util.encode(dv == null ? new byte[0] : dv))
                        .end(), true));
        return true;
    }

    @Override
    public boolean writeDescriptor(String address, String service, String characteristic, String descriptor, String value) {
        SimulatorState s = BluetoothSimulator.state();
        SimulatedCharacteristic ch = lookupCharacteristic(address, service, characteristic);
        if (ch == null || !ch.hasDescriptor(descriptor)) {
            return errorAsync("writeDescriptor", "writeDescriptor", "Unknown descriptor");
        }
        ch.setDescriptorValue(descriptor, Base64Util.decode(value));
        s.schedule(() -> BluetoothCallbackRegistry.sendResult("writeDescriptor",
                JsonBuilder.start()
                        .put("status", "writtenDescriptor")
                        .put("address", address)
                        .put("service", service)
                        .put("characteristic", characteristic)
                        .put("descriptor", descriptor)
                        .end(), true));
        return true;
    }

    @Override
    public boolean rssi(String address) {
        SimulatorState s = BluetoothSimulator.state();
        SimulatedPeripheral p = s.getPeripheral(address);
        int r = p == null ? -127 : p.getRssi();
        s.schedule(() -> BluetoothCallbackRegistry.sendResult("rssi",
                JsonBuilder.start()
                        .put("status", "rssi")
                        .put("address", address)
                        .put("rssi", r)
                        .end(), true));
        return true;
    }

    @Override
    public boolean mtu(String address, int mtu) {
        SimulatorState s = BluetoothSimulator.state();
        s.schedule(() -> BluetoothCallbackRegistry.sendResult("mtu",
                JsonBuilder.start()
                        .put("status", "mtu")
                        .put("address", address)
                        .put("mtu", mtu)
                        .end(), true));
        return true;
    }

    @Override
    public boolean requestConnectionPriority(String address, String priority) {
        SimulatorState s = BluetoothSimulator.state();
        s.schedule(() -> BluetoothCallbackRegistry.sendResult("requestConnectionPriority",
                JsonBuilder.start()
                        .put("status", "connectionPriorityRequested")
                        .put("address", address)
                        .put("connectionPriority", priority == null ? "" : priority)
                        .end(), true));
        return true;
    }

    // Pure state queries are delivered synchronously: the Android plugin
    // implements them with callbackContext.success(...) inside the action
    // handler, so by the time the dispatch call returns the callback has
    // already received its payload. Keeping them sync here removes a
    // scheduler hop and matches the contract the public Bluetooth API
    // relies on (callback.getResponseAndWait(500) sees a complete callback
    // immediately).

    @Override
    public boolean isInitialized() {
        SimulatorState s = BluetoothSimulator.state();
        boolean v = s.isInitialized();
        s.runSync(() -> BluetoothCallbackRegistry.sendResult("isInitialized",
                JsonBuilder.start().put("isInitialized", v).end(), true));
        return true;
    }

    @Override
    public boolean isEnabled() {
        SimulatorState s = BluetoothSimulator.state();
        boolean v = s.isEnabled();
        s.runSync(() -> BluetoothCallbackRegistry.sendResult("isEnabled",
                JsonBuilder.start().put("isEnabled", v).end(), true));
        return true;
    }

    @Override
    public boolean isScanning() {
        SimulatorState s = BluetoothSimulator.state();
        boolean v = s.isScanning();
        s.runSync(() -> BluetoothCallbackRegistry.sendResult("isScanning",
                JsonBuilder.start().put("isScanning", v).end(), true));
        return true;
    }

    @Override
    public boolean wasConnected(String address) {
        SimulatorState s = BluetoothSimulator.state();
        SimulatorState.ConnectionState cs = s.connectionFor(address, false);
        boolean v = cs != null && cs.wasConnected;
        s.runSync(() -> BluetoothCallbackRegistry.sendResult("wasConnected",
                JsonBuilder.start()
                        .put("address", address)
                        .put("wasConnected", v)
                        .end(), true));
        return true;
    }

    @Override
    public boolean isConnected(String address) {
        SimulatorState s = BluetoothSimulator.state();
        SimulatorState.ConnectionState cs = s.connectionFor(address, false);
        boolean v = cs != null && cs.connected;
        s.runSync(() -> BluetoothCallbackRegistry.sendResult("isConnected",
                JsonBuilder.start()
                        .put("address", address)
                        .put("isConnected", v)
                        .end(), true));
        return true;
    }

    @Override
    public boolean isDiscovered(String address) {
        SimulatorState s = BluetoothSimulator.state();
        SimulatorState.ConnectionState cs = s.connectionFor(address, false);
        boolean v = cs != null && cs.discovered;
        s.runSync(() -> BluetoothCallbackRegistry.sendResult("isDiscovered",
                JsonBuilder.start()
                        .put("address", address)
                        .put("isDiscovered", v)
                        .end(), true));
        return true;
    }

    @Override
    public boolean hasPermission() {
        SimulatorState s = BluetoothSimulator.state();
        boolean v = s.hasPermission();
        s.runSync(() -> BluetoothCallbackRegistry.sendResult("hasPermission",
                JsonBuilder.start().put("hasPermission", v).end(), true));
        return true;
    }

    @Override
    public boolean requestPermission() {
        SimulatorState s = BluetoothSimulator.state();
        s.setHasPermission(true);
        s.runSync(() -> BluetoothCallbackRegistry.sendResult("requestPermission",
                JsonBuilder.start().put("requestPermission", true).end(), true));
        return true;
    }

    @Override
    public boolean isLocationEnabled() {
        SimulatorState s = BluetoothSimulator.state();
        boolean v = s.isLocationEnabled();
        s.runSync(() -> BluetoothCallbackRegistry.sendResult("isLocationEnabled",
                JsonBuilder.start().put("isLocationEnabled", v).end(), true));
        return true;
    }

    @Override
    public boolean requestLocation() {
        SimulatorState s = BluetoothSimulator.state();
        s.setLocationEnabled(true);
        s.runSync(() -> BluetoothCallbackRegistry.sendResult("requestLocation",
                JsonBuilder.start().put("requestLocation", true).end(), true));
        return true;
    }

    private SimulatedCharacteristic lookupCharacteristic(String address, String service, String characteristic) {
        SimulatorState s = BluetoothSimulator.state();
        SimulatedPeripheral p = s.getPeripheral(address);
        if (p == null) return null;
        SimulatedService svc = p.findService(service);
        if (svc == null) return null;
        return svc.findCharacteristic(characteristic);
    }

    private boolean errorAsync(final String operation, final String error, final String message) {
        SimulatorState s = BluetoothSimulator.state();
        s.schedule(() -> BluetoothCallbackRegistry.sendResult(operation,
                JsonBuilder.start()
                        .put("error", error)
                        .put("message", message == null ? "" : message)
                        .end(), false));
        return true;
    }

    private boolean failIfPresent(String operation) {
        SimulatorState.Failure f = BluetoothSimulator.state().consumeFailure(operation);
        if (f == null) return false;
        errorAsync(operation, f.error, f.message);
        return true;
    }

    private static String buildDiscoveredJson(SimulatedPeripheral p) {
        StringBuilder services = new StringBuilder("[");
        boolean firstS = true;
        for (SimulatedService svc : p.getServices()) {
            if (!firstS) services.append(',');
            firstS = false;
            services.append(JsonBuilder.start()
                    .put("service", svc.getUuid())
                    .putRaw("characteristics", buildCharacteristicsArray(svc))
                    .end());
        }
        services.append(']');
        return JsonBuilder.start()
                .put("status", "discovered")
                .put("address", p.getAddress())
                .put("name", p.getName())
                .putRaw("services", services.toString())
                .end();
    }

    private static String buildCharacteristicsArray(SimulatedService svc) {
        StringBuilder arr = new StringBuilder("[");
        boolean firstC = true;
        for (SimulatedCharacteristic ch : svc.getCharacteristics()) {
            if (!firstC) arr.append(',');
            firstC = false;
            StringBuilder props = new StringBuilder("[");
            boolean firstP = true;
            for (String p : ch.getProperties()) {
                if (!firstP) props.append(',');
                firstP = false;
                props.append('"').append(p).append('"');
            }
            props.append(']');
            arr.append(JsonBuilder.start()
                    .put("characteristic", ch.getUuid())
                    .putRaw("properties", props.toString())
                    .end());
        }
        arr.append(']');
        return arr.toString();
    }

    private void checkBluetoothUsageDescription() {
        if (!bluetoothUsageDescriptionChecked) {
            bluetoothUsageDescriptionChecked = true;
            Map<String, String> m = Display.getInstance().getProjectBuildHints();
            if (m != null) {
                if (!m.containsKey("ios.NSBluetoothPeripheralUsageDescription")) {
                    Display.getInstance().setProjectBuildHint("ios.NSBluetoothPeripheralUsageDescription", "Some functionality of the application requires Bluetooth functionality");
                }
                if (!m.containsKey("ios.NSBluetoothAlwaysUsageDescription")) {
                    Display.getInstance().setProjectBuildHint("ios.NSBluetoothAlwaysUsageDescription", "Some functionality of the application requires Bluetooth functionality");
                }
            }
        }
    }

    private void installBuildHints() {
        try {
            checkBluetoothUsageDescription();
        } catch (Throwable t) {
            // Display may not be initialized when called from non-CN1 contexts
            // (eg pure JUnit). Fall through silently — the build hints are only
            // needed for actual native builds, not for simulator/test runs.
        }
    }

    @SuppressWarnings("unused")
    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
