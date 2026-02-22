#!/bin/bash
# Uninstall Wayy from connected device

echo "🗑️  Uninstalling Wayy from device..."

docker-compose run --rm android-builder adb uninstall com.wayy

if [ $? -eq 0 ]; then
    echo "✅ Wayy uninstalled successfully!"
else
    echo "ℹ️  Wayy was not installed or uninstall failed."
fi
