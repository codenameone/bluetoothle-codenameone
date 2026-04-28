package com.codename1.bluetoothle;

/// Static configuration facade for the JavaSE Bluetooth LE simulator.
///
/// The simulator backs the JavaSE [BluetoothNativeBridgeImpl] so that the
/// public [Bluetooth] API can be exercised end-to-end inside the Codename One
/// simulator and from CN1 unit tests, without any real Bluetooth hardware.
///
/// Default state when the bridge is constructed:
/// - permission granted, location enabled, Bluetooth off
/// - no advertised peripherals
/// - 10ms async callback latency
///
/// Tests typically:
/// 1. Call [reset()] in setup.
/// 2. Add peripherals with [addPeripheral(SimulatedPeripheral)].
/// 3. Drive the public [Bluetooth] API.
/// 4. Optionally use [pushNotification] to fire subscription updates or
///    [failNext] to script errors.
public final class BluetoothSimulator {

    private static volatile SimulatorState state = new SimulatorState();

    private BluetoothSimulator() {
    }

    static SimulatorState state() {
        return state;
    }

    /// Resets all simulator state: clears peripherals, scripted errors, queued
    /// notifications, callback latency, and the enabled/permission flags back
    /// to their defaults. Always call this between tests.
    public static void reset() {
        state = new SimulatorState();
    }

    public static void addPeripheral(SimulatedPeripheral peripheral) {
        state.addPeripheral(peripheral);
    }

    public static void removePeripheral(String address) {
        state.removePeripheral(address);
    }

    public static void clearPeripherals() {
        state.clearPeripherals();
    }

    /// When true, the simulator behaves as if the user has already toggled
    /// Bluetooth on. When false, [Bluetooth#isEnabled()] returns false and
    /// most operations fail with an `isDisabled` error until [Bluetooth#enable()]
    /// is called.
    public static void setEnabled(boolean enabled) {
        state.setEnabled(enabled);
    }

    public static boolean isEnabled() {
        return state.isEnabled();
    }

    public static void setHasPermission(boolean hasPermission) {
        state.setHasPermission(hasPermission);
    }

    public static void setLocationEnabled(boolean locationEnabled) {
        state.setLocationEnabled(locationEnabled);
    }

    /// Configures the latency, in milliseconds, before each callback is
    /// dispatched. Defaults to 10ms which matches typical native bridge
    /// timing well enough that race conditions in client code show up.
    public static void setCallbackLatencyMillis(int millis) {
        state.setCallbackLatencyMillis(millis);
    }

    /// Scripts the next call to a given operation key (eg "connect", "read",
    /// "subscribe") to fail with the given error code and message.
    /// Used to exercise client error-handling paths.
    public static void failNext(String operation, String errorCode, String message) {
        state.queueFailure(operation, errorCode, message);
    }

    /// Pushes a notification to a previously-subscribed characteristic.
    /// Has no effect if the client is not subscribed.
    public static void pushNotification(String address, String service, String characteristic, byte[] value) {
        state.pushNotification(address, service, characteristic, value);
    }

    /// Disconnects an already-connected peripheral and dispatches a
    /// `disconnected` callback to the original `connect` listener.
    public static void disconnectFromRemote(String address) {
        state.disconnectFromRemote(address);
    }
}
