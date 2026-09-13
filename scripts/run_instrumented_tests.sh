#!/usr/bin/env bash
set -euo pipefail

echo "Waiting for emulated storage to be mounted..."
storage_ready=false
for i in $(seq 1 30); do
  if adb shell ls /storage/emulated/0 >/dev/null 2>&1; then
    echo "Storage mounted."
    storage_ready=true
    break
  fi
  echo "  attempt $i: storage not ready yet, waiting 2s..."
  sleep 2
done
if [ "$storage_ready" != "true" ]; then
  echo "ERROR: /storage/emulated/0 not mounted after 60s"
  exit 1
fi

adb shell mkdir -p /storage/emulated/0/screenshots
res=0
./gradlew connectedOssNoadsDebugAndroidTest -PAPPID=de.szalkowski.activitylauncher.oss --build-cache || res=$?
mkdir -p screenshots
adb pull /storage/emulated/0/screenshots/. screenshots/ || true
exit $res
