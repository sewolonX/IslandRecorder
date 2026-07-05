package com.island.recorder.di

import com.island.recorder.core.audio.AudioRecorder
import com.island.recorder.core.codec.AudioEncoder
import com.island.recorder.core.codec.VideoEncoder
import com.island.recorder.core.reflection.ReflectionProvider
import com.island.recorder.core.reflection.ReflectionProviderImpl
import com.island.recorder.domain.device.provider.PermissionChecker
import com.island.recorder.domain.recording.provider.RecordingStorageProvider
import com.island.recorder.framework.permission.AndroidPermissionChecker
import com.island.recorder.framework.privileged.core.infrastructure.lifecycle.RecyclerManager
import com.island.recorder.framework.privileged.core.infrastructure.process.AppProcessTerminal
import com.island.recorder.framework.privileged.core.infrastructure.recycler.AppProcessRecycler
import com.island.recorder.framework.privileged.core.infrastructure.recycler.ProcessHookRecycler
import com.island.recorder.framework.privileged.core.infrastructure.recycler.ShizukuHookRecycler
import com.island.recorder.framework.privileged.provider.DeviceCapabilityProvider
import com.island.recorder.framework.privileged.provider.DeviceCapabilityProviderImpl
import com.island.recorder.framework.privileged.provider.PrivilegedOperationProvider
import com.island.recorder.framework.storage.SafRecordingStorageProviderImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val coreModule = module {
    single<ReflectionProvider> { ReflectionProviderImpl() }
    single<PermissionChecker> { AndroidPermissionChecker(androidContext()) }
    factory { AudioRecorder() }
    factory { AudioEncoder() }
    factory { params ->
        VideoEncoder(
            width = params[0],
            height = params[1],
            bitrate = params[2],
            frameRate = params[3],
            maxFpsToEncoder = params[4],
            mimeType = params[5],
            isHdrEnabled = params[6]
        )
    }
    single<RecordingStorageProvider> { SafRecordingStorageProviderImpl(androidContext(), get()) }
    single<DeviceCapabilityProvider> { DeviceCapabilityProviderImpl() }
    single {
        RecyclerManager<AppProcessTerminal, AppProcessRecycler> { terminal ->
            AppProcessRecycler(
                terminal
            )
        }
    }
    factory { (terminal: AppProcessTerminal) ->
        ProcessHookRecycler(
            terminal = terminal,
            context = androidContext(),
            appProcessRecyclerManager = get()
        )
    }
    single { ShizukuHookRecycler() }
    single { PrivilegedOperationProvider(androidContext(), get(), get(), get()) }
}
