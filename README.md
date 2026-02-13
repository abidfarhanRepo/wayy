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

## Testing

```bash
./gradlew test
./gradlew connectedAndroidTest
```

## AR Modes & Capture

Wayy supports three AR modes: `DISABLED`, `PIP_OVERLAY`, and `FULL_AR`.
`FULL_AR` uses ARCore when available and falls back to a full-screen camera overlay when unsupported.

Navigation capture stores video clips and metadata locally (2 GB cap) while AR mode is active at:

```
/data/data/com.wayy/files/capture
```

Diagnostics logs are written to:

```
/data/data/com.wayy/files/diagnostics
```

Example export (USB debugging required):

```bash
adb pull /data/data/com.wayy/files/capture ./capture
adb pull /data/data/com.wayy/files/diagnostics ./diagnostics
```

## Offline Maps

The app auto-downloads a local offline region (~12 km radius) around your first GPS fix using the current map style URL. By default it uses OSM vector tiles via the shortbread style (`https://vector.openstreetmap.org/styles/shortbread/shadow.json`). For production or heavy use, switch to a self-hosted tile server to avoid public tile limits. Offline tiles are stored in `mbgl-offline.db` inside app storage.

## Protomaps (Self-Hosted Qatar)

Wayy supports Protomaps PMTiles served via `pmtiles serve` (ZXY + TileJSON). For Qatar, extract a regional PMTiles file and run the server locally:

```bash
# 1) Download a daily PMTiles build (example date) and extract Qatar
docker pull protomaps/go-pmtiles
docker run --rm -v $(pwd):/data protomaps/go-pmtiles \
  extract https://build.protomaps.com/20260210.pmtiles /data/qatar.pmtiles \
  --bbox=50.7500,24.4700,52.6500,26.6500

# 2) Serve PMTiles as ZXY + TileJSON
docker run --rm -p 8080:8080 -v $(pwd):/data protomaps/go-pmtiles \
  serve /data --public-url http://<HOST_IP>:8080 --cors=*
```

This serves:
```
http://<HOST_IP>:8080/qatar.json
http://<HOST_IP>:8080/qatar/{z}/{x}/{y}.mvt
```

Build the app to point at the TileJSON:

```bash
# Emulator: use 10.0.2.2
./gradlew assembleDebug -Pwayy.pmtilesTilejsonUrl=http://10.0.2.2:8080/qatar.json

# Physical device: use your machine's LAN IP
./gradlew assembleDebug -Pwayy.pmtilesTilejsonUrl=http://<HOST_IP>:8080/qatar.json
```

When `wayy.pmtilesTilejsonUrl` is set, the app uses the bundled `protomaps_style.json` (dark flavor) with English labels.
Offline downloads will cache tiles from the PMTiles server into `mbgl-offline.db` (the `.pmtiles` file itself stays on the server).
The app currently allows cleartext HTTP so the local PMTiles server can be reached; for production, serve over HTTPS and tighten `network_security_config.xml`.

To use a self-hosted TileServer GL Light or Martin instance, pass a style URL at build time:

```bash
./gradlew assembleDebug -Pwayy.mapStyleUrl=http://10.0.2.2:8080/styles/basic/style.json
```

For TileServer GL Light with MBTiles:

```bash
docker run -v $(pwd)/tiles:/data -p 8080:8080 maptiler/tileserver-gl-light
```

This same style URL is used for offline downloads, so the device will cache tiles from your server. Map tile requests include a Wayy User-Agent header and cache responses for up to 7 days. Label language is forced to English when available (`name:en` → `name` fallback).

## ML & Data Collection Roadmap

Current status: ML is not implemented yet (no training containers, no data pipeline, no model serving). The UI exposes a **beta on-device scanning toggle**, but no model is bundled by default.

**Phase 1 (Now): On-device inference only**
- Use pre-trained TF Lite models (lane detection, traffic signs, object detection).
- Models run locally on the phone (optional toggle).
- Drop a model file at `app/src/main/assets/ml/model.tflite` to enable scanning (we now support YOLOv8n Float16 there).
- Scanning uses live camera frames and currently runs when the camera preview is active (AR/PIP mode).

**Phase 2: Opt-in data collection**
- Export drive captures, then upload to a secure bucket with explicit opt-in.
- Annotation pipeline + storage (lanes, signs, behavior tags).
- Training data is Qatar-first and curated from your drives.

**Phase 3: Fine-tuning**
- Train on collected data (no from-scratch training).
- Iterate for Qatar-specific accuracy.

### Option 1 (Fastest): Pre-converted model
Place the model at `app/src/main/assets/ml/model.tflite` (already downloaded on your machine).

```
app/src/main/assets/ml/model.tflite
```

## Training Pipeline (Drive Data → Improved Models)

This pipeline uses capture exports (video + GPS/time/speed metadata) to build datasets and fine-tune models over time.

### 1) Export drives from the phone
Use **Settings → Export Capture + Logs** to generate `wayy_export_*.zip`, then place it in the repo `exports/` folder.

```
mkdir -p exports
# Option A: copy wayy_export_*.zip into ./exports
# Option B (ADB raw capture):
# adb pull /data/data/com.wayy/files/capture ./exports/<stamp>/capture
# adb pull /data/data/com.wayy/files/diagnostics ./exports/<stamp>/diagnostics
```

### 2) Prepare a dataset manifest
```
python3 scripts/ml_pipeline/prepare_wayy_exports.py \
  --input exports \
  --output ml_pipeline/wayy_dataset \
  --copy-media \
  --extract-frames \
  --frame-rate 2
```

This produces:
- `ml_pipeline/wayy_dataset/wayy_locations.jsonl` (GPS/time/speed per frame)
- `ml_pipeline/wayy_dataset/captures/` (video + metadata)
- `ml_pipeline/wayy_dataset/frames/` (optional extracted frames)

### 2b) Automated local pipeline (watch folder)
```
pip install ultralytics
python3 scripts/ml_pipeline/run_pipeline.py --watch
```

Outputs:
- `ml_pipeline/output/<export_id>/road_conditions.geojson`
- `ml_pipeline/output/<export_id>/traffic_segments.geojson`
- `ml_pipeline/output/<export_id>/summary.json`
- `ml_pipeline/output/latest/*` (most recent artifacts)

### 3) Label & train
Label frames with your tool (e.g., CVAT) and export YOLO labels. Then fine-tune:
```
python3 scripts/ml_pipeline/train_yolo.py \
  --data /path/to/data.yaml \
  --base-model app/src/main/assets/ml/yolov8n.pt \
  --epochs 50 --imgsz 640 --export-tflite
```

### 4) Deploy
Replace `app/src/main/assets/ml/model.tflite` with the new model and rebuild the APK.

## Geocoding

Search uses Nominatim with a Photon fallback when rate-limited (e.g. HTTP 509/429).

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

- **Map rendering** with MapLibre + OSM vector tiles (configurable style URL)
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
