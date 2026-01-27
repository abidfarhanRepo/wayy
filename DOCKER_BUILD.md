# Wayy - Docker Build Guide

Build Wayy without Android Studio using Docker! Much lighter on resources.

## Quick Start

### 1. Build the APK
```bash
docker-compose run --rm android-builder
```

### 2. Install on Connected Device
```bash
# Build and install in one command
./scripts/build-and-install.sh

# Or manually:
docker-compose run --rm android-builder adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Available Commands

| Command | Description |
|---------|-------------|
| `docker-compose build` | Build the Docker image (first time only) |
| `docker-compose run --rm android-builder` | Build APK only |
| `./scripts/build.sh` | Quick build script |
| `./scripts/build-and-install.sh` | Build and install to device |
| `./scripts/install.sh` | Install existing APK to device |
| `./scripts/logs.sh` | View ADB logs |
| `./scripts/uninstall.sh` | Uninstall app from device |

---

## Requirements

- Docker installed on your system
- Android device connected via USB with USB debugging enabled
- `docker-compose` installed (comes with Docker Desktop)

---

## Device Setup

### Enable USB Debugging on Android:

1. **Enable Developer Options:**
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times

2. **Enable USB Debugging:**
   - Go to Settings → Developer Options
   - Enable "USB Debugging"
   - Enable "USB Configuration" → "MTP"

3. **Authorize Computer:**
   - Connect device via USB
   - Allow USB debugging when prompted
   - Check "Always allow from this computer"

---

## Troubleshooting

### Device not detected?
```bash
# Check if device is connected
docker-compose run --rm android-builder adb devices

# Should show: "device" (not "unauthorized")
```

### Build fails with SDK errors?
```bash
# Clear cache and rebuild
docker-compose down -v
docker-compose run --rm android-builder
```

### "INSTALL_FAILED_UPDATE_INCOMPATIBLE"?
```bash
# Uninstall old version first
docker-compose run --rm android-builder adb uninstall com.wayy
```

---

## Project Info

- **Default Location:** Doha, Qatar (25.2854°N, 51.5310°E)
- **Demo Route:** Doha → The Pearl, Qatar
- **Package Name:** com.wayy
- **Output:** `app/build/outputs/apk/debug/app-debug.apk`

---

## Customization

### Change Default Location
Edit `app/src/main/java/com/wayy/ui/screens/MainNavigationScreen.kt`:
```kotlin
// Line ~105 - Change these coordinates
.target(LatLng(25.2854, 51.5310))  // Latitude, Longitude
```

### Change Map Style
Edit `app/src/main/java/com/wayy/map/MapStyleManager.kt`:
```kotlin
// Replace styleUrl with:
val styleUrl = "https://your-style-url.com/style.json"
```

### Change Colors
Edit `app/src/main/java/com/wayy/ui/theme/Color.kt` to customize the theme.

---

## What Can Be Customized

| Feature | File Location | Description |
|---------|---------------|-------------|
| **Colors** | `ui/theme/Color.kt` | App colors (lime, cyan, etc.) |
| **Typography** | `ui/theme/Type.kt` | Font sizes and styles |
| **Default Location** | `ui/screens/MainNavigationScreen.kt` | Starting map position |
| **Map Style** | `map/MapStyleManager.kt` | Map tile source & appearance |
| **Route Colors** | `map/WazeStyleManager.kt` | Route glow, border, line colors |
| **Speedometer** | `ui/components/gauges/Speedometer.kt` | Gauge appearance |
| **UI Components** | `ui/components/` | Buttons, cards, overlays |

---

## Advanced: Self-Hosted Map Style

Want full control? Host your own map style:

1. **Use MapLibre Style Editor:** https://editor.maplibre.com/
2. **Export style JSON**
3. **Host on your server**
4. **Update MapStyleManager.kt:**
   ```kotlin
   val styleUrl = "https://your-server.com/style.json"
   ```

---

## Free Map Tile Options

| Provider | Style URL | Notes |
|----------|-----------|-------|
| MapLibre Demo | `https://demotiles.maplibre.org/style.json` | **Current** - Vector tiles |
| MapTiler | Requires API key | Free tier available |
| CartoDB | Custom JSON needed | Dark Matter available |
| OSM | Custom JSON needed | Basic tiles |

---

## Performance Tips

1. **First build:** ~5 minutes (downloads Android SDK)
2. **Subsequent builds:** ~30 seconds (cached)
3. **Keep container running:** Use `docker-compose up` for faster rebuilds
4. **Parallel builds:** Run `docker-compose run -d android-builder` for background builds
