package com.island.recorder.framework.privileged.core.infrastructure.recycler

import com.island.recorder.framework.privileged.core.execution.authorization.requireShizukuPermissionGranted
import com.island.recorder.framework.privileged.core.infrastructure.lifecycle.Recycler
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import rikka.shizuku.Shizuku
import timber.log.Timber
import java.io.Closeable
import kotlin.time.Duration.Companion.milliseconds

class ShizukuHookRecycler : Recycler<ShizukuHookRecycler.HookedUserService>() {

    class HookedUserService : Closeable {
        override fun close() {
            Timber.tag("ShizukuHookRecycler").d("close() called, no action needed in hook mode.")
        }
    }

    override fun onMake(): HookedUserService = runBlocking {
        requireShizukuPermissionGranted {
            ensureBinderReady()
            HookedUserService()
        }
    }

    private suspend fun ensureBinderReady() {
        repeat(5) {
            if (Shizuku.pingBinder()) return
            delay(100.milliseconds)
        }
    }
}
