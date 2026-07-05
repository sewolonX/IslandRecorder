package com.island.recorder.ui.page.home

import com.island.recorder.domain.recording.model.RecordingSettings
import com.island.recorder.framework.privileged.Authorizer
import com.island.recorder.framework.privileged.DeviceCapability
import com.island.recorder.framework.privileged.RootMode
import com.island.recorder.framework.privileged.ShizukuMode

data class HomeState(
    val recordingSettings: RecordingSettings = RecordingSettings(),
    val selectedAuthorizer: Authorizer = Authorizer.Shizuku,
    val capability: DeviceCapability = DeviceCapability(RootMode.None, ShizukuMode.NotRunning)
)