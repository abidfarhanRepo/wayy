#!/bin/bash
# Build and install Wayy in one command

echo "🚀 Building and installing Wayy..."

# Build the APK
echo ""
echo "🔨 Step 1: Building APK..."
docker-compose run --rm android-builder

if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi

echo ""
echo "✅ Build successful!"
echo ""
echo "📱 Step 2: Installing on device..."

# Check if device is connected
docker-compose run --rm android-builder adb devices | grep -q "device$"
if [ $? -ne 0 ]; then
    echo "❌ No device found! Connect your Android device and enable USB debugging."
    echo ""
    echo "To install manually later, run:"
    echo "  ./scripts/install.sh"
    exit 1
fi

# Uninstall old version
echo "🗑️  Removing old version..."
docker-compose run --rm android-builder adb uninstall com.wayy 2>/dev/null

# Install new APK
echo "📲 Installing APK..."
docker-compose run --rm android-builder adb install app/build/outputs/apk/debug/app-debug.apk

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Done! Wayy is now on your device."
    echo ""
    echo "🎉 Launch the app and explore Qatar!"
else
    echo "❌ Installation failed!"
    exit 1
fi
