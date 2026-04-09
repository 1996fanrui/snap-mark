#!/usr/bin/env bash
# build.sh — Build the SnapMark APK and copy it to build/ for easy access.
#
# Usage:
#   bash scripts/build.sh           # build debug APK
#   bash scripts/build.sh install   # build and install to connected device

set -e

export JAVA_HOME="${JAVA_HOME:-/home/fanrui/.local/jdk/jdk-17.0.2}"

echo "[INFO] Building release APK..."
./gradlew assembleRelease

mkdir -p build
cp app/build/outputs/apk/release/app-release.apk build/SnapMark.apk
echo "[INFO] APK copied to build/SnapMark.apk"

if [ "${1:-}" = "install" ]; then
    echo "[INFO] Installing to device..."
    adb install -r build/SnapMark.apk
    echo "[INFO] Installed."
fi
