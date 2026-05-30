package com.goydevv.gitide.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goydevv.gitide.LexendFontFamily
import com.goydevv.gitide.services.SetupWizardService
import com.goydevv.gitide.services.SetupWizardService.Companion.SetupState
import com.goydevv.gitide.ui.components.FloatingVibrantShapesBackground

@Composable
fun SetupWizardScreen(context: Context, onSetupComplete: () -> Unit) {
    val setupState by SetupWizardService.setupState.collectAsState()
    val terminalLines = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    var statusHeadline by remember { mutableStateOf("Environment Subsystem") }
    var runningPercentage by remember { mutableStateOf(0) }
    var showActionButton by remember { mutableStateOf(true) }
    var executionErrorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(setupState) {
        when (val state = setupState) {
            is SetupState.Idle -> {
                statusHeadline = "Subsystem Engine Dormant"
                runningPercentage = 0
                showActionButton = true
            }
            is SetupState.Progress -> {
                statusHeadline = state.message
                runningPercentage = state.percentage
                showActionButton = false
            }
            is SetupState.TerminalLog -> {
                terminalLines.add(state.line)
                listState.animateScrollToItem(terminalLines.size - 1)
            }
            is SetupState.Success -> {
                onSetupComplete()
            }
            is SetupState.Error -> {
                executionErrorMsg = state.reason
                showActionButton = true
            }
        }
    }

    // Material 3 Button Morph Spec (Sharp Rect to Pill-Shape during installation processing)
    val buttonRadius by animateDpAsState(
        targetValue = if (!showActionButton) 24.dp else 12.dp,
        label = "ButtonMorph"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        FloatingVibrantShapesBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                Text(
                    text = "GitIDE Core Sandbox",
                    fontFamily = LexendFontFamily,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Compiling PRoot virtual environments with native hooks.",
                    fontFamily = LexendFontFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            // Glassmorphic status panel
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(statusHeadline, fontFamily = LexendFontFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    LinearProgressIndicator(
                        progress = { runningPercentage / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bootstrap Matrix Progress", fontFamily = LexendFontFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$runningPercentage%", fontFamily = LexendFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            // Deep-black terminal log space
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF07040F), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                if (terminalLines.isEmpty()) {
                    Text(
                        text = "Matrix terminal console awaiting launch authorization...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(terminalLines) { line ->
                            Text(
                                text = line,
                                color = if (line.contains("ERR:") || line.contains("failed")) Color(0xFFF87171) else Color(0xFF34D399),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (executionErrorMsg != null) {
                Text(
                    text = "Critical Failure: $executionErrorMsg",
                    fontFamily = LexendFontFamily,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = {
                    if (showActionButton) {
                        executionErrorMsg = null
                        terminalLines.clear()
                        val intent = Intent(context, SetupWizardService::class.java)
                        context.startService(intent)
                    }
                },
                enabled = showActionButton,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(buttonRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                )
            ) {
                if (showActionButton) {
                    Text(
                        text = if (executionErrorMsg != null) "Re-initialize Infrastructure" else "Build Native Core",
                        fontFamily = LexendFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.5.dp)
                }
            }
        }
    }
}
