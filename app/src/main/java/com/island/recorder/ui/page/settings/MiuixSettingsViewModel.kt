package com.island.recorder.ui.page.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.island.recorder.domain.recording.model.RecordingSettings
import com.island.recorder.domain.settings.repository.AppSettingsRepository
import com.island.recorder.framework.privileged.Authorizer
import com.island.recorder.framework.privileged.DeviceCapability
import com.island.recorder.framework.privileged.RootMode
import com.island.recorder.framework.privileged.ShizukuMode
import com.island.recorder.framework.privileged.provider.DeviceCapabilityProvider
import com.island.recorder.framework.privileged.provider.PrivilegedOperationProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import rikka.shizuku.Shizuku

data class MiuixSettingsState(
    val currentSettings: RecordingSettings = RecordingSettings(),
    val storageTreeUri: String = "",
    val selectedAuthorizer: Authorizer = Authorizer.Shizuku,
    val capability: DeviceCapability = DeviceCapability(RootMode.None, ShizukuMode.NotRunning),
    val isProjectMediaGranted: Boolean = false
)

class MiuixSettingsViewModel(
    appSettingsRepository: AppSettingsRepository,
    private val capabilityProvider: DeviceCapabilityProvider,
    private val privilegedOperations: PrivilegedOperationProvider
) : ViewModel() {
    val state: StateFlow<MiuixSettingsState> = combine(
        appSettingsRepository.preferencesFlow,
        capabilityProvider.capabilityFlow,
        privilegedOperations.projectMediaAllowedFlow
    ) { preferences, capability, isProjectMediaGranted ->
            MiuixSettingsState(
                currentSettings = preferences.recordingSettings,
                storageTreeUri = preferences.storageTreeUri,
            selectedAuthorizer = preferences.authorizer,
            capability = capability,
            isProjectMediaGranted = isProjectMediaGranted
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MiuixSettingsState()
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
