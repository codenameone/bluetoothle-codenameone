package com.codename1.btle;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.codename1.bluetoothle.Bluetooth;
import com.codename1.bluetoothle.BluetoothCallback;
import com.codename1.bluetoothle.BluetoothCallbackRegistry;
import com.codename1.ui.events.ActionEvent;
import com.codename1.ui.events.ActionListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/// On-device end-to-end test driver for the cn1-bluetooth library.
///
/// Boots a real BLE peripheral in-process via `BluetoothLeAdvertiser` +
/// `BluetoothGattServer` with the deterministic GATT layout the simulator
/// also uses, then drives the actual `com.codename1.bluetoothle.Bluetooth`
/// public API as a BLE central against itself end-to-end. On completion
/// posts a GitHub Check Run update so CI can resolve the PR check.
///
/// Active only when assets/device_test_config.properties is present in the
/// APK — i.e. only in CI-built test APKs. Normal user builds of BTDemo are
/// unaffected.
public class DeviceTestRunner {

    private static final String TAG = "DeviceTestRunner";

    private static final String CONFIG_ASSET = "device_test_config.properties";
    private static final int PERMISSION_REQUEST_CODE = 0xBE57;

    // Match scripts/native-tests/bumble_peripheral.py and the JavaSE
    // simulator's AbstractBluetoothSimulatorTest peripheral. Keep these
    // three sources in lock-step so regressions in any layer are
    // comparable.
    private static final UUID SERVICE_UUID = UUID.fromString("0000a000-0000-1000-8000-00805f9b34fb");
    private static final UUID READ_CHAR_UUID = UUID.fromString("0000a001-0000-1000-8000-00805f9b34fb");
    private static final UUID WRITE_CHAR_UUID = UUID.fromString("0000a002-0000-1000-8000-00805f9b34fb");
    private static final UUID NOTIFY_CHAR_UUID = UUID.fromString("0000a003-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final byte[] EXPECTED_READ_VALUE = "BUMBLE_OK".getBytes();
    private static final String EXPECTED_DEVICE_NAME = "BumbleSensor";

    private static volatile boolean started;

    public static boolean isTestMode(Context ctx) {
        try {
            ctx.getAssets().open(CONFIG_ASSET).close();
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    public static synchronized void start(Activity activity) {
        if (started) {
            return;
        }
        started = true;
        if (!hasAllPermissions(activity)) {
            requestPermissions(activity);
            return;
        }
        runOnBackground(activity);
    }

    public static void onPermissionsResult(Activity activity, int requestCode,
                                           String[] permissions, int[] grantResults) {
        if (requestCode != PERMISSION_REQUEST_CODE) {
            return;
        }
        for (int r : grantResults) {
            if (r != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Required permission denied; aborting on-device test");
                postResult(activity, false, "Required Bluetooth permissions denied. Re-launch and grant.");
                return;
            }
        }
        runOnBackground(activity);
    }

    private static String[] requiredPermissions() {
        // Use literal strings rather than Manifest.permission.* constants:
        // BLUETOOTH_CONNECT/SCAN/ADVERTISE are API 31+ symbols and CN1's
        // generated project compiles against compileSdkVersion 30, where
        // those identifiers do not exist. The literal values are stable
        // and the runtime is always >= the device's actual API level.
        if (Build.VERSION.SDK_INT >= 31) {
            return new String[] {
                    "android.permission.BLUETOOTH_CONNECT",
                    "android.permission.BLUETOOTH_SCAN",
                    "android.permission.BLUETOOTH_ADVERTISE",
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }
        return new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION
        };
    }

    private static boolean hasAllPermissions(Context ctx) {
        for (String p : requiredPermissions()) {
            if (ctx.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private static void requestPermissions(Activity activity) {
        activity.requestPermissions(requiredPermissions(), PERMISSION_REQUEST_CODE);
    }

    private static void runOnBackground(final Activity activity) {
        Toast.makeText(activity, "On-device cn1-bluetooth test running…", Toast.LENGTH_LONG).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String summary;
                boolean ok;
                StringWriter buf = new StringWriter();
                // Tee every line to logcat under tag "DeviceTestRunner" so
                // `adb logcat -s DeviceTestRunner:*` (or piping that to a
                // file) gives the maintainer the full log even if the
                // POST-back to GitHub fails.
                PrintWriter log = new PrintWriter(new TeeWriter(buf));
                try {
                    summary = executeTests(activity, log);
                    ok = !summary.startsWith("FAIL:");
                } catch (Throwable err) {
                    err.printStackTrace(log);
                    summary = "FAIL: unexpected " + err.getClass().getSimpleName() + ": " + err.getMessage();
                    ok = false;
                }
                log.flush();

                String fullLog = buf.toString();
                Log.i(TAG, "test summary: " + summary);

                String reportStatus = postResult(activity, ok, summary + "\n\n```\n" + fullLog + "```");
                Log.i(TAG, "report-back to GitHub: " + reportStatus);

                showResultDialog(activity, ok, summary, fullLog, reportStatus);
            }
        }, "device-test-runner").start();
    }

    private static void showResultDialog(final Activity activity, final boolean ok,
                                         final String summary, final String fullLog,
                                         final String reportStatus) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String header = (ok ? "PASS" : "FAIL") + " — " + summary
                        + "\n\nReport back: " + reportStatus
                        + "\n\nFor a copyable transcript: adb logcat -s DeviceTestRunner:*\n\n";
                TextView text = new TextView(activity);
                text.setTextSize(12);
                text.setPadding(24, 24, 24, 24);
                text.setMovementMethod(new ScrollingMovementMethod());
                text.setGravity(Gravity.TOP);
                // Long-press to select & copy the transcript. Without this,
                // there's no way to share the on-device log when the
                // GitHub POST-back fails.
                text.setTextIsSelectable(true);
                text.setText(header + fullLog);
                // Also drop the transcript into the clipboard immediately
                // so the maintainer can paste it without scrolling.
                try {
                    android.content.ClipboardManager cm =
                            (android.content.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText(
                                "device-test transcript", header + fullLog));
                    }
                } catch (Throwable ignore) {
                }
                ScrollView scroll = new ScrollView(activity);
                scroll.addView(text);

                new AlertDialog.Builder(activity)
                        .setTitle(ok ? "Device test PASS" : "Device test FAIL")
                        .setView(scroll)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    /// Forwards everything written to the underlying writer to logcat as
    /// well, line by line, under tag DeviceTestRunner. Lets the maintainer
    /// pipe the on-device log back through `adb logcat` even when the
    /// HTTPS POST to GitHub fails.
    private static class TeeWriter extends java.io.Writer {
        private final java.io.Writer delegate;
        private final StringBuilder line = new StringBuilder();

        TeeWriter(java.io.Writer delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            delegate.write(cbuf, off, len);
            for (int i = off; i < off + len; i++) {
                char c = cbuf[i];
                if (c == '\n') {
                    Log.i(TAG, line.toString());
                    line.setLength(0);
                } else if (c != '\r') {
                    line.append(c);
                }
            }
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
            if (line.length() > 0) {
                Log.i(TAG, line.toString());
                line.setLength(0);
            }
        }

        @Override
        public void close() throws IOException {
            flush();
            delegate.close();
        }
    }

    private static String executeTests(Activity activity, PrintWriter log) throws Exception {
        log.println("=== cn1-bluetooth on-device test ===");
        log.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ", API " + Build.VERSION.SDK_INT + ")");

        BluetoothManager bm = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bm.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            return "FAIL: Bluetooth must be enabled on the device before running this test";
        }
        BluetoothLeAdvertiser advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            return "FAIL: device does not support BLE advertising";
        }

        Peripheral peripheral = new Peripheral(activity, bm, adapter, advertiser, log);
        try {
            peripheral.start();
            log.println("In-process peripheral advertising as " + EXPECTED_DEVICE_NAME);

            return drivCentral(activity, peripheral, log);
        } finally {
            peripheral.stop();
        }
    }

    private static String drivCentral(Activity activity, Peripheral peripheral, final PrintWriter log) throws Exception {
        Bluetooth bt = new Bluetooth();

        // initialize is a *blocking* method on Bluetooth: it registers its
        // own internal BluetoothCallback under "initialize" in the
        // registry, calls the bridge, and waits for the callback. Manually
        // pre-registering another callback under the same key (like an
        // earlier version of this test did) loses to the internal one and
        // never fires. Trust the boolean return.
        if (!bt.initialize(false, false, "device-test")) {
            return "FAIL: bt.initialize returned false (BT not enabled, or platform unsupported)";
        }
        log.println("initialize OK");

        // Scan until we see ourselves. Log every advertisement we observe
        // so the on-device transcript distinguishes "scan returns nothing
        // at all" (library / radio bug) from "scan sees other devices but
        // not the in-process peripheral" (well-known same-radio
        // scan-to-self limitation on Samsung and similar BT stacks).
        final AtomicReference<String> foundAddr = new AtomicReference<String>();
        final CountDownLatch scanLatch = new CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicInteger sawAny = new java.util.concurrent.atomic.AtomicInteger();
        bt.startScan(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                Map m = (Map) evt.getSource();
                if (!"scanResult".equals(m.get("status"))) return;
                int n = sawAny.incrementAndGet();
                if (n <= 50) {
                    // Cap log volume; first 50 advertisements is plenty to
                    // confirm scan is alive and to spot our peripheral.
                    log.println("  scan #" + n + " name=" + m.get("name")
                            + " address=" + m.get("address")
                            + " rssi=" + m.get("rssi"));
                }
                if (EXPECTED_DEVICE_NAME.equals(m.get("name"))) {
                    if (foundAddr.compareAndSet(null, (String) m.get("address"))) {
                        scanLatch.countDown();
                    }
                }
            }
        }, null, true,
                Bluetooth.SCAN_MODE_LOW_LATENCY,
                Bluetooth.MATCH_MODE_AGGRESSIVE,
                Bluetooth.MATCH_NUM_MAX_ADVERTISEMENT,
                Bluetooth.CALLBACK_TYPE_ALL_MATCHES);
        if (!scanLatch.await(30, TimeUnit.SECONDS)) {
            int seen = sawAny.get();
            bt.stopScan();
            if (seen == 0) {
                return "FAIL: scan returned NO advertisements at all in 30s — likely a library / radio issue, not just same-device scan-to-self";
            }
            return "FAIL: scan saw " + seen + " advertisements but never one named " + EXPECTED_DEVICE_NAME
                    + " — likely Android same-radio scan-to-self limitation on this device";
        }
        bt.stopScan();
        String address = foundAddr.get();
        log.println("scan saw " + address + " (after " + sawAny.get() + " total scanResult events)");

        // connect
        final CountDownLatch connectLatch = new CountDownLatch(1);
        final AtomicReference<String> connectStatus = new AtomicReference<String>();
        bt.connect(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                Map m = (Map) evt.getSource();
                String s = (String) m.get("status");
                if ("connected".equals(s) && connectStatus.compareAndSet(null, s)) {
                    connectLatch.countDown();
                }
            }
        }, address);
        if (!connectLatch.await(15, TimeUnit.SECONDS)) {
            return "FAIL: connect never reported status=connected within 15s";
        }
        log.println("connect OK");

        // discover
        final CountDownLatch discoverLatch = new CountDownLatch(1);
        bt.discover(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                discoverLatch.countDown();
            }
        }, address);
        if (!discoverLatch.await(15, TimeUnit.SECONDS)) {
            return "FAIL: discover never fired within 15s";
        }
        log.println("discover OK");

        // read
        final CountDownLatch readLatch = new CountDownLatch(1);
        final AtomicReference<Map> readResp = new AtomicReference<Map>();
        bt.read(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                readResp.set((Map) evt.getSource());
                readLatch.countDown();
            }
        }, address, SERVICE_UUID.toString(), READ_CHAR_UUID.toString());
        if (!readLatch.await(10, TimeUnit.SECONDS)) {
            return "FAIL: read never fired within 10s";
        }
        Map r = readResp.get();
        String b64 = (String) r.get("value");
        byte[] readBytes = decodeBase64(b64);
        if (!java.util.Arrays.equals(EXPECTED_READ_VALUE, readBytes)) {
            return "FAIL: read returned " + java.util.Arrays.toString(readBytes)
                    + " (expected " + java.util.Arrays.toString(EXPECTED_READ_VALUE) + ")";
        }
        log.println("read OK");

        // write
        final CountDownLatch writeLatch = new CountDownLatch(1);
        bt.write(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                writeLatch.countDown();
            }
        }, address, SERVICE_UUID.toString(), WRITE_CHAR_UUID.toString(),
                encodeBase64("hello".getBytes()), false);
        if (!writeLatch.await(10, TimeUnit.SECONDS)) {
            return "FAIL: write never fired within 10s";
        }
        log.println("write OK");

        // subscribe + receive a notification
        final CountDownLatch subscribed = new CountDownLatch(1);
        final CountDownLatch notified = new CountDownLatch(1);
        bt.subscribe(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                Map m = (Map) evt.getSource();
                String s = (String) m.get("status");
                if ("subscribed".equals(s)) {
                    subscribed.countDown();
                } else if ("subscribedResult".equals(s)) {
                    notified.countDown();
                }
            }
        }, address, SERVICE_UUID.toString(), NOTIFY_CHAR_UUID.toString());
        if (!subscribed.await(10, TimeUnit.SECONDS)) {
            return "FAIL: subscribe ack never fired within 10s";
        }
        // peripheral fires notifications after subscribe via CCCD write.
        peripheral.fireNotificationSoon();
        if (!notified.await(10, TimeUnit.SECONDS)) {
            return "FAIL: subscribed notification never delivered within 10s";
        }
        log.println("subscribe+notify OK");

        bt.disconnect(address);
        return "PASS: scan/connect/discover/read/write/subscribe round-trip OK";
    }

    private static String encodeBase64(byte[] data) {
        return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
    }

    private static byte[] decodeBase64(String s) {
        if (s == null) return new byte[0];
        return android.util.Base64.decode(s, android.util.Base64.DEFAULT);
    }

    /// Returns a human-readable status string describing how the POST went.
    /// Always returns something — never throws — so the caller can include
    /// it in the on-device result dialog regardless of whether GitHub
    /// accepted the update.
    private static String postResult(Context ctx, boolean ok, String summary) {
        Properties cfg = loadConfig(ctx);
        if (cfg == null) {
            return "no config asset (test mode mis-injected?)";
        }
        String token = cfg.getProperty("github_token");
        String repo = cfg.getProperty("repo");
        String checkRunId = cfg.getProperty("check_run_id");
        if (token == null || repo == null || checkRunId == null) {
            return "incomplete config (token/repo/check_run_id missing)";
        }
        // Some HTTP clients don't allow PATCH on Android's HttpURLConnection
        // — Android's HTTP/1.1 stack does, but older versions disallow
        // PATCH. Fall back to X-HTTP-Method-Override.
        String url = "https://api.github.com/repos/" + repo + "/check-runs/" + checkRunId;
        String safeSummary = summary == null ? "" : summary;
        if (safeSummary.length() > 60_000) {
            safeSummary = safeSummary.substring(0, 60_000) + "\n\n[truncated]";
        }
        String json = "{"
                + "\"status\":\"completed\","
                + "\"conclusion\":\"" + (ok ? "success" : "failure") + "\","
                + "\"output\":{"
                + "\"title\":\"On-device cn1-bluetooth test\","
                + "\"summary\":" + jsonEscape(safeSummary)
                + "}"
                + "}";
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            try {
                conn.setRequestMethod("PATCH");
            } catch (java.net.ProtocolException pe) {
                conn.setRequestMethod("POST");
                conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            }
            conn.setDoOutput(true);
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            conn.setRequestProperty("Content-Type", "application/json");
            OutputStream out = conn.getOutputStream();
            out.write(json.getBytes("UTF-8"));
            out.close();
            int code = conn.getResponseCode();
            String body = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            String tail = body.length() > 400 ? body.substring(0, 400) + "…" : body;
            String status = "HTTP " + code + " " + (code >= 200 && code < 300 ? "OK" : "FAIL")
                    + (tail.isEmpty() ? "" : (" body=" + tail));
            Log.i(TAG, "GitHub check-run update " + status);
            return status;
        } catch (Throwable ex) {
            String msg = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            Log.e(TAG, "failed to report result to GitHub: " + msg, ex);
            return "request threw " + msg;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readStream(java.io.InputStream in) {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[2048];
        try {
            int n;
            while ((n = in.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, "UTF-8"));
            }
        } catch (IOException ignore) {
        } finally {
            try { in.close(); } catch (IOException ignore) {}
        }
        return sb.toString();
    }

    private static Properties loadConfig(Context ctx) {
        Properties p = new Properties();
        InputStream in = null;
        try {
            in = ctx.getAssets().open(CONFIG_ASSET);
            p.load(in);
            return p;
        } catch (IOException ex) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (IOException ignore) {}
        }
    }

    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static class Peripheral {
        private final Activity activity;
        private final BluetoothManager manager;
        private final BluetoothAdapter adapter;
        private final BluetoothLeAdvertiser advertiser;
        private final PrintWriter log;
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private BluetoothGattServer server;
        private final List<BluetoothDevice> subscribers = new ArrayList<BluetoothDevice>();
        private BluetoothGattCharacteristic notifyChar;
        private final AdvertiseCallback advCb = new AdvertiseCallback() {
            @Override
            public void onStartFailure(int errorCode) {
                log.println("advertise start failed: " + errorCode);
            }
        };

        Peripheral(Activity activity, BluetoothManager manager, BluetoothAdapter adapter,
                   BluetoothLeAdvertiser advertiser, PrintWriter log) {
            this.activity = activity;
            this.manager = manager;
            this.adapter = adapter;
            this.advertiser = advertiser;
            this.log = log;
        }

        void start() throws Exception {
            // Set the local name so scans see EXPECTED_DEVICE_NAME. Setting
            // the BT adapter name globally is the only knob non-system apps
            // have for this; Bumble's "BumbleSensor" doubles as our local
            // name for parity with the simulator + bumble layers.
            adapter.setName(EXPECTED_DEVICE_NAME);

            BluetoothGattService svc = new BluetoothGattService(SERVICE_UUID,
                    BluetoothGattService.SERVICE_TYPE_PRIMARY);

            BluetoothGattCharacteristic readCh = new BluetoothGattCharacteristic(
                    READ_CHAR_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ);
            readCh.setValue(EXPECTED_READ_VALUE);
            svc.addCharacteristic(readCh);

            BluetoothGattCharacteristic writeCh = new BluetoothGattCharacteristic(
                    WRITE_CHAR_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE
                            | BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_WRITE
                            | BluetoothGattCharacteristic.PERMISSION_READ);
            svc.addCharacteristic(writeCh);

            notifyChar = new BluetoothGattCharacteristic(
                    NOTIFY_CHAR_UUID,
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ);
            BluetoothGattDescriptor cccd = new BluetoothGattDescriptor(CCCD_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ
                            | BluetoothGattDescriptor.PERMISSION_WRITE);
            notifyChar.addDescriptor(cccd);
            svc.addCharacteristic(notifyChar);

            server = manager.openGattServer(activity, new BluetoothGattServerCallback() {
                @Override
                public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                    log.println("server: connection " + device.getAddress() + " state=" + newState);
                }

                @Override
                public void onCharacteristicReadRequest(BluetoothDevice device, int requestId,
                                                        int offset, BluetoothGattCharacteristic ch) {
                    byte[] value = ch.getValue();
                    if (value == null) value = new byte[0];
                    server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                }

                @Override
                public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                         BluetoothGattCharacteristic ch,
                                                         boolean preparedWrite, boolean responseNeeded,
                                                         int offset, byte[] value) {
                    ch.setValue(value);
                    if (responseNeeded) {
                        server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                    }
                }

                @Override
                public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                                     BluetoothGattDescriptor descriptor,
                                                     boolean preparedWrite, boolean responseNeeded,
                                                     int offset, byte[] value) {
                    if (CCCD_UUID.equals(descriptor.getUuid())) {
                        synchronized (subscribers) {
                            if (!subscribers.contains(device)) {
                                subscribers.add(device);
                            }
                        }
                    }
                    if (responseNeeded) {
                        server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                    }
                }
            });
            server.addService(svc);

            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .build();
            AdvertiseData data = new AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .addServiceUuid(new android.os.ParcelUuid(SERVICE_UUID))
                    .build();
            advertiser.startAdvertising(settings, data, advCb);
        }

        void fireNotificationSoon() {
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    fireNotification();
                }
            }, 250);
        }

        private void fireNotification() {
            byte[] payload = "PING".getBytes();
            notifyChar.setValue(payload);
            List<BluetoothDevice> snapshot;
            synchronized (subscribers) {
                snapshot = new ArrayList<BluetoothDevice>(subscribers);
            }
            for (BluetoothDevice d : snapshot) {
                try {
                    server.notifyCharacteristicChanged(d, notifyChar, false);
                } catch (Throwable t) {
                    log.println("notify failed: " + t);
                }
            }
        }

        void stop() {
            try { advertiser.stopAdvertising(advCb); } catch (Throwable ignore) {}
            try { if (server != null) server.close(); } catch (Throwable ignore) {}
        }
    }
}
