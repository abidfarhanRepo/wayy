#!/bin/bash
# Install Wayy APK on connected Android device

echo "📱 Installing Wayy on connected device..."

# Check if device is connected
docker-compose run --rm android-builder adb devices | grep -q "device$"
if [ $? -ne 0 ]; then
    echo "❌ No device found! Make sure:"
    echo "   1. Device is connected via USB"
    echo "   2. USB debugging is enabled"
    echo "   3. Device is authorized"
    echo ""
    echo "Run: ./scripts/logs.sh to see device status"
    exit 1
fi

# Uninstall old version if exists
echo "🗑️  Removing old version..."
docker-compose run --rm android-builder adb uninstall com.wayy 2>/dev/null

# Install new APK
echo "📲 Installing APK..."
docker-compose run --rm android-builder adb install app/build/outputs/apk/debug/app-debug.apk

if [ $? -eq 0 ]; then
    echo "✅ Installation successful!"
    echo ""
    echo "Look for 'Wayy' in your app drawer"
else
    echo "❌ Installation failed!"
    echo "Run: ./scripts/logs.sh to see error logs"
    exit 1
fi
