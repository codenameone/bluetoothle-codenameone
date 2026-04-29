package com.codename1.btle;

import com.codename1.testing.TestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class BluetoothConnectDiscoverTest extends AbstractBluetoothSimulatorTest {

    @Override
    public boolean runTest() throws Exception {
        initEnabled();

        final CountDownLatch connected = new CountDownLatch(1);
        final AtomicReference<Map> connectPayload = new AtomicReference<>();
        bt.connect(evt -> {
            Map m = (Map) evt.getSource();
            if ("connected".equals(m.get("status"))) {
                connectPayload.set(m);
                connected.countDown();
            }
        }, DEVICE_ADDRESS);

        TestUtils.assertTrue(connected.await(2, TimeUnit.SECONDS), "connect listener never fired");
        TestUtils.assertEqual(DEVICE_ADDRESS, connectPayload.get().get("address"));
        TestUtils.assertTrue(bt.isConnected(DEVICE_ADDRESS), "isConnected should be true");
        TestUtils.assertTrue(bt.wasConnected(DEVICE_ADDRESS), "wasConnected should be true");

        final CountDownLatch discovered = new CountDownLatch(1);
        final AtomicReference<Map> discoveredPayload = new AtomicReference<>();
        bt.discover(evt -> {
            discoveredPayload.set((Map) evt.getSource());
            discovered.countDown();
        }, DEVICE_ADDRESS);

        TestUtils.assertTrue(discovered.await(2, TimeUnit.SECONDS), "discover listener never fired");
        TestUtils.assertTrue(bt.isDiscovered(DEVICE_ADDRESS), "isDiscovered should be true");

        Map d = discoveredPayload.get();
        TestUtils.assertEqual("discovered", d.get("status"));
        Object services = d.get("services");
        TestUtils.assertNotNull(services, "discover payload missing services");
        TestUtils.assertTrue(services instanceof List, "services should be a list");
        TestUtils.assertEqual(1, ((List) services).size(), "expected 1 service");
        return true;
    }
}
