package com.codename1.btle;

import com.codename1.bluetoothle.Bluetooth;
import com.codename1.testing.TestUtils;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class BluetoothScanTest extends AbstractBluetoothSimulatorTest {

    @Override
    public boolean runTest() throws Exception {
        initEnabled();

        final CountDownLatch sawResult = new CountDownLatch(1);
        final AtomicReference<Map> seenResult = new AtomicReference<>();

        bt.startScan(evt -> {
            Map m = (Map) evt.getSource();
            if ("scanResult".equals(m.get("status"))) {
                seenResult.compareAndSet(null, m);
                sawResult.countDown();
            }
        }, null, true,
                Bluetooth.SCAN_MODE_LOW_POWER,
                Bluetooth.MATCH_MODE_STICKY,
                Bluetooth.MATCH_NUM_MAX_ADVERTISEMENT,
                Bluetooth.CALLBACK_TYPE_ALL_MATCHES);

        TestUtils.assertTrue(sawResult.await(2, TimeUnit.SECONDS),
                "scan listener never received scanResult");

        Map result = seenResult.get();
        TestUtils.assertNotNull(result, "scanResult payload missing");
        TestUtils.assertEqual(DEVICE_ADDRESS, result.get("address"));
        TestUtils.assertEqual(DEVICE_NAME, result.get("name"));

        TestUtils.assertTrue(bt.isScanning(), "isScanning should be true mid-scan");
        bt.stopScan();
        Thread.sleep(50);
        TestUtils.assertFalse(bt.isScanning(), "isScanning false after stopScan");
        return true;
    }
}
