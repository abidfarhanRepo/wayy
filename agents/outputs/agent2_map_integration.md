Agent 2 — Map Integration

Summary

- MapLibre dependency already present in `app/build.gradle.kts`.
- Map composables and managers exist: `MapLibreView.kt`, `MapLibreManager.kt`, `MapStyleManager.kt`, `WazeStyleManager.kt`.
- `WayyApp` initializes MapLibre on app start.

What I did

- Verified MapLibre SDK in `app/build.gradle.kts` (org.maplibre.gl:android-sdk:11.0.0).
- Verified permissions in `app/src/main/AndroidManifest.xml`.
- Verified map lifecycle composables and manager implementation.

Next steps (Agent 3)

- Implement `RouteRepository` OSRM client and route parsing.
- Expose route model and add ViewModel wiring to push route geometry to `MapLibreManager.drawRoute()`.

Quick usage snippet (already compatible with codebase)

In a Compose screen (example):

MapViewAutoLifecycle(
    manager = MapLibreManager(context),
    modifier = Modifier.fillMaxSize(),
    onMapReady = { map ->
        // Style and initial camera already handled by MapStyleManager
    }
)

Notes

- Map tile provider uses CartoDB Dark tiles (no API key). For production, consider MapTiler or self-hosted tiles and secure any API keys via build config.
- OSRM routing calls will be added in Agent 3.
