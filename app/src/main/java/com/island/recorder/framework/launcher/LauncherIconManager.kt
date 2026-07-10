package com.island.recorder.framework.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

class LauncherIconManager(private val context: Context) {
    fun setHidden(hidden: Boolean) {
        val state = if (hidden) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, LAUNCHER_ALIAS),
            state,
            PackageManager.DONT_KILL_APP
        )
    }

    private companion object {
        private const val LAUNCHER_ALIAS =
            "com.island.recorder.ui.activity.LauncherAlias"
    }
}
