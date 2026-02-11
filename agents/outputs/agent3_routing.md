Agent 3 — Routing & OSRM Integration

Files added

- `app/src/main/java/com/wayy/data/model/Route.kt` — `Route` and `LatLng` data classes.
- `app/src/main/java/com/wayy/data/repository/RouteRepository.kt` — `RouteRepository` with a suspend `getRoute(...)` method that calls the OSRM demo server and parses GeoJSON geometry into `Route`.
- `scripts/osrm_test.py` — Python script that calls the public OSRM demo service and prints a summary.

Sample cURL

```bash
curl "https://router.project-osrm.org/route/v1/driving/-122.4194,37.7749;-122.4094,37.7849?overview=full&geometries=geojson"
```

Notes

- `RouteRepository` uses `OkHttp` and `Gson` (both already dependencies in the project). The function is `suspend` and uses `withContext(Dispatchers.IO)` to perform network IO.
- For production, consider self-hosted OSRM or a paid routing API with SLA. The demo server is rate-limited.
- Next: wire `RouteRepository` into `NavigationViewModel`, call `getRoute(...)` when destination is selected and pass the geometry to `MapLibreManager.drawRoute(...)`.
