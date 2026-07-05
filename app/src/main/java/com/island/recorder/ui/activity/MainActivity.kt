package com.island.recorder.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.island.recorder.domain.settings.model.AppPreferences
import com.island.recorder.domain.settings.repository.AppSettingsRepository
import com.island.recorder.ui.navigation.MiuixNavContainer
import com.island.recorder.ui.theme.IslandRecorderTheme
import com.island.recorder.ui.theme.LocalWindowLayoutInfo
import com.island.recorder.ui.theme.rememberWindowLayoutInfo
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity(), KoinComponent {
    private val appSettingsRepository by inject<AppSettingsRepository>()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        var isLoaded = false
        splashScreen.setKeepOnScreenCondition { !isLoaded }

        super.onCreate(savedInstanceState)

        setContent {
            val appPreferences by appSettingsRepository.preferencesFlow.collectAsStateWithLifecycle(
                AppPreferences()
            )
            isLoaded = appPreferences.isLoaded

            if (!isLoaded) return@setContent

            val layoutInfo = rememberWindowLayoutInfo()

            CompositionLocalProvider(
                LocalWindowLayoutInfo provides layoutInfo
            ) {
                IslandRecorderTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MiuixTheme.colorScheme.surface)
                    ) {
                        MiuixNavContainer()
                    }
                }
            }
        }
    }
}
