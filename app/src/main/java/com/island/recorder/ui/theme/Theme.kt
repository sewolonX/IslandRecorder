package com.island.recorder.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

private val LocalIsDark = staticCompositionLocalOf { false }

object IslandTheme {
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalIsDark.current
}

@Composable
fun IslandRecorderTheme(
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as ComponentActivity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    val colorSchemeMode = ColorSchemeMode.System

    val controller = remember(isDark) {
        ThemeController(
            colorSchemeMode = colorSchemeMode,
            isDark = isDark
        )
    }

    CompositionLocalProvider(
        LocalIsDark provides isDark
    ) {
        NavigationBarContrastHandler()
        MiuixTheme(controller = controller, content = content)
    }
}

@Composable
private fun NavigationBarContrastHandler() {
    val configuration = LocalConfiguration.current
    val activity = LocalActivity.current

    DisposableEffect(configuration) {
        val window = activity?.window
        window?.isNavigationBarContrastEnforced = false
        onDispose {}
    }
}
