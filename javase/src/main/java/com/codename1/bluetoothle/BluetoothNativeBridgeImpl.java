package com.codename1.bluetoothle;

/// JavaSE / Codename One simulator implementation of the BLE native bridge.
///
/// Thin dispatcher that picks one of two real implementations at construction
/// time:
///
/// <ul>
///   <li>{@link SimulatorBluetoothBackend} — default; in-memory scriptable
///   peripherals, drivable from tests and the simulator's Bluetooth menu;</li>
///   <li>{@link NativeBleBackend} — talks to real BLE hardware via a
///   bundled Rust helper that wraps the btleplug crate. Supports macOS
///   (CoreBluetooth), Linux (BlueZ) and Windows (WinRT).</li>
/// </ul>
///
/// Selection priority:
/// <ol>
///   <li>System property {@code cn1.bluetoothle.javase.backend} ({@code simulator}
///   or {@code nativeBle});</li>
///   <li>Environment variable {@code CN1_BLUETOOTHLE_BACKEND};</li>
///   <li>Default {@code simulator}.</li>
/// </ol>
///
/// {@code coreBluetooth} is accepted as a backward-compatible alias for
/// {@code nativeBle}. Tests always pin {@code simulator} for predictability.
public class BluetoothNativeBridgeImpl implements BluetoothNativeBridge {

    static final String BACKEND_PROPERTY = "cn1.bluetoothle.javase.backend";
    static final String BACKEND_ENV = "CN1_BLUETOOTHLE_BACKEND";
    static final String BACKEND_SIMULATOR = "simulator";
    static final String BACKEND_NATIVE_BLE = "nativeBle";
    /** Pre-rename alias kept so users with `coreBluetooth` flags don't break. */
    static final String BACKEND_NATIVE_BLE_LEGACY = "coreBluetooth";

    /// Volatile static so a runtime backend swap is visible to every
    /// previously-constructed {@code BluetoothNativeBridgeImpl} instance
    /// (CN1's {@code NativeLookup} caches one). The Bluetooth menu's
    /// "Switch backend" items mutate this through {@link #switchBackend}.
    private static volatile BluetoothNativeBridge currentBackend;

    public BluetoothNativeBridgeImpl() {
        ensureBackend();
    }

    /// Test seam: pin a specific backend instance.
    BluetoothNativeBridgeImpl(BluetoothNativeBridge backend) {
        currentBackend = backend;
    }

    private static synchronized void ensureBackend() {
        if (currentBackend == null) {
            currentBackend = selectBackend();
        }
    }

    static BluetoothNativeBridge selectBackend() {
        String name = System.getProperty(BACKEND_PROPERTY);
        if (name == null || name.isEmpty()) {
            name = System.getenv(BACKEND_ENV);
        }
        if (name == null || name.isEmpty()) {
            name = BACKEND_SIMULATOR;
        }
        return buildBackend(name);
    }

    private static BluetoothNativeBridge buildBackend(String name) {
        boolean wantsNative = BACKEND_NATIVE_BLE.equalsIgnoreCase(name)
                || BACKEND_NATIVE_BLE_LEGACY.equalsIgnoreCase(name);
        if (wantsNative) {
            if (!NativeBleBackend.isAvailable()) {
                System.err.println("BluetoothNativeBridgeImpl: '" + name
                        + "' requested but unavailable (host OS not supported by btleplug or helper not packaged); falling back to simulator");
                return new SimulatorBluetoothBackend();
            }
            return new NativeBleBackend();
        }
        return new SimulatorBluetoothBackend();
    }

    /// Hot-swap to a different backend at runtime. The previous backend's
    /// resources (e.g., {@link NativeBleBackend}'s helper process) are
    /// released. Any cached {@code Bluetooth} instance in the app
    /// immediately sees the new backend on its next call. State carried by
    /// the public {@code Bluetooth} API (initialized / enabled / connections)
    /// resets — callers should re-run {@code bt.initialize()} after switching.
    public static synchronized void switchBackend(String name) {
        BluetoothNativeBridge prev = currentBackend;
        System.setProperty(BACKEND_PROPERTY, name);
        currentBackend = buildBackend(name);
        if (prev != null && prev != currentBackend && prev instanceof NativeBleBackend) {
            ((NativeBleBackend) prev).shutdown();
        }
    }

    /// Which backend is currently active. Reported as the lowercase short
    /// name ({@code simulator} or {@code nativeBle}). Used by the simulator
    /// menu's status indicator and by tests asserting selection.
    public String activeBackendName() {
        ensureBackend();
        return currentBackend instanceof NativeBleBackend ? BACKEND_NATIVE_BLE : BACKEND_SIMULATOR;
    }

    private static BluetoothNativeBridge backend() {
        ensureBackend();
        return currentBackend;
    }

    @Override public boolean isSupported() { return backend().isSupported(); }
    @Override public boolean initialize(boolean request, boolean statusReceiver, String restoreKey) { return backend().initialize(request, statusReceiver, restoreKey); }
    @Override public boolean enable() { return backend().enable(); }
    @Override public boolean disable() { return backend().disable(); }
    @Override public boolean startScan(String servicesJson, boolean allowDuplicates, int scanMode, int matchMode, int matchNum, int callbackType) { return backend().startScan(servicesJson, allowDuplicates, scanMode, matchMode, matchNum, callbackType); }
    @Override public boolean stopScan() { return backend().stopScan(); }
    @Override public boolean retrieveConnected(String servicesJson) { return backend().retrieveConnected(servicesJson); }
    @Override public boolean connect(String address) { return backend().connect(address); }
    @Override public boolean reconnect(String address) { return backend().reconnect(address); }
    @Override public boolean disconnect(String address) { return backend().disconnect(address); }
    @Override public boolean close(String address) { return backend().close(address); }
    @Override public boolean discover(String address) { return backend().discover(address); }
    @Override public boolean services(String address, String servicesJson) { return backend().services(address, servicesJson); }
    @Override public boolean characteristics(String address, String service, String characteristicsJson) { return backend().characteristics(address, service, characteristicsJson); }
    @Override public boolean descriptors(String address, String service, String characteristic) { return backend().descriptors(address, service, characteristic); }
    @Override public boolean read(String address, String service, String characteristic) { return backend().read(address, service, characteristic); }
    @Override public boolean subscribe(String address, String service, String characteristic) { return backend().subscribe(address, service, characteristic); }
    @Override public boolean unsubscribe(String address, String service, String characteristic) { return backend().unsubscribe(address, service, characteristic); }
    @Override public boolean write(String address, String service, String characteristic, String value, boolean noResponse) { return backend().write(address, service, characteristic, value, noResponse); }
    @Override public boolean writeQ(String address, String service, String characteristic, String value, boolean noResponse) { return backend().writeQ(address, service, characteristic, value, noResponse); }
    @Override public boolean readDescriptor(String address, String service, String characteristic, String descriptor) { return backend().readDescriptor(address, service, characteristic, descriptor); }
    @Override public boolean writeDescriptor(String address, String service, String characteristic, String descriptor, String value) { return backend().writeDescriptor(address, service, characteristic, descriptor, value); }
    @Override public boolean rssi(String address) { return backend().rssi(address); }
    @Override public boolean mtu(String address, int mtu) { return backend().mtu(address, mtu); }
    @Override public boolean requestConnectionPriority(String address, String priority) { return backend().requestConnectionPriority(address, priority); }
    @Override public boolean isInitialized() { return backend().isInitialized(); }
    @Override public boolean isEnabled() { return backend().isEnabled(); }
    @Override public boolean isScanning() { return backend().isScanning(); }
    @Override public boolean wasConnected(String address) { return backend().wasConnected(address); }
    @Override public boolean isConnected(String address) { return backend().isConnected(address); }
    @Override public boolean isDiscovered(String address) { return backend().isDiscovered(address); }
    @Override public boolean hasPermission() { return backend().hasPermission(); }
    @Override public boolean requestPermission() { return backend().requestPermission(); }
    @Override public boolean isLocationEnabled() { return backend().isLocationEnabled(); }
    @Override public boolean requestLocation() { return backend().requestLocation(); }
}
