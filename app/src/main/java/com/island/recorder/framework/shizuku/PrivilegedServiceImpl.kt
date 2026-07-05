package com.island.recorder.framework.shizuku

import android.net.IConnectivityManager
import android.os.ServiceManager
import android.provider.Settings
import timber.log.Timber
import com.island.recorder.IPrivilegedService
import com.android.internal.app.IAppOpsService
import com.island.recorder.core.reflection.ReflectionProvider
import com.island.recorder.core.reflection.ReflectionProviderImpl
import com.island.recorder.core.reflection.invoke
import com.island.recorder.core.reflection.invokeStatic

class PrivilegedServiceImpl : IPrivilegedService.Stub() {
    private val CHAIN_OEM_DENY_3 = 9
    private val RULE_DEFAULT = 0
    private val RULE_DENY = 2
    private val MODE_ALLOWED = 0
    private val MODE_IGNORED = 1
    private val OP_PROJECT_MEDIA = 46
    private val OPSTR_PROJECT_MEDIA = "android:project_media"
    private val reflection: ReflectionProvider = ReflectionProviderImpl()

    private val connectivityManager: IConnectivityManager? by lazy {
        try {
            IConnectivityManager.Stub.asInterface(ServiceManager.getService("connectivity"))
        } catch (e: Exception) {
            Timber.e("Failed to get IConnectivityManager: ${e.message}")
            null
        }
    }

    private val appOpsService: IAppOpsService? by lazy {
        try {
            IAppOpsService.Stub.asInterface(ServiceManager.getService("appops"))
        } catch (e: Exception) {
            Timber.e("Failed to get IAppOpsService: ${e.message}")
            null
        }
    }

    override fun setPackageNetworkingEnabled(uid: Int, enabled: Boolean): Boolean {
        val cm = connectivityManager ?: return false
        return try {
            val rule = if (enabled) RULE_DEFAULT else RULE_DENY

            if (!enabled) {
                cm.setFirewallChainEnabled(CHAIN_OEM_DENY_3, true)
            }

            cm.setUidFirewallRule(CHAIN_OEM_DENY_3, uid, rule)

            Timber.d("Set UID $uid networking to $enabled")
            true
        } catch (e: Exception) {
            Timber.e("Failed to set networking: ${e.message}")
            false
        }
    }

    override fun setShowTouches(enabled: Boolean): Boolean {
        return try {
            Settings.System.putInt(systemContext.contentResolver, "show_touches", if (enabled) 1 else 0)
            true
        } catch (e: Exception) {
            Timber.e("Failed to set show_touches: ${e.message}")
            false
        }
    }

    override fun setProjectMediaAllowed(packageName: String, uid: Int, allowed: Boolean): Boolean {
        val service = appOpsService ?: return false
        return try {
            service.setMode(projectMediaOpCode(), uid, packageName, if (allowed) MODE_ALLOWED else MODE_IGNORED)
            true
        } catch (e: Exception) {
            Timber.e("Failed to set PROJECT_MEDIA: ${e.message}")
            false
        }
    }

    override fun isProjectMediaAllowed(packageName: String, uid: Int): Boolean {
        val service = appOpsService ?: return false
        return try {
            service.checkOperation(projectMediaOpCode(), uid, packageName) == MODE_ALLOWED
        } catch (e: Exception) {
            Timber.e("Failed to check PROJECT_MEDIA: ${e.message}")
            false
        }
    }

    private fun projectMediaOpCode(): Int {
        val appOpsManager = Class.forName("android.app.AppOpsManager")
        return reflection.invokeStatic(
            name = "strOpToOp",
            clazz = appOpsManager,
            parameterTypes = arrayOf(String::class.java),
            OPSTR_PROJECT_MEDIA
        ) ?: OP_PROJECT_MEDIA
    }

    private val systemContext: android.content.Context by lazy {
        val activityThread = Class.forName("android.app.ActivityThread")
        val thread = reflection.invokeStatic<Any>(
            name = "systemMain",
            clazz = activityThread
        ) ?: error("Unable to obtain ActivityThread")
        reflection.invoke(thread, "getSystemContext") ?: error("Unable to obtain system context")
    }
}

