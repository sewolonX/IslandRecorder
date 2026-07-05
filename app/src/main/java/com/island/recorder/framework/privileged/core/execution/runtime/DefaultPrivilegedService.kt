package com.island.recorder.framework.privileged.core.execution.runtime

import android.annotation.SuppressLint
import android.content.AttributionSource
import android.content.Context
import android.net.IConnectivityManager
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import com.android.internal.app.IAppOpsService
import com.island.recorder.core.reflection.ReflectionProvider
import com.island.recorder.core.reflection.invoke
import com.island.recorder.core.reflection.invokeStatic
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

@SuppressLint("PrivateApi")
class DefaultPrivilegedService private constructor(
    private val runtime: PrivilegedRuntime
) : PrivilegedOperations, KoinComponent {
    companion object {
        private const val TAG = "PrivilegedService"
        private const val FIREWALL_CHAIN_OEM_DENY_3 = 9
        private const val FIREWALL_RULE_DEFAULT = 0
        private const val FIREWALL_RULE_DENY = 2
        private const val MODE_ALLOWED = 0
        private const val MODE_IGNORED = 1
        private const val OP_PROJECT_MEDIA = 46
        private const val OPSTR_PROJECT_MEDIA = "android:project_media"
        private const val SETTING_SHOW_TOUCHES = "show_touches"
        private const val CALL_METHOD_PUT_SYSTEM = "PUT_system"
        private const val CALL_METHOD_USER_KEY = "_user"
        private const val CALL_METHOD_OVERRIDEABLE_BY_RESTORE_KEY = "_overrideable_by_restore"

        fun system() = DefaultPrivilegedService(PrivilegedRuntime.SystemApp)

        fun userService() = DefaultPrivilegedService(PrivilegedRuntime.UserService)

        fun shizukuHook() = DefaultPrivilegedService(PrivilegedRuntime.ShizukuHooked)

        fun binderWrapped(
            name: String,
            binderWrapper: (IBinder) -> IBinder
        ) = DefaultPrivilegedService(PrivilegedRuntime.BinderWrapped(name, binderWrapper))
    }

    private val context by inject<Context>()
    private val reflect by inject<ReflectionProvider>()

    private val connectivityManager: IConnectivityManager by lazy {
        runtime.connectivityManager()
    }

    private val appOpsService: IAppOpsService? by lazy {
        runtime.appOpsBinder()?.let { IAppOpsService.Stub.asInterface(it) }
    }

    override fun setPackageNetworkingEnabled(uid: Int, enabled: Boolean): Boolean {
        return try {
            val rule = if (enabled) FIREWALL_RULE_DEFAULT else FIREWALL_RULE_DENY

            if (!enabled) {
                connectivityManager.setFirewallChainEnabled(FIREWALL_CHAIN_OEM_DENY_3, true)
            }

            connectivityManager.setUidFirewallRule(FIREWALL_CHAIN_OEM_DENY_3, uid, rule)
            Timber.tag(TAG).d("Set UID $uid networking to $enabled via ${runtime.name}")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to set networking via ${runtime.name}")
            false
        }
    }

    override fun setShowTouches(enabled: Boolean): Boolean {
        return try {
            val targetValue = if (enabled) "1" else "0"
            putSystemSettingWithHookedProvider(SETTING_SHOW_TOUCHES, targetValue)
            Timber.tag(TAG).i("Set show_touches to $targetValue via ${runtime.name}.")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to set show_touches via ${runtime.name}")
            false
        }
    }

    private fun putSystemSettingWithHookedProvider(name: String, value: String) {
        val targetBinder = runtime.settingsBinder(reflect, Settings.System::class.java)
            ?: throw IllegalStateException("Privileged Settings.System binder is unavailable")
        val provider = hookedContentProvider(targetBinder)
        val extras = Bundle().apply {
            putString(Settings.NameValueTable.VALUE, value)
            putInt(CALL_METHOD_USER_KEY, android.os.Process.myUid() / 100000)
            putBoolean(CALL_METHOD_OVERRIDEABLE_BY_RESTORE_KEY, true)
        }

        reflect.invoke<Bundle>(
            obj = provider,
            name = "call",
            clazz = provider.javaClass,
            parameterTypes = arrayOf(
                AttributionSource::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                Bundle::class.java
            ),
            runtime.settingsResolverContext(context).attributionSource,
            Settings.System.CONTENT_URI.authority,
            CALL_METHOD_PUT_SYSTEM,
            name,
            extras
        )
    }

    private fun hookedContentProvider(binder: IBinder): Any {
        val contentProviderNative = Class.forName("android.content.ContentProviderNative")
        return reflect.invokeStatic(
            name = "asInterface",
            clazz = contentProviderNative,
            parameterTypes = arrayOf(IBinder::class.java),
            binder
        ) ?: throw IllegalStateException("Failed to create hooked Settings provider")
    }

    override fun setProjectMediaAllowed(packageName: String, uid: Int, allowed: Boolean): Boolean {
        val service = appOpsService ?: return false
        return try {
            service.setMode(projectMediaOpCode(), uid, packageName, if (allowed) MODE_ALLOWED else MODE_IGNORED)
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to set PROJECT_MEDIA via ${runtime.name}")
            false
        }
    }

    override fun isProjectMediaAllowed(packageName: String, uid: Int): Boolean {
        val service = appOpsService ?: return false
        return try {
            service.checkOperation(projectMediaOpCode(), uid, packageName) == MODE_ALLOWED
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to check PROJECT_MEDIA via ${runtime.name}")
            false
        }
    }

    private fun projectMediaOpCode(): Int {
        val appOpsManager = Class.forName("android.app.AppOpsManager")
        return reflect.invokeStatic(
            name = "strOpToOp",
            clazz = appOpsManager,
            parameterTypes = arrayOf(String::class.java),
            OPSTR_PROJECT_MEDIA
        ) ?: OP_PROJECT_MEDIA
    }
}
