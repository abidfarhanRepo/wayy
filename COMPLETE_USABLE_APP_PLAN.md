# Wayy Complete Usable App Plan

## Current State Summary

### What's Actually Implemented

#### Committed in Phase 6 (commit `2b880eb`)
| File | Lines | Status |
|------|-------|--------|
| `LearningEntities.kt` | 245 | Committed |
| `LearningDao.kt` | 195 | Committed |
| `LearningSettingsRepository.kt` | 260 | Committed |
| `RouteDecisionLogger.kt` | 273 | Committed |
| `LearningDataExportManager.kt` | 444 | Committed |
| `LearningSystemInitializer.kt` | 155 | Committed |

#### Uncommitted (exists but not in git)
| File | Lines | Status |
|------|-------|--------|
| `PreferenceLearningEngine.kt` | 576 | **Uncommitted** |
| `RouteScoringService.kt` | 298 | **Uncommitted** |
| `PreferenceSettingsScreen.kt` | 757 | **Uncommitted** |

### Integration Gaps

| Gap | Impact |
|-----|--------|
| LearningSystemInitializer not called in WayyApp | No initialization |
| PreferenceLearningEngine not in ViewModel | No route scoring |
| RouteScoringService not used | Routes not personalized |
| Route choices not recorded | No learning happens |
| PreferenceSettingsScreen not in navigation | Can't view preferences |

---

## Implementation Plan

### PHASE 1: Commit Existing Code (IMMEDIATE)

```bash
git add app/src/main/java/com/wayy/data/learning/
git add app/src/main/java/com/wayy/ui/screens/PreferenceSettingsScreen.kt
git commit -m "Phase 6: Add PreferenceLearningEngine and RouteScoringService"
```

---

### PHASE 2: Wire Learning System

#### 2.1 Initialize Learning in WayyApp
**File:** `WayyApp.kt`

```kotlin
import com.wayy.data.local.LearningSystemInitializer

override fun onCreate() {
    super.onCreate()
    MapLibreManager(this).initialize()
    TileCacheManager(this).initialize()
    LearningSystemInitializer(this).initialize()  // ADD
    createNotificationChannel()
}
```

#### 2.2 Create Hilt Module
**File:** `di/LearningModule.kt` (NEW)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object LearningModule {
    
    @Provides @Singleton
    fun providePreferenceLearningEngine(dao: LearningDao): PreferenceLearningEngine =
        PreferenceLearningEngine(dao)
    
    @Provides @Singleton
    fun provideRouteScoringService(
        @ApplicationContext context: Context,
        dao: LearningDao,
        engine: PreferenceLearningEngine
    ): RouteScoringService = RouteScoringService(context, dao, engine)
    
    @Provides
    fun provideRouteDecisionLogger(dao: LearningDao): RouteDecisionLogger =
        RouteDecisionLogger(dao)
}
```

#### 2.3 Inject into NavigationViewModel
**File:** `NavigationViewModel.kt`

```kotlin
@HiltViewModel
class NavigationViewModel @Inject constructor(
    // ... existing ...
    private val preferenceEngine: PreferenceLearningEngine,
    private val routeScoringService: RouteScoringService,
    private val routeDecisionLogger: RouteDecisionLogger
) : ViewModel() {

    private val _scoredRoutes = MutableStateFlow<List<ScoredRoute>>(emptyList())
    val scoredRoutes: StateFlow<List<ScoredRoute>> = _scoredRoutes
```

#### 2.4 Score Routes
**File:** `NavigationViewModel.kt`

```kotlin
private suspend fun fetchAndScoreRoutes(start: Point, end: Point) {
    routeRepository.getRouteWithAlternatives(start, end).onSuccess { routes ->
        val scored = routeScoringService.scoreAndRankRoutes(routes)
        _scoredRoutes.value = scored
    }
}
```

#### 2.5 Record Route Choice
**File:** `NavigationViewModel.kt`

```kotlin
fun selectRoute(index: Int) {
    val routes = _scoredRoutes.value.map { it.route }
    val context = RouteDecisionContext(
        departureTime = System.currentTimeMillis(),
        dayOfWeek = LocalDate.now().dayOfWeek.value,
        hourOfDay = LocalTime.now().hour,
        isWeekend = LocalDate.now().dayOfWeek.value >= 6
    )
    routeScoringService.recordRouteChoice(index, routes, context)
    // ... proceed with navigation
}
```

---

### PHASE 3: Wire UI

#### 3.1 Add Preference Settings to Navigation
**File:** `MainActivity.kt`

```kotlin
composable("preference_settings") {
    PreferenceSettingsScreen(onBack = { navController.popBackStack() })
}
```

#### 3.2 Add Link in Settings
**File:** `SettingsScreen.kt`

```kotlin
SettingsItem(
    title = "Route Preferences",
    onClick = { navController.navigate("preference_settings") }
)
```

#### 3.3 Show Scores in RouteOverview
**File:** `RouteOverviewScreen.kt`

```kotlin
val scoredRoutes by viewModel.scoredRoutes.collectAsState()

scoredRoutes.forEachIndexed { i, sr ->
    RouteCard(
        route = sr.route,
        score = sr.score.totalScore,
        isRecommended = i == 0
    )
}
```

---

### PHASE 4: Missing UI Elements

#### 4.1 Lane Guidance
**File:** `MainNavigationScreen.kt`

```kotlin
if (isNavigating && laneResult != null) {
    LaneGuidanceView(
        lanes = laneResult.lanes,
        recommendedLane = laneResult.recommendedLane
    )
}
```

#### 4.2 Speed Limit
**File:** `MainNavigationScreen.kt`

```kotlin
if (isNavigating && speedLimit > 0) {
    SpeedLimitIndicator(
        speedLimitKmh = speedLimit,
        currentSpeedKmh = (currentSpeed * 3.6).toInt()
    )
}
```

#### 4.3 Real History Data
**File:** `HistoryScreen.kt`

```kotlin
val history by viewModel.recentRoutes.collectAsState(initial = emptyList())
```

---

### PHASE 5: Voice Navigation (Optional)

#### 5.1 TTS Manager
**File:** `navigation/TtsManager.kt` (NEW)

```kotlin
class TtsManager @Inject constructor(
    @ApplicationContext context: Context
) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context, this)
    private var ready = false
    
    fun speak(text: String) {
        if (ready) tts.speak(text, QUEUE_ADD, null, null)
    }
    
    override fun onInit(status: Int) { ready = status == SUCCESS }
}
```

---

## File Summary

### New Files (2)
- `di/LearningModule.kt`
- `navigation/TtsManager.kt`

### Modified Files (7)
- `WayyApp.kt`
- `NavigationViewModel.kt`
- `MainActivity.kt`
- `SettingsScreen.kt`
- `RouteOverviewScreen.kt`
- `MainNavigationScreen.kt`
- `HistoryScreen.kt`

---

## Estimated Hours

| Phase | Hours |
|-------|-------|
| Phase 1: Commit | 0.5 |
| Phase 2: Wire Learning | 4-6 |
| Phase 3: Wire UI | 2-3 |
| Phase 4: Missing UI | 2-3 |
| Phase 5: Voice | 2 |
| **Total** | **10-14** |

---

## Success Criteria

- [ ] Routes ranked by learned preferences
- [ ] Route choices logged for learning
- [ ] Preferences viewable in settings
- [ ] Lane guidance visible
- [ ] Speed limit visible
- [ ] History shows real data
