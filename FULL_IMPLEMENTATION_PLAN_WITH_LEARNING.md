# Wayy Navigation App - Full Implementation Plan (Updated with Self-Learning System)

## Executive Summary

Wayy is a comprehensive Android navigation application featuring two distinct but complementary learning systems:

1. **Self-Learning System** (NEW): On-device intelligence that learns user preferences from GPS data and route choices
2. **ML Pipeline**: Cloud-based road condition detection from video captures

This document outlines the complete system architecture, all implemented phases, and the autonomous learning infrastructure.

---

## Table of Contents

1. [System Architecture Overview](#1-system-architecture-overview)
2. [Implemented Phases](#2-implemented-phases)
3. [Self-Learning System (NEW)](#3-self-learning-system-new)
4. [ML Pipeline Architecture](#4-ml-pipeline-architecture)
5. [Android App Components](#5-android-app-components)
6. [Data Collection Infrastructure](#6-data-collection-infrastructure)
7. [Backend Processing System](#7-backend-processing-system)
8. [Roadmap & Future Enhancements](#8-roadmap--future-enhancements)

---

## 1. System Architecture Overview

### 1.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    WAYY NAVIGATION SYSTEM v2.0                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    SELF-LEARNING SYSTEM (On-Device)              │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │   │
│  │  │ Preference   │  │ Route        │  │ Decision     │          │   │
│  │  │ Learning     │──►Scoring      │──►Logger       │          │   │
│  │  │ Engine       │  │ Service      │  │ (GPS Data)   │          │   │
│  │  └──────────────┘  └──────────────┘  └──────────────┘          │   │
│  │         │                   │                                       │   │
│  │         ▼                   ▼                                       │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │   │
│  │  │ Traffic      │  │ Anomaly      │  │ Pattern      │          │   │
│  │  │ Models       │  │ Detection    │  │ Recognition  │          │   │
│  │  └──────────────┘  └──────────────┘  └──────────────┘          │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                              │                                          │
│                              ▼                                          │
│  ┌──────────────────────┐          ┌──────────────────────┐            │
│  │   ANDROID CLIENT     │          │   BACKEND SERVER     │            │
│  │   (Kotlin/Jetpack)   │◄────────►│   (Python/FastAPI)   │            │
│  └──────────┬───────────┘          └──────────┬───────────┘            │
│             │                                  │                        │
│             │ Video + Metadata                 │                        │
│             │                                  │                        │
│             ▼                                  ▼                        │
│  ┌──────────────────────────────────────────────────────────────┐      │
│  │                    ML PIPELINE (Python)                       │      │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐     │      │
│  │  │ Frame    │  │ YOLOv8   │  │ Geo      │  │ Dataset  │     │      │
│  │  │ Extract  │──►Inference │──►Aggregation│──►Export   │     │      │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘     │      │
│  └──────────────────────────────────────────────────────────────┘      │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Mobile App** | Kotlin + Jetpack Compose | Navigation UI, GPS tracking, video capture |
| **Self-Learning** | Room + Coroutines | On-device ML for preferences |
| **Maps** | MapLibre + PMTiles | Offline vector maps |
| **Routing** | OSRM + Custom Scoring | Free routing with personalization |
| **ML Inference** | TensorFlow Lite + YOLOv8n | Real-time object detection |
| **ML Training** | Python + Ultralytics | Model training pipeline |
| **Backend** | FastAPI + SQLite | Video processing, data aggregation |

---

## 2. Implemented Phases

### Phase 1: Foundation (Agent 1)
**Status**: ✅ Completed

**Achievements**:
- Repository structure analysis
- Component inventory and prioritization
- Asset manifest creation

---

### Phase 2: Map Integration (Agent 2)
**Status**: ✅ Completed

**Components**:
- MapLibre SDK v11.0.0
- Custom style management
- Offline PMTiles support (Doha, Qatar)
- Waze-like styling

---

### Phase 3: Routing Engine (Agent 3)
**Status**: ✅ Completed

**Features**:
- OSRM integration
- Global search (removed Qatar-only restriction)
- Multi-attempt fallback strategy
- Route geometry rendering

---

### Phase 4: UI Integration (Agent 4)
**Status**: ✅ Completed

**Design Changes**:
- Dark theme (#000000 background)
- No glassmorphism (60fps target)
- New screens: Settings, History, Saved Places

**Key Components**:
```kotlin
- TurnBanner.kt (72dp compact)
- Speedometer.kt (horizontal bar)
- TopBar.kt (solid background)
- AppDrawer.kt
```

---

### Phase 5: GPS Enhancement
**Status**: ✅ Completed

**Components**:
- `GpsKalmanFilter.kt` - Noise reduction
- `MapMatcher.kt` - Road snapping
- Adaptive thresholds based on speed

---

### Phase 6-7: Testing & Documentation
**Status**: ✅ Completed

---

### Phase 8: Rerouting & Navigation
**Status**: ✅ Completed

---

### **Phase 9: Self-Learning System (NEW MAJOR FEATURE)**
**Status**: ✅ **COMPLETED - Production Ready**

This is a comprehensive on-device intelligence system that learns from user behavior.

---

## 3. Self-Learning System (NEW)

### 3.1 Overview

The Self-Learning System is a sophisticated on-device intelligence layer that learns user routing preferences from GPS data and route choices. It operates entirely on-device for privacy and uses online gradient descent to continuously improve route recommendations.

### 3.2 Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│              SELF-LEARNING SYSTEM ARCHITECTURE                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   GPS Data In → Preference Learning Engine → Route Scoring      │
│        │              │                          │              │
│        ▼              ▼                          ▼              │
│   ┌──────────┐  ┌──────────┐              ┌──────────┐         │
│   │ Decision │  │ User     │              │ Scored   │         │
│   │ Logger   │  │ Profile  │─────────────►│ Routes   │         │
│   └──────────┘  └──────────┘              └──────────┘         │
│        │              │                          │              │
│        ▼              ▼                          ▼              │
│   ┌──────────┐  ┌──────────┐              ┌──────────┐         │
│   │ Learning │  │ Online   │              │ UI       │         │
│   │ Database │  │ Gradient │              │ Display  │         │
│   │ (Room)   │  │ Descent  │              │ (New)    │         │
│   └──────────┘  └──────────┘              └──────────┘         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 3.3 Core Components

#### 3.3.1 PreferenceLearningEngine.kt (576 lines)
**Purpose**: Central learning algorithm that manages user preference profiles

**Key Features**:
- **UserPreferenceProfile**: Stores learned preferences
  - Time weight (0.0-1.0): Preference for speed
  - Distance weight (0.0-1.0): Preference for shorter routes
  - Simplicity weight (0.0-1.0): Preference for fewer turns
  - Scenic weight (0.0-1.0): Preference for scenic routes
  - Highway preference (-1.0 to 1.0): Avoid vs prefer highways

- **Online Learning Algorithm**:
```kotlin
// Adaptive learning rate that decreases with more samples
val adaptiveRate = LEARNING_RATE / (1 + sampleCount / 50f)

// Update weights based on user choices
newWeight = currentWeight + (delta * adaptiveRate)
```

- **Pre-built Profiles**:
  - `balanced()`: 50% time, 30% distance, 20% simplicity
  - `fastest()`: 80% time, 10% distance, 10% simplicity
  - `simplest()`: 30% time, 20% distance, 50% simplicity
  - `scenic()`: 20% time, 10% distance, 20% simplicity, 50% scenic

- **Route Scoring**:
```kotlin
// Normalizes metrics across all available routes
val normalizedTime = normalizeLowerIsBetter(route.duration, minDuration, maxDuration)
val normalizedDistance = normalizeLowerIsBetter(route.distance, minDistance, maxDistance)

// Weighted score calculation
val totalScore = (timeScore * timeWeight) + 
                 (distanceScore * distanceWeight) + 
                 (simplicityScore * simplicityWeight) + 
                 (scenicScore * scenicWeight)
```

**Code Location**: `app/src/main/java/com/wayy/data/learning/PreferenceLearningEngine.kt`

---

#### 3.3.2 RouteScoringService.kt (298 lines)
**Purpose**: Integrates preference scoring with the routing system

**Key Methods**:
- `scoreAndRankRoutes()`: Scores multiple OSRM alternatives and returns sorted by preference
- `getBestRoute()`: Returns top-scoring route
- `recordRouteChoice()`: Logs user selection for learning
- `getRouteScoreExplanation()`: Generates human-readable explanations

**Usage Flow**:
```kotlin
// 1. Get routes from OSRM
val routes = routeRepository.getRoutes(start, end)

// 2. Score with personalization
val scoredRoutes = routeScoringService.scoreAndRankRoutes(routes, usePersonalization = true)

// 3. User selects route
routeScoringService.recordRouteChoice(chosenIndex, routes, context)

// 4. System learns from choice (async)
preferenceEngine.updatePreferencesFromChoice(chosenIndex, routes, currentProfile)
```

**Code Location**: `app/src/main/java/com/wayy/data/learning/RouteScoringService.kt`

---

#### 3.3.3 LearningEntities.kt (246 lines)
**Purpose**: Room database entities for learning data

**Entities**:

1. **UserPreferenceEntity**: Stores learned preference weights
```kotlin
@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
    @PrimaryKey val id: String = "default",
    val timeWeight: Float = 0.5f,
    val distanceWeight: Float = 0.3f,
    val simplicityWeight: Float = 0.2f,
    val scenicWeight: Float = 0.0f,
    val highwayPreference: Float = 0.0f,
    val rerouteAcceptanceRate: Float = 0.7f,
    val totalRoutesAnalyzed: Int = 0,
    val confidenceScore: Float = 0.0f
)
```

2. **DestinationPatternEntity**: Learns routine trips
```kotlin
@Entity(tableName = "destination_patterns")
data class DestinationPatternEntity(
    @PrimaryKey val patternId: String,
    val destinationLat: Double,
    val destinationLng: Double,
    val destinationName: String,
    val destinationCategory: String?, // HOME, WORK, GYM, etc.
    val dayOfWeek: Int = -1,
    val hourOfDay: Int = -1,
    val confidenceScore: Float = 0.0f,
    val occurrenceCount: Int = 0
)
```

3. **TrafficModelEntity**: Per-street traffic patterns
```kotlin
@Entity(tableName = "traffic_models")
data class TrafficModelEntity(
    @PrimaryKey val streetName: String,
    val hourlyAverages: String, // JSON array of 24 values
    val weeklyPatterns: String, // JSON array of 7 patterns
    val sampleCount: Int = 0,
    val accuracyScore: Float = 0.0f
)
```

4. **RerouteDecisionEntity**: Logs reroute suggestions and user responses
```kotlin
@Entity(tableName = "reroute_decisions")
data class RerouteDecisionEntity(
    @PrimaryKey val decisionId: String,
    val tripId: String,
    val originalRouteDuration: Int,
    val suggestedRouteDuration: Int,
    val timeSavings: Int,
    val userAction: String, // ACCEPTED, IGNORED, CANCELLED, LATER_ACCEPTED
    val triggerReason: String? // TRAFFIC, USER_REQUESTED, DEVIATION, FASTER_ROUTE
)
```

5. **DetectedAnomalyEntity**: Road closures, construction, accidents
```kotlin
@Entity(tableName = "detected_anomalies")
data class DetectedAnomalyEntity(
    @PrimaryKey val anomalyId: String,
    val type: String, // ROAD_CLOSURE, CONSTRUCTION, ACCIDENT, etc.
    val latitude: Double,
    val longitude: Double,
    val confidence: Float = 0.0f,
    val expiresAt: Long // TTL for temporary conditions
)
```

6. **RouteChoiceEntity**: Logs which route user selected
```kotlin
@Entity(tableName = "route_choices")
data class RouteChoiceEntity(
    @PrimaryKey val choiceId: String,
    val tripId: String,
    val chosenRouteIndex: Int,
    val alternativesJson: String, // All available routes
    val decisionContextJson: String // Time, weather, traffic conditions
)
```

**Code Location**: `app/src/main/java/com/wayy/data/local/LearningEntities.kt`

---

#### 3.3.4 LearningDao.kt (196 lines)
**Purpose**: Data access object for learning database

**Key Queries**:
```kotlin
// User preferences
@Query("SELECT * FROM user_preferences WHERE id = 'default' LIMIT 1")
suspend fun getUserPreferences(): UserPreferenceEntity?

// Pattern recognition
@Query("""
    SELECT * FROM destination_patterns 
    WHERE isActive = 1 
    AND (dayOfWeek = :dayOfWeek OR dayOfWeek = -1) 
    AND (hourOfDay = :hourOfDay OR hourOfDay = -1) 
    AND confidenceScore >= :minConfidence 
    ORDER BY confidenceScore DESC, occurrenceCount DESC 
    LIMIT :limit
""")
suspend fun getPatternsForTimeContext(dayOfWeek: Int, hourOfDay: Int, ...)

// Reroute analytics
@Query("SELECT userAction, COUNT(*) as count FROM reroute_decisions WHERE timestamp >= :since GROUP BY userAction")
suspend fun getRerouteAcceptanceStats(since: Long): List<RerouteAcceptanceStat>

// Anomaly detection
@Query("SELECT * FROM detected_anomalies WHERE expiresAt > :currentTime AND confidence >= :minConfidence")
suspend fun getActiveAnomalies(currentTime: Long, minConfidence: Float)
```

**Code Location**: `app/src/main/java/com/wayy/data/local/LearningDao.kt`

---

#### 3.3.5 RouteDecisionLogger.kt (274 lines)
**Purpose**: Captures route selection decisions with full context

**Key Features**:
- Logs which route user chose from alternatives
- Captures decision context (time, day, weather, traffic)
- Analyzes preference deltas
- Supports both detailed and simplified logging

**Context Captured**:
```kotlin
data class RouteDecisionContext(
    val departureTime: Long?,
    val dayOfWeek: Int,        // 0-6
    val hourOfDay: Int,        // 0-23
    val isWeekend: Boolean,
    val weatherCondition: String?, // sunny, rainy, etc.
    val trafficCondition: String?, // light, moderate, heavy
    val userRushLevel: String?     // normal, rushed, relaxed
)
```

**Usage**:
```kotlin
val logger = RouteDecisionLogger(context, learningDao)

logger.logRouteChoice(
    tripId = tripId,
    chosenRouteIndex = selectedRouteIndex,
    routes = availableRoutes,
    context = RouteDecisionContext(
        departureTime = System.currentTimeMillis(),
        dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1,
        hourOfDay = calendar.get(Calendar.HOUR_OF_DAY),
        isWeekend = isWeekend
    ),
    wasRerouted = false
)
```

**Code Location**: `app/src/main/java/com/wayy/data/local/RouteDecisionLogger.kt`

---

#### 3.3.6 LearningSystemInitializer.kt (156 lines)
**Purpose**: Singleton pattern for learning system initialization

**Features**:
- Thread-safe singleton database instance
- Lazy initialization of DAO and loggers
- Automatic cleanup of expired data
- Default preference initialization

**Usage**:
```kotlin
// Initialize on app startup
val initializer = LearningSystemInitializer(context)
initializer.initialize()

// Access components anywhere
val dao = LearningSystem.dao(context)
val logger = LearningSystem.logger(context)
val exportManager = LearningSystem.exportManager(context)
```

**Code Location**: `app/src/main/java/com/wayy/data/local/LearningSystemInitializer.kt`

---

### 3.4 New UI: PreferenceSettingsScreen.kt (757 lines)

**Purpose**: Visual interface for managing learned preferences

**Features**:

#### 3.4.1 Learning Status Visualization
- Confidence score progress bar
- Routes analyzed counter
- Reliability badge (Reliable/Learning)
- Dominant preference indicator

#### 3.4.2 Preference Visualization
- Animated preference bars:
  - Speed preference (blue)
  - Distance preference (green)
  - Simplicity preference (yellow)
  - Scenic preference (purple)
- Percentage display with smooth animations

#### 3.4.3 Road Type Preferences
- Highway preference (Prefer/Avoid/Neutral)
- Main roads preference
- Residential roads preference

#### 3.4.4 Learning Statistics
- Total routes analyzed
- Recent choices (30 days)
- Confidence percentage

#### 3.4.5 Actions
- **Quick Calibration**: Choose from preset profiles
  - Fastest Route
  - Simplest Route
  - Balanced
  - Scenic Route
- **Reset Preferences**: Clear all learned data

#### 3.4.6 UI Code Example
```kotlin
@Composable
fun PreferenceSettingsScreen(onBack: () -> Unit) {
    val database = remember { LearningSystemInitializer.getDatabase(context) }
    val learningDao = remember { database.learningDao() }
    val preferenceEngine = remember { PreferenceLearningEngine(learningDao) }
    
    // Load learning statistics
    var preferenceStats by remember { mutableStateOf<PreferenceLearningStats?>(null) }
    LaunchedEffect(Unit) {
        preferenceStats = preferenceEngine.getLearningStatistics()
    }
    
    // Display learning status card
    preferenceStats?.let { stats ->
        LearningStatusCard(stats = stats)
        PreferenceVisualizationCard(profile = stats.profile)
        RoadTypePreferencesCard(profile = stats.profile)
    }
}
```

**Code Location**: `app/src/main/java/com/wayy/ui/screens/PreferenceSettingsScreen.kt`

---

### 3.5 Learning Algorithm Details

#### 3.5.1 Online Gradient Descent
```kotlin
// Adaptive learning rate decreases as we get more data
// This prevents overfitting to early choices
val adaptiveRate = LEARNING_RATE / (1 + sampleCount / 50f)

// Calculate preference delta from route choice
// Positive delta = user preferred this characteristic
val timeDelta = if (avgAltDuration > 0) {
    ((avgAltDuration - chosen.duration) / avgAltDuration).toFloat()
} else 0f

// Update weight with momentum
var newWeight = currentWeight + (delta * adaptiveRate)

// Constrain to valid range
newWeight = newWeight.coerceIn(MIN_WEIGHT, MAX_WEIGHT)

// Normalize all weights to sum to 1.0
val totalWeight = newTimeWeight + newDistanceWeight + 
                  newSimplicityWeight + newScenicWeight
newTimeWeight /= totalWeight
newDistanceWeight /= totalWeight
// ... etc
```

#### 3.5.2 Confidence Calculation
```kotlin
// Sigmoid function for confidence
// Confidence approaches 1.0 as sample count increases
// Plateaus around 20+ samples
fun calculateConfidence(sampleCount: Int): Float {
    return (1 - exp(-sampleCount / 20.0)).toFloat().coerceIn(0f, 1f)
}

// Reliability check
fun isReliable(minRoutes: Int = 10, minConfidence: Float = 0.3f): Boolean {
    return totalRoutesAnalyzed >= minRoutes && confidenceScore >= minConfidence
}
```

#### 3.5.3 Route Complexity Calculation
```kotlin
// Complexity = number of maneuvers / steps
fun calculateRouteComplexity(route: Route): Int {
    return route.legs.sumOf { leg -> leg.steps.size }
}

// Simplicity score (normalized)
val normalizedComplexity = normalizeLowerIsBetter(
    complexity.toDouble(), 
    minComplexity, 
    maxComplexity
)
```

#### 3.5.4 Scenic Score Estimation
```kotlin
// Estimate scenic value based on:
// 1. Average speed (scenic roads often 10-20 m/s)
// 2. Route complexity (winding roads have more turns)
fun estimateScenicScore(route: Route): Double {
    val avgSpeed = route.distance / route.duration
    val complexity = calculateRouteComplexity(route)
    
    // Moderate speeds suggest scenic roads
    val speedScore = when {
        avgSpeed in 10.0..20.0 -> 0.8
        avgSpeed in 8.0..25.0 -> 0.5
        else -> 0.2
    }
    
    // Some complexity is good for scenic
    val complexityScore = (complexity / 15.0).coerceIn(0.0, 1.0)
    
    return (speedScore * 0.6 + complexityScore * 0.4)
}
```

---

### 3.6 Integration Points

#### 3.6.1 NavigationViewModel Integration
```kotlin
// In NavigationViewModel.kt
class NavigationViewModel(private val learningDao: LearningDao) : ViewModel() {
    private val preferenceEngine = PreferenceLearningEngine(learningDao)
    private val routeScoringService = RouteScoringService(context, learningDao, preferenceEngine)
    
    suspend fun calculateRoutes(start: LatLng, end: LatLng): List<ScoredRoute> {
        val osrmRoutes = routeRepository.getRoutes(start, end)
        return routeScoringService.scoreAndRankRoutes(osrmRoutes, usePersonalization = true)
    }
    
    fun onRouteSelected(chosenIndex: Int, routes: List<Route>) {
        routeScoringService.recordRouteChoice(chosenIndex, routes, context)
    }
}
```

#### 3.6.2 RerouteUtils Integration
```kotlin
// Adaptive reroute threshold based on learned preferences
fun getAdaptiveThreshold(speed: Double): Double {
    val baseThreshold = if (speed > 20) 100.0 else 50.0
    val acceptanceRate = preferenceProfile.rerouteAcceptanceRate
    return baseThreshold * (0.5 + acceptanceRate)
}
```

#### 3.6.3 Settings Integration
```kotlin
// LearningSettingsRepository.kt
val settingsFlow: Flow<LearningSettings> = context.learningSettingsDataStore.data.map { prefs ->
    LearningSettings(
        learningEnabled = prefs[LEARNING_ENABLED] ?: true,
        trafficLearningEnabled = prefs[TRAFFIC_LEARNING_ENABLED] ?: true,
        rerouteLearningEnabled = prefs[REROUTE_LEARNING_ENABLED] ?: true,
        preferenceLearningEnabled = prefs[PREFERENCE_LEARNING_ENABLED] ?: true,
        dataRetentionDays = prefs[DATA_RETENTION_DAYS] ?: 90,
        federatedLearningEnabled = prefs[FEDERATED_LEARNING_ENABLED] ?: false
    )
}
```

---

### 3.7 Privacy & Data Controls

#### 3.7.1 Privacy Levels
```kotlin
enum class PrivacyLevel {
    LOCAL_ONLY,      // All learning on-device only
    ANONYMOUS,       // Anonymized aggregation only
    PERSONALIZED     // Full personalization with cloud sync
}
```

#### 3.7.2 Data Retention
- User preferences: Permanent
- Route choices: 90 days (configurable)
- Reroute decisions: 90 days
- Anomalies: Until expired (TTL)
- Traffic models: 30 days of samples

#### 3.7.3 Data Export
```kotlin
// Export all learning data (JSON format)
val exportManager = LearningSystem.exportManager(context)
exportManager.exportAllLearningData(format = ExportFormat.JSON)

// Create backup
exportManager.createBackup()

// Delete all data
exportManager.deleteAllLearningData()
```

---

## 4. ML Pipeline Architecture

### 4.1 Video-Based Learning (Existing)

**Purpose**: Detect road conditions from video captures

**Pipeline**:
```
Video Capture → Frame Extraction (2fps) → YOLOv8 Inference → 
Geotagging → Aggregation → Training Dataset
```

**Components**:
- `prepare_wayy_exports.py`: Extract video and metadata
- `run_pipeline.py`: Orchestrate full pipeline
- `train_yolo.py`: Model training

**Outputs**:
- `road_conditions.geojson`: Aggregated detections
- `traffic_segments.geojson`: Speed-based segments
- `labels_auto.jsonl`: Auto-generated training labels

---

## 5. Android App Components

### 5.1 Complete File Structure

```
app/src/main/java/com/wayy/
├── MainActivity.kt
├── WayyApp.kt
│
├── capture/                          # Video recording
│   ├── CaptureConfig.kt
│   ├── CaptureMetadataWriter.kt
│   ├── CaptureStorageManager.kt
│   └── NavigationCaptureController.kt
│
├── data/
│   ├── learning/                     # NEW: Self-learning system
│   │   ├── PreferenceLearningEngine.kt      # 576 lines
│   │   └── RouteScoringService.kt           # 298 lines
│   │
│   ├── local/                        # Database layer
│   │   ├── LearningDao.kt                   # 196 lines
│   │   ├── LearningDataExportManager.kt
│   │   ├── LearningEntities.kt              # 246 lines
│   │   ├── LearningSystemInitializer.kt     # 156 lines
│   │   ├── RouteDecisionLogger.kt           # 274 lines
│   │   ├── TripLoggingDao.kt
│   │   ├── TripLoggingDatabase.kt
│   │   ├── TripLoggingEntities.kt
│   │   └── TripLoggingManager.kt
│   │
│   ├── model/                        # Data models
│   │   └── Route.kt
│   │
│   ├── repository/                   # Data repositories
│   │   ├── LocalPoiManager.kt
│   │   ├── RouteHistoryManager.kt
│   │   ├── RouteParser.kt
│   │   ├── RouteRepository.kt
│   │   └── TrafficReportManager.kt
│   │
│   ├── sensor/                       # GPS & sensors
│   │   ├── DeviceOrientationManager.kt
│   │   └── LocationManager.kt
│   │
│   └── settings/                     # Settings repositories
│       ├── LearningSettingsRepository.kt    # NEW
│       ├── MapSettingsRepository.kt
│       ├── MlSettingsRepository.kt
│       └── NavigationSettingsRepository.kt
│
├── debug/                            # Debug utilities
│   ├── DiagnosticLogger.kt
│   └── ExportBundleManager.kt
│
├── map/                              # Map components
│   ├── MapLibreManager.kt
│   ├── MapLibreView.kt
│   ├── MapStyleManager.kt
│   ├── OfflineMapManager.kt
│   ├── TileCacheManager.kt
│   └── WazeStyleManager.kt
│
├── ml/                               # ML inference
│   ├── DetectionTracker.kt
│   ├── LaneSegmentationManager.kt
│   ├── MlFrameAnalyzer.kt
│   └── OnDeviceMlManager.kt
│
├── navigation/                       # Navigation logic
│   ├── GpsKalmanFilter.kt
│   ├── MapMatcher.kt
│   ├── NavigationUtils.kt
│   ├── RerouteUtils.kt
│   └── TurnInstructionProvider.kt
│
├── ui/
│   ├── components/
│   │   ├── camera/
│   │   │   ├── CameraPreviewCard.kt
│   │   │   ├── CameraPreviewSurface.kt
│   │   │   └── TurnArrowOverlay.kt
│   │   ├── common/
│   │   │   ├── AppDrawer.kt
│   │   │   ├── QuickActionsBar.kt
│   │   │   ├── StatCard.kt
│   │   │   └── TopBar.kt
│   │   ├── gauges/
│   │   │   └── Speedometer.kt
│   │   ├── glass/
│   │   │   ├── GlassButton.kt
│   │   │   └── GlassCard.kt
│   │   └── navigation/
│   │       ├── ETACard.kt
│   │       ├── LaneGuidanceView.kt
│   │       ├── NavigationOverlay.kt
│   │       ├── SpeedLimitIndicator.kt
│   │       └── TurnBanner.kt
│   │
│   ├── screens/                      # UI Screens
│   │   ├── HistoryScreen.kt
│   │   ├── MainNavigationScreen.kt
│   │   ├── PreferenceSettingsScreen.kt      # NEW: 757 lines
│   │   ├── RouteOverviewScreen.kt
│   │   ├── SavedPlacesScreen.kt
│   │   ├── SearchDestinationScreen.kt
│   │   └── SettingsScreen.kt
│   │
│   └── theme/
│       ├── Animation.kt
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
│
├── viewmodel/
│   └── NavigationViewModel.kt
│
└── ml/                               # ML components
    ├── DetectionTracker.kt
    ├── LaneSegmentationManager.kt
    ├── MlFrameAnalyzer.kt
    └── OnDeviceMlManager.kt
```

---

## 6. Data Collection Infrastructure

### 6.1 Dual Data Streams

**Stream 1: GPS + Route Decisions** (Self-Learning)
- Route choice logging
- GPS trajectory tracking
- Reroute decision outcomes
- Traffic speed patterns

**Stream 2: Video + Metadata** (ML Pipeline)
- Video recording (MP4)
- Location metadata (JSONL)
- Frame extraction
- YOLO inference

### 6.2 Database Schema

**TripLoggingDatabase** (Room):
```sql
-- Core tables
user_preferences
destination_patterns
traffic_models
reroute_decisions
detected_anomalies
route_choices
learned_sessions
trips
detections

-- Relationships
trips → route_choices (1:N)
trips → reroute_decisions (1:N)
trips → learned_sessions (1:1)
```

---

## 7. Backend Processing System

### 7.1 FastAPI Server

**Endpoints**:
```python
POST /api/trips/upload              # Upload video + metadata
GET  /api/trips/{id}/status         # Check processing status
GET  /api/road-conditions           # Get aggregated road conditions
GET  /api/traffic-flow              # Get traffic patterns
POST /api/learning/sync             # Sync learning data (optional)
```

### 7.2 Processing Pipeline

**Async Tasks**:
```python
@celery_app.task
def process_video_upload(trip_id: str):
    # 1. Extract frames
    # 2. Run YOLO inference
    # 3. Geotag detections
    # 4. Aggregate road conditions
    # 5. Update database
    pass
```

---

## 8. Roadmap & Future Enhancements

### 8.1 Phase 2: Self-Learning Enhancements (Next 3 months)

#### 8.1.1 Federated Learning
- Share anonymized model updates
- Privacy-preserving aggregation
- Community-driven improvements

#### 8.1.2 Advanced Pattern Recognition
- Predictive destination suggestions
- "You usually go to WORK at this time"
- Calendar integration

#### 8.1.3 Traffic Prediction
- Time-series forecasting per street
- "Leave now to avoid traffic"
- Predicted vs actual comparison

### 8.2 Phase 3: Ecosystem (3-6 months)

#### 8.2.1 Community Features
- Report road conditions
- Verify others' reports
- Reputation system

#### 8.2.2 Integration
- Android Auto support
- Wear OS companion
- Web dashboard

### 8.3 Phase 4: Advanced AI (6-12 months)

#### 8.3.1 Reinforcement Learning
- RL-based route optimization
- Continuous policy improvement
- Multi-objective optimization

#### 8.3.2 Natural Language
- Voice preferences: "I prefer highways"
- Route explanations in natural language
- Conversational interface

---

## 9. Key Statistics

### 9.1 Code Metrics

| Component | Lines of Code | Status |
|-----------|---------------|--------|
| PreferenceLearningEngine.kt | 576 | ✅ Complete |
| PreferenceSettingsScreen.kt | 757 | ✅ Complete |
| RouteScoringService.kt | 298 | ✅ Complete |
| LearningEntities.kt | 246 | ✅ Complete |
| LearningDao.kt | 196 | ✅ Complete |
| RouteDecisionLogger.kt | 274 | ✅ Complete |
| LearningSystemInitializer.kt | 156 | ✅ Complete |
| **Self-Learning Total** | **2,503** | ✅ **Production** |
| ML Pipeline (Python) | ~800 | ✅ Complete |
| **Total Learning System** | **~3,300** | ✅ **Complete** |

### 9.2 Database Entities

| Entity | Purpose | Records |
|--------|---------|---------|
| User Preferences | Learned weights | 1 per user |
| Destination Patterns | Routine trips | Unlimited |
| Traffic Models | Per-street patterns | Unlimited |
| Reroute Decisions | Reroute outcomes | 90 days |
| Detected Anomalies | Road issues | TTL-based |
| Route Choices | Selection history | 90 days |
| Learned Sessions | Trip metadata | 90 days |

---

## 10. Deployment Checklist

### 10.1 Self-Learning System
- [x] PreferenceLearningEngine implemented
- [x] RouteScoringService integrated
- [x] Learning database schema created
- [x] RouteDecisionLogger operational
- [x] PreferenceSettingsScreen UI complete
- [x] LearningSettingsRepository configured
- [x] Privacy controls implemented
- [x] Data export functionality
- [ ] Federated learning (Phase 2)
- [ ] Cloud sync (Phase 2)

### 10.2 ML Pipeline
- [x] Frame extraction
- [x] YOLO inference
- [x] Geo-aggregation
- [x] Auto-labeling
- [ ] Active learning (Phase 2)
- [ ] Federated training (Phase 3)

---

## 11. Conclusion

The Wayy navigation app now features **two complementary learning systems**:

### System 1: Self-Learning (On-Device) ✅
- **Privacy-first**: All data stays on device
- **Personalized**: Learns individual user preferences
- **Real-time**: Adapts to choices immediately
- **Transparent**: Users can see and control what's learned

### System 2: ML Pipeline (Cloud) ✅
- **Community-driven**: Aggregates data from all users
- **Road conditions**: Detects potholes, traffic, accidents
- **Computer vision**: Uses YOLO for object detection
- **Continuous improvement**: Retrains models periodically

### Key Innovations

1. **Dual Learning**: Personal preferences + community road data
2. **Online Learning**: Adapts in real-time to user choices
3. **Explainable AI**: Shows why routes are recommended
4. **Privacy by Design**: On-device learning by default
5. **User Control**: Full transparency and control over learning

### Success Metrics

- **Preference Accuracy**: % of recommended routes accepted
- **Learning Speed**: Routes needed to reach 70% confidence
- **User Retention**: Stickiness due to personalization
- **Privacy Score**: % of users keeping local-only mode

---

**Document Version**: 2.0  
**Last Updated**: 2026-02-19  
**Project**: Wayy Navigation App  
**Status**: Production Ready with Self-Learning System

---

## Appendix: Quick Reference

### Accessing Learning System
```kotlin
// Initialize
val initializer = LearningSystemInitializer(context)
initializer.initialize()

// Get profile
val dao = LearningSystem.dao(context)
val engine = PreferenceLearningEngine(dao)
val profile = engine.getUserProfile()

// Score routes
val service = RouteScoringService(context, dao, engine)
val scoredRoutes = service.scoreAndRankRoutes(routes)

// Log decision
val logger = LearningSystem.logger(context)
logger.logRouteChoice(tripId, chosenIndex, routes, context)
```

### Database Migrations
```kotlin
// Room handles migrations automatically
// Fallback to destructive migration during development
Room.databaseBuilder(context, TripLoggingDatabase::class.java, "trip_logging.db")
    .fallbackToDestructiveMigration() // Reset on schema change
    .build()
```

### Export Format
```json
{
  "version": "2.0",
  "exportedAt": "2026-02-19T10:00:00Z",
  "userPreferences": {
    "timeWeight": 0.6,
    "distanceWeight": 0.2,
    "simplicityWeight": 0.2,
    "confidenceScore": 0.75
  },
  "destinationPatterns": [...],
  "routeChoices": [...]
}
```
