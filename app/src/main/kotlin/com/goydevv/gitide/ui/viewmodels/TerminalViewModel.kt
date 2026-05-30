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
     * Uses /system/bin/sh to execute the launcher, ensuring stable PTY allocation.
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
            // entrypoint: /system/bin/sh
            // args[0]: /data/data/.../usr/bin/proot_launch.sh
            executable = "/system/bin/sh"
            args = arrayOf(launcherScript.absolutePath)

            envp = arrayOf(
                "TERM=xterm-256color",
                "LANG=C.UTF-8"
            )
        } else {
            // Emergency fallback
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
