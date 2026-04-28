#!/usr/bin/env bash
#
# End-to-end Android instrumentation test driving the BluetoothNativeBridge
# against a Python Bumble peripheral acting as a virtual GATT server.
#
# Order of operations matters — see comments at each step.
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

# Filtered background logcat. Capturing the full firehose contributed to
# system_server "Lost network stack" cascades during gradle's APK install
# phase by overloading the runner.
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

PERIPHERAL_LOG="${PERIPHERAL_LOG:-$(mktemp -t bumble-peripheral.XXXXXX).log}"
PERIPHERAL_PID=

cleanup() {
  if [[ -n "${PERIPHERAL_PID:-}" ]] && kill -0 "$PERIPHERAL_PID" 2>/dev/null; then
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

# Step 1: Enable Bluetooth on the emulator BEFORE Bumble attaches.
# Theory under test: Bumble's HCI_RESET on attach was racing with the
# emulator BT stack startup, leaving the BT system service "not connected"
# even though mEnable=true. Bring up the emulator side first, confirm
# state=ON, then attach Bumble.
echo "--- Step 1: enabling emulator-side Bluetooth ---"
adb devices -l || true

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
  sleep 8
done
final=$(bt_state || echo UNKNOWN)
echo "Final bt_state=$final"
if [[ "$final" != "ON" ]]; then
  echo "Bluetooth never reached ON state on the emulator." >&2
  echo "--- bluetooth_manager dumpsys ---" >&2
  adb shell dumpsys bluetooth_manager 2>&1 | head -60 >&2 || true
  echo "--- emulator BT process ---" >&2
  adb shell ps -A 2>&1 | grep -i blue || true
  exit 1
fi

# Step 2: Now that the emulator BT controller is up, attach Bumble as a
# virtual peripheral via netsim.
echo "--- Step 2: starting Bumble peripheral ---"
echo "--- netsim CLI status (if available) ---"
NETSIM_BIN=""
if command -v netsim >/dev/null 2>&1; then
  NETSIM_BIN="netsim"
elif [[ -x "${ANDROID_SDK_ROOT:-$ANDROID_HOME}/emulator/netsim" ]]; then
  NETSIM_BIN="${ANDROID_SDK_ROOT:-$ANDROID_HOME}/emulator/netsim"
fi
if [[ -n "$NETSIM_BIN" ]]; then
  "$NETSIM_BIN" version || true
  "$NETSIM_BIN" devices || true
else
  echo "(netsim CLI not found; skipping)"
fi

export BUMBLE_TRANSPORT="${BUMBLE_TRANSPORT:-android-netsim}"
export BUMBLE_LOG_LEVEL="${BUMBLE_LOG_LEVEL:-DEBUG}"
echo "Starting Bumble (transport=$BUMBLE_TRANSPORT); logs at $PERIPHERAL_LOG"
python3 "$ROOT_DIR/scripts/native-tests/bumble_peripheral.py" >"$PERIPHERAL_LOG" 2>&1 &
PERIPHERAL_PID=$!

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

# Step 3: Re-check that the emulator BT is still ON after Bumble attached.
# If Bumble's HCI reset took down the emulator's controller, surface that
# clearly instead of letting the test fail with a confusing "scan never
# returned a result".
post_bumble=$(bt_state || echo UNKNOWN)
echo "Post-Bumble bt_state=$post_bumble"
if [[ "$post_bumble" != "ON" ]]; then
  echo "Bluetooth dropped to $post_bumble after Bumble attached — Bumble's HCI reset trampled the emulator side." >&2
  exit 1
fi

if [[ -n "$NETSIM_BIN" ]]; then
  echo "--- netsim devices after Bumble attached ---"
  "$NETSIM_BIN" devices || true
fi

# Step 4: Run instrumentation.
echo "--- Step 3: running instrumentation suite with E2E test ---"
BUMBLE_PERIPHERAL=1 "$ROOT_DIR/scripts/native-tests/run-android-native-tests.sh"
