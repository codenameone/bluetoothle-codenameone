package com.codename1.btle;

import com.codename1.bluetoothle.BluetoothSimulator;
import com.codename1.testing.TestUtils;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class BluetoothErrorPathTest extends AbstractBluetoothSimulatorTest {

    @Override
    public boolean runTest() throws Exception {
        initEnabled();
        connectAndDiscover();

        BluetoothSimulator.failNext("read", "readError", "Characteristic read failed");

        final AtomicReference<Map> errPayload = new AtomicReference<>();
        final CountDownLatch failed = new CountDownLatch(1);
        bt.read(evt -> {
            errPayload.set((Map) evt.getSource());
            failed.countDown();
        }, DEVICE_ADDRESS, SERVICE_UUID, CHAR_READ_UUID);

        TestUtils.assertTrue(failed.await(2, TimeUnit.SECONDS),
                "scripted read failure should fire the listener");
        Map err = errPayload.get();
        TestUtils.assertEqual("readError", err.get("error"));
        TestUtils.assertEqual("Characteristic read failed", err.get("message"));

        // After consuming the scripted failure the next read should succeed.
        final CountDownLatch ok = new CountDownLatch(1);
        final AtomicReference<Map> okPayload = new AtomicReference<>();
        bt.read(evt -> {
            okPayload.set((Map) evt.getSource());
            ok.countDown();
        }, DEVICE_ADDRESS, SERVICE_UUID, CHAR_READ_UUID);
        TestUtils.assertTrue(ok.await(2, TimeUnit.SECONDS),
                "subsequent read should not be affected by previous scripted failure");
        TestUtils.assertEqual("read", okPayload.get().get("status"));

        // Calling connect on an unknown device should also surface as an error to the listener.
        BluetoothSimulator.clearPeripherals();
        final CountDownLatch unknown = new CountDownLatch(1);
        final AtomicReference<Map> unknownPayload = new AtomicReference<>();
        bt.connect(evt -> {
            unknownPayload.set((Map) evt.getSource());
            unknown.countDown();
        }, "FF:FF:FF:FF:FF:FF");
        TestUtils.assertTrue(unknown.await(2, TimeUnit.SECONDS),
                "connect to unknown device should fire listener with error");
        TestUtils.assertEqual("neverConnected", unknownPayload.get().get("error"));
        return true;
    }
}
