#!/bin/bash
# Quick build script for Wayy Android app

echo "🔨 Building Wayy..."

docker-compose run --rm android-builder

if [ $? -eq 0 ]; then
    echo "✅ Build successful!"
    echo "📦 APK: app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "To install on device, run:"
    echo "  ./scripts/install.sh"
else
    echo "❌ Build failed!"
    exit 1
fi
