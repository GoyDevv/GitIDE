package com.goydevv.gitide.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
    LaunchedEffect(Unit) {
        viewModel.startShell(filesDir, currentProjectDir)
    }

    val session = viewModel.terminalSession

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090514))
    ) {
        if (session != null) {
            AndroidView(
                factory = { context ->
                    TerminalView(context, null).apply {
                        changeSession(session)
                        requestFocus()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}