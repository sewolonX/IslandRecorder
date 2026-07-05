package com.island.recorder.framework.privileged.provider

import android.content.pm.PackageManager
import com.island.recorder.framework.privileged.DeviceCapability
import com.island.recorder.framework.privileged.RootMode
import com.island.recorder.framework.privileged.ShizukuMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import timber.log.Timber

class DeviceCapabilityProviderImpl : DeviceCapabilityProvider {
    companion object {
        private const val ROOT_DETECTION_EXTRA_PATH =
            "/data/adb/ksu/bin:/data/adb/ap/bin:/data/adb/magisk/bin"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _rootModeFlow = MutableStateFlow(RootMode.None)
    override val rootModeFlow: StateFlow<RootMode> = _rootModeFlow.asStateFlow()

    private val _shizukuModeFlow = MutableStateFlow(ShizukuMode.NotRunning)
    override val shizukuModeFlow: StateFlow<ShizukuMode> = _shizukuModeFlow.asStateFlow()

    private val _capabilityFlow = MutableStateFlow(
        DeviceCapability(
            RootMode.None,
            ShizukuMode.NotRunning
        )
    )
    override val capabilityFlow: StateFlow<DeviceCapability> = _capabilityFlow.asStateFlow()

    init {
        Shizuku.addBinderReceivedListenerSticky {
            updateShizukuMode()
        }
        Shizuku.addBinderDeadListener {
            _shizukuModeFlow.value = ShizukuMode.NotRunning
            updateCapability()
        }
        refreshPrivilegeStatus()
    }

    override fun current(): DeviceCapability = capabilityFlow.value

    override fun refreshPrivilegeStatus() {
        updateShizukuMode()
        scope.launch(Dispatchers.IO) {
            _rootModeFlow.value = detectRootMode()
            updateCapability()
        }
    }

    override fun requestShizukuPermission(requestCode: Int) {
        if (shizukuModeFlow.value == ShizukuMode.NotAuthorized) {
            Shizuku.requestPermission(requestCode)
        }
    }

    override fun addShizukuPermissionResultListener(
        listener: Shizuku.OnRequestPermissionResultListener
    ) {
        Shizuku.addRequestPermissionResultListener(listener)
    }

    override fun removeShizukuPermissionResultListener(
        listener: Shizuku.OnRequestPermissionResultListener
    ) {
        Shizuku.removeRequestPermissionResultListener(listener)
    }

    private fun updateShizukuMode() {
        scope.launch(Dispatchers.IO) {
            _shizukuModeFlow.value = detectShizukuMode()
            updateCapability()
        }
    }

    private fun updateCapability() {
        _capabilityFlow.value = DeviceCapability(
            rootMode = rootModeFlow.value,
            shizukuMode = shizukuModeFlow.value
        )
    }

    private fun detectShizukuMode(): ShizukuMode =
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    ShizukuMode.Authorized
                } else {
                    ShizukuMode.NotAuthorized
                }
            } else {
                ShizukuMode.NotRunning
            }
        } catch (e: Throwable) {
            Timber.d(e, "Failed to detect Shizuku mode")
            ShizukuMode.NotRunning
        }

    private suspend fun detectRootMode(): RootMode = withContext(Dispatchers.IO) {
        if (runSuProbe("ksud -V")) return@withContext RootMode.KernelSU
        if (runSuProbe("magisk -v")) return@withContext RootMode.Magisk
        if (runSuProbe("apd -V")) return@withContext RootMode.APatch

        RootMode.None
    }

    private fun runSuProbe(command: String): Boolean =
        try {
            val shellCommand = "export PATH=\$PATH:$ROOT_DETECTION_EXTRA_PATH && $command"
            val process = ProcessBuilder("su", "-c", shellCommand)
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "Root probe failed: %s", command)
            false
        }
}
