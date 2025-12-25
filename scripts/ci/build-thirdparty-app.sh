#!/usr/bin/env bash
# Simple helper to build a Codename One Maven app using published artifacts
# from Maven Central. Useful for CI jobs that want to validate the toolchain
# without building Codename One from source.
set -euo pipefail

function usage() {
  cat <<'USAGE'
Usage: build-thirdparty-app.sh <android|ios>

Options are provided via environment variables:
  APP_DIR            Path to an existing Codename One Maven project. If set,
                     the script builds this directory directly.
  APP_REPO           Git URL for a Codename One Maven project to clone.
                     Ignored when APP_DIR is set.
  APP_REF            Optional git ref (branch, tag, or commit) to check out
                     after cloning APP_REPO.
  WORK_DIR           Temporary workspace for cloned/copied sources. Default:
                     <repo>/scripts/ci/.thirdparty-app
  CODENAMEONE_VERSION  Codename One runtime version to request from Maven
                     Central. Defaults to LATEST.
  CODENAMEONE_PLUGIN_VERSION
                     Codename One Maven plugin version. Defaults to
                     CODENAMEONE_VERSION.
  BUILD_TARGET       Overrides the codename1.buildTarget value passed to Maven.
                     Defaults to android-device for android or ios-source for
                     ios.

Examples:
  # Build a local Maven app for Android
  APP_DIR=/path/to/app ./scripts/ci/build-thirdparty-app.sh android

  # Build a remote project for iOS from a specific tag
  APP_REPO=https://github.com/example/my-cn1-app \
  APP_REF=v1.2.3 \
  CODENAMEONE_VERSION=8.0.0 \
  ./scripts/ci/build-thirdparty-app.sh ios

  # Use the bundled hello-codenameone sample as a fallback
  ./scripts/ci/build-thirdparty-app.sh android
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

CODENAMEONE_VERSION=${CODENAMEONE_VERSION:-LATEST}
CODENAMEONE_PLUGIN_VERSION=${CODENAMEONE_PLUGIN_VERSION:-$CODENAMEONE_VERSION}
WORK_DIR=${WORK_DIR:-"$(cd "$(dirname "${BASH_SOURCE[0]}")"/.. && pwd)/ci/.thirdparty-app"}
APP_WORK_DIR="$WORK_DIR/app"

function info() {
  echo "[build-thirdparty] $*"
}

function prepare_workspace() {
  rm -rf "$WORK_DIR"
  mkdir -p "$WORK_DIR"
}

function copy_local_app() {
  local source_dir=$1
  info "Using local app at $source_dir"
  cp -R "$source_dir" "$APP_WORK_DIR"
}

function clone_app() {
  local repo_url=$1
  info "Cloning $repo_url"
  git clone --depth 1 "$repo_url" "$APP_WORK_DIR"
  if [[ -n ${APP_REF:-} ]]; then
    pushd "$APP_WORK_DIR" >/dev/null
    git fetch origin "$APP_REF" --depth 1
    git checkout "$APP_REF"
    popd >/dev/null
  fi
}

function use_bundled_sample() {
  local root_dir
  root_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")"/../.. && pwd)
  local sample_dir="$root_dir/scripts/hellocodenameone"
  info "Falling back to bundled sample at $sample_dir"
  cp -R "$sample_dir" "$APP_WORK_DIR"
}

function prepare_app() {
  prepare_workspace
  if [[ -n ${APP_DIR:-} ]]; then
    copy_local_app "$APP_DIR"
    return
  fi
  if [[ -n ${APP_REPO:-} ]]; then
    clone_app "$APP_REPO"
    return
  fi
  use_bundled_sample
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
      build_target=${BUILD_TARGET:-android-device}
      ;;
    ios)
      build_target=${BUILD_TARGET:-ios-source}
      ;;
  esac

  pushd "$APP_WORK_DIR" >/dev/null
  info "Building $TARGET with Codename One $CODENAMEONE_VERSION"
  "$mvn_cmd" \
    -B \
    -U \
    -DskipTests \
    -Dcn1.version="$CODENAMEONE_VERSION" \
    -Dcn1.plugin.version="$CODENAMEONE_PLUGIN_VERSION" \
    -Dcodename1.platform="$TARGET" \
    -Dcodename1.buildTarget="$build_target" \
    package
  popd >/dev/null
}

prepare_app
build_target
info "Build complete. Output available under $APP_WORK_DIR"
