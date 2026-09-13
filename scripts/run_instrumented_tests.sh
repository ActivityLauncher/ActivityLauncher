#!/usr/bin/env bash
set -euo pipefail

# Wait for boot (android-emulator-runner already does this, but be safe)
echo "Waiting for device..."
adb wait-for-device

res=0
./gradlew connectedOssNoadsDebugAndroidTest -PAPPID=de.szalkowski.activitylauncher.oss --build-cache || res=$?
mkdir -p screenshots
adb pull /storage/emulated/0/screenshots/. screenshots/ || true
exit $res
