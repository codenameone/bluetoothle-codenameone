# CI Samples for Building Third-Party Codename One Apps

This directory contains sample scripts that demonstrate how to build a Codename One
application for Android or iOS using only the artifacts that are published on Maven
Central. They are meant as documentation examples for CI jobs that want to validate
the toolchain without building Codename One from source.

## build-thirdparty-app.sh

`scripts/ci/build-thirdparty-app.sh` orchestrates the steps required to fetch a Codename One
Maven application and invoke the Codename One Maven plugin with the correct platform
properties. The script keeps the inputs simple so it can drop into build pipelines
as-is or be used as a starting point for custom automation.

### How it works

1. The script prepares a temporary workspace at `scripts/ci/.thirdparty-app`.
2. It selects an app source using the following order:
   - `APP_DIR` if you already have the Maven project checked out locally.
   - `APP_REPO` (with an optional `APP_REF`) to clone from Git.
   - The bundled [`scripts/hellocodenameone`](../../scripts/hellocodenameone/README.adoc) sample as a fallback.
3. It runs Maven with the `codename1.platform` and `codename1.buildTarget` properties
   needed by the Codename One Maven plugin. Both the runtime and plugin versions are
   forwarded via `cn1.version` and `cn1.plugin.version` so Maven pulls dependencies
   from Maven Central rather than this repository.

### Usage

```bash
# Android build using a local checkout
APP_DIR=/path/to/your/app \
CODENAMEONE_VERSION=8.0.0 \
./scripts/ci/build-thirdparty-app.sh android

# iOS Xcode project generation using a Git repository and an explicit plugin version
APP_REPO=https://github.com/example/my-cn1-app \
APP_REF=release/1.1.0 \
CODENAMEONE_VERSION=8.0.0 \
CODENAMEONE_PLUGIN_VERSION=8.0.0 \
BUILD_TARGET=ios-source \
./scripts/ci/build-thirdparty-app.sh ios

# Minimal run that falls back to the bundled hello-codenameone sample
./scripts/ci/build-thirdparty-app.sh android
```

### Android emulator bootstrap (start-android-emulator.sh)

`scripts/ci/start-android-emulator.sh` provisions and boots a headless Android emulator with
KVM, framebuffer, and GPU settings that work on GitHub-hosted Ubuntu runners. It installs the
requested system image with `sdkmanager`, creates the AVD if necessary, and waits for
`sys.boot_completed` before returning.

Key environment variables:

| Variable | Purpose | Default |
| --- | --- | --- |
| `ANDROID_SDK_ROOT` | Android SDK path. Must already contain the cmdline tools. | _required_ |
| `AVD_PACKAGE` | System image identifier passed to `sdkmanager`. | `system-images;android-33;google_apis;x86_64` |
| `AVD_NAME` | Emulator name. | `cn1-ci-api33` |
| `AVD_DEVICE` | Device profile. | `pixel_5` |
| `EMULATOR_FLAGS` | Extra flags forwarded to the `emulator` binary. | _unset_ |

Example: install the platform tools, boot the emulator, and run `adb logcat` in CI:

```bash
export ANDROID_SDK_ROOT=$HOME/android-sdk
./scripts/ci/start-android-emulator.sh
adb shell getprop ro.product.model
adb logcat -d | tail
```

### iOS simulator runner (run-ios-simulator.sh)

`scripts/ci/run-ios-simulator.sh` targets macOS runners. It creates (if needed) and boots an
iOS simulator, installs a `.app` bundle, and launches it while streaming simulator logs to aid
debugging. Pair this with `build-thirdparty-app.sh` using `BUILD_TARGET=ios-sim` to produce the
simulator-ready app bundle.

Key environment variables:

| Variable | Purpose | Default |
| --- | --- | --- |
| `APP_BUNDLE` | Path to the built `.app` bundle to install. | _required_ |
| `BUNDLE_ID` | Bundle identifier to launch via `simctl launch`. | _required_ |
| `SIM_DEVICE_NAME` | Simulator name to create/use. | `iPhone 15` |
| `SIM_RUNTIME` | Simulator runtime identifier. | `iOS 17.2` |
| `SIM_TIMEOUT` | Seconds to wait for boot + launch. | `180` |

Example on macOS after generating the simulator build:

```bash
BUILD_TARGET=ios-sim ./scripts/ci/build-thirdparty-app.sh ios
APP_BUNDLE=scripts/ci/.thirdparty-app/target/ios-sim/HelloCodenameOne.app \
BUNDLE_ID=com.codename1.hellocodenameone \
./scripts/ci/run-ios-simulator.sh
```

### Environment variables

| Variable | Purpose | Default |
| --- | --- | --- |
| `APP_DIR` | Path to an existing Codename One Maven project. | _unset_ |
| `APP_REPO` | Git URL to clone when `APP_DIR` is not set. | _unset_ |
| `APP_REF` | Optional Git branch, tag, or commit to checkout after cloning. | _unset_ |
| `WORK_DIR` | Temporary workspace used by the script. | `scripts/ci/.thirdparty-app` |
| `CODENAMEONE_VERSION` | Codename One runtime version to resolve from Maven Central. | `LATEST` |
| `CODENAMEONE_PLUGIN_VERSION` | Codename One Maven plugin version. | `CODENAMEONE_VERSION` |
| `BUILD_TARGET` | Overrides the `codename1.buildTarget` passed to Maven. | `android-device` for Android, `ios-source` for iOS |

### Notes for CI

- The script uses `mvnw` if the target project ships with a Maven wrapper; otherwise
  it falls back to the system `mvn` executable.
- Add `-DskipTests` or additional Maven flags by editing the script or wrapping it in
  your pipeline definition.
- For iOS builds on non-macOS agents, the `ios-source` target is a safe default that
  generates the Xcode project without requiring Apple toolchains.
- Use the emulator/simulator helpers above to run instrumentation smoke tests against
  the built artifacts.

## End-to-end GitHub Actions examples

The snippets below show how to combine the scripts for Android and iOS validation.

### Android (Ubuntu) with hardware acceleration and emulator log piping

```yaml
name: android-ci
on: [push, pull_request]
jobs:
  android:
    runs-on: ubuntu-22.04
    steps:
      - uses: actions/checkout@v4
      - name: Install Java 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - name: Install Android SDK
        uses: android-actions/setup-android@v3
        with:
          packages: |-
            platform-tools
            emulator
            system-images;android-33;google_apis;x86_64
      - name: Enable KVM
        run: |
          sudo apt-get update
          sudo apt-get install -y qemu-kvm libvirt-daemon-system libvirt-clients bridge-utils
          sudo usermod -aG kvm $USER
          sudo udevadm control --reload-rules && sudo udevadm trigger
      - name: Build app with Maven Central artifacts
        run: |
          APP_REPO=https://github.com/codenameone/HelloCodenameOneMaven \
          CODENAMEONE_VERSION=LATEST \
          ./scripts/ci/build-thirdparty-app.sh android
      - name: Boot emulator and run smoke test
        env:
          ANDROID_SDK_ROOT: ${{ env.ANDROID_SDK_ROOT }}
        run: |
          ./scripts/ci/start-android-emulator.sh
          adb install -r scripts/ci/.thirdparty-app/target/*-android-device.apk
          adb shell am start -n com.codename1.hellocodenameone/.HelloCodenameOne
          adb logcat -d | tail -n 200
```

### iOS (macOS) with simulator boot and launch output

```yaml
name: ios-ci
on: [push, pull_request]
jobs:
  ios:
    runs-on: macos-14
    steps:
      - uses: actions/checkout@v4
      - name: Install Java 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - name: Build simulator bundle from Maven Central
        run: |
          BUILD_TARGET=ios-sim \
          APP_REPO=https://github.com/codenameone/HelloCodenameOneMaven \
          ./scripts/ci/build-thirdparty-app.sh ios
      - name: Boot simulator, install, and launch
        env:
          APP_BUNDLE: scripts/ci/.thirdparty-app/target/ios-sim/HelloCodenameOne.app
          BUNDLE_ID: com.codename1.hellocodenameone
        run: |
          ./scripts/ci/run-ios-simulator.sh
```
