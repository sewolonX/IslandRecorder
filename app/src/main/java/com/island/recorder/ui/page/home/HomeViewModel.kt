package com.island.recorder.ui.page.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.island.recorder.domain.settings.repository.AppSettingsRepository
import com.island.recorder.framework.privileged.provider.DeviceCapabilityProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    appSettingsRepository: AppSettingsRepository,
    private val capabilityProvider: DeviceCapabilityProvider
) : ViewModel() {
    val state: StateFlow<HomeState> = combine(
        appSettingsRepository.preferencesFlow,
        capabilityProvider.capabilityFlow
    ) { preferences, capability ->
        HomeState(
            recordingSettings = preferences.recordingSettings,
            selectedAuthorizer = preferences.authorizer,
            capability = capability
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeState()
    )

    fun refreshCapability() {
        capabilityProvider.refreshPrivilegeStatus()
    }
}
