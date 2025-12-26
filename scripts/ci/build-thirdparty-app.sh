#!/usr/bin/env bash
# Helper to build a Codename One Maven app.
# Useful for CI jobs that want to validate the toolchain.
set -euo pipefail

function usage() {
  cat <<'USAGE'
Usage: build-thirdparty-app.sh <android|ios>

Options are provided via environment variables:
  APP_DIR            Path to an existing Codename One Maven project. (Required)
  CODENAMEONE_VERSION  Codename One runtime version to pass to Maven.
                     If unset, uses the version defined in pom.xml.
  CODENAMEONE_PLUGIN_VERSION
                     Codename One Maven plugin version.
  BUILD_TARGET       Overrides the codename1.buildTarget value passed to Maven.
                     Defaults to android-source for android or ios-source for
                     ios.

Examples:
  # Build a local Maven app for Android
  APP_DIR=BTDemo ./scripts/ci/build-thirdparty-app.sh android
USAGE
}

if [[ ${1:-} == "--help" || ${1:-} == "-h" ]]; then
  usage
  exit 0
fi

TARGET=${1:-android}
if [[ "$TARGET" != "android" && "$TARGET" != "ios" ]]; then
  echo "Unsupported target '$TARGET'. Expected 'android' or 'ios'." >&2
  usage
  exit 1
fi

if [[ -z ${APP_DIR:-} ]]; then
  echo "APP_DIR must be set." >&2
  exit 1
fi

APP_WORK_DIR="$APP_DIR"

function info() {
  echo "[build-app] $*"
}

function resolve_maven() {
  local mvnw="$APP_WORK_DIR/mvnw"
  if [[ -x "$mvnw" ]]; then
    echo "$mvnw"
  else
    echo "mvn"
  fi
}

function build_target() {
  local mvn_cmd
  mvn_cmd=$(resolve_maven)
  local build_target
  case "$TARGET" in
    android)
      build_target=${BUILD_TARGET:-android-source}
      ;;
    ios)
      build_target=${BUILD_TARGET:-ios-source}
      ;;
  esac

  pushd "$APP_WORK_DIR" >/dev/null
  info "Building $TARGET (target: $build_target)"

  local mvn_args=(
    -B
    -U
    -DskipTests
    -Dcodename1.platform="$TARGET"
    -Dcodename1.buildTarget="$build_target"
  )

  if [[ -n ${CODENAMEONE_VERSION:-} ]]; then
    mvn_args+=("-Dcn1.version=$CODENAMEONE_VERSION")
  fi

  if [[ -n ${CODENAMEONE_PLUGIN_VERSION:-} ]]; then
    mvn_args+=("-Dcn1.plugin.version=$CODENAMEONE_PLUGIN_VERSION")
  fi

  # Use cn1:build to generate native sources
  "$mvn_cmd" "${mvn_args[@]}" cn1:build
  popd >/dev/null
}

build_target
info "Build complete. Output available under $APP_WORK_DIR"
