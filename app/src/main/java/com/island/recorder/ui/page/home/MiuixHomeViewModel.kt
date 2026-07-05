package com.island.recorder.ui.page.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.island.recorder.domain.recording.model.RecordingSettings
import com.island.recorder.domain.settings.repository.AppSettingsRepository
import com.island.recorder.framework.privileged.Authorizer
import com.island.recorder.framework.privileged.DeviceCapability
import com.island.recorder.framework.privileged.RootMode
import com.island.recorder.framework.privileged.ShizukuMode
import com.island.recorder.framework.privileged.provider.DeviceCapabilityProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MiuixHomeState(
    val recordingSettings: RecordingSettings = RecordingSettings(),
    val selectedAuthorizer: Authorizer = Authorizer.Shizuku,
    val capability: DeviceCapability = DeviceCapability(RootMode.None, ShizukuMode.NotRunning)
)

class MiuixHomeViewModel(
    appSettingsRepository: AppSettingsRepository,
    private val capabilityProvider: DeviceCapabilityProvider
) : ViewModel() {
    val state: StateFlow<MiuixHomeState> = combine(
        appSettingsRepository.preferencesFlow,
        capabilityProvider.capabilityFlow
    ) { preferences, capability ->
        MiuixHomeState(
            recordingSettings = preferences.recordingSettings,
            selectedAuthorizer = preferences.authorizer,
            capability = capability
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MiuixHomeState()
    )

    fun refreshCapability() {
        capabilityProvider.refreshPrivilegeStatus()
    }
}
