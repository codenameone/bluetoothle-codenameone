package com.codename1.btle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Base64;

import com.codename1.bluetoothle.BluetoothCallback;
import com.codename1.bluetoothle.BluetoothCallbackRegistry;
import com.codename1.bluetoothle.BluetoothNativeBridgeImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/// End-to-end Android instrumentation test driving the BluetoothNativeBridge
/// against a Python Bumble peripheral running on the host. Asserts the same
/// UUIDs and values defined in scripts/native-tests/bumble_peripheral.py — keep
/// the two in lock-step.
///
/// Source level: the generated Android project compiles with `-source 7`, so
/// no lambdas / method references / try-with-resources etc. Match the style
/// of the existing smoke instrumentation test.
@RunWith(AndroidJUnit4.class)
public class BluetoothEmulatorEndToEndTest {

    private static final String SERVICE_UUID = "0000a000-0000-1000-8000-00805f9b34fb";
    private static final String READ_CHAR_UUID = "0000a001-0000-1000-8000-00805f9b34fb";
    private static final String WRITE_CHAR_UUID = "0000a002-0000-1000-8000-00805f9b34fb";
    private static final String NOTIFY_CHAR_UUID = "0000a003-0000-1000-8000-00805f9b34fb";
    private static final String EXPECTED_DEVICE_NAME = "BumbleSensor";
    private static final byte[] EXPECTED_READ_VALUE = "BUMBLE_OK".getBytes();

    private BluetoothNativeBridgeImpl bridge;
    private final List<String> activeCallbacks = new ArrayList<String>();
    private Activity launchedActivity;
    private String foundAddress;

    @Before
    public void setUp() throws Exception {
        // The Android plugin's initializeAction calls bridge.getActivity()
        // (-> AndroidNativeUtil.getActivity()) which returns null until a CN1
        // activity has been started, causing an NPE on its first
        // getSystemService() call. Launch the BTDemo stub here so the test
        // exercises the same activity-attached code path real users hit.
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context targetContext = instrumentation.getTargetContext();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(targetContext, "com.codename1.btle.BTDemoStub");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        launchedActivity = instrumentation.startActivitySync(intent);
        assertNotNull("BTDemoStub activity must launch", launchedActivity);
        // Give the CN1 framework a beat to initialize Display / register the
        // activity in AndroidNativeUtil before we hit the bridge.
        Thread.sleep(1500);

        bridge = new BluetoothNativeBridgeImpl();
    }

    @After
    public void tearDown() {
        for (String key : activeCallbacks) {
            BluetoothCallbackRegistry.removeMethodCallback(key);
        }
        activeCallbacks.clear();
        if (launchedActivity != null) {
            launchedActivity.finish();
        }
    }

    @Test
    public void scanConnectReadWriteSubscribe() throws Exception {
        // Bring the bridge up. initialize/enable are best-effort: emulator may
        // already report enabled. If they fail, scan will surface the error.
        BluetoothCallback initCb = registerCallback("initialize");
        assertTrue("initialize dispatch", bridge.initialize(true, false, "e2e"));
        initCb.getResponseAndWait(5000);

        BluetoothCallback enableCb = registerCallback("enable");
        assertTrue("enable dispatch", bridge.enable());
        enableCb.getResponseAndWait(5000);

        BluetoothCallback scanCb = registerCallback("startScan");
        assertTrue("startScan dispatch", bridge.startScan("", true, 0, 1, 1, 1));
        Map scanResult = waitForStatus(scanCb, "scanResult", 30000);
        foundAddress = (String) scanResult.get("address");
        assertNotNull("scanResult must include address", foundAddress);
        assertEquals(EXPECTED_DEVICE_NAME, scanResult.get("name"));

        BluetoothCallback stopCb = registerCallback("stopScan");
        assertTrue("stopScan dispatch", bridge.stopScan());
        stopCb.getResponseAndWait(5000);

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

        BluetoothCallback disconnectCb = registerCallback("disconnect");
        assertTrue("disconnect dispatch", bridge.disconnect(foundAddress));
        disconnectCb.getResponseAndWait(5000);
    }

    private BluetoothCallback registerCallback(String key) {
        BluetoothCallback cb = new BluetoothCallback();
        BluetoothCallbackRegistry.setMethodCallback(key, cb);
        activeCallbacks.add(key);
        return cb;
    }

    private Map waitForResponse(BluetoothCallback cb, int timeoutMs) {
        Map response = cb.getResponseAndWait(timeoutMs);
        assertNotNull("no response within " + timeoutMs + "ms", response);
        return response;
    }

    private Map waitForStatus(BluetoothCallback cb, String expectedStatus, int timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        Map last = null;
        while (System.currentTimeMillis() < deadline) {
            int wait = (int) Math.max(50, deadline - System.currentTimeMillis());
            Map response = cb.getResponseAndWait(wait);
            if (response != null) {
                last = response;
                if (expectedStatus.equals(response.get("status"))) {
                    return response;
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("never observed status=" + expectedStatus + " within "
                + timeoutMs + "ms; last response=" + last);
    }
}
