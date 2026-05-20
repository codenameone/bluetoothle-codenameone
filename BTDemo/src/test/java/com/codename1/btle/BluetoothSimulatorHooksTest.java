package com.codename1.btle;

import com.codename1.bluetoothle.Bluetooth;
import com.codename1.bluetoothle.BluetoothSimulator;
import com.codename1.testing.TestUtils;
import com.codename1.ui.CN;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Exercises the cn1-bluetooth simulator hooks through the cross-platform
 * {@code CN.execute("bluetooth:itemN")} URL-style entry point — i.e., the
 * same way any CN1 UnitTest living in a cn1-bluetooth-using app's
 * {@code common/} project would drive them. The test imports nothing from
 * the JavaSE port and does no reflection; if it ever needs to reach into
 * {@code com.codename1.impl.javase.*} that's a regression.
 *
 * <p>Item indices correspond to the position in
 * {@code simulator-hooks.properties}:
 * <pre>
 *   item1 = toggleAdapter
 *   item2 = addDemoPeripheral
 *   item3 = disconnectAll
 *   item4 = pushDemoNotification
 *   item5 = clearPeripherals
 *   item6 = switchToNativeBle    (UI-only here)
 *   item7 = switchToSimulator    (UI-only here)
 *   item8 = primeReadFailure     (API-only — no menu label)
 * </pre>
 */
public class BluetoothSimulatorHooksTest extends AbstractBluetoothSimulatorTest {

    @Override
    public boolean runTest() throws Exception {
        verifyHooksAreRegisteredOnSimulator();
        verifyToggleAdapterFlipsState();
        verifyClearPeripheralsRemovesAll();
        verifyAddDemoPeripheralRegistersPeripheral();
        verifyDisconnectAllClosesActiveConnection();
        verifyPushDemoNotificationDeliversToSubscriber();
        verifyApiOnlyHookPrimesScriptedFailure();
        return true;
    }

    /**
     * Sanity check: CN.canExecute reports our hook urls as executable
     * (only true inside the simulator). On Android/iOS this would return
     * something other than TRUE and the CN1 test harness short-circuits
     * the test infrastructure long before reaching here, but the assertion
     * still guards against framework regressions.
     */
    private void verifyHooksAreRegisteredOnSimulator() {
        TestUtils.assertTrue(Boolean.TRUE.equals(CN.canExecute("bluetooth:item1")),
                "bluetooth:item1 must be registered by the cn1lib's simulator-hooks.properties");
        TestUtils.assertTrue(Boolean.TRUE.equals(CN.canExecute("bluetooth:item8")),
                "label-less hook bluetooth:item8 (primeReadFailure) must also be canExecute=true");
    }

    private void verifyToggleAdapterFlipsState() {
        BluetoothSimulator.setEnabled(false);
        CN.execute("bluetooth:item1"); // toggleAdapter
        TestUtils.assertTrue(BluetoothSimulator.isEnabled(), "first toggle should turn adapter ON");
        CN.execute("bluetooth:item1");
        TestUtils.assertFalse(BluetoothSimulator.isEnabled(), "second toggle should turn adapter OFF");
    }

    private void verifyClearPeripheralsRemovesAll() {
        // prepare() registers the default peripheral, so the simulator
        // starts non-empty.
        TestUtils.assertTrue(BluetoothSimulator.registeredPeripheralCount() >= 1,
                "prepare() should have registered the default peripheral");
        CN.execute("bluetooth:item5"); // clearPeripherals
        TestUtils.assertEqual(0, BluetoothSimulator.registeredPeripheralCount(),
                "clearPeripherals should leave the simulator empty");
    }

    private void verifyAddDemoPeripheralRegistersPeripheral() {
        BluetoothSimulator.clearPeripherals();
        CN.execute("bluetooth:item2"); // addDemoPeripheral
        TestUtils.assertTrue(
                BluetoothSimulator.isPeripheralRegistered(DEVICE_ADDRESS),
                "addDemoPeripheral should register the demo MAC");
        TestUtils.assertEqual(1, BluetoothSimulator.registeredPeripheralCount(),
                "exactly one peripheral after addDemoPeripheral on a cleared simulator");
    }

    private void verifyDisconnectAllClosesActiveConnection() throws Exception {
        initEnabled();
        connectAndDiscover();

        TestUtils.assertTrue(bt.isConnected(DEVICE_ADDRESS),
                "precondition: connectAndDiscover should leave the peripheral connected");

        CN.execute("bluetooth:item3"); // disconnectAll

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
        CN.execute("bluetooth:item2"); // addDemoPeripheral
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
        }, DEVICE_ADDRESS);
        TestUtils.assertTrue(connected.await(2, TimeUnit.SECONDS), "connect callback should fire");

        final CountDownLatch discovered = new CountDownLatch(1);
        bt.discover(evt -> discovered.countDown(), DEVICE_ADDRESS);
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
        }, DEVICE_ADDRESS, SERVICE_UUID, CHAR_NOTIFY_UUID);

        // Give the subscribe handshake a moment to land before pushing.
        Thread.sleep(50);

        CN.execute("bluetooth:item4"); // pushDemoNotification

        TestUtils.assertTrue(notified.await(2, TimeUnit.SECONDS),
                "pushDemoNotification should deliver a payload to the subscriber");
    }

    /**
     * Covers the label-less hook path. {@code item8} is declared in
     * {@code simulator-hooks.properties} without a {@code label8}, so it's
     * invisible in the menu but still callable via {@code CN.execute}.
     * After it fires, the next read against the demo peripheral's read
     * characteristic surfaces a scripted error to the Bluetooth API listener.
     */
    private void verifyApiOnlyHookPrimesScriptedFailure() throws Exception {
        BluetoothSimulator.reset();
        BluetoothSimulator.setCallbackLatencyMillis(2);
        BluetoothSimulator.setHasPermission(true);
        BluetoothSimulator.setEnabled(true);
        CN.execute("bluetooth:item2"); // addDemoPeripheral

        Bluetooth bt = new Bluetooth();
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
        }, DEVICE_ADDRESS);
        TestUtils.assertTrue(connected.await(2, TimeUnit.SECONDS));

        // Prime via the label-less API hook (item8).
        CN.execute("bluetooth:item8");

        final CountDownLatch readDone = new CountDownLatch(1);
        final AtomicReference<String> errorRef = new AtomicReference<>();
        bt.read(evt -> {
            Map m = (Map) evt.getSource();
            Object err = m.get("error");
            if (err != null) {
                errorRef.set(err.toString());
            }
            readDone.countDown();
        }, DEVICE_ADDRESS, SERVICE_UUID, CHAR_READ_UUID);

        TestUtils.assertTrue(readDone.await(2, TimeUnit.SECONDS),
                "read callback should fire even on scripted failure");
        TestUtils.assertNotNull(errorRef.get(),
                "primed read should produce an error payload");
    }
}
