package com.island.recorder.di

import com.island.recorder.ui.page.home.MiuixHomeViewModel
import com.island.recorder.ui.page.settings.MiuixSettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::MiuixHomeViewModel)
    viewModelOf(::MiuixSettingsViewModel)
}
