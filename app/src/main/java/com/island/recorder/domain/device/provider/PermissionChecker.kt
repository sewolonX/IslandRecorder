package com.island.recorder.domain.device.provider

import com.island.recorder.domain.device.model.PermissionType

interface PermissionChecker {
    fun hasPermission(type: PermissionType): Boolean
}
