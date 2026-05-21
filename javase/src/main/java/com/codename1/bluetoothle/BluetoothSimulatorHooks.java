/*
 * Copyright (c) Codename One 2008-2026 - GPL v2 with Classpath Exception.
 */
package com.codename1.bluetoothle;

import com.codename1.components.ToastBar;
import com.codename1.ui.Display;
import java.util.Map;

/**
 * Static actions wired to the simulator menu via
 * {@code META-INF/codenameone/simulator-hooks.properties}. Each method:
 *
 * <ul>
 *   <li>is invoked on the CN1 EDT by the framework's
 *   {@code SimulatorHookLoader}, so it may call CN1 UI APIs directly;</li>
 *   <li>mutates the shared {@link BluetoothSimulator} state so a running
 *   {@code BTDemo} (or any app under test) observes the change immediately;</li>
 *   <li>is callable from unit tests without going through the menu —
 *   the action is the API, the menu is just one way to trigger it.</li>
 * </ul>
 *
 * The "demo" peripheral mirrors the one used by
 * {@code AbstractBluetoothSimulatorTest} so the manual simulator UX exposes
 * the same shape developers see in tests.
 */
public final class BluetoothSimulatorHooks {

    public static final String DEMO_DEVICE_ADDRESS = "AA:BB:CC:DD:EE:01";
    public static final String DEMO_DEVICE_NAME = "SimulatedSensor";
    public static final String DEMO_SERVICE_UUID = "0000180a-0000-1000-8000-00805f9b34fb";
    public static final String DEMO_CHAR_READ_UUID = "00002a29-0000-1000-8000-00805f9b34fb";
    public static final String DEMO_CHAR_WRITE_UUID = "00002a30-0000-1000-8000-00805f9b34fb";
    public static final String DEMO_CHAR_NOTIFY_UUID = "00002a31-0000-1000-8000-00805f9b34fb";
    public static final String DEMO_CCCD_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    private BluetoothSimulatorHooks() {}

    /// Switch the JavaSE bridge to the real BLE backend (Rust + btleplug:
    /// CoreBluetooth/BlueZ/WinRT). Falls back to the simulator backend with
    /// a warning toast if the host OS or packaged helper isn't available.
    /// After switching, the public {@code Bluetooth} API resets — call
    /// {@code bt.initialize()} again.
    public static void switchToNativeBle() {
        if (!NativeBleBackend.isAvailable()) {
            toast("Native BLE unavailable: helper not packaged for " + System.getProperty("os.name"));
            return;
        }
        BluetoothNativeBridgeImpl.switchBackend(BluetoothNativeBridgeImpl.BACKEND_NATIVE_BLE);
        toast("Switched to native BLE backend — call Initialize again");
    }

    /// Switch the JavaSE bridge back to the in-memory simulator backend.
    /// Useful for returning to scripted tests after exercising real hardware.
    public static void switchToSimulator() {
        BluetoothNativeBridgeImpl.switchBackend(BluetoothNativeBridgeImpl.BACKEND_SIMULATOR);
        toast("Switched to simulator backend — call Initialize again");
    }

    /** Flips the simulated adapter between enabled and disabled. */
    public static void toggleAdapter() {
        boolean next = !BluetoothSimulator.isEnabled();
        BluetoothSimulator.setEnabled(next);
        toast("Bluetooth adapter " + (next ? "ON" : "OFF"));
    }

    /**
     * Adds (or replaces) the standard demo peripheral. Same UUIDs and
     * properties as the AbstractBluetoothSimulatorTest fixture so what you
     * scan in BTDemo matches what tests assert against.
     */
    public static void addDemoPeripheral() {
        BluetoothSimulator.addPeripheral(buildDemoPeripheral());
        toast("Added demo peripheral " + DEMO_DEVICE_NAME);
    }

    /** Removes every registered peripheral. */
    public static void clearPeripherals() {
        BluetoothSimulator.clearPeripherals();
        toast("Cleared peripherals");
    }

    /** Disconnects every currently-connected peripheral as if the device went away. */
    public static void disconnectAll() {
        Map<String, SimulatorState.ConnectionState> connections =
                BluetoothSimulator.state().snapshotConnections();
        int count = 0;
        for (String address : connections.keySet()) {
            BluetoothSimulator.disconnectFromRemote(address);
            count++;
        }
        toast("Disconnected " + count + " peripheral" + (count == 1 ? "" : "s"));
    }

    /**
     * Primes the simulator to fail the next read against the demo
     * characteristic with a scripted "read" error. API-only hook (no menu
     * label); used by CN1 UnitTests via
     * {@code CN.executeHook("bluetooth:primeReadFailure")} to verify the
     * cn1lib's error-propagation path without manipulating internal state
     * from common/ test code.
     */
    public static void primeReadFailure() {
        BluetoothSimulator.failNext("read", "read", "primed by test hook");
    }

    /**
     * Pushes a single notification on the demo peripheral's notify
     * characteristic. The byte value is a 1-byte rolling counter so repeated
     * triggers produce visibly different payloads on the receiving side.
     */
    public static void pushDemoNotification() {
        byte[] payload = new byte[]{(byte) (System.currentTimeMillis() & 0xFF)};
        BluetoothSimulator.pushNotification(
                DEMO_DEVICE_ADDRESS, DEMO_SERVICE_UUID, DEMO_CHAR_NOTIFY_UUID, payload);
        toast("Pushed notification to " + DEMO_DEVICE_NAME);
    }

    static SimulatedPeripheral buildDemoPeripheral() {
        return new SimulatedPeripheral(DEMO_DEVICE_ADDRESS, DEMO_DEVICE_NAME)
                .withRssi(-55)
                .withService(new SimulatedService(DEMO_SERVICE_UUID)
                        .withCharacteristic(new SimulatedCharacteristic(DEMO_CHAR_READ_UUID)
                                .withProperty(SimulatedCharacteristic.PROPERTY_READ)
                                .withValue(new byte[]{0x42, 0x43, 0x44}))
                        .withCharacteristic(new SimulatedCharacteristic(DEMO_CHAR_WRITE_UUID)
                                .withProperty(SimulatedCharacteristic.PROPERTY_WRITE))
                        .withCharacteristic(new SimulatedCharacteristic(DEMO_CHAR_NOTIFY_UUID)
                                .withProperty(SimulatedCharacteristic.PROPERTY_NOTIFY)
                                .withDescriptor(DEMO_CCCD_DESCRIPTOR_UUID, new byte[]{0, 0})));
    }

    /**
     * Best-effort user feedback. Swallowed if no Display is available (e.g.,
     * inside a JUnit unit test that calls a hook method directly).
     */
    private static void toast(String message) {
        try {
            if (Display.isInitialized()) {
                ToastBar.showInfoMessage(message);
            }
        } catch (Throwable ignored) {
        }
    }
}
