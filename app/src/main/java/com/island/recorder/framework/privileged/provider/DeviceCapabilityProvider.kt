package com.island.recorder.framework.privileged.provider

import com.island.recorder.framework.privileged.DeviceCapability
import com.island.recorder.framework.privileged.RootMode
import com.island.recorder.framework.privileged.ShizukuMode
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

interface DeviceCapabilityProvider {
    val rootModeFlow: StateFlow<RootMode>
    val shizukuModeFlow: StateFlow<ShizukuMode>
    val capabilityFlow: StateFlow<DeviceCapability>

    fun current(): DeviceCapability
    fun refreshPrivilegeStatus()
    fun requestShizukuPermission(requestCode: Int)
    fun addShizukuPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener)
    fun removeShizukuPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener)
}
