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

    /**
     * Standardizes shell launch through the proot_launch.sh script.
     * Fixed to use /system/bin/sh as the entry point to ensureargv[0] compliance.
     */
    fun startShell(
        filesDir: File,
        workingDir: File
    ) {
        if (terminalSession != null) return

        val launcherScript = File(filesDir, "usr/bin/proot_launch.sh")

        val executable: String
        val args: Array<String>
        val envp: Array<String>

        if (launcherScript.exists()) {
            // FIX: Use system shell to execute the launcher.
            // Passing launcher path in args ensures argv[1] is valid for sh.
            executable = "/system/bin/sh"
            args = arrayOf(launcherScript.absolutePath)

            // We only need basic environment variables here;
            // proot_launch.sh will set up the internal guest environment.
            envp = arrayOf(
                "TERM=xterm-256color",
                "LANG=C.UTF-8"
            )
        } else {
            // Emergency fallback to local Android shell if bootstrap is broken.
            executable = "/system/bin/sh"
            args = emptyArray()
            envp = arrayOf(
                "TERM=xterm-256color",
                "PATH=/system/bin:/system/xbin",
                "HOME=${workingDir.absolutePath}"
            )
        }

        val client = object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) {}
            override fun onTitleChanged(changedSession: TerminalSession) {}
            override fun onSessionFinished(finishedSession: TerminalSession) {}
            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
            override fun onPasteTextFromClipboard(session: TerminalSession?) {}
            override fun onBell(session: TerminalSession) {}
            override fun onColorsChanged(session: TerminalSession) {}
            override fun onTerminalCursorStateChange(state: Boolean) {}
            override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
            override fun getTerminalCursorStyle(): Int? = null
            override fun logError(tag: String, message: String) {}
            override fun logWarn(tag: String, message: String) {}
            override fun logInfo(tag: String, message: String) {}
            override fun logDebug(tag: String, message: String) {}
            override fun logVerbose(tag: String, message: String) {}
            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
            override fun logStackTrace(tag: String, e: Exception) {}
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
        try {
            terminalSession?.finishIfRunning()
        } catch (_: Exception) {}
        super.onCleared()
    }
}
