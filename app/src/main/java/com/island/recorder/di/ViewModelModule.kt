package com.island.recorder.di

import com.island.recorder.ui.page.home.HomeViewModel
import com.island.recorder.ui.page.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::HomeViewModel)
    viewModelOf(::SettingsViewModel)
}
