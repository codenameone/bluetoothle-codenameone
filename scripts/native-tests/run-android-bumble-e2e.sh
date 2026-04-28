#!/usr/bin/env bash
#
# End-to-end Android instrumentation test driving the BluetoothNativeBridge
# against a Python Bumble peripheral acting as a virtual GATT server.
#
# Prerequisites:
#   - Python 3.10+ with the `bumble` package installed (pip install bumble)
#   - An Android emulator with the netsim virtual radio backend running.
#     The reactivecircus/android-emulator-runner action launches one
#     automatically when the emulator binary supports netsim (API 33+).
#   - Standard env: JAVA_HOME, ANDROID_SDK_ROOT, CN1 build client jar.
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required" >&2
  exit 1
fi

if ! python3 -c "import bumble" 2>/dev/null; then
  echo "Python 'bumble' package is missing. Install with: pip install bumble" >&2
  exit 1
fi

PERIPHERAL_LOG="${PERIPHERAL_LOG:-$(mktemp -t bumble-peripheral.XXXXXX).log}"
echo "Starting Bumble peripheral; logs at $PERIPHERAL_LOG"

# Surface the transport choice so users can override it (eg android-emulator,
# tcp-server:127.0.0.1:9000). Default targets the netsim daemon spawned by
# modern Android emulators.
export BUMBLE_TRANSPORT="${BUMBLE_TRANSPORT:-android-netsim}"
# Bumble's logging is fairly quiet at INFO; turn up to DEBUG when iterating
# in CI so we can diagnose connection / advertise problems.
export BUMBLE_LOG_LEVEL="${BUMBLE_LOG_LEVEL:-DEBUG}"

# Surface what netsim is exposing — useful to confirm the emulator side of
# the link is up before Bumble tries to attach.
echo "--- netsim CLI status (if available) ---"
if command -v netsim >/dev/null 2>&1; then
  netsim version || true
  netsim devices || true
elif [[ -x "${ANDROID_SDK_ROOT:-$ANDROID_HOME}/emulator/netsim" ]]; then
  "${ANDROID_SDK_ROOT:-$ANDROID_HOME}/emulator/netsim" version || true
  "${ANDROID_SDK_ROOT:-$ANDROID_HOME}/emulator/netsim" devices || true
else
  echo "(netsim CLI not found; skipping)"
fi
echo "--- adb devices ---"
adb devices -l || true

# Bumble at DEBUG is verbose. Silence stdout to a file only — no inline
# tail — to avoid stealing CPU/disk during the heavy Maven phase. The
# cleanup dump prints the tail on failure.
python3 "$ROOT_DIR/scripts/native-tests/bumble_peripheral.py" >"$PERIPHERAL_LOG" 2>&1 &
PERIPHERAL_PID=$!

# Background logcat capture, FILTERED to library + framework tags we
# actually care about. Capturing the full firehose was generating tens of
# MBs/min on a runner already pushing the memory ceiling and contributed
# to system_server "Lost network stack" cascades during gradle's APK
# install phase.
LOGCAT_LOG="${LOGCAT_LOG:-$(mktemp -t logcat.XXXXXX).log}"
adb logcat -c 2>/dev/null || true
( adb logcat -v threadtime \
    AndroidRuntime:E \
    ActivityManager:W \
    PackageManager:W \
    System.err:W \
    BluetoothLePlugin:* \
    BluetoothManager:* \
    BluetoothAdapter:* \
    TestRunner:* \
    com.codename1.bluetoothle:* \
    '*:F' \
    > "$LOGCAT_LOG" 2>&1 ) &
LOGCAT_PID=$!

cleanup() {
  if kill -0 "${PERIPHERAL_PID:-0}" 2>/dev/null; then
    kill "$PERIPHERAL_PID" 2>/dev/null || true
    wait "$PERIPHERAL_PID" 2>/dev/null || true
  fi
  if kill -0 "${LOGCAT_PID:-0}" 2>/dev/null; then
    kill "$LOGCAT_PID" 2>/dev/null || true
  fi
  echo "--- Bumble peripheral log (tail) ---"
  tail -200 "$PERIPHERAL_LOG" 2>/dev/null || true
  echo "--- logcat (filtered, tail) ---"
  tail -500 "$LOGCAT_LOG" 2>/dev/null || true
  if [[ -n "${RUNNER_TEMP:-}" ]]; then
    cp "$PERIPHERAL_LOG" "$RUNNER_TEMP/bumble-peripheral.log" 2>/dev/null || true
    cp "$LOGCAT_LOG" "$RUNNER_TEMP/logcat-streamed.log" 2>/dev/null || true
  fi
}
trap cleanup EXIT

# Give the peripheral a moment to attach and start advertising before the
# emulator scans. Bumble emits "advertising as ..." once ready.
for _ in $(seq 1 60); do
  if grep -q "advertising as" "$PERIPHERAL_LOG" 2>/dev/null; then
    break
  fi
  if ! kill -0 "$PERIPHERAL_PID" 2>/dev/null; then
    echo "Bumble peripheral exited before it began advertising" >&2
    exit 1
  fi
  sleep 1
done

if ! grep -q "advertising as" "$PERIPHERAL_LOG"; then
  echo "Bumble peripheral never reported readiness" >&2
  exit 1
fi

echo "Bumble peripheral ready; verifying it shows up on netsim before instrumentation"
if command -v netsim >/dev/null 2>&1; then
  netsim devices || true
elif [[ -x "${ANDROID_SDK_ROOT:-$ANDROID_HOME}/emulator/netsim" ]]; then
  "${ANDROID_SDK_ROOT:-$ANDROID_HOME}/emulator/netsim" devices || true
fi

# Pre-enable Bluetooth on the AVD and poll until the adapter actually
# reports state=ON. Without polling we have observed netsim-backed BT
# bounce between ON and OFF for ~15s after `svc bluetooth enable` reports
# "Success", which causes the plugin's initialize() to read status=disabled.
echo "--- Pre-enabling Bluetooth on the emulator ---"
bt_state() {
  adb shell dumpsys bluetooth_manager 2>/dev/null \
    | awk '/^\s*state:/ {print $2; exit}' \
    | tr -d '\r\n'
}
for attempt in $(seq 1 6); do
  current=$(bt_state || echo UNKNOWN)
  echo "attempt=$attempt bt_state=$current"
  if [[ "$current" == "ON" ]]; then
    break
  fi
  adb shell svc bluetooth enable 2>&1 || true
  # Stabilization wait — netsim-backed BT can flap for several seconds.
  sleep 8
done
final=$(bt_state || echo UNKNOWN)
echo "Final bt_state=$final"
if [[ "$final" != "ON" ]]; then
  echo "Bluetooth never reached ON state on the emulator — instrumentation will fail" >&2
  adb shell dumpsys bluetooth_manager 2>&1 | head -40 >&2 || true
fi

echo "Running instrumentation suite with E2E test"
BUMBLE_PERIPHERAL=1 "$ROOT_DIR/scripts/native-tests/run-android-native-tests.sh"
