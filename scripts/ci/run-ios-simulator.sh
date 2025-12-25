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
  SIM_RUNTIME       Simulator runtime identifier. Default: iOS 17.2
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
SIM_RUNTIME=${SIM_RUNTIME:-"iOS 17.2"}
SIM_TIMEOUT=${SIM_TIMEOUT:-180}

function info() {
  echo "[ios-sim] $*"
}

function ensure_device() {
  local runtime_id
  runtime_id=$(xcrun simctl list runtimes | awk -v r="$SIM_RUNTIME" '/com.apple.CoreSimulator.SimRuntime/ && $0 ~ r {print $2}')
  if [[ -z $runtime_id ]]; then
    echo "Simulator runtime $SIM_RUNTIME not found" >&2
    exit 1
  fi
  local existing
  existing=$(xcrun simctl list devices | awk -v n="$SIM_DEVICE_NAME" -v r="$runtime_id" '$0 ~ n" (" && $0 ~ r {print $1}')
  if [[ -n $existing ]]; then
    echo "$existing"
    return
  fi
  xcrun simctl create "$SIM_DEVICE_NAME" "$runtime_id" "com.apple.CoreSimulator.SimDeviceType.iPhone-15"
  existing=$(xcrun simctl list devices | awk -v n="$SIM_DEVICE_NAME" -v r="$runtime_id" '$0 ~ n" (" && $0 ~ r {print $1}')
  echo "$existing"
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
