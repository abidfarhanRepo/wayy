# wayy - Android Navigation App

A clean, modern Android navigation app with solid dark UI and essential navigation features.

## Features

- **Clean Dark UI** - Solid #000000 background with blue accents, no glassmorphism
- **Navigation** - Route calculation using OSRM (no API key required)
- **MapLibre Integration** - Open-source map rendering
- **Location Services** - GPS tracking with Fused Location Provider
- **Offline Maps** - Embedded PMTiles for Doha, Qatar (works without internet)

## Design

- **Minimal HUD** - Map takes 90% of screen, essential info only
- **Solid Colors** - No blur, no glassmorphism, no neon
- **High Contrast** - White text on black for readability
- **48dp Touch Targets** - All buttons meet accessibility standards

### Color Palette

| Color | Hex | Usage |
|-------|-----|-------|
| Background | `#000000` | Full screen |
| Surface | `#171717` | Cards, buttons |
| Primary | `#FFFFFF` | Main text |
| Muted | `#A3A3A3` | Secondary text |
| Accent | `#3B82F6` | Interactive elements |
| Success | `#22C55E` | Good GPS |
| Warning | `#EAB308` | Caution |
| Error | `#EF4444` | Errors |

## Tech Stack

- **Kotlin** - Primary language
- **Jetpack Compose** - Modern UI framework
- **MapLibre Android SDK** - Open-source maps
- **OSRM** - Open Source Routing Machine (free routing)
- **Material 3** - Design system
- **Coroutines & Flow** - Async programming

## Project Structure

```
app/src/main/java/com/wayy/
├── MainActivity.kt              # Main entry point
├── WayyApp.kt                  # Application class
├── ui/
│   ├── theme/                  # Colors, typography, theme
│   ├── components/
│   │   ├── glass/              # GlassCard, GlassButton (solid now)
│   │   ├── gauges/             # Speedometer (horizontal bar)
│   │   ├── navigation/         # TurnBanner, ETACard, LaneGuidance
│   │   ├── camera/             # AR camera overlay
│   │   └── common/             # TopBar, QuickActionsBar, AppDrawer
│   └── screens/                # Main screens
├── viewmodel/                  # NavigationViewModel
├── data/
│   ├── repository/             # Route, POI, Traffic managers
│   ├── settings/               # DataStore preferences
│   └── sensor/                # Location, Orientation managers
└── map/
    ├── MapLibreManager         # Map operations
    └── MapStyleManager         # Dark theme styling
```

## Building the Project

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34
- Minimum SDK 26 (Android 8.0)

### Build with Gradle

```bash
./gradlew assembleDebug
```

### Build with Android Studio

1. Open the project in Android Studio
2. Wait for Gradle sync to complete
3. Click "Run" or press Shift+F10

## Testing

```bash
./gradlew test
./gradlew connectedAndroidTest
```

## Navigation Screens

- **Main** - Map with speedometer, quick actions
- **Search** - Route overview with destination search
- **Settings** - Map, navigation, ML options
- **History** - Recent routes
- **Saved Places** - Bookmarked locations

## Offline Maps (Embedded PMTiles)

**Default: Doha, Qatar PMTiles are embedded in the APK.** The app ships with `doha.pmtiles` covering central Doha. No server or internet required.

### Expand Coverage

Replace `app/src/main/assets/doha.pmtiles`:

```bash
docker run --rm -v $(pwd):/data protomaps/go-pmtiles \
  extract https://build.protomaps.com/20260210.pmtiles /data/doha_expanded.pmtiles \
  --bbox=51.0,24.8,52.0,26.0

mv doha_expanded.pmtiles app/src/main/assets/doha.pmtiles
```

## ML & Data Collection

ML is optional. Place TF Lite models at:
- `app/src/main/assets/ml/model.tflite` (object detection)
- `app/src/main/assets/ml/lane_model.tflite` (lane detection)

## Permissions

- `INTERNET` - Map tiles and routing
- `ACCESS_FINE_LOCATION` - GPS tracking
- `ACCESS_COARSE_LOCATION` - Network location
- `ACCESS_NETWORK_STATE` - Connectivity checks
- `CAMERA` - AR mode (optional)

## Architecture

- **MVVM** - Model-View-ViewModel
- **Repository Pattern** - Data layer abstraction
- **StateFlow** - Reactive state management

## License

MIT License
