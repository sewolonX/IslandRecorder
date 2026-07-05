package com.island.recorder.data.settings.repository

import androidx.datastore.preferences.core.Preferences
import com.island.recorder.data.settings.local.datastore.AppDataStore
import com.island.recorder.domain.recording.model.AudioSource
import com.island.recorder.domain.recording.model.FrameRate
import com.island.recorder.domain.recording.model.RecordingSettings
import com.island.recorder.domain.recording.model.ScreenOrientation
import com.island.recorder.domain.recording.model.TileStyle
import com.island.recorder.domain.recording.model.VideoBitrate
import com.island.recorder.domain.recording.model.VideoCodec
import com.island.recorder.domain.recording.model.VideoQuality
import com.island.recorder.domain.settings.model.AppPreferences
import com.island.recorder.domain.settings.repository.AppSettingsRepository
import com.island.recorder.domain.settings.repository.BooleanSetting
import com.island.recorder.domain.settings.repository.StringSetting
import com.island.recorder.framework.privileged.Authorizer
import com.island.recorder.framework.storage.SafRecordingStorageProviderImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

class AppSettingsRepositoryImpl(
    private val appDataStore: AppDataStore,
    appScope: CoroutineScope
) : AppSettingsRepository {

    override val preferencesFlow: Flow<AppPreferences> =
        appDataStore.data.map { prefs: Preferences ->
            AppPreferences(
                recordingSettings = RecordingSettings(
                    videoQuality = safeValueOf(prefs[AppDataStore.VIDEO_QUALITY], VideoQuality.FHD),
                    videoBitrate = safeValueOf(
                        prefs[AppDataStore.VIDEO_BITRATE],
                        VideoBitrate.AUTO
                    ),
                    screenOrientation = safeValueOf(
                        prefs[AppDataStore.SCREEN_ORIENTATION],
                        ScreenOrientation.AUTO
                    ),
                    frameRate = safeValueOf(prefs[AppDataStore.FRAME_RATE], FrameRate.AUTO),
                    audioSource = safeValueOf(prefs[AppDataStore.AUDIO_SOURCE], AudioSource.BOTH),
                    videoCodec = safeValueOf(prefs[AppDataStore.VIDEO_CODEC], VideoCodec.H264),
                    showTouches = prefs[AppDataStore.SHOW_TOUCHES] ?: false,
                    bypassFocusIsland = prefs[AppDataStore.BYPASS_FOCUS_ISLAND] ?: false,
                    tileStyle = safeValueOf(prefs[AppDataStore.TILE_STYLE], TileStyle.DEFAULT)
                ),
                storageTreeUri = prefs[AppDataStore.STORAGE_TREE_URI]
                    ?: SafRecordingStorageProviderImpl.DEFAULT_STORAGE_TREE_URI,
                isFirstLaunch = prefs[AppDataStore.FIRST_LAUNCH] ?: true,
                authorizer = safeValueOf(prefs[AppDataStore.AUTHORIZER], Authorizer.Shizuku),
                isLoaded = true
            )
        }.shareIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            replay = 1
        )

    override val recordingSettingsFlow: Flow<RecordingSettings> =
        preferencesFlow.map { it.recordingSettings }

    override val storageTreeUriFlow: Flow<String> =
        preferencesFlow.map { it.storageTreeUri }

    override val isFirstLaunchFlow: Flow<Boolean> =
        preferencesFlow.map { it.isFirstLaunch }

    override suspend fun putString(setting: StringSetting, value: String) =
        appDataStore.putString(stringKey(setting), value)

    override fun getString(setting: StringSetting, default: String): Flow<String> =
        appDataStore.getString(stringKey(setting), default)

    override suspend fun putBoolean(setting: BooleanSetting, value: Boolean) =
        appDataStore.putBoolean(booleanKey(setting), value)

    override fun getBoolean(setting: BooleanSetting, default: Boolean): Flow<Boolean> =
        appDataStore.getBoolean(booleanKey(setting), default)

    override suspend fun setFirstLaunchCompleted() =
        appDataStore.putBoolean(AppDataStore.FIRST_LAUNCH, false)

    private fun stringKey(setting: StringSetting): Preferences.Key<String> =
        when (setting) {
            StringSetting.VideoQuality -> AppDataStore.VIDEO_QUALITY
            StringSetting.VideoBitrate -> AppDataStore.VIDEO_BITRATE
            StringSetting.ScreenOrientation -> AppDataStore.SCREEN_ORIENTATION
            StringSetting.FrameRate -> AppDataStore.FRAME_RATE
            StringSetting.AudioSource -> AppDataStore.AUDIO_SOURCE
            StringSetting.VideoCodec -> AppDataStore.VIDEO_CODEC
            StringSetting.TileStyle -> AppDataStore.TILE_STYLE
            StringSetting.StorageTreeUri -> AppDataStore.STORAGE_TREE_URI
            StringSetting.Authorizer -> AppDataStore.AUTHORIZER
        }

    private fun booleanKey(setting: BooleanSetting): Preferences.Key<Boolean> =
        when (setting) {
            BooleanSetting.ShowTouches -> AppDataStore.SHOW_TOUCHES
            BooleanSetting.BypassFocusIsland -> AppDataStore.BYPASS_FOCUS_ISLAND
            BooleanSetting.FirstLaunch -> AppDataStore.FIRST_LAUNCH
        }

    private inline fun <reified T : Enum<T>> safeValueOf(name: String?, default: T): T {
        if (name == null) return default
        return try {
            java.lang.Enum.valueOf(T::class.java, name)
        } catch (_: IllegalArgumentException) {
            default
        }
    }
}
