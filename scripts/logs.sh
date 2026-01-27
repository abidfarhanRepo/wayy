#!/bin/bash
# View ADB logs for Wayy app

echo "📋 Showing Wayy logs (Ctrl+C to exit)..."
echo ""

docker-compose run --rm android-builder bash -c "export ANDROID_SDK_ROOT=/opt/android-sdk && export PATH=\$PATH:\$ANDROID_SDK_ROOT/platform-tools && adb logcat -c && adb logcat -v time | grep -iE '(Wayy|MapStyle|MapLibre|OSRM|tile|style)'"
