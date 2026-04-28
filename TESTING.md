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

## Layer 3 — Real-device Android end-to-end (`device-test.yml`)

The whole point of this layer is to surface regressions in the native
Android `BluetoothLePlugin` code that the simulator (layer 1) cannot see
by definition — the simulator is a model of how the bridge *should*
behave, not a recording of how it actually does. Required job, no
`continue-on-error`.

The job runs against a real Android device (the maintainer's phone). On
each PR/push it:

1. Builds a fresh BTDemo APK with the library version under test.
2. Injects `scripts/device-tests/DeviceTestRunner.java` into the generated
   Android source — a self-contained driver that on activity start brings
   up an in-process BLE peripheral via `BluetoothLeAdvertiser` +
   `BluetoothGattServer` (deterministic UUIDs matching the simulator and
   the bumble peripheral), then drives the cn1-bluetooth public API as a
   central against itself end-to-end (scan / connect / discover / read /
   write / subscribe).
3. Bakes a one-shot GitHub token + the SHA's check-run id into the APK as
   an asset.
4. Creates a `device-test (real-hardware)` check run on the PR's commit
   in `in_progress` state.
5. Uploads the APK as a workflow artifact and posts a PR comment with the
   download link plus an `adb install / am start` snippet.

The maintainer's task on each CI run:
- Download the APK from the workflow run.
- `adb install` and launch (or sideload).
- Grant the Bluetooth permission prompt on first launch.

That's it. The test runs on its own and PATCHes the check run with the
result, which resolves the PR's "device-test (real-hardware)" check
automatically. A watchdog job times the check out after 2h if no device
result is reported, so unrun PRs don't sit pending indefinitely.

### Why not Bumble + netsim?

We tried it (`scripts/native-tests/bumble_peripheral.py` + the Bumble e2e
script + workflow) and proved empirically across 8 iterations that BT
cannot be enabled on GitHub-hosted Linux runners with
`-packet-streamer-endpoint default`, regardless of AVD config (API
33+google_apis and API 34+aosp_atd both fail the same way:
`mEnable=true / state=OFF / "Bluetooth Service not connected"` — the BT
system service binder never binds, regardless of whether Bumble is
involved). The Bumble scripts and instrumentation test stay in the repo
under `scripts/native-tests/` for future use on a self-hosted runner with
real BT or if the emulator+netsim path becomes viable.

## iOS end-to-end coverage

There is no analogue of layer 3 for iOS: the iOS Simulator has no
CoreBluetooth (`CBCentralManager.state` is `unsupported`) and no public hook
for a virtual controller. The iOS smoke test special-cases this. For
real-device behavior coverage, rely on layer 1 (simulator) plus periodic
manual smoke testing on a physical iPhone, or run a single nightly job
against a CI-attached device.
