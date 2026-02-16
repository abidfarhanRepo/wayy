package com.wayy.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class NavigationSettings(
    val gpsSmoothingEnabled: Boolean = true,
    val mapMatchingEnabled: Boolean = true
)

private val Context.navigationSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "navigation_settings"
)

class NavigationSettingsRepository(private val context: Context) {

    private object Keys {
        val GPS_SMOOTHING_ENABLED = booleanPreferencesKey("gps_smoothing_enabled")
        val MAP_MATCHING_ENABLED = booleanPreferencesKey("map_matching_enabled")
    }

    val settingsFlow: Flow<NavigationSettings> = context.navigationSettingsDataStore.data.map { prefs ->
        NavigationSettings(
            gpsSmoothingEnabled = prefs[Keys.GPS_SMOOTHING_ENABLED] ?: true,
            mapMatchingEnabled = prefs[Keys.MAP_MATCHING_ENABLED] ?: true
        )
    }

    suspend fun setGpsSmoothingEnabled(enabled: Boolean) {
        context.navigationSettingsDataStore.edit { prefs ->
            prefs[Keys.GPS_SMOOTHING_ENABLED] = enabled
        }
    }

    suspend fun setMapMatchingEnabled(enabled: Boolean) {
        context.navigationSettingsDataStore.edit { prefs ->
            prefs[Keys.MAP_MATCHING_ENABLED] = enabled
        }
    }
}