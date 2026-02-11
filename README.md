# Wayy - Android Navigation App

A modern Android navigation app with glassmorphism UI, real-time speedometer, and cyberpunk aesthetics.

## Features

- **Glassmorphism UI** - Translucent panels with vibrant lime/cyan accents
- **Navigation** - Route calculation using OSRM (no API key required)
- **MapLibre Integration** - Open-source map rendering
- **Location Services** - GPS tracking with Fused Location Provider
- **Cyberpunk Aesthetic** - Dark theme with glowing accents

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
│   │   ├── glass/              # GlassButton, GlassCard
│   │   ├── gauges/             # Speedometer
│   │   ├── navigation/         # NavigationOverlay, ETACard
│   │   ├── camera/             # AR PiP camera overlay components
│   │   └── common/             # TopBar, StatCard, QuickActionsBar
│   └── screens/                # MainNavigationScreen, RouteOverviewScreen
├── viewmodel/                  # NavigationViewModel
├── data/
│   ├── model/                  # Route data models
│   ├── repository/             # RouteRepository + DataStore managers
│   ├── local/                  # Room trip logging (GPS + street segments)
│   └── sensor/                 # LocationManager + DeviceOrientationManager
└── map/
    ├── MapLibreManager         # Map operations
    ├── MapStyleManager         # Dark theme styling
    └── MapLibreView            # Composable wrapper
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

## Permissions

The app requires the following permissions:
- `INTERNET` - For map tiles and routing
- `ACCESS_FINE_LOCATION` - For GPS tracking
- `ACCESS_COARSE_LOCATION` - For network location
- `ACCESS_NETWORK_STATE` - For connectivity checks

## Design System

### Colors

- **Primary Lime** - `#A3E635` - Main accent
- **Primary Cyan** - `#22D3EE` - Secondary accent
- **Primary Purple** - `#A855F7` - AR mode
- **Primary Orange** - `#FB923C` - 3D view
- **Bg Primary** - `#020617` - Main background
- **Bg Secondary** - `#0F172A` - Cards
- **Bg Tertiary** - `#1E293B` - Elevated surfaces

### Typography

- Scale: 12px to 49px (Major Third - 1.25 ratio)
- Font: System default (San Francisco/Roboto)
- Weights: 400 (Normal), 500 (Medium), 600 (SemiBold), 700 (Bold)

## Architecture

- **MVVM** - Model-View-ViewModel pattern
- **Repository Pattern** - Data layer abstraction
- **StateFlow** - Reactive state management
- **Dependency Injection** - Manual (can add Hilt later)

## Current MVP Status (Implemented)

- **Map rendering** with MapLibre + OSM tiles
- **Live GPS location** with camera follow and user marker
- **Destination search** via Nominatim (typed query → results list)
- **Routing + navigation** via OSRM (route line + turn banner + ETA/remaining distance)
- **Minimal navigation shell** focused on function (demo screens removed)
- **Route history** persistence + recent routes list
- **Local POI + traffic reporting** (stored on-device)
- **Trip telemetry logging** (Room DB: GPS samples + per-street segment timing)
- **Local traffic stats aggregation** (per-street time buckets for ETA tuning)
- **Traffic intensity debug overlay** (map heat layer + speed metrics)
- **POI categories + filters** (gas/food/parking/lodging)
- **POI marker interactions** (tap to view, long-press to add, swipe to delete)
- **Traffic report improvements** (severity selection, pulse markers, expiry)
- **Route traffic badge** (severity overlay on route line)
- **AR PiP camera overlay** (turn arrow + lane guidance card)
- **Turn banner metrics** (ETA/remaining/speed/traffic/report summary in top bar)
- **Bottom quick actions visibility** (opaque buttons for map readability)

## Current MVP Status (Pending)

- **Traffic intensity validation** + tuning
- **On-device validation** (POIs, traffic, ETA stability)
- **AR overlay validation** (camera permission + performance)
- **Offline maps** and additional traffic/POI enhancements

## Future Enhancements

- Real ML/Camera integration for lane detection
- Offline map support
- Voice navigation
- Route saving and history
- Points of interest
- Traffic data integration
- Self-hosted OSRM instance

## License

MIT License - Feel free to use this project for learning and development.

## Credits

- **MapLibre** - Open-source map rendering
- **OSRM** - Open-source routing engine
- **Jetpack Compose** - Modern Android UI toolkit
