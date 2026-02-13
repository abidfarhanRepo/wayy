package com.wayy.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class MapSettings(
    val tilejsonUrl: String = "",
    val mapStyleUrl: String = ""
)

private val Context.mapSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "map_settings"
)

class MapSettingsRepository(private val context: Context) {

    private object Keys {
        val TILEJSON_URL = stringPreferencesKey("tilejson_url")
        val MAP_STYLE_URL = stringPreferencesKey("map_style_url")
    }

    val settingsFlow: Flow<MapSettings> = context.mapSettingsDataStore.data.map { prefs ->
        MapSettings(
            tilejsonUrl = prefs[Keys.TILEJSON_URL].orEmpty(),
            mapStyleUrl = prefs[Keys.MAP_STYLE_URL].orEmpty()
        )
    }

    suspend fun setTilejsonUrl(value: String) {
        context.mapSettingsDataStore.edit { prefs ->
            prefs[Keys.TILEJSON_URL] = value.trim()
        }
    }

    suspend fun setMapStyleUrl(value: String) {
        context.mapSettingsDataStore.edit { prefs ->
            prefs[Keys.MAP_STYLE_URL] = value.trim()
        }
    }

    suspend fun clearTilejsonUrl() {
        context.mapSettingsDataStore.edit { prefs ->
            prefs.remove(Keys.TILEJSON_URL)
        }
    }

    suspend fun clearMapStyleUrl() {
        context.mapSettingsDataStore.edit { prefs ->
            prefs.remove(Keys.MAP_STYLE_URL)
        }
    }
}
