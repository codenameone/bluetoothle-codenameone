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
 * {@link CN#executeHook} entry point — i.e., the same way a CN1 UnitTest
 * living in a cn1-bluetooth-using app's {@code common/} project would
 * drive them. The test imports nothing from the JavaSE port; if the
 * file ever compiled against {@code com.codename1.impl.javase.*} that
 * would be a regression.
 *
 * <p>Each hook covered here also has a corresponding manual menu item
 * the developer can click, except {@code primeReadFailure}, which is
 * declared label-less in {@code simulator-hooks.properties} (an API-only
 * hook). That last test pins both the label-less branch of the
 * framework loader and the failure-priming code path of the lib.</p>
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
     * Sanity check: the framework's hook registry sees the cn1lib's
     * properties file. Off-simulator (Android/iOS/JavaScript), this
     * would return false and {@code AbstractTest} infrastructure has
     * already short-circuited the run; here we run inside the JavaSE
     * simulator so the hooks must be present.
     */
    private void verifyHooksAreRegisteredOnSimulator() {
        TestUtils.assertTrue(CN.executeHook("bluetooth:toggleAdapter"),
                "bluetooth:toggleAdapter must be registered by the cn1lib's simulator-hooks.properties");
        // Restore state — the toggle above flipped enabled. Tests below
        // start from a clean adapter via initEnabled() if they need it.
        BluetoothSimulator.setEnabled(false);
    }

    private void verifyToggleAdapterFlipsState() {
        BluetoothSimulator.setEnabled(false);
        TestUtils.assertTrue(CN.executeHook("bluetooth:toggleAdapter"));
        TestUtils.assertTrue(BluetoothSimulator.isEnabled(), "first toggle should turn adapter ON");
        TestUtils.assertTrue(CN.executeHook("bluetooth:toggleAdapter"));
        TestUtils.assertFalse(BluetoothSimulator.isEnabled(), "second toggle should turn adapter OFF");
    }

    private void verifyClearPeripheralsRemovesAll() {
        // prepare() registers the default peripheral, so the simulator
        // starts non-empty.
        TestUtils.assertTrue(BluetoothSimulator.registeredPeripheralCount() >= 1,
                "prepare() should have registered the default peripheral");
        TestUtils.assertTrue(CN.executeHook("bluetooth:clearPeripherals"));
        TestUtils.assertEqual(0, BluetoothSimulator.registeredPeripheralCount(),
                "clearPeripherals should leave the simulator empty");
    }

    private void verifyAddDemoPeripheralRegistersPeripheral() {
        BluetoothSimulator.clearPeripherals();
        TestUtils.assertTrue(CN.executeHook("bluetooth:addDemoPeripheral"));
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

        TestUtils.assertTrue(CN.executeHook("bluetooth:disconnectAll"));

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
        TestUtils.assertTrue(CN.executeHook("bluetooth:addDemoPeripheral"));
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

        TestUtils.assertTrue(CN.executeHook("bluetooth:pushDemoNotification"));

        TestUtils.assertTrue(notified.await(2, TimeUnit.SECONDS),
                "pushDemoNotification should deliver a payload to the subscriber");
    }

    /**
     * Covers the label-less hook path. {@code bluetooth:primeReadFailure}
     * is declared in {@code simulator-hooks.properties} without a label,
     * so it's invisible in the menu but still callable from tests via
     * CN.executeHook. After it fires, the next read against the demo
     * peripheral's read characteristic surfaces a scripted error to
     * the Bluetooth API listener.
     */
    private void verifyApiOnlyHookPrimesScriptedFailure() throws Exception {
        BluetoothSimulator.reset();
        BluetoothSimulator.setCallbackLatencyMillis(2);
        BluetoothSimulator.setHasPermission(true);
        BluetoothSimulator.setEnabled(true);
        TestUtils.assertTrue(CN.executeHook("bluetooth:addDemoPeripheral"));

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

        // Prime via the label-less API hook.
        TestUtils.assertTrue(CN.executeHook("bluetooth:primeReadFailure"),
                "primeReadFailure must be callable even without a menu label");

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
