package com.island.recorder.framework.privileged.core.infrastructure.recycler

import com.island.recorder.framework.privileged.core.infrastructure.lifecycle.Recycler
import com.island.recorder.framework.privileged.core.infrastructure.process.AppProcessTerminal
import com.island.recorder.framework.privileged.core.infrastructure.process.SHELL_COMMAND_PLACEHOLDER
import com.island.recorder.framework.privileged.core.infrastructure.process.SHELL_ROOT
import com.island.recorder.framework.privileged.core.infrastructure.process.SHELL_SYSTEM
import com.island.recorder.framework.privileged.core.infrastructure.process.ShellCommand
import com.rosan.app_process.AppProcess
import java.io.PrintWriter

class AppProcessRecycler(private val terminal: AppProcessTerminal) : Recycler<AppProcess>() {

    override val delayDuration: Long = 100L

    private class CustomizeAppProcess(private val shell: ShellCommand) : AppProcess.Default() {
        override fun newProcess(params: ProcessParams): Process {
            val command = params.cmdList.joinToString(" ") { it.shellQuote() }
            return if (shell.hasCommandPlaceholder()) {
                val cmdList = shell.parts.map { part -> part.replace(SHELL_COMMAND_PLACEHOLDER, command) }
                super.newProcess(ProcessParams(params).setCmdList(cmdList))
            } else {
                val process = super.newProcess(ProcessParams(params).setCmdList(shell.parts))
                val printWriter = PrintWriter(process.outputStream, true)
                printWriter.println(command)
                printWriter.println("exit $?")
                process
            }
        }

        private fun ShellCommand.hasCommandPlaceholder(): Boolean {
            return parts.any { SHELL_COMMAND_PLACEHOLDER in it }
        }

        private fun String.shellQuote(): String {
            if (isEmpty()) return "''"
            if (all { it.isLetterOrDigit() || it in "-_./:=,@" }) return this
            return "'${replace("'", "'\\''")}'"
        }
    }

    override fun onMake(): AppProcess = newAppProcess().apply {
        if (init()) return@apply

        if (terminal == AppProcessTerminal.Root) {
            throw IllegalStateException("Cannot access su command")
        } else {
            throw IllegalStateException("AppProcess init failed for shell: ${terminal.commandName()}")
        }
    }

    private fun newAppProcess(): AppProcess = when (val current = terminal) {
        AppProcessTerminal.Root -> AppProcess.Root()
        AppProcessTerminal.RootSystem -> AppProcess.RootSystem()
        is AppProcessTerminal.Customize -> CustomizeAppProcess(current.command)
    }

    private fun AppProcessTerminal.commandName(): String = when (this) {
        AppProcessTerminal.Root -> SHELL_ROOT
        AppProcessTerminal.RootSystem -> SHELL_SYSTEM
        is AppProcessTerminal.Customize -> command.toString()
    }
}
