package com.island.recorder.framework.privileged.core.infrastructure.lifecycle

import com.island.recorder.framework.privileged.core.infrastructure.process.AppProcessTerminal
import com.island.recorder.framework.privileged.core.infrastructure.recycler.ProcessHookRecycler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

const val PROCESS_HOOK_RECYCLER_MANAGER_QUALIFIER = "processHookRecyclerManager"

class RecordingRootProcessSession(
    private val processHookRecyclerManager:
        RecyclerManager<AppProcessTerminal, ProcessHookRecycler>
) {
    private val mutex = Mutex()
    private val nextShortcutOwner = AtomicLong(0L)
    private val shortcutOwners = mutableSetOf<Long>()
    private var handle: Recyclable<ProcessHookRecycler.HookedUserService>? = null
    private var recorderServiceOwnsHandle = false

    fun newShortcutOwner(): Long = nextShortcutOwner.incrementAndGet()

    suspend fun warmUpForShortcut(owner: Long): Boolean = mutex.withLock {
        shortcutOwners += owner
        if (handle != null) return@withLock true

        return@withLock try {
            handle = rootRecycler().make()
            Timber.tag(TAG).i("Root app_process warmed for recording shortcut")
            true
        } catch (e: Exception) {
            shortcutOwners -= owner
            Timber.tag(TAG).e(e, "Failed to warm Root app_process for recording shortcut")
            false
        }
    }

    suspend fun handOffToRecorderService(shortcutOwner: Long): Boolean = mutex.withLock {
        shortcutOwners -= shortcutOwner
        if (handle == null) {
            try {
                handle = rootRecycler().make()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to acquire Root app_process for recorder service")
                return@withLock false
            }
        }

        recorderServiceOwnsHandle = true
        Timber.tag(TAG).i("Root app_process ownership transferred to recorder service")
        true
    }

    suspend fun releaseShortcut(owner: Long) = mutex.withLock {
        shortcutOwners -= owner
        releaseIfUnowned("shortcut owner $owner")
    }

    suspend fun releaseRecorderService() = mutex.withLock {
        recorderServiceOwnsHandle = false
        releaseIfUnowned("recorder service")
    }

    private fun releaseIfUnowned(releasedBy: String) {
        if (shortcutOwners.isNotEmpty() || recorderServiceOwnsHandle) return
        val currentHandle = handle ?: return
        currentHandle.close()
        handle = null
        Timber.tag(TAG).i("Root app_process released after $releasedBy ended")
    }

    private fun rootRecycler(): ProcessHookRecycler =
        processHookRecyclerManager.get(AppProcessTerminal.Root)

    private companion object {
        private const val TAG = "RecordingRootSession"
    }
}
