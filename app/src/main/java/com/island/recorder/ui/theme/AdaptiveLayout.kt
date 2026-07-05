package com.island.recorder.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

enum class WindowLayoutType {
    COMPACT,
    MEDIUM,
    EXPANDED
}

data class WindowLayoutInfo(
    val type: WindowLayoutType,
    val isLandscape: Boolean,
    val showNavigationRail: Boolean,
    val isMediumPortrait: Boolean
)

val LocalWindowLayoutInfo = compositionLocalOf<WindowLayoutInfo> {
    error("WindowLayoutInfo not provided")
}

val Context.isPhoneDevice: Boolean
    get() = resources.configuration.smallestScreenWidthDp < 600

val Configuration.windowLayoutType: WindowLayoutType
    get() = when {
        screenWidthDp >= 840 -> WindowLayoutType.EXPANDED
        screenWidthDp >= 600 -> WindowLayoutType.MEDIUM
        else -> WindowLayoutType.COMPACT
    }

@Composable
fun rememberWindowLayoutInfo(): WindowLayoutInfo {
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current

    val screenWidthDp = with(density) { containerSize.width.toDp().value }
    val screenHeightDp = with(density) { containerSize.height.toDp().value }
    val isLandscape = screenWidthDp > screenHeightDp

    val type = when {
        screenWidthDp >= 840f -> WindowLayoutType.EXPANDED
        screenWidthDp >= 600f -> WindowLayoutType.MEDIUM
        else -> WindowLayoutType.COMPACT
    }

    val showNavigationRail = type == WindowLayoutType.EXPANDED || (type == WindowLayoutType.MEDIUM && isLandscape)
    val isMediumPortrait = type == WindowLayoutType.MEDIUM && !isLandscape

    return WindowLayoutInfo(
        type = type,
        isLandscape = isLandscape,
        showNavigationRail = showNavigationRail,
        isMediumPortrait = isMediumPortrait
    )
}
