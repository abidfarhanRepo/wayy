# Wayy App - Logging Guide

This document describes the comprehensive logging system added throughout the app to facilitate debugging and monitoring.

## Quick Reference

### Filter Logs by Component
```bash
# View Location Manager logs
adb logcat -s LocationManager:D

# View Navigation logs
adb logcat -s GpsKalmanFilter:D MapMatcher:D

# View Route/Search logs
adb logcat -s RouteRepository:D

# View all Wayy logs
adb logcat | grep -E "Wayy|LocationManager|GpsKalmanFilter|MapMatcher|RouteRepository"
```

### Log Levels Used
- **VERBOSE (V)**: Detailed flow tracking, values
- **DEBUG (D)**: Normal operations, successful operations
- **WARNING (W)**: Recoverable issues, timeouts, fallbacks
- **ERROR (E)**: Failures, exceptions

## Location Manager (`LocationManager`)

### Settings Changes
```
[SETTINGS] Kalman filter enabled/disabled
[SETTINGS] Map matching enabled/disabled
```

### Location Updates Flow
```
[LOCATION_RAW] Raw GPS data from provider
[SPEED_RESOLVE] Speed calculation decisions
[SPEED_COMPUTE] Speed from distance/time delta
[FILTER_APPLY] Kalman filter processing
[FILTER_RESULT] Filter output
[FILTER_REJECT] Location rejected by filter
[LOCATION_PROCESSED] Final location sent to UI
[LOCATION_SHIFT] Distance moved by filtering
```

### Update Statistics
```
[STATS] Total: X, Filtered: Y, Rejected: Z
[FUSED_UPDATE] #N from FusedLocationProvider
[SYSTEM_UPDATE] #N from system GPS/Network
```

### Permission & Providers
```
[PERMISSION_DENIED] Cannot start - permission issue
[SYSTEM_PROVIDERS] Available providers list
[SYSTEM_REGISTER] Registering GPS/Network provider
```

### Session Lifecycle
```
[UPDATES_START] Starting location updates session
[UPDATES_STOP] Stopping session
[UPDATES_CONFIG] Configuration details
[UPDATES_STATS] Final session statistics
```

### One-time Location Requests
```
[LAST_KNOWN] Requesting last known location
[LAST_KNOWN_ERROR] Error getting last location
[CURRENT_LOC] Requesting fresh location
[CURRENT_LOC_REQUEST] Specific priority request
```

## GPS Kalman Filter (`GpsKalmanFilter`)

### Filter Processing
```
[KALMAN_INPUT] Raw input values
[KALMAN_PARAMS] Process/measurement noise values
[KALMAN_DISTANCE] Distance from last position
[KALMAN_STATE] Filter state (error, gain)
[KALMAN_OUTPUT] Final filtered position
[KALMAN_SHIFT] Lat/lon shift amount
```

### Filter Decisions
```
[KALMAN_SKIP] Poor accuracy or small movement
[KALMAN_REJECT] Large jump detected
[KALMAN_INIT] First measurement initialization
```

## Map Matcher (`MapMatcher`)

### Single Point Matching
```
[MATCHER_START] Beginning road snap
[MATCHER_REQUEST] OSRM API URL
[MATCHER_RESPONSE] HTTP response received
[MATCHER_PARSE] Parsing waypoint data
[MATCHER_SKIP] Too far from road
[MATCHER_SUCCESS] Successfully snapped to road
[MATCHER_ERROR] HTTP or parsing error
```

### Path Matching (Batch)
```
[MATCHPATH_START] Batch matching begin
[MATCHPATH_INPUT] Input point count
[MATCHPATH_REQUEST] API call details
[MATCHPATH_RESPONSE] Response received
[MATCHPATH_POINT] Individual point match status
[MATCHPATH_SUCCESS] Completion stats
```

## Route Repository (`RouteRepository`)

### Route Calculation
```
[ROUTE_START] New route request
[ROUTE_INPUT] Start/end coordinates
[ROUTE_REQUEST] OSRM URL being called
[ROUTE_NETWORK] Sending request
[ROUTE_RESPONSE] HTTP response received
[ROUTE_PARSE] Parsing response
[ROUTE_SUCCESS] Route found details
[ROUTE_ERROR] HTTP or parsing error
[ROUTE_EXCEPTION] Unexpected exception
[ROUTE_STATS] Performance statistics
```

### Search Operations
```
[SEARCH_START] New search initiated
[SEARCH_STRATEGY] Number of attempts planned
[SEARCH_ATTEMPT_N] Starting attempt N
[SEARCH_NOMINATIM_N] Nominatim call details
[SEARCH_FALLBACK] Trying Photon backup
[SEARCH_PHOTON] Photon API call
[SEARCH_SUCCESS] Results found
[SEARCH_FAILED] All attempts exhausted
[SEARCH_STATS] Performance statistics
```

## Common Log Patterns

### Successful Flow Example
```
D/LocationManager: [UPDATES_START] Starting location updates
D/LocationManager: [SYSTEM_REGISTER] Registering GPS provider
V/LocationManager: [FUSED_UPDATE] #1 received from provider=fused
V/LocationManager: [LOCATION_RAW] lat=25.2854, lon=51.5310, accuracy=12.5m
V/LocationManager: [FILTER_APPLY] Applying Kalman filter
D/GpsKalmanFilter: [KALMAN_OUTPUT] Original: (25.2854, 51.5310) -> Filtered: (25.2854, 51.5310)
D/LocationManager: [LOCATION_PROCESSED] lat=25.2854, lon=51.5310, smoothed=true, confidence=0.95
```

### Error Recovery Example
```
W/LocationManager: [SYSTEM_REGISTER] GPS provider is disabled
W/LocationManager: [LAST_KNOWN] Fused provider returned null
D/LocationManager: [SYSTEM_LAST_KNOWN] Trying system providers
D/LocationManager: [SYSTEM_LAST_KNOWN] NETWORK location available
D/LocationManager: [SYSTEM_LAST_KNOWN] Selected best location from NETWORK
```

### Route Calculation Example
```
D/RouteRepository: [ROUTE_START] Request #5
D/RouteRepository: [ROUTE_INPUT] Start: (25.2854, 51.5310)
D/RouteRepository: [ROUTE_INPUT] End: (25.3212, 51.5521)
D/RouteRepository: [ROUTE_NETWORK] Sending request to OSRM...
D/RouteRepository: [ROUTE_SUCCESS] Route found: distance=5432m, duration=420s
D/RouteRepository: [ROUTE_SUCCESS] Total time: 850ms (network: 820ms)
```

### Search Example
```
D/RouteRepository: [SEARCH_START] Query: 'starbucks', Location: (25.2854, 51.5310)
D/RouteRepository: [SEARCH_ATTEMPT_1] radius=10.0km, bounded=true
V/RouteRepository: [SEARCH_NOMINATIM_1] Found 8 results
D/RouteRepository: [SEARCH_SUCCESS] Found 8 results in attempt #1, took 1250ms
```

## Debugging Tips

### 1. GPS Not Smoothing
Check for:
- `[SETTINGS] Kalman filter enabled` - should see "enabled"
- `[FILTER_APPLY]` - should see this on every update
- `[FILTER_RESULT]` - check confidence values
- `[KALMAN_REJECT]` - might be rejecting due to jumps

### 2. Search Not Working
Check for:
- `[SEARCH_START]` - verify query is being sent
- `[SEARCH_ATTEMPT_1]` - check attempt parameters
- `[SEARCH_NOMINATIM_1]` HTTP response code
- `[SEARCH_FALLBACK]` - if Nominatim fails
- Network timeouts in `[SEARCH_EXCEPTION]`

### 3. Location Jumping
Check for:
- `[KALMAN_REJECT]` - should catch large jumps
- `[LOCATION_RAW]` vs `[LOCATION_PROCESSED]` - compare values
- `[STATS]` - see rejection rate
- Accuracy values in `[LOCATION_RAW]`

### 4. No Location Updates
Check for:
- `[PERMISSION_DENIED]` - permission issues
- `[SYSTEM_REGISTER]` - provider registration
- `[SYSTEM_PROVIDERS]` - which providers available
- `[FUSED_UPDATE]` or `[SYSTEM_UPDATE]` - receiving updates?

### 5. Slow Route Calculation
Check for:
- `[ROUTE_NETWORK]` to `[ROUTE_SUCCESS]` time difference
- `[ROUTE_STATS]` - overall performance metrics
- HTTP timeouts in `[ROUTE_ERROR]`

## Performance Monitoring

### Track Filter Performance
```bash
adb logcat -s GpsKalmanFilter:D | grep "STATS"
```

### Track Route Performance
```bash
adb logcat -s RouteRepository:D | grep -E "ROUTE_STATS|ROUTE_SUCCESS"
```

### Track Search Performance
```bash
adb logcat -s RouteRepository:D | grep -E "SEARCH_STATS|SEARCH_SUCCESS"
```

### Track Location Update Rate
```bash
adb logcat -s LocationManager:D | grep "UPDATES_STATS"
```

## Logcat Commands for Common Scenarios

### Full Debug Session
```bash
adb logcat -c  # Clear logs
# Use the app...
adb logcat -d | grep -E "Wayy|LocationManager|GpsKalmanFilter|MapMatcher|RouteRepository" > wayy_debug.log
```

### Real-time Monitoring
```bash
# Watch location updates
adb logcat -s LocationManager:D | grep "LOCATION_PROCESSED"

# Watch routing
adb logcat -s RouteRepository:D | grep -E "ROUTE_|SEARCH_"

# Watch errors only
adb logcat -s LocationManager:E GpsKalmanFilter:E MapMatcher:E RouteRepository:E
```

### Export Specific Component
```bash
# Export all location manager logs
adb logcat -d -s LocationManager:V > location_logs.txt

# Export all routing logs
adb logcat -d -s RouteRepository:V > routing_logs.txt
```

## Adding New Logs

When adding logs to new components, follow these conventions:

1. **Use component-specific TAG constant**
2. **Prefix with [CATEGORY]** for easy filtering
3. **Include relevant values** in the log
4. **Use appropriate log level:**
   - `Log.v()` - Detailed flow, values
   - `Log.d()` - Normal operations
   - `Log.w()` - Recoverable issues
   - `Log.e()` - Failures with exceptions

Example:
```kotlin
private const val TAG = "MyComponent"

Log.d(TAG, "[OPERATION_START] Starting operation with param=$value")
Log.v(TAG, "[OPERATION_DETAIL] Intermediate value: $intermediate")
Log.w(TAG, "[OPERATION_WARNING] Recoverable issue: $message")
Log.e(TAG, "[OPERATION_ERROR] Failed: ${e.message}", e)
```

## Privacy Note

Logs may contain location coordinates. When sharing logs:
- Remove or obfuscate precise coordinates if sharing publicly
- Location data is logged at DEBUG and VERBOSE levels only
- Consider using `adb logcat` filters to exclude location data when needed