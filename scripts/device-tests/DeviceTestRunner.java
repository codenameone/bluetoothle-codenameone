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
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
        if (Build.VERSION.SDK_INT >= 31) {
            return new String[] {
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
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
                PrintWriter log = new PrintWriter(buf);
                try {
                    summary = executeTests(activity, log);
                    ok = !summary.startsWith("FAIL:");
                } catch (Throwable err) {
                    err.printStackTrace(log);
                    summary = "FAIL: unexpected " + err.getClass().getSimpleName() + ": " + err.getMessage();
                    ok = false;
                }
                log.flush();
                postResult(activity, ok, summary + "\n\n```\n" + buf.toString() + "```");
                final boolean finalOk = ok;
                final String finalSummary = summary;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(activity,
                                (finalOk ? "PASS: " : "FAIL: ") + finalSummary,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "device-test-runner").start();
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

    private static String drivCentral(Activity activity, Peripheral peripheral, PrintWriter log) throws Exception {
        Bluetooth bt = new Bluetooth();

        BluetoothCallback initCb = new BluetoothCallback();
        BluetoothCallbackRegistry.setMethodCallback("initialize", initCb);
        if (!bt.initialize(false, false, "device-test")) {
            return "FAIL: bt.initialize dispatch returned false";
        }
        Map initResp = initCb.getResponseAndWait(5000);
        if (initResp == null) return "FAIL: initialize callback never fired";
        if (!"enabled".equals(initResp.get("status"))) {
            return "FAIL: initialize returned status=" + initResp.get("status") + " (expected enabled)";
        }
        log.println("initialize OK");

        // Scan until we see ourselves.
        final AtomicReference<String> foundAddr = new AtomicReference<String>();
        final CountDownLatch scanLatch = new CountDownLatch(1);
        bt.startScan(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                Map m = (Map) evt.getSource();
                if (!"scanResult".equals(m.get("status"))) return;
                if (!EXPECTED_DEVICE_NAME.equals(m.get("name"))) return;
                if (foundAddr.compareAndSet(null, (String) m.get("address"))) {
                    scanLatch.countDown();
                }
            }
        }, null, true,
                Bluetooth.SCAN_MODE_LOW_LATENCY,
                Bluetooth.MATCH_MODE_AGGRESSIVE,
                Bluetooth.MATCH_NUM_MAX_ADVERTISEMENT,
                Bluetooth.CALLBACK_TYPE_ALL_MATCHES);
        if (!scanLatch.await(30, TimeUnit.SECONDS)) {
            return "FAIL: scan never saw the in-process peripheral within 30s";
        }
        bt.stopScan();
        String address = foundAddr.get();
        log.println("scan saw " + address);

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

    private static void postResult(Context ctx, boolean ok, String summary) {
        Properties cfg = loadConfig(ctx);
        if (cfg == null) {
            Log.w(TAG, "no config; cannot report result");
            return;
        }
        String token = cfg.getProperty("github_token");
        String repo = cfg.getProperty("repo");
        String checkRunId = cfg.getProperty("check_run_id");
        if (token == null || repo == null || checkRunId == null) {
            Log.w(TAG, "incomplete config; cannot report result");
            return;
        }
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
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("PATCH");
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
            Log.i(TAG, "GitHub check-run update returned " + code);
            conn.disconnect();
        } catch (IOException ex) {
            Log.e(TAG, "failed to report result to GitHub", ex);
        }
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
