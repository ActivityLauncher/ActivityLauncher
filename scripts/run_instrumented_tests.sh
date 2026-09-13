#!/usr/bin/env bash
set -euo pipefail

echo "Waiting for device & PackageManager..."
adb wait-for-device

for i in {1..30}; do
  if adb shell pm path android >/dev/null 2>&1; then
    echo "PackageManager service is ready!"
    break
  fi
  echo "Waiting for PackageManager service..."
  sleep 2
done

res=0
./gradlew connectedOssNoadsDebugAndroidTest -PAPPID=de.szalkowski.activitylauncher.oss --build-cache || res=$?
mkdir -p screenshots
adb pull /storage/emulated/0/screenshots/. screenshots/ || true
exit $res
