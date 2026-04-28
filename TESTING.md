# Bluetooth Test Strategy

This project is exercised in CI at three levels of fidelity. Each catches a
different class of bug; together they let regressions be caught without
manually debugging against physical devices.

Workflow file: `.github/workflows/native-bluetooth-tests.yml`

## Layer 1 — Simulator behavior (`simulator-tests` job)

Hardware-free behavioral coverage of the public `Bluetooth` API. Runs on every
push and PR; this is the workhorse and gates most regressions before the
heavier native jobs even start.

- JavaSE port `BluetoothNativeBridgeImpl` is a real, scriptable simulator
  (peripherals, services, characteristics, scripted errors, queued
  notifications). State is configured through the public
  `com.codename1.bluetoothle.BluetoothSimulator` facade.
- Tests live under `BTDemo/src/test/java/com/codename1/btle/` and implement
  `com.codename1.testing.UnitTest` (via `AbstractTest`). They run inside a
  real Codename One Display so the listener path
  (`Display.callSerially(...)`), the blocking path
  (`callback.getResponseAndWait(...)`), and JSON shapes are all exercised
  exactly as a real app would on a device.
- Driven from CI by `mvn -pl BTDemo cn1:test -Dcodename1.platform=javase`,
  which runs `com.codename1.impl.javase.TestRunner` and emits JUnit XML
  reports under `BTDemo/target/cn1-reports`.

What this catches:

- API-surface regressions (parameter coercion, JSON serialization,
  base64 encoding, callback wiring, registry leaks, status payload shape).
- Listener vs blocking-call timing bugs.
- Error-path handling.

What this does **not** catch:

- Platform-specific GATT quirks.
- Real Bluetooth-stack timing.

The same simulator is wired into the JavaSE port the Codename One simulator
loads, so running `BTDemo` in the simulator now exercises real
scan/connect/discover/read/write flows against a virtual peripheral. To
register your own peripherals from app code:

```java
BluetoothSimulator.addPeripheral(
    new SimulatedPeripheral("AA:BB:CC:DD:EE:01", "Sensor")
        .withRssi(-55)
        .withService(new SimulatedService("0000180a-0000-1000-8000-00805f9b34fb")
            .withCharacteristic(new SimulatedCharacteristic("00002a29-0000-1000-8000-00805f9b34fb")
                .withProperty(SimulatedCharacteristic.PROPERTY_READ)
                .withValue("hello"))));
```

## Layer 2 — Native bridge dispatch smoke (`ios-native-tests`, `android-native-tests`)

Generates the iOS / Android native projects from `BTDemo` using the Codename
One Maven target, injects an XCTest / instrumentation test, and verifies the
native bridge is linked and dispatches into the platform plugin. iOS uses the
simulator runtime; Android uses an emulator.

### iOS native tests — `scripts/native-tests/run-ios-native-tests.sh`

Injected XCTest class: `BTDemoBluetoothNativeTests`

1. `testBluetoothPluginClassIsLinked` — verifies `BluetoothLePlugin` is linked
   into the generated app target.
2. `testNativeBridgeDispatchesLibraryActions` — instantiates
   `com_codename1_bluetoothle_BluetoothNativeBridgeImpl`, calls
   `isInitialized()`, `isEnabled()`, `isScanning()` on the main thread.
3. `testCoreBluetoothInitializes` — confirms CoreBluetooth is functional in
   the simulator runtime.

### Android native tests — `scripts/native-tests/run-android-native-tests.sh`

Injected instrumentation class: `BluetoothNativeInstrumentationTest`

1. `bluetoothStackIsAvailable` — `BluetoothManager`, `BluetoothAdapter`, and
   `FEATURE_BLUETOOTH_LE` all present.
2. `nativeBridgeInvokesLibraryActions` — bridge dispatch into
   `BluetoothLePlugin` produces the expected callback payloads
   (`isInitialized`, `isEnabled`, `isScanning`).

What this layer catches:

- Native source generation breakage after Maven changes.
- Linker / packaging regressions.
- Bridge dispatch + callback registry round-trip on the real platform stack.

What this layer does **not** catch:

- End-to-end scan/connect/read/write against a real or virtual peripheral.

## Layer 3 — Android end-to-end with Bumble virtual peripheral (`android-bumble-e2e-tests`)

The whole point of this layer is to surface regressions in the native
Android `BluetoothLePlugin` code that the simulator (layer 1) cannot see
by definition — the simulator is a model of how the bridge *should*
behave, not a recording of how it actually does. Required job, no
`continue-on-error`. On failure the job uploads `adb logcat`, the Bumble
peripheral log, and the Android test report as artifacts under
`bumble-e2e-diagnostics`.

A Python [Bumble](https://google.github.io/bumble/) peripheral
(`scripts/native-tests/bumble_peripheral.py`) attaches to the emulator's
virtual BT controller via the `android-netsim` transport and advertises a
deterministic GATT layout. The instrumentation test
(`scripts/native-tests/BluetoothEmulatorEndToEndTest.java`, injected into the
generated Android project when `BUMBLE_PERIPHERAL=1`) drives
`BluetoothNativeBridgeImpl` through the full lifecycle: scan → connect →
discover → read → write → round-trip read → subscribe → notification.

The wrapper `scripts/native-tests/run-android-bumble-e2e.sh` boots the
peripheral, waits for it to advertise, then defers to the standard
`run-android-native-tests.sh` with `BUMBLE_PERIPHERAL=1` so the e2e test class
is injected alongside the smoke test.

The CI emulator is started with `-packet-streamer-endpoint default` so the
modern netsim virtual radio backend is enabled.

What this layer catches:

- Real GATT timing on the Android stack.
- End-to-end value round-trips against a known-shaped peripheral.
- Subscription/notification delivery.

## iOS end-to-end coverage

There is no analogue of layer 3 for iOS: the iOS Simulator has no
CoreBluetooth (`CBCentralManager.state` is `unsupported`) and no public hook
for a virtual controller. The iOS smoke test special-cases this. For
real-device behavior coverage, rely on layer 1 (simulator) plus periodic
manual smoke testing on a physical iPhone, or run a single nightly job
against a CI-attached device.
