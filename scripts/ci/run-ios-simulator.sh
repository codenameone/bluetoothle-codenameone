#!/usr/bin/env bash
# Boot an iOS simulator, install a built app bundle, and launch it.
# This script is intended for macOS GitHub Actions runners.
set -euo pipefail

function usage() {
  cat <<'USAGE'
Usage: run-ios-simulator.sh [--help]

Environment variables:
  APP_BUNDLE        Path to the .app bundle to install (required).
  BUNDLE_ID         Bundle identifier used to launch the app (required).
  SIM_DEVICE_NAME   Simulator name to create/use. Default: iPhone 15
  SIM_RUNTIME       Simulator runtime identifier. Default: iOS (auto-detect latest)
  SIM_TIMEOUT       Seconds to wait for boot and launch. Default: 180

The script will create the simulator if missing, boot it headlessly,
install APP_BUNDLE, and launch BUNDLE_ID while streaming device logs.
USAGE
}

if [[ ${1:-} == "--help" || ${1:-} == "-h" ]]; then
  usage
  exit 0
fi

if [[ -z ${APP_BUNDLE:-} || -z ${BUNDLE_ID:-} ]]; then
  echo "APP_BUNDLE and BUNDLE_ID must be set" >&2
  exit 1
fi

SIM_DEVICE_NAME=${SIM_DEVICE_NAME:-"iPhone 15"}
SIM_RUNTIME=${SIM_RUNTIME:-"iOS"}
SIM_TIMEOUT=${SIM_TIMEOUT:-180}

function info() {
  echo "[ios-sim] $*" >&2
}

function ensure_device() {
  # Try to find the exact runtime first, or fallback to the latest available iOS runtime
  # We extract the last column which is the runtime identifier (e.g. com.apple.CoreSimulator.SimRuntime.iOS-17-2)
  local runtime_id
  runtime_id=$(xcrun simctl list runtimes | awk -v r="$SIM_RUNTIME" '/com.apple.CoreSimulator.SimRuntime/ && $0 ~ r {print $NF}' | sort | tail -n 1)

  if [[ -z $runtime_id && "$SIM_RUNTIME" == "iOS" ]]; then
     runtime_id=$(xcrun simctl list runtimes | awk '/com.apple.CoreSimulator.SimRuntime.iOS/ {print $NF}' | sort | tail -n 1)
  fi

  if [[ -z $runtime_id ]]; then
    echo "Simulator runtime matching '$SIM_RUNTIME' not found" >&2
    echo "Available runtimes:" >&2
    xcrun simctl list runtimes >&2
    exit 1
  fi

  info "Selected runtime: $runtime_id"

  local existing
  # Escape parenthesis for awk regex or use index
  # We look for: Name (UUID) ... RuntimeID
  # awk '$0 ~ n" (" ...' failed because of the open parenthesis in regex.
  # We can check if line starts with "$SIM_DEVICE_NAME ("
  existing=$(xcrun simctl list devices "$runtime_id" | grep -F "$SIM_DEVICE_NAME (" | head -n 1 | awk -F '[()]' '{print $2}')

  if [[ -n $existing ]]; then
    echo "$existing"
    return
  fi

  # Create device
  # Usage: xcrun simctl create <name> <device_type_id> <runtime_id>
  local device_type="com.apple.CoreSimulator.SimDeviceType.iPhone-15"
  # Verify device type exists, fallback to generic if iPhone 15 missing?
  # For now assume iPhone-15 exists on macos-15 runner.

  local udid
  udid=$(xcrun simctl create "$SIM_DEVICE_NAME" "$device_type" "$runtime_id")
  echo "$udid"
}

function boot_device() {
  local udid=$1
  info "Booting simulator $SIM_DEVICE_NAME ($udid)"
  xcrun simctl boot "$udid" >/dev/null 2>&1 || true
  xcrun simctl bootstatus "$udid" -b --timeout "$SIM_TIMEOUT"
}

function install_and_launch() {
  local udid=$1
  info "Installing $APP_BUNDLE"
  xcrun simctl install "$udid" "$APP_BUNDLE"
  info "Launching $BUNDLE_ID"
  # Stream logs in the background to aid debugging
  xcrun simctl spawn "$udid" log stream --style compact --predicate "processImagePath CONTAINS '$BUNDLE_ID'" >"/tmp/ios-sim-${udid}.log" 2>&1 &
  xcrun simctl launch "$udid" "$BUNDLE_ID"
  info "Recent simulator logs:"
  tail -n 50 "/tmp/ios-sim-${udid}.log" || true
}

udid=$(ensure_device)
boot_device "$udid"
install_and_launch "$udid"
info "Simulator run complete"
