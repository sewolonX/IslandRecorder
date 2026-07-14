package com.island.recorder.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplayTransitionEffects
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.island.recorder.ui.page.settings.SettingsPage

@Composable
fun MiuixNavContainer() {
    val backStack = rememberNavBackStack(Route.Settings)
    val navigator = remember(backStack) { Navigator(backStack) }

    CompositionLocalProvider(
        LocalNavigator provides navigator,
    ) {
        val entries = rememberDecoratedNavEntries(
            backStack = navigator.backStack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
                NavEntryDecorator { content ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        content.Content()
                    }
                }
            ),
            entryProvider = entryProvider {
                entry<Route.Settings> {
                    SettingsPage()
                }
            }
        )

        val sceneState = rememberSceneState(
            entries = entries,
            sceneStrategies = listOf(SinglePaneSceneStrategy()),
            sceneDecoratorStrategies = emptyList(),
            sharedTransitionScope = null,
            onBack = { navigator.pop() },
        )
        val scene = sceneState.currentScene
        val navigationEventState = rememberNavigationEventState(
            currentInfo = androidx.navigation3.scene.SceneInfo(scene),
            backInfo = sceneState.previousScenes.map { androidx.navigation3.scene.SceneInfo(it) }
        )

        NavigationBackHandler(
            state = navigationEventState,
            isBackEnabled = scene.previousEntries.isNotEmpty(),
            onBackCompleted = { navigator.pop() },
            onBackCancelled = {}
        )

        NavDisplay(
            sceneState = sceneState,
            navigationEventState = navigationEventState,
            contentAlignment = Alignment.TopStart,
            sizeTransform = null,
            transitionEffects = NavDisplayTransitionEffects(
                blockInputDuringTransition = true
            )
        )
    }
}
