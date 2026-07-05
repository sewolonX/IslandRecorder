package com.island.recorder.framework.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.island.recorder.domain.device.model.PermissionType
import com.island.recorder.domain.device.provider.PermissionChecker

class AndroidPermissionChecker(
    private val context: Context
) : PermissionChecker {
    override fun hasPermission(type: PermissionType): Boolean =
        when (type) {
            PermissionType.RecordAudio ->
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

            PermissionType.PostNotifications ->
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        }
}
