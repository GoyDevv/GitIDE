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
            override fun onTextChanged(session: TerminalSession) {}
            override fun onTitleChanged(session: TerminalSession) {}
            override fun onSessionFinished(session: TerminalSession) {}
            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
            override fun onPasteTextFromClipboard(session: TerminalSession?) {}
            override fun onBell(session: TerminalSession) {}
            override fun onColorsChanged(session: TerminalSession) {}
        }

        terminalSession = TerminalSession(
            executable,
            workingDir.absolutePath,
            args,
            envp,
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