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

2. `testCordovaBridgeDispatchesLibraryActions`
- Instantiates `com_codename1_cordova_CordovaNativeImpl`.
- Calls bridge methods that dispatch into library plugin actions:
  - `execute("isInitialized", "")`
  - `execute("isEnabled", "")`
- Verifies unknown action returns `false`:
  - `execute("__unknown_action__", "")`
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

2. `cordovaBridgeInvokesLibraryActions`
- Instantiates `com.codename1.cordova.CordovaNativeImpl`.
- Registers callbacks through `CordovaCallbackManager`.
- Invokes library actions through bridge:
  - `execute("isInitialized", "")`
  - `execute("isEnabled", "")`
- Asserts callback payload contains expected keys:
  - `isInitialized`
  - `isEnabled`
- Verifies unknown action returns `false`:
  - `execute("__unknown_action__", "")`

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
