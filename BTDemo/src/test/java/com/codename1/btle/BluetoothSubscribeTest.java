package com.codename1.btle;

import com.codename1.bluetoothle.BluetoothSimulator;
import com.codename1.testing.TestUtils;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class BluetoothSubscribeTest extends AbstractBluetoothSimulatorTest {

    @Override
    public boolean runTest() throws Exception {
        initEnabled();
        connectAndDiscover();

        final CountDownLatch subscribed = new CountDownLatch(1);
        final CountDownLatch notified = new CountDownLatch(1);
        final AtomicReference<Map> notifyPayload = new AtomicReference<>();

        bt.subscribe(evt -> {
            Map m = (Map) evt.getSource();
            String status = (String) m.get("status");
            if ("subscribed".equals(status)) {
                subscribed.countDown();
            } else if ("subscribedResult".equals(status)) {
                notifyPayload.compareAndSet(null, m);
                notified.countDown();
            }
        }, DEVICE_ADDRESS, SERVICE_UUID, CHAR_NOTIFY_UUID);

        TestUtils.assertTrue(subscribed.await(2, TimeUnit.SECONDS),
                "subscribe ack never fired");

        // Push a notification from "the peripheral".
        // base64("PING") = "UElORw=="
        BluetoothSimulator.pushNotification(DEVICE_ADDRESS, SERVICE_UUID, CHAR_NOTIFY_UUID,
                new byte[]{'P', 'I', 'N', 'G'});

        TestUtils.assertTrue(notified.await(2, TimeUnit.SECONDS),
                "notification listener never fired");
        TestUtils.assertEqual("UElORw==", notifyPayload.get().get("value"));

        final CountDownLatch unsub = new CountDownLatch(1);
        bt.unsubscribe(evt -> unsub.countDown(),
                DEVICE_ADDRESS, SERVICE_UUID, CHAR_NOTIFY_UUID);
        TestUtils.assertTrue(unsub.await(2, TimeUnit.SECONDS));
        return true;
    }
}
