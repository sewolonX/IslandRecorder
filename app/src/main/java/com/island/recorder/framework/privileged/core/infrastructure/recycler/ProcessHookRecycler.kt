package com.island.recorder.framework.privileged.core.infrastructure.recycler

import android.content.Context
import android.os.IBinder
import com.island.recorder.framework.privileged.core.infrastructure.lifecycle.Recyclable
import com.island.recorder.framework.privileged.core.infrastructure.lifecycle.Recycler
import com.island.recorder.framework.privileged.core.infrastructure.lifecycle.RecyclerManager
import com.island.recorder.framework.privileged.core.infrastructure.process.AppProcessTerminal
import com.rosan.app_process.AppProcess
import java.io.Closeable

class ProcessHookRecycler(
    private val terminal: AppProcessTerminal,
    private val context: Context,
    private val appProcessRecyclerManager: RecyclerManager<AppProcessTerminal, AppProcessRecycler>
) : Recycler<ProcessHookRecycler.HookedUserService>() {

    override val delayDuration: Long = 5_000L

    class HookedUserService(
        private val appProcessHandle: Recyclable<AppProcess>
    ) : Closeable {
        fun binderWrapper(binder: IBinder): IBinder = appProcessHandle.entity.binderWrapper(binder)

        override fun close() {
            appProcessHandle.recycle()
        }
    }

    override fun onMake(): HookedUserService {
        val appProcessHandle = appProcessRecyclerManager.get(terminal).make()

        if (!appProcessHandle.entity.init(context)) {
            throw IllegalStateException("Failed to initialize AppProcess for hook mode.")
        }

        return HookedUserService(appProcessHandle)
    }
}
