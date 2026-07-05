package com.island.recorder.ui.common.permission

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.island.recorder.domain.device.model.PermissionType
import com.island.recorder.domain.device.provider.PermissionChecker
import timber.log.Timber

class PermissionRequester(
    private val activity: ComponentActivity,
    private val permissionChecker: PermissionChecker
) {
    private var pendingTypes: Set<PermissionType> = emptySet()
    private var onPermissionsResult: ((Map<PermissionType, Boolean>) -> Unit)? = null

    private val requestPermissionsLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val permissionResults = pendingTypes.associateWith { type ->
                permissionChecker.hasPermission(type) || results[manifestPermission(type)] == true
            }
            permissionResults.forEach { (type, isGranted) ->
                if (isGranted) {
                    Timber.d("$type permission granted.")
                } else {
                    Timber.w("$type permission denied.")
                }
            }
            onPermissionsResult?.invoke(permissionResults)
        }

    fun requestPermissions(
        types: Set<PermissionType>,
        onResult: (Map<PermissionType, Boolean>) -> Unit
    ) {
        pendingTypes = types
        onPermissionsResult = onResult

        val missingPermissions = types
            .filterNot(permissionChecker::hasPermission)
            .map(::manifestPermission)

        if (missingPermissions.isEmpty()) {
            onPermissionsResult?.invoke(types.associateWith { true })
        } else {
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    fun requestRecordAudioPermission(
        onGranted: () -> Unit,
        onDenied: (type: PermissionType) -> Unit
    ) {
        requestPermissions(setOf(PermissionType.RecordAudio)) { results ->
            if (results[PermissionType.RecordAudio] == true) {
                onGranted()
            } else {
                onDenied(PermissionType.RecordAudio)
            }
        }
    }

    private fun manifestPermission(type: PermissionType): String =
        when (type) {
            PermissionType.RecordAudio -> Manifest.permission.RECORD_AUDIO
            PermissionType.PostNotifications -> Manifest.permission.POST_NOTIFICATIONS
        }
}
