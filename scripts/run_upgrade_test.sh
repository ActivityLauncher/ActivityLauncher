#!/usr/bin/env bash
set -euo pipefail

# Wait for boot (android-emulator-runner already does this)
echo "Waiting for device..."
adb wait-for-device

python3 scripts/test_upgrade_shortcut.py
