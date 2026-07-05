package com.island.recorder.ui.page.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.island.recorder.domain.settings.repository.AppSettingsRepository
import com.island.recorder.framework.privileged.provider.DeviceCapabilityProvider
import com.island.recorder.framework.privileged.provider.PrivilegedOperationProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import rikka.shizuku.Shizuku

class SettingsViewModel(
    appSettingsRepository: AppSettingsRepository,
    private val capabilityProvider: DeviceCapabilityProvider,
    private val privilegedOperations: PrivilegedOperationProvider
) : ViewModel() {
    val state: StateFlow<SettingsState> = combine(
        appSettingsRepository.preferencesFlow,
        capabilityProvider.capabilityFlow,
        privilegedOperations.projectMediaAllowedFlow
    ) { preferences, capability, isProjectMediaGranted ->
        SettingsState(
            currentSettings = preferences.recordingSettings,
            storageTreeUri = preferences.storageTreeUri,
            selectedAuthorizer = preferences.authorizer,
            capability = capability,
            isProjectMediaGranted = isProjectMediaGranted
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsState()
    )

    fun refreshCapability() {
        capabilityProvider.refreshPrivilegeStatus()
        privilegedOperations.refreshProjectMediaAllowed()
    }

    fun requestShizukuPermission(requestCode: Int) {
        capabilityProvider.requestShizukuPermission(requestCode)
    }

    fun addShizukuPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        capabilityProvider.addShizukuPermissionResultListener(listener)
    }

    fun removeShizukuPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        capabilityProvider.removeShizukuPermissionResultListener(listener)
    }

    fun setProjectMediaAllowed(allowed: Boolean) {
        privilegedOperations.setProjectMediaAllowedAsync(allowed = allowed)
    }
}
