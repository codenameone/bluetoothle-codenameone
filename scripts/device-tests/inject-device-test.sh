#!/usr/bin/env bash
#
# Injects DeviceTestRunner into the cn1:build-generated Android source
# project, configures BTDemoStub to invoke it on activity start, drops the
# device_test_config.properties asset (with the GitHub token + check-run
# id), and patches the manifest for BLE peripheral permissions.
#
# Required env:
#   GITHUB_TOKEN_FOR_DEVICE   short-lived token (typically ${{ github.token }})
#   GITHUB_REPOSITORY         owner/repo
#   GITHUB_CHECK_RUN_ID       numeric check-run id created in CI
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if [[ -z "${GITHUB_TOKEN_FOR_DEVICE:-}" || -z "${GITHUB_REPOSITORY:-}" || -z "${GITHUB_CHECK_RUN_ID:-}" ]]; then
  echo "GITHUB_TOKEN_FOR_DEVICE, GITHUB_REPOSITORY, GITHUB_CHECK_RUN_ID must all be set" >&2
  exit 1
fi

ANDROID_SRC="$(find BTDemo/target -maxdepth 1 -type d -name '*-android-source' | sort | tail -n 1)"
if [[ -z "$ANDROID_SRC" ]]; then
  echo "Generated Android source project missing — run cn1:build first" >&2
  exit 1
fi

APP_SRC="$ANDROID_SRC/app/src/main/java/com/codename1/btle"
ASSETS_DIR="$ANDROID_SRC/app/src/main/assets"
MANIFEST="$ANDROID_SRC/app/src/main/AndroidManifest.xml"
APP_BUILD_GRADLE="$ANDROID_SRC/app/build.gradle"

if [[ ! -d "$APP_SRC" ]]; then
  echo "Expected $APP_SRC to exist" >&2
  exit 1
fi
mkdir -p "$ASSETS_DIR"

# 1. Copy the runner.
cp "$ROOT_DIR/scripts/device-tests/DeviceTestRunner.java" "$APP_SRC/DeviceTestRunner.java"

# 2. Inject the trigger into BTDemoStub. Add a call at the end of onCreate
# and add an onRequestPermissionsResult override before the class's closing
# brace. perl handles multi-line regex cleanly.
STUB="$APP_SRC/BTDemoStub.java"
if [[ ! -f "$STUB" ]]; then
  echo "Expected $STUB to exist" >&2
  exit 1
fi

if ! grep -q "DeviceTestRunner" "$STUB"; then
  perl -0pi -e '
    s/(public void onCreate\(Bundle savedInstanceState\) \{(?:[^{}]|\{[^{}]*\})*?)\n    \}/$1\n        com.codename1.btle.DeviceTestRunner.start(this);\n    \}/s
  ' "$STUB"
  # Append onRequestPermissionsResult before the final class closing brace.
  perl -0pi -e '
    s/\n\}\s*$/\n\n    \@Override\n    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) \{\n        super.onRequestPermissionsResult(requestCode, permissions, grantResults);\n        com.codename1.btle.DeviceTestRunner.onPermissionsResult(this, requestCode, permissions, grantResults);\n    \}\n\}\n/s
  ' "$STUB"
fi

if ! grep -q "DeviceTestRunner.start" "$STUB" || ! grep -q "DeviceTestRunner.onPermissionsResult" "$STUB"; then
  echo "Failed to inject DeviceTestRunner hooks into BTDemoStub.java" >&2
  exit 1
fi

# 3. Drop the runtime config asset.
cat > "$ASSETS_DIR/device_test_config.properties" <<EOF
github_token=$GITHUB_TOKEN_FOR_DEVICE
repo=$GITHUB_REPOSITORY
check_run_id=$GITHUB_CHECK_RUN_ID
EOF
chmod 600 "$ASSETS_DIR/device_test_config.properties"

# 4. Add API 31+ BT permissions to the manifest if they're not already
# there. The CN1 lib only declares the legacy BLUETOOTH/BLUETOOTH_ADMIN +
# COARSE_LOCATION; on a real Android 12+ device we additionally need
# BLUETOOTH_SCAN/CONNECT/ADVERTISE for peripheral + central operation.
add_permission() {
  local perm="$1"
  if ! grep -q "android.permission.${perm}" "$MANIFEST"; then
    perl -i -pe "s|<uses-permission android:name=\"android.permission.BLUETOOTH\"/>|<uses-permission android:name=\"android.permission.BLUETOOTH\"/><uses-permission android:name=\"android.permission.${perm}\"/>|" "$MANIFEST"
  fi
}
add_permission "BLUETOOTH_SCAN"
add_permission "BLUETOOTH_CONNECT"
add_permission "BLUETOOTH_ADVERTISE"
add_permission "ACCESS_FINE_LOCATION"

# 5. Patch the gradle file the same way run-android-native-tests.sh does
# — the cn1:build target writes 0 / '0' literals expecting downstream
# patching.
perl -0pi -e "s/compileSdkVersion\\s+0/compileSdkVersion 30/g; s/targetSdkVersion\\s+0/targetSdkVersion 30/g; s/buildToolsVersion\\s+'0'/buildToolsVersion '30.0.3'/g" "$APP_BUILD_GRADLE"
perl -0pi -e "s/com\\.android\\.support:support-v4:0\\.\\+/com.android.support:support-v4:28.0.0/g; s/com\\.android\\.support:appcompat-v7:0\\.\\+/com.android.support:appcompat-v7:28.0.0/g" "$APP_BUILD_GRADLE"

# 6. Drop the legacy stale com/codename1/util JSON utils that the smoke
# script also removes.
LEGACY="$ANDROID_SRC/app/src/main/java/com/codename1/util"
rm -f "$LEGACY/JSONParserUtils.java" "$LEGACY/JSONUtils.java"

echo "Injected DeviceTestRunner; config:"
echo "  repo=$GITHUB_REPOSITORY"
echo "  check_run_id=$GITHUB_CHECK_RUN_ID"
echo "  android source=$ANDROID_SRC"
