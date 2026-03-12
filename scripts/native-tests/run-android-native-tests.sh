#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

CN1_BUILD_CLIENT_JAR="${CN1_BUILD_CLIENT_JAR:-$HOME/.codenameone/CodeNameOneBuildClient.jar}"
if [[ ! -f "$CN1_BUILD_CLIENT_JAR" ]]; then
  echo "Missing Codename One build client at $CN1_BUILD_CLIENT_JAR" >&2
  echo "Download it first or set CN1_BUILD_CLIENT_JAR to its path." >&2
  exit 1
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME must point to Java 11+ for Codename One native source generation." >&2
  exit 1
fi

if [[ -z "${ANDROID_SDK_ROOT:-}" ]] && [[ -z "${ANDROID_HOME:-}" ]]; then
  echo "ANDROID_SDK_ROOT or ANDROID_HOME must be set." >&2
  exit 1
fi

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$JAVA_HOME/bin:$PATH"

mkdir -p BTDemo/target
find BTDemo/target -maxdepth 1 -type d -name '*-android-source' -exec rm -rf {} +

# Ensure all platform-specific reactor artifacts are installed locally before CN1 native-source generation.
mvn -DskipTests -Dcodename1.platform=android install

mvn -pl BTDemo -am cn1:build -DskipTests -Dcodename1.platform=android -Dcodename1.buildTarget=android-source -Dopen=false

ANDROID_SRC="$(find BTDemo/target -maxdepth 1 -type d -name '*-android-source' | sort | tail -n 1)"
if [[ -z "$ANDROID_SRC" ]]; then
  echo "Failed to locate generated Android source project under BTDemo/target." >&2
  exit 1
fi

if [[ ! -x "$ANDROID_SRC/gradlew" ]]; then
  echo "Missing gradlew in generated Android project: $ANDROID_SRC" >&2
  exit 1
fi

APP_BUILD_GRADLE="$ANDROID_SRC/app/build.gradle"
if [[ ! -f "$APP_BUILD_GRADLE" ]]; then
  echo "Missing app/build.gradle in generated Android project." >&2
  exit 1
fi

ensure_gradle_property() {
  local file="$1"
  local key="$2"
  local value="$3"
  local tmp
  tmp="$(mktemp)"
  touch "$file"
  awk -v k="$key" -v v="$value" '
    BEGIN { done=0 }
    {
      line=$0
      gsub(/^[[:space:]]+/, "", line)
      if (index(line, k "=") == 1) {
        print k "=" v
        done=1
      } else {
        print $0
      }
    }
    END {
      if (!done) {
        print k "=" v
      }
    }
  ' "$file" > "$tmp"
  mv "$tmp" "$file"
}

GRADLE_PROPERTIES="$ANDROID_SRC/gradle.properties"
APP_GRADLE_PROPERTIES="$ANDROID_SRC/app/gradle.properties"
ensure_gradle_property "$GRADLE_PROPERTIES" "android.useAndroidX" "false"
ensure_gradle_property "$GRADLE_PROPERTIES" "android.enableJetifier" "false"
ensure_gradle_property "$APP_GRADLE_PROPERTIES" "android.useAndroidX" "false"
ensure_gradle_property "$APP_GRADLE_PROPERTIES" "android.enableJetifier" "false"

perl -0pi -e "s/compileSdkVersion\\s+0/compileSdkVersion 30/g; s/targetSdkVersion\\s+0/targetSdkVersion 30/g; s/buildToolsVersion\\s+'0'/buildToolsVersion '30.0.3'/g" "$APP_BUILD_GRADLE"
perl -0pi -e "s/com\\.android\\.support:support-v4:0\\.\\+/com.android.support:support-v4:28.0.0/g; s/com\\.android\\.support:appcompat-v7:0\\.\\+/com.android.support:appcompat-v7:28.0.0/g" "$APP_BUILD_GRADLE"

TEST_DIR="$ANDROID_SRC/app/src/androidTest/java/com/codename1/btle"
TEST_FILE="$TEST_DIR/BluetoothNativeInstrumentationTest.java"
CORDOVA_STUB="$ANDROID_SRC/app/src/main/java/com/codename1/cordova/CordovaNativeStub.java"
CORDOVA_IMPL="$ANDROID_SRC/app/src/main/java/com/codename1/cordova/CordovaNativeImpl.java"

mkdir -p "$TEST_DIR"

if [[ -f "$CORDOVA_STUB" ]] && [[ ! -f "$CORDOVA_IMPL" ]]; then
  cat > "$CORDOVA_STUB" <<'STUBEOF'
package com.codename1.cordova;

public class CordovaNativeStub implements CordovaNative {
    @Override
    public boolean execute(String param0, String param1) {
        return false;
    }

    @Override
    public boolean isSupported() {
        return false;
    }
}
STUBEOF
fi

cat > "$TEST_FILE" <<'TESTEOF'
package com.codename1.btle;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;

import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BluetoothNativeInstrumentationTest {

    @Test
    public void bluetoothStackIsAvailable() {
        Context context = InstrumentationRegistry.getTargetContext();
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        assertNotNull("BluetoothManager should be available", manager);

        PackageManager packageManager = context.getPackageManager();
        assertTrue(
            "Emulator/device must advertise BLE feature",
            packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        );

        BluetoothAdapter adapter = manager.getAdapter();
        assertNotNull("BluetoothAdapter should be available", adapter);
    }
}
TESTEOF

EXAMPLE_TEST="$ANDROID_SRC/app/src/androidTest/java/com/example/myapplication2/ExampleInstrumentedTest.java"
if [[ -f "$EXAMPLE_TEST" ]]; then
  rm -f "$EXAMPLE_TEST"
fi

if ! rg -q 'testInstrumentationRunner "android.support.test.runner.AndroidJUnitRunner"' "$APP_BUILD_GRADLE"; then
  TMP_GRADLE="$(mktemp)"
  awk '
    {
      print
      if ($0 ~ /^[[:space:]]*defaultConfig[[:space:]]*\{[[:space:]]*$/ && !runnerInserted) {
        print "        testInstrumentationRunner \"android.support.test.runner.AndroidJUnitRunner\""
        runnerInserted = 1
      }
    }
  ' "$APP_BUILD_GRADLE" > "$TMP_GRADLE"
  mv "$TMP_GRADLE" "$APP_BUILD_GRADLE"
fi

TEST_DEP_CONF="androidTestImplementation"
if ! rg -q "^[[:space:]]*implementation[[:space:]]" "$APP_BUILD_GRADLE"; then
  TEST_DEP_CONF="androidTestCompile"
fi

# Remove stale injected test dependency lines from previous runs.
perl -ni -e 'print unless /(androidx\.test:(runner|ext:junit|espresso-core)|com\.android\.support\.test:(runner|rules|espresso-core))/' "$APP_BUILD_GRADLE"

if ! rg -q "com\.android\.support\.test:runner" "$APP_BUILD_GRADLE"; then
  cat >> "$APP_BUILD_GRADLE" <<EOF

dependencies {
    $TEST_DEP_CONF "com.android.support.test:runner:1.0.2"
    $TEST_DEP_CONF "com.android.support.test:rules:1.0.2"
    $TEST_DEP_CONF "com.android.support.test.espresso:espresso-core:3.0.2"
}
EOF
fi

chmod +x "$ANDROID_SRC/gradlew"
(
  cd "$ANDROID_SRC"
  ./gradlew connectedDebugAndroidTest --stacktrace
)
