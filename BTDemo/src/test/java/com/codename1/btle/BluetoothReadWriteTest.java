package com.codename1.btle;

import com.codename1.testing.TestUtils;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class BluetoothReadWriteTest extends AbstractBluetoothSimulatorTest {

    @Override
    public boolean runTest() throws Exception {
        initEnabled();
        connectAndDiscover();

        final AtomicReference<Map> readPayload = new AtomicReference<>();
        final CountDownLatch readDone = new CountDownLatch(1);
        bt.read(evt -> {
            readPayload.set((Map) evt.getSource());
            readDone.countDown();
        }, DEVICE_ADDRESS, SERVICE_UUID, CHAR_READ_UUID);
        TestUtils.assertTrue(readDone.await(2, TimeUnit.SECONDS), "read listener never fired");
        Map r = readPayload.get();
        TestUtils.assertEqual("read", r.get("status"));
        String base64Value = (String) r.get("value");
        TestUtils.assertNotNull(base64Value, "read returned no value");
        // Initial value: 0x42 0x43 0x44 == "BCD"
        TestUtils.assertEqual("QkNE", base64Value, "read should return seeded value");

        final AtomicReference<Map> writePayload = new AtomicReference<>();
        final CountDownLatch writeDone = new CountDownLatch(1);
        // base64("HEY") = "SEVZ"
        bt.write(evt -> {
            writePayload.set((Map) evt.getSource());
            writeDone.countDown();
        }, DEVICE_ADDRESS, SERVICE_UUID, CHAR_WRITE_UUID, "SEVZ", false);
        TestUtils.assertTrue(writeDone.await(2, TimeUnit.SECONDS), "write listener never fired");
        TestUtils.assertEqual("written", writePayload.get().get("status"));

        // Round-trip: write to the read characteristic, then read back.
        final CountDownLatch w2 = new CountDownLatch(1);
        bt.write(evt -> w2.countDown(),
                DEVICE_ADDRESS, SERVICE_UUID, CHAR_READ_UUID, "U0VOVA==", false);
        TestUtils.assertTrue(w2.await(2, TimeUnit.SECONDS));

        final AtomicReference<Map> r2 = new AtomicReference<>();
        final CountDownLatch r2Done = new CountDownLatch(1);
        bt.read(evt -> {
            r2.set((Map) evt.getSource());
            r2Done.countDown();
        }, DEVICE_ADDRESS, SERVICE_UUID, CHAR_READ_UUID);
        TestUtils.assertTrue(r2Done.await(2, TimeUnit.SECONDS));
        TestUtils.assertEqual("U0VOVA==", r2.get().get("value"),
                "write should be visible to a subsequent read");
        return true;
    }
}
