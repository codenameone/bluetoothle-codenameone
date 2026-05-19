#!/usr/bin/env bash
# Smoke test for the cn1-ble-helper executable on a CI runner.
#
# On a developer's machine with Bluetooth available the helper emits
#   {"event":"stateChanged","state":"poweredOn"}
# Hosted runners may have no Bluetooth adapter at all, in which case btleplug
# reports no adapters and the helper emits
#   {"event":"stateChanged","state":"unsupported"}
# Either is a valid pass signal — the only failure mode this script catches
# is "helper never emitted anything," which usually means a build /
# linking / runtime regression.
set -euo pipefail

# Cross-platform helper path: macOS/Linux ship the binary unsuffixed under
# target/release/, Windows uses .exe. Caller can override with $HELPER.
DEFAULT_HELPER="javase/src/main/rust/cn1-ble-helper/target/release/cn1-ble-helper"
if [ "${OS:-}" = "Windows_NT" ]; then
  DEFAULT_HELPER="${DEFAULT_HELPER}.exe"
fi
HELPER="${HELPER:-$DEFAULT_HELPER}"

if [ ! -x "$HELPER" ] && [ ! -f "$HELPER" ]; then
  echo "ERROR: helper not found at $HELPER — did the cn1lib build run?" >&2
  exit 2
fi

OUT=$(mktemp)
ERR=$(mktemp)
trap 'rm -f "$OUT" "$ERR"' EXIT

# Drive the helper: ask for state, wait for the first event, then shut down.
(
  echo '{"cmd":"initialize","id":1}'
  sleep 3
  echo '{"cmd":"shutdown"}'
) | "$HELPER" > "$OUT" 2> "$ERR" &
HELPER_PID=$!

# Allow up to 10s for the helper to spin up and emit the first event. The
# btleplug + tokio startup on a cold-cache runner can take a few seconds.
for _ in $(seq 10); do
  if grep -q '"event":"stateChanged"' "$OUT"; then
    break
  fi
  sleep 1
done

if kill -0 "$HELPER_PID" 2>/dev/null; then
  kill "$HELPER_PID" 2>/dev/null || true
  wait "$HELPER_PID" 2>/dev/null || true
fi

echo "--- helper stdout ---"
cat "$OUT"
echo "--- helper stderr ---"
cat "$ERR" 2>/dev/null || true
echo "---"

if ! grep -q '"event":"stateChanged"' "$OUT"; then
  echo "FAIL: helper never emitted a stateChanged event." >&2
  exit 1
fi

echo "OK: cn1-ble-helper produced a stateChanged event."
