package com.codename1.btle;

import com.codename1.bluetoothle.BluetoothSimulator;
import com.codename1.bluetoothle.BluetoothSimulatorHooks;
import com.codename1.impl.javase.simulator.SimulatorHook;
import com.codename1.impl.javase.simulator.SimulatorHookLoader;
import com.codename1.testing.TestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * End-to-end coverage for the cn1-bluetooth simulator hooks. Exercises:
 *
 * <ul>
 *   <li>the action methods on {@link BluetoothSimulatorHooks} directly,
 *   verifying they mutate {@link BluetoothSimulator} state in the way the
 *   menu would,</li>
 *   <li>that the framework's {@link SimulatorHookLoader} actually picks up
 *   this cn1lib's {@code META-INF/codenameone/simulator-hooks.properties}
 *   from the classpath and resolves each action method.</li>
 * </ul>
 *
 * The first set is what gives the menu items their meaning. The second is
 * what proves the menu would even appear when running BTDemo in the simulator.
 */
public class BluetoothSimulatorHooksTest extends AbstractBluetoothSimulatorTest {

    @Override
    public boolean runTest() throws Exception {
        verifyHooksDiscoveredFromClasspath();
        verifyToggleAdapterFlipsState();
        verifyClearPeripheralsRemovesAll();
        verifyAddDemoPeripheralRegistersPeripheral();
        verifyDisconnectAllClosesActiveConnection();
        verifyPushDemoNotificationDeliversToSubscriber();
        return true;
    }

    private void verifyHooksDiscoveredFromClasspath() {
        List<SimulatorHook> hooks = SimulatorHookLoader.load();

        int bluetoothItems = 0;
        boolean sawToggle = false;
        boolean sawAdd = false;
        boolean sawDisconnect = false;
        boolean sawPush = false;
        boolean sawClear = false;
        for (SimulatorHook h : hooks) {
            if (!"Bluetooth".equals(h.getMenuName())) {
                continue;
            }
            bluetoothItems++;
            String label = h.getLabel();
            if (label.startsWith("Toggle adapter")) sawToggle = true;
            else if (label.startsWith("Add demo")) sawAdd = true;
            else if (label.startsWith("Disconnect all")) sawDisconnect = true;
            else if (label.startsWith("Push demo")) sawPush = true;
            else if (label.startsWith("Clear")) sawClear = true;
            TestUtils.assertNotNull(h.getInvoke(),
                    "Bluetooth hook '" + label + "' has no resolved Runnable");
        }
        TestUtils.assertTrue(bluetoothItems >= 5,
                "Expected at least 5 Bluetooth hooks but loader returned " + bluetoothItems);
        TestUtils.assertTrue(sawToggle, "Toggle adapter hook missing");
        TestUtils.assertTrue(sawAdd, "Add demo peripheral hook missing");
        TestUtils.assertTrue(sawDisconnect, "Disconnect all hook missing");
        TestUtils.assertTrue(sawPush, "Push demo notification hook missing");
        TestUtils.assertTrue(sawClear, "Clear peripherals hook missing");
    }

    private void verifyToggleAdapterFlipsState() {
        BluetoothSimulator.setEnabled(false);
        BluetoothSimulatorHooks.toggleAdapter();
        TestUtils.assertTrue(BluetoothSimulator.isEnabled(), "first toggle should turn adapter ON");
        BluetoothSimulatorHooks.toggleAdapter();
        TestUtils.assertFalse(BluetoothSimulator.isEnabled(), "second toggle should turn adapter OFF");
    }

    private void verifyClearPeripheralsRemovesAll() {
        // prepare() adds the default peripheral, so the simulator starts non-empty.
        TestUtils.assertTrue(BluetoothSimulator.registeredPeripheralCount() >= 1,
                "prepare() should have registered the default peripheral");
        BluetoothSimulatorHooks.clearPeripherals();
        TestUtils.assertEqual(0, BluetoothSimulator.registeredPeripheralCount(),
                "clearPeripherals should leave the simulator empty");
    }

    private void verifyAddDemoPeripheralRegistersPeripheral() {
        BluetoothSimulator.clearPeripherals();
        BluetoothSimulatorHooks.addDemoPeripheral();
        TestUtils.assertTrue(
                BluetoothSimulator.isPeripheralRegistered(BluetoothSimulatorHooks.DEMO_DEVICE_ADDRESS),
                "addDemoPeripheral should register the demo MAC");
        TestUtils.assertEqual(1, BluetoothSimulator.registeredPeripheralCount(),
                "exactly one peripheral after addDemoPeripheral on a cleared simulator");
    }

    private void verifyDisconnectAllClosesActiveConnection() throws Exception {
        initEnabled();
        connectAndDiscover();

        TestUtils.assertTrue(bt.isConnected(DEVICE_ADDRESS),
                "precondition: connectAndDiscover should leave the peripheral connected");

        BluetoothSimulatorHooks.disconnectAll();

        // Disconnect is dispatched asynchronously via the simulator's scheduler;
        // poll isConnected() (which IS synchronous) instead of racing a listener.
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline && bt.isConnected(DEVICE_ADDRESS)) {
            Thread.sleep(20);
        }
        TestUtils.assertFalse(bt.isConnected(DEVICE_ADDRESS),
                "disconnectAll should drop the active connection within 2s");
    }

    private void verifyPushDemoNotificationDeliversToSubscriber() throws Exception {
        // Fresh state: enable, add demo peripheral, connect, discover, subscribe.
        BluetoothSimulator.reset();
        BluetoothSimulator.setCallbackLatencyMillis(2);
        BluetoothSimulator.setHasPermission(true);
        BluetoothSimulator.setEnabled(true);
        BluetoothSimulatorHooks.addDemoPeripheral();
        bt.initialize(true, false, "test");
        if (!bt.isEnabled()) {
            bt.enable();
        }

        final CountDownLatch connected = new CountDownLatch(1);
        bt.connect(evt -> {
            Map m = (Map) evt.getSource();
            if ("connected".equals(m.get("status"))) {
                connected.countDown();
            }
        }, BluetoothSimulatorHooks.DEMO_DEVICE_ADDRESS);
        TestUtils.assertTrue(connected.await(2, TimeUnit.SECONDS), "connect callback should fire");

        final CountDownLatch discovered = new CountDownLatch(1);
        bt.discover(evt -> discovered.countDown(), BluetoothSimulatorHooks.DEMO_DEVICE_ADDRESS);
        TestUtils.assertTrue(discovered.await(2, TimeUnit.SECONDS), "discover callback should fire");

        final CountDownLatch notified = new CountDownLatch(1);
        bt.subscribe(evt -> {
            Map m = (Map) evt.getSource();
            // Subscribe listeners receive both the initial confirm and each
            // subsequent notification; we only count the notification with a
            // value payload.
            if (m.get("value") != null) {
                notified.countDown();
            }
        }, BluetoothSimulatorHooks.DEMO_DEVICE_ADDRESS,
                BluetoothSimulatorHooks.DEMO_SERVICE_UUID,
                BluetoothSimulatorHooks.DEMO_CHAR_NOTIFY_UUID);

        // Give the subscribe handshake a moment to land before pushing.
        Thread.sleep(50);

        BluetoothSimulatorHooks.pushDemoNotification();

        TestUtils.assertTrue(notified.await(2, TimeUnit.SECONDS),
                "pushDemoNotification should deliver a payload to the subscriber");
    }
}
