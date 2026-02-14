package com.wayy.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class MlSettings(
    val modelPath: String = DEFAULT_MODEL_PATH,
    val laneModelPath: String = DEFAULT_LANE_MODEL_PATH
)

const val DEFAULT_MODEL_PATH = "asset://ml/model.tflite"
const val DEFAULT_LANE_MODEL_PATH = "asset://ml/lane_model.tflite"

private val Context.mlSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ml_settings"
)

class MlSettingsRepository(private val context: Context) {

    private object Keys {
        val MODEL_PATH = stringPreferencesKey("model_path")
        val LANE_MODEL_PATH = stringPreferencesKey("lane_model_path")
    }

    val settingsFlow: Flow<MlSettings> = context.mlSettingsDataStore.data.map { prefs ->
        MlSettings(
            modelPath = prefs[Keys.MODEL_PATH] ?: DEFAULT_MODEL_PATH,
            laneModelPath = prefs[Keys.LANE_MODEL_PATH] ?: DEFAULT_LANE_MODEL_PATH
        )
    }

    suspend fun setModelPath(value: String) {
        context.mlSettingsDataStore.edit { prefs ->
            prefs[Keys.MODEL_PATH] = value.trim()
        }
    }

    suspend fun setLaneModelPath(value: String) {
        context.mlSettingsDataStore.edit { prefs ->
            prefs[Keys.LANE_MODEL_PATH] = value.trim()
        }
    }
}
