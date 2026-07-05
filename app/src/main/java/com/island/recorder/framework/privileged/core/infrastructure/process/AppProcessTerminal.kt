package com.island.recorder.framework.privileged.core.infrastructure.process

sealed interface AppProcessTerminal {
    data object Root : AppProcessTerminal
    data object RootSystem : AppProcessTerminal
    data class Customize(val command: ShellCommand) : AppProcessTerminal
}
