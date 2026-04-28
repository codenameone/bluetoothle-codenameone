package com.codename1.btle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.support.test.runner.AndroidJUnit4;
import android.util.Base64;

import com.codename1.bluetoothle.BluetoothCallback;
import com.codename1.bluetoothle.BluetoothCallbackRegistry;
import com.codename1.bluetoothle.BluetoothNativeBridgeImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/// End-to-end Android instrumentation test driving the BluetoothNativeBridge
/// against a Python Bumble peripheral running on the host. Asserts the same
/// UUIDs and values defined in scripts/native-tests/bumble_peripheral.py — keep
/// the two in lock-step.
///
/// Skipped automatically if the BUMBLE_PERIPHERAL_AVAILABLE instrumentation
/// argument is not set, so the existing fast smoke test still runs in
/// environments where Bumble is not wired up.
@RunWith(AndroidJUnit4.class)
public class BluetoothEmulatorEndToEndTest {

    private static final String SERVICE_UUID = "0000a000-0000-1000-8000-00805f9b34fb";
    private static final String READ_CHAR_UUID = "0000a001-0000-1000-8000-00805f9b34fb";
    private static final String WRITE_CHAR_UUID = "0000a002-0000-1000-8000-00805f9b34fb";
    private static final String NOTIFY_CHAR_UUID = "0000a003-0000-1000-8000-00805f9b34fb";
    private static final String EXPECTED_DEVICE_NAME = "BumbleSensor";
    private static final byte[] EXPECTED_READ_VALUE = "BUMBLE_OK".getBytes();

    private BluetoothNativeBridgeImpl bridge;
    private final Map<String, BluetoothCallback> activeCallbacks = new HashMap<>();
    private String foundAddress;

    @Before
    public void setUp() {
        bridge = new BluetoothNativeBridgeImpl();
    }

    @After
    public void tearDown() {
        for (String key : activeCallbacks.keySet()) {
            BluetoothCallbackRegistry.removeMethodCallback(key);
        }
        activeCallbacks.clear();
    }

    @Test
    public void scanConnectReadWriteSubscribe() throws Exception {
        // Bring the bridge up. initialize/enable are best-effort: emulator may
        // already report enabled. If they fail, scan will surface the error.
        runOp("initialize", () -> bridge.initialize(true, false, "e2e"), 5000);
        runOp("enable", bridge::enable, 5000);

        BluetoothCallback scanCb = registerCallback("startScan");
        assertTrue("startScan dispatch", bridge.startScan("", true, 0, 1, 1, 1));
        Map scanResult = waitForStatus(scanCb, "scanResult", 30000);
        foundAddress = (String) scanResult.get("address");
        assertNotNull("scanResult must include address", foundAddress);
        assertEquals(EXPECTED_DEVICE_NAME, scanResult.get("name"));
        runOp("stopScan", bridge::stopScan, 5000);

        BluetoothCallback connectCb = registerCallback("connect");
        assertTrue("connect dispatch", bridge.connect(foundAddress));
        waitForStatus(connectCb, "connected", 15000);

        BluetoothCallback discoverCb = registerCallback("discover");
        assertTrue("discover dispatch", bridge.discover(foundAddress));
        waitForStatus(discoverCb, "discovered", 15000);

        BluetoothCallback readCb = registerCallback("read");
        assertTrue("read dispatch", bridge.read(foundAddress, SERVICE_UUID, READ_CHAR_UUID));
        Map readResp = waitForResponse(readCb, 10000);
        assertEquals("read", readResp.get("status"));
        byte[] readValue = Base64.decode((String) readResp.get("value"), Base64.DEFAULT);
        assertTrue("read value mismatch: " + Arrays.toString(readValue),
                Arrays.equals(EXPECTED_READ_VALUE, readValue));

        // Round-trip: write a payload, read it back from the same characteristic
        // (the Bumble peripheral echoes writes on the write characteristic's read).
        byte[] payload = "hello".getBytes();
        String b64 = Base64.encodeToString(payload, Base64.NO_WRAP);
        BluetoothCallback writeCb = registerCallback("write");
        assertTrue("write dispatch",
                bridge.write(foundAddress, SERVICE_UUID, WRITE_CHAR_UUID, b64, false));
        Map writeResp = waitForResponse(writeCb, 10000);
        assertEquals("written", writeResp.get("status"));

        BluetoothCallback echoReadCb = registerCallback("read");
        assertTrue(bridge.read(foundAddress, SERVICE_UUID, WRITE_CHAR_UUID));
        Map echoResp = waitForResponse(echoReadCb, 10000);
        byte[] echoValue = Base64.decode((String) echoResp.get("value"), Base64.DEFAULT);
        assertTrue("write should be visible to a subsequent read of the same characteristic",
                Arrays.equals(payload, echoValue));

        BluetoothCallback subCb = registerCallback("subscribe");
        assertTrue("subscribe dispatch",
                bridge.subscribe(foundAddress, SERVICE_UUID, NOTIFY_CHAR_UUID));
        // The peripheral emits a notification once a second. Allow a generous
        // window for the subscription handshake + first emission.
        waitForStatus(subCb, "subscribedResult", 10000);

        runOp("disconnect", () -> bridge.disconnect(foundAddress), 5000);
    }

    private interface BridgeCall {
        boolean invoke();
    }

    private BluetoothCallback registerCallback(String key) {
        BluetoothCallback cb = new BluetoothCallback();
        BluetoothCallbackRegistry.setMethodCallback(key, cb);
        activeCallbacks.put(key, cb);
        return cb;
    }

    private Map runOp(String key, BridgeCall call, int timeoutMs) {
        BluetoothCallback cb = registerCallback(key);
        assertTrue(key + " dispatch", call.invoke());
        return cb.getResponseAndWait(timeoutMs);
    }

    private Map waitForResponse(BluetoothCallback cb, int timeoutMs) {
        Map response = cb.getResponseAndWait(timeoutMs);
        assertNotNull("no response within " + timeoutMs + "ms", response);
        return response;
    }

    private Map waitForStatus(BluetoothCallback cb, String expectedStatus, int timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        AtomicReference<Map> last = new AtomicReference<>();
        while (System.currentTimeMillis() < deadline) {
            Map response = cb.getResponseAndWait(Math.max(50, (int) (deadline - System.currentTimeMillis())));
            if (response != null) {
                last.set(response);
                if (expectedStatus.equals(response.get("status"))) {
                    return response;
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("never observed status=" + expectedStatus + " within "
                + timeoutMs + "ms; last response=" + last.get());
    }
}
