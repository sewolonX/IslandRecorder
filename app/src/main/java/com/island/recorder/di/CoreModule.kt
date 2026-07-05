package com.island.recorder.di

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
