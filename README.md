# wayy - Android Navigation App

A clean, modern Android navigation app with solid dark UI and essential navigation features.

## Features

- **Clean Dark UI** - Solid #000000 background with blue accents, no glassmorphism
- **GPS Smoothing** - Kalman filter reduces GPS jitter and jumping
- **Map Matching** - Snaps GPS coordinates to roads for accurate positioning
- **Global Search** - Search for places worldwide using Nominatim/Photon
- **Navigation** - Route calculation using OSRM (no API key required)
- **MapLibre Integration** - Open-source map rendering
- **Location Services** - GPS tracking with Fused Location Provider
- **Offline Maps** - Embedded PMTiles for Doha, Qatar (works without internet)

## Screens

### 1. Main Navigation Screen
The primary map view with navigation controls.

**Components:**
- Map view (full screen)
- Top bar with GPS indicator
- Speed display (horizontal bar)
- Quick action buttons (Search, Navigate, Record)

### 2. Search Destination Screen
Clean search interface for finding destinations.

**Features:**
- Search input field
- Recent destinations list
- Direct navigation to selected destination

### 3. App Drawer (Hamburger Menu)
Side menu accessed via hamburger icon.

**Menu Items:**
- Search destination
- Recent routes
- Saved places
- Settings
- About

### 4. Settings Screen
App configuration options.

**Sections:**
- Map settings (TileJSON, Map Style URL)
- Navigation settings (Voice, Speed alerts, Camera alerts)
- ML & Recording (Detection model, Lane model)
- About

### 5. History Screen
View recent navigation routes.

### 6. Saved Places Screen
Manage bookmarked locations.

## Design

### Color Palette

| Role | Hex | Usage |
|------|-----|-------|
| Background | `#000000` | Full screen |
| Surface | `#171717` | Cards, buttons |
| Surface Variant | `#262626` | Borders |
| Primary | `#FFFFFF` | Main text |
| Primary Muted | `#A3A3A3` | Secondary text |
| Accent | `#3B82F6` | Interactive elements |
| Success | `#22C55E` | Good GPS |
| Warning | `#EAB308` | Caution |
| Error | `#EF4444` | Errors |

### Typography

- **Title**: 18-28sp Bold
- **Body**: 14-15sp Regular/Medium  
- **Caption**: 11-13sp Regular

### Spacing

- 8pt grid system
- 48dp minimum touch targets
- 12dp corner radius (cards), 8dp (buttons)

## Architecture

```
app/src/main/java/com/wayy/
├── MainActivity.kt              # Main entry point & navigation
├── ui/
│   ├── theme/                  # Colors, typography, theme
│   ├── components/
│   │   ├── common/            # TopBar, QuickActionsBar, AppDrawer
│   │   ├── gauges/            # Speedometer
│   │   └── navigation/        # TurnBanner, ETACard
│   └── screens/
│       ├── MainNavigationScreen.kt
│       ├── SearchDestinationScreen.kt
│       ├── SettingsScreen.kt
│       ├── HistoryScreen.kt
│       └── SavedPlacesScreen.kt
└── viewmodel/
    └── NavigationViewModel.kt
```

## Building

### Prerequisites

- Android Studio Hedgehog (2023.1.1)+
- JDK 17
- Android SDK 34
- Minimum SDK 26

### Build

```bash
./gradlew assembleDebug
```

### Install

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Permissions

- `INTERNET` - Map tiles and routing
- `ACCESS_FINE_LOCATION` - GPS tracking
- `ACCESS_COARSE_LOCATION` - Network location
- `CAMERA` - AR mode (optional)

## Offline Maps

Default: Doha, Qatar PMTiles embedded in APK. Works without internet in covered area.

## Tech Stack

- Kotlin
- Jetpack Compose
- MapLibre Android SDK
- OSRM (routing)
- Material 3

## License

MIT License
