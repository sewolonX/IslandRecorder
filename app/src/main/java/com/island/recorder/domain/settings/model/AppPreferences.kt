package com.island.recorder.domain.settings.model

import com.island.recorder.domain.recording.model.RecordingSettings
import com.island.recorder.framework.privileged.Authorizer

data class AppPreferences(
    val recordingSettings: RecordingSettings = RecordingSettings(),
    val storageTreeUri: String = "",
    val isFirstLaunch: Boolean = true,
    val authorizer: Authorizer = Authorizer.Shizuku,
    val hideLauncherIcon: Boolean = false,
    val isLoaded: Boolean = false
)
