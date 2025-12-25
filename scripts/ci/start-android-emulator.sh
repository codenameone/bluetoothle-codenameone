#!/usr/bin/env bash
# Helper to provision and boot an Android emulator suitable for CI.
# It installs the requested system image, creates an AVD, boots it
# headlessly, and waits for boot completion.
set -euo pipefail

function usage() {
  cat <<'USAGE'
Usage: start-android-emulator.sh [--help]

Environment variables:
  ANDROID_SDK_ROOT   Path to the Android SDK (required).
  AVD_NAME           Name for the emulator AVD. Default: cn1-ci-api33.
  AVD_PACKAGE        System image to install. Default:
                     system-images;android-33;google_apis;x86_64
  AVD_DEVICE         Device profile to use. Default: pixel_5
  EMULATOR_PORT      Optional TCP port for the emulator console. Default: 5554
  ADB_SERVER_PORT    Optional TCP port for the adb server. Default: 5037
  EMULATOR_FLAGS     Extra flags passed directly to the emulator binary.

The script ensures the emulator is booted and ready for adb commands.
It is optimized for CI runners (no-window, KVM, swiftshader GPU).
USAGE
}

if [[ ${1:-} == "--help" || ${1:-} == "-h" ]]; then
  usage
  exit 0
fi

if [[ -z ${ANDROID_SDK_ROOT:-} ]]; then
  echo "ANDROID_SDK_ROOT must be set" >&2
  exit 1
fi

AVD_NAME=${AVD_NAME:-cn1-ci-api33}
AVD_PACKAGE=${AVD_PACKAGE:-system-images;android-33;google_apis;x86_64}
AVD_DEVICE=${AVD_DEVICE:-pixel_5}
EMULATOR_PORT=${EMULATOR_PORT:-5554}
ADB_SERVER_PORT=${ADB_SERVER_PORT:-5037}

PATH="$ANDROID_SDK_ROOT/emulator:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"

function info() {
  echo "[android-emulator] $*"
}

function ensure_sdk_tools() {
  if ! command -v sdkmanager >/dev/null; then
    echo "sdkmanager not found in ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT" >&2
    exit 1
  fi
  yes | sdkmanager --licenses >/dev/null
  sdkmanager --install "platform-tools" "emulator" "$AVD_PACKAGE"
}

function create_avd() {
  if avdmanager list avd | grep -q "Name: $AVD_NAME"; then
    info "AVD $AVD_NAME already exists"
    return
  fi
  echo "no" | avdmanager create avd \
    --name "$AVD_NAME" \
    --package "$AVD_PACKAGE" \
    --device "$AVD_DEVICE" \
    --force
}

function start_emulator() {
  local emulator_bin
  emulator_bin=$(command -v emulator)
  if [[ ! -x $emulator_bin ]]; then
    echo "emulator binary not found" >&2
    exit 1
  fi
  info "Starting emulator $AVD_NAME on port $EMULATOR_PORT"
  # Avoid stale instances
  if pgrep -f "-avd $AVD_NAME" >/dev/null; then
    pkill -9 -f "-avd $AVD_NAME"
  fi

  # Launch headless emulator
  "${emulator_bin}" -avd "$AVD_NAME" \
    -port "$EMULATOR_PORT" \
    -no-window \
    -no-audio \
    -no-boot-anim \
    -gpu swiftshader_indirect \
    -accel on \
    -camera-back none \
    -camera-front none \
    -verbose \
    ${EMULATOR_FLAGS:-} \
    >"$HOME/.android/avd/$AVD_NAME/emulator.log" 2>&1 &
}

function wait_for_boot() {
  export ANDROID_ADB_SERVER_PORT="$ADB_SERVER_PORT"
  adb start-server >/dev/null
  adb wait-for-device
  info "Waiting for boot completion"
  local booted="0"
  local attempts=0
  until [[ "$booted" == "1" ]]; do
    booted=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    sleep 3
    ((attempts++))
    if (( attempts > 120 )); then
      info "Emulator failed to boot"
      tail -n 200 "$HOME/.android/avd/$AVD_NAME/emulator.log" || true
      exit 1
    fi
  done
  info "Emulator booted"
  adb shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
  adb shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
  adb shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true
}

info "Using SDK at $ANDROID_SDK_ROOT"
ensure_sdk_tools
create_avd
start_emulator
wait_for_boot
info "Emulator $AVD_NAME is ready for use"
