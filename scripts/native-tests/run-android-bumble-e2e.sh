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
# Status: experimental. The Bumble<->android-emulator transport occasionally
# drifts between Android emulator versions, so the corresponding CI job is
# allowed to fail (continue-on-error: true) until it has soaked.
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

PERIPHERAL_LOG="$(mktemp -t bumble-peripheral.XXXXXX).log"
echo "Starting Bumble peripheral; logs at $PERIPHERAL_LOG"

# Surface the transport choice so users can override it (eg android-emulator,
# tcp-server:127.0.0.1:9000). Default targets the netsim daemon spawned by
# modern Android emulators.
export BUMBLE_TRANSPORT="${BUMBLE_TRANSPORT:-android-netsim}"

python3 "$ROOT_DIR/scripts/native-tests/bumble_peripheral.py" >"$PERIPHERAL_LOG" 2>&1 &
PERIPHERAL_PID=$!

cleanup() {
  if kill -0 "$PERIPHERAL_PID" 2>/dev/null; then
    kill "$PERIPHERAL_PID" 2>/dev/null || true
    wait "$PERIPHERAL_PID" 2>/dev/null || true
  fi
  echo "--- Bumble peripheral log ---"
  cat "$PERIPHERAL_LOG" || true
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

echo "Bumble peripheral ready; running instrumentation suite with E2E test"
BUMBLE_PERIPHERAL=1 exec "$ROOT_DIR/scripts/native-tests/run-android-native-tests.sh"
