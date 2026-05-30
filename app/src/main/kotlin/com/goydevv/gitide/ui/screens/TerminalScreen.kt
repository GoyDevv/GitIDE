package com.goydevv.gitide.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goydevv.gitide.ui.viewmodels.TerminalViewModel
import com.termux.view.TerminalView
import java.io.File

@Composable
fun TerminalScreen(
    filesDir: File,
    currentProjectDir: File,
    viewModel: TerminalViewModel = viewModel()
) {

    LaunchedEffect(filesDir.absolutePath, currentProjectDir.absolutePath) {
        viewModel.startShell(
            filesDir = filesDir,
            workingDir = currentProjectDir
        )
    }

    val session = viewModel.terminalSession

    if (session == null) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090514)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
        }

    } else {

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090514)),
            factory = { context ->

                TerminalView(context, null).apply {
                    attachSession(session)

                    // CRITICAL: PTY and emulator state often depend on initial size.
                    // TerminalView.updateSize() internally calls session.updateSize().
                    post {
                        updateSize()
                    }

                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()
                }
            },
            update = { view ->
                if (view.currentSession != session) {
                    view.attachSession(session)
                    view.updateSize()
                }
            }
        )
    }
}
