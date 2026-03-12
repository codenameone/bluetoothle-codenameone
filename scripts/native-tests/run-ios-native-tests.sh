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
  if [[ "$(uname -s)" == "Darwin" ]] && [[ -x /usr/libexec/java_home ]]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 1.8)"
    export JAVA_HOME
  fi
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME must point to Java 8 for Codename One native source generation." >&2
  exit 1
fi

export PATH="$JAVA_HOME/bin:$PATH"

find BTDemo/target -maxdepth 1 -type d -name '*-ios-source' -exec rm -rf {} +

mvn -pl BTDemo -am cn1:build -DskipTests -Dcodename1.platform=ios -Dcodename1.buildTarget=ios-source -Dopen=false

IOS_SRC="$(find BTDemo/target -maxdepth 1 -type d -name '*-ios-source' | sort | tail -n 1)"
if [[ -z "$IOS_SRC" ]]; then
  echo "Failed to locate generated iOS source project under BTDemo/target." >&2
  exit 1
fi

TEST_DIR="$IOS_SRC/BTDemoTests"
PBXPROJ="$IOS_SRC/BTDemo.xcodeproj/project.pbxproj"
TEST_FILE="$TEST_DIR/BTDemoBluetoothNativeTests.m"
PLIST_FILE="$TEST_DIR/BTDemoTests-Info.plist"
IOS_NATIVE_FILE="$IOS_SRC/BTDemo-src/IOSNative.m"

mkdir -p "$TEST_DIR"
cat > "$TEST_FILE" <<'TESTEOF'
#import <XCTest/XCTest.h>
#import <CoreBluetooth/CoreBluetooth.h>

@interface BTDemoBluetoothNativeTests : XCTestCase <CBCentralManagerDelegate>
@property (nonatomic, strong) CBCentralManager *centralManager;
@property (nonatomic, strong) XCTestExpectation *stateUpdateExpectation;
@end

@implementation BTDemoBluetoothNativeTests

- (void)testBluetoothPluginClassIsLinked {
    XCTAssertNotNil(NSClassFromString(@"BluetoothLePlugin"), @"BluetoothLePlugin class was not linked into the host app target.");
}

- (void)testCoreBluetoothInitializes {
    self.stateUpdateExpectation = [self expectationWithDescription:@"CoreBluetooth state callback"];
    self.centralManager = [[CBCentralManager alloc] initWithDelegate:self queue:nil options:nil];
    [self waitForExpectations:@[self.stateUpdateExpectation] timeout:10.0];
}

- (void)centralManagerDidUpdateState:(CBCentralManager *)central {
    if (central.state == CBManagerStateUnknown || central.state == CBManagerStateResetting) {
        return;
    }

    [self.stateUpdateExpectation fulfill];
}

@end
TESTEOF

cat > "$PLIST_FILE" <<'PLISTEOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleDevelopmentRegion</key>
  <string>en</string>
  <key>CFBundleExecutable</key>
  <string>$(EXECUTABLE_NAME)</string>
  <key>CFBundleIdentifier</key>
  <string>$(PRODUCT_BUNDLE_IDENTIFIER)</string>
  <key>CFBundleInfoDictionaryVersion</key>
  <string>6.0</string>
  <key>CFBundleName</key>
  <string>$(PRODUCT_NAME)</string>
  <key>CFBundlePackageType</key>
  <string>BNDL</string>
  <key>CFBundleShortVersionString</key>
  <string>1.0</string>
  <key>CFBundleVersion</key>
  <string>1</string>
</dict>
</plist>
PLISTEOF

if [[ -f "$IOS_NATIVE_FILE" ]]; then
  perl -0pi -e 's/CN1_THREAD_STATE_MULTI_ARG instanceObject/CN1_THREAD_STATE_MULTI_ARG JAVA_OBJECT instanceObject/g' "$IOS_NATIVE_FILE"
fi

if ! rg -q "BTDemoBluetoothNativeTests.m in Sources" "$PBXPROJ"; then
  TMP_PBXPROJ="$(mktemp)"
  awk '
    BEGIN {
      buildId = "AA11AA11AA11AA11AA11AA11"
      fileId = "AA22AA22AA22AA22AA22AA22"
      insertedBuildFile = 0
      insertedFileRef = 0
      insertedGroupChild = 0
      insertedSourcesEntry = 0
      inTestsGroup = 0
      inTestSources = 0
    }
    {
      if ($0 ~ /\/\* Begin PBXBuildFile section \*\// && !insertedBuildFile) {
        print
        print "\t\t" buildId " /* BTDemoBluetoothNativeTests.m in Sources */ = {isa = PBXBuildFile; fileRef = " fileId " /* BTDemoBluetoothNativeTests.m */; };"
        insertedBuildFile = 1
        next
      }

      if ($0 ~ /\/\* Begin PBXFileReference section \*\// && !insertedFileRef) {
        print
        print "\t\t" fileId " /* BTDemoBluetoothNativeTests.m */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.c.objc; path = BTDemoBluetoothNativeTests.m; sourceTree = \"<group>\"; };"
        insertedFileRef = 1
        next
      }

      if ($0 ~ /0F634EA818E9ABBC002F3D1D \/\* BTDemoTests \*\/ = \{/) {
        inTestsGroup = 1
      }

      if (inTestsGroup && $0 ~ /^\t\t\tchildren = \($/ && !insertedGroupChild) {
        print
        print "\t\t\t\t" fileId " /* BTDemoBluetoothNativeTests.m */,"
        insertedGroupChild = 1
        next
      }

      if (inTestsGroup && $0 ~ /^\t\t\};$/) {
        inTestsGroup = 0
      }

      if ($0 ~ /0F634E9D18E9ABBC002F3D1D \/\* Sources \*\/ = \{/) {
        inTestSources = 1
      }

      if (inTestSources && $0 ~ /^\t\t\tfiles = \($/ && !insertedSourcesEntry) {
        print
        print "\t\t\t\t" buildId " /* BTDemoBluetoothNativeTests.m in Sources */,"
        insertedSourcesEntry = 1
        next
      }

      if (inTestSources && $0 ~ /^\t\t\};$/) {
        inTestSources = 0
      }

      print
    }
    END {
      if (!insertedBuildFile || !insertedFileRef || !insertedGroupChild || !insertedSourcesEntry) {
        exit 2
      }
    }
  ' "$PBXPROJ" > "$TMP_PBXPROJ"
  mv "$TMP_PBXPROJ" "$PBXPROJ"
fi

if ! rg -q "AA33AA33AA33AA33AA33AA33 /\\* CoreBluetooth.framework in Frameworks \\*/" "$PBXPROJ"; then
  TMP_PBXPROJ="$(mktemp)"
  awk '
    BEGIN {
      buildId = "AA33AA33AA33AA33AA33AA33"
      insertedBuildFile = 0
      insertedFrameworkEntry = 0
      inTestFrameworks = 0
    }
    {
      if ($0 ~ /\/\* Begin PBXBuildFile section \*\// && !insertedBuildFile) {
        print
        print "\t\t" buildId " /* CoreBluetooth.framework in Frameworks */ = {isa = PBXBuildFile; fileRef = 0F63EADB18E9ABBC002F3D1D /* CoreBluetooth.framework */; };"
        insertedBuildFile = 1
        next
      }

      if ($0 ~ /0F634E9E18E9ABBC002F3D1D \/\* Frameworks \*\/ = \{/) {
        inTestFrameworks = 1
      }

      if (inTestFrameworks && $0 ~ /^\t\t\tfiles = \($/ && !insertedFrameworkEntry) {
        print
        print "\t\t\t\t" buildId " /* CoreBluetooth.framework in Frameworks */,"
        insertedFrameworkEntry = 1
        next
      }

      if (inTestFrameworks && $0 ~ /^\t\t\};$/) {
        inTestFrameworks = 0
      }

      print
    }
    END {
      if (!insertedBuildFile || !insertedFrameworkEntry) {
        exit 2
      }
    }
  ' "$PBXPROJ" > "$TMP_PBXPROJ"
  mv "$TMP_PBXPROJ" "$PBXPROJ"
fi

perl -0pi -e 's/BUNDLE_LOADER = "\$\(BUILT_PRODUCTS_DIR\)\/BTDemo\.app\/BTDemo-src";/BUNDLE_LOADER = "\$\(BUILT_PRODUCTS_DIR\)\/BTDemo.app\/BTDemo";/g' "$PBXPROJ"

if [[ -z "${IOS_SIM_DESTINATION:-}" ]]; then
  SIM_ID="$(xcrun simctl list devices available | awk -F '[()]' '/iPhone/ {print $2; exit}')"
  if [[ -z "$SIM_ID" ]]; then
    echo "Could not find an available iPhone simulator." >&2
    exit 1
  fi
  IOS_SIM_DESTINATION="id=$SIM_ID"
fi

echo "Running iOS native tests in: $IOS_SIM_DESTINATION"

xcodebuild \
  -workspace "$IOS_SRC/BTDemo.xcworkspace" \
  -scheme BTDemoTests \
  -configuration Debug \
  -destination "$IOS_SIM_DESTINATION" \
  test
