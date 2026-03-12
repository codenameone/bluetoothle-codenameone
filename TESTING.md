# Native Bluetooth Test Logic

This project runs native Bluetooth verification in CI using generated native projects for iOS and Android.

## CI Workflow

Workflow file: `.github/workflows/native-bluetooth-tests.yml`

Jobs:
- `ios-native-tests` (macOS)
- `android-native-tests` (Linux + emulator)

Both jobs:
- Build/install this library locally first (including platform-specific artifacts)
- Generate native source from `BTDemo` using Codename One Maven target
- Inject native tests into generated native project
- Run native test runner (`xcodebuild test` or `connectedDebugAndroidTest`)

## What Is Actually Exercised

### iOS Native Tests

Script: `scripts/native-tests/run-ios-native-tests.sh`

Injected XCTest class: `BTDemoBluetoothNativeTests`

Tests executed:
1. `testBluetoothPluginClassIsLinked`
- Verifies Objective-C plugin class `BluetoothLePlugin` is linked into generated app target.

2. `testNativeBridgeDispatchesLibraryActions`
- Instantiates `com_codename1_bluetoothle_BluetoothNativeBridgeImpl`.
- Calls direct native bridge methods that dispatch into library plugin actions:
  - `isInitialized()`
  - `isEnabled()`
  - `isScanning()`
  - `isConnected(address)`
  - `isDiscovered(address)`
  - `startScan(...)`
  - `stopScan()`
  - `connect(address)`
  - `disconnect(address)`
  - `read(address, service, characteristic)`
  - `write(address, service, characteristic, value, noResponse)`
  - `subscribe(address, service, characteristic)`
  - `unsubscribe(address, service, characteristic)`
- This validates dispatch from bridge into `BluetoothLePlugin` action handlers.

3. `testCoreBluetoothInitializes`
- Creates `CBCentralManager` and waits for state callback.
- Confirms CoreBluetooth stack is functional in simulator runtime.

### Android Native Tests

Script: `scripts/native-tests/run-android-native-tests.sh`

Injected instrumentation class: `BluetoothNativeInstrumentationTest`

Tests executed:
1. `bluetoothStackIsAvailable`
- Verifies `BluetoothManager` and `BluetoothAdapter` are available.
- Verifies device/emulator advertises BLE feature (`FEATURE_BLUETOOTH_LE`).

2. `nativeBridgeInvokesLibraryActions`
- Instantiates `com.codename1.bluetoothle.BluetoothNativeBridgeImpl`.
- Registers callbacks through `BluetoothCallbackRegistry`.
- Invokes library actions through direct bridge methods:
  - `isInitialized()`
  - `isEnabled()`
  - `isScanning()`
  - `isConnected(address)`
  - `isDiscovered(address)`
  - `startScan(...)`
  - `stopScan()`
  - `connect(address)`
  - `disconnect(address)`
  - `read(...)`
  - `write(...)`
  - `subscribe(...)`
  - `unsubscribe(...)`
- Asserts callback payload contains expected keys:
  - `isInitialized`
  - `isEnabled`
  - `isScanning`
- Unknown-action dispatch is no longer exposed in the public/native bridge contract.

This confirms the Android bridge and plugin action routing execute and produce callback payloads.

## Important Coverage Notes

Current CI validates:
- Native project generation still works after Maven migration.
- Library is linked and dispatch paths are callable on both platforms.
- Basic Bluetooth runtime availability in native environments.

Current CI does **not** fully validate:
- Real BLE device discovery/connect/read/write against physical peripherals.
- End-to-end scan/connect flows with deterministic external hardware assertions.

Those require dedicated hardware-in-the-loop tests and stable peripheral fixtures.
