package com.island.recorder.framework.privileged.core.infrastructure.lifecycle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

abstract class Recycler<T : Closeable> : Closeable {

    private val lock = ReentrantLock()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var _entity: T? = null
    protected val entity: T? get() = _entity

    private val _referenceCount = AtomicInteger(0)
    val referenceCount: Int get() = _referenceCount.get()

    @Volatile
    private var recycleJob: Job? = null

    @Volatile
    private var closed = false

    protected open val delayDuration: Long = 15_000L

    fun make(): Recyclable<T> = lock.withLock {
        check(!closed) { "Recycler is closed" }

        recycleJob?.cancel()
        recycleJob = null

        val localEntity = _entity ?: onMake().also {
            _entity = it
            Timber.d("${this::class.simpleName}: Entity created")
        }

        _referenceCount.incrementAndGet()
        Timber.d("${this::class.simpleName}: make() called, refCount=${_referenceCount.get()}")

        return Recyclable(localEntity) {
            decrementAndScheduleRecycle()
        }
    }

    protected abstract fun onMake(): T

    protected open fun onRecycle() {}

    private fun decrementAndScheduleRecycle() {
        lock.withLock {
            val count = _referenceCount.decrementAndGet()
            Timber.d("${this::class.simpleName}: recycle() called, refCount=$count")

            if (count > 0 || closed) return

            recycleJob?.cancel()
            recycleJob = scope.launch {
                delay(delayDuration)
                doRecycle(force = false)
            }
        }
    }

    fun recycleForcibly() {
        lock.withLock {
            doRecycle(force = true)
        }
    }

    private fun doRecycle(force: Boolean) {
        lock.withLock {
            if (!force && _referenceCount.get() > 0) {
                Timber.d("${this::class.simpleName}: Recycle cancelled, refCount=${_referenceCount.get()}")
                return
            }

            recycleJob?.cancel()
            recycleJob = null

            _entity?.let { entity ->
                Timber.d("${this::class.simpleName}: Closing entity")
                runCatching { entity.close() }
                    .onFailure { Timber.e(it, "Error closing entity") }
                _entity = null
            }

            if (force) {
                _referenceCount.set(0)
            }

            runCatching { onRecycle() }
                .onFailure { Timber.e(it, "Error in onRecycle callback") }
        }
    }

    override fun close() {
        lock.withLock {
            if (closed) return
            closed = true
            scope.cancel()
            doRecycle(force = true)
        }
    }
}
