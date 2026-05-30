package com.goydevv.gitide.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

class TerminalViewModel : ViewModel() {

    var terminalSession by mutableStateOf<TerminalSession?>(null)
        private set

    fun startShell(filesDir: File, workingDir: File) {
        if (terminalSession != null) return

        val prootBinary = File(filesDir, "usr/bin/proot")
        val rootfsDir = File(filesDir, "proot/rootfs")

        val executable: String
        val args: Array<String>

        if (prootBinary.exists() && rootfsDir.exists()) {
            executable = prootBinary.absolutePath
            args = arrayOf(
                "-r", rootfsDir.absolutePath,
                "-0",
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
                "-b", "/sdcard",
                "-w", "/home/goydevv",
                "/bin/sh"
            )
        } else {
            executable = "/system/bin/sh"
            args = emptyArray()
        }

        val envp = arrayOf(
            "TERM=xterm-256color",
            "HOME=/home/goydevv",
            "PATH=/usr/bin:/bin:/usr/sbin:/sbin",
            "LANG=en_US.UTF-8"
        )

        val client = object : TerminalSessionClient {

            override fun onTextChanged(changedSession: TerminalSession) {}

            override fun onTitleChanged(changedSession: TerminalSession) {}

            override fun onSessionFinished(finishedSession: TerminalSession) {}

            override fun onCopyTextToClipboard(
                session: TerminalSession,
                text: String
            ) {}

            override fun onPasteTextFromClipboard(
                session: TerminalSession?
            ) {}

            override fun onBell(session: TerminalSession) {}

            override fun onColorsChanged(session: TerminalSession) {}

            override fun onTerminalCursorStateChange(state: Boolean) {}

            override fun setTerminalShellPid(
                session: TerminalSession,
                pid: Int
            ) {}

            override fun getTerminalCursorStyle(): Int? = null

            override fun logError(
                tag: String,
                message: String
            ) {}

            override fun logWarn(
                tag: String,
                message: String
            ) {}

            override fun logInfo(
                tag: String,
                message: String
            ) {}

            override fun logDebug(
                tag: String,
                message: String
            ) {}

            override fun logVerbose(
                tag: String,
                message: String
            ) {}

            override fun logStackTraceWithMessage(
                tag: String,
                message: String,
                e: Exception
            ) {}

            override fun logStackTrace(
                tag: String,
                e: Exception
            ) {}
        }

        terminalSession = TerminalSession(
            executable,
            workingDir.absolutePath,
            args,
            envp,
            10000,
            client
        )
    }

    override fun onCleared() {
        super.onCleared()

        try {
            terminalSession?.finishIfRunning()
        } catch (_: Exception) {
        }
    }
}