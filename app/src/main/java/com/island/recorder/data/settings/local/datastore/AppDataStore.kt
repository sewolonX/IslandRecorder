package com.island.recorder.data.settings.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppDataStore(
    private val dataStore: DataStore<Preferences>
) {
    val data: Flow<Preferences> = dataStore.data

    companion object {
        val VIDEO_QUALITY: Preferences.Key<String> = stringPreferencesKey("video_quality")
        val VIDEO_BITRATE: Preferences.Key<String> = stringPreferencesKey("video_bitrate")
        val SCREEN_ORIENTATION: Preferences.Key<String> = stringPreferencesKey("screen_orientation")
        val FRAME_RATE: Preferences.Key<String> = stringPreferencesKey("frame_rate")
        val AUDIO_SOURCE: Preferences.Key<String> = stringPreferencesKey("audio_source")
        val VIDEO_CODEC: Preferences.Key<String> = stringPreferencesKey("video_codec")
        val SHOW_TOUCHES: Preferences.Key<Boolean> = booleanPreferencesKey("show_touches")
        val BYPASS_FOCUS_ISLAND: Preferences.Key<Boolean> = booleanPreferencesKey("bypass_focus_island")
        val TILE_STYLE: Preferences.Key<String> = stringPreferencesKey("tile_style")
        val STORAGE_TREE_URI: Preferences.Key<String> = stringPreferencesKey("storage_tree_uri")
        val AUTHORIZER: Preferences.Key<String> = stringPreferencesKey("authorizer")
        val FIRST_LAUNCH: Preferences.Key<Boolean> = booleanPreferencesKey("first_launch")
    }

    suspend fun putString(key: Preferences.Key<String>, value: String) {
        dataStore.edit { it[key] = value }
    }

    fun getString(key: Preferences.Key<String>, default: String = ""): Flow<String> =
        dataStore.data.map { it[key] ?: default }

    suspend fun putBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { it[key] = value }
    }

    fun getBoolean(key: Preferences.Key<Boolean>, default: Boolean = false): Flow<Boolean> =
        dataStore.data.map { it[key] ?: default }
}
