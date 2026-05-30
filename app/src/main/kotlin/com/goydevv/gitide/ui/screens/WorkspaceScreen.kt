package com.goydevv.gitide.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
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
import com.goydevv.gitide.engine.GitEngine
import com.goydevv.gitide.ui.components.FloatingVibrantShapesBackground
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    projectPath: String,
    onBackToManager: () -> Unit
) {
    val projectFile = remember(projectPath) { File(projectPath) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Embed the premium visual environment backdrop
        FloatingVibrantShapesBackground()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            text = projectFile.name, 
                            fontFamily = LexendFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackToManager) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                                contentDescription = "Back to Manager",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                GitWindow(projectFile = projectFile)
            }
        }
    }
}

@Composable
fun GitWindow(projectFile: File) {
    var changeList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var historyLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var commitMessage by remember { mutableStateOf("") }
    var operationTerminalOutput by remember { mutableStateOf("") }

    fun refreshRepoState() {
        changeList = GitEngine.getStatus(projectFile)
        historyLogs = GitEngine.getLog(projectFile)
    }

    LaunchedEffect(projectFile) {
        refreshRepoState()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Source Control Matrix", 
                fontFamily = LexendFontFamily,
                fontSize = 18.sp, 
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            IconButton(onClick = { refreshRepoState() }) {
                Icon(
                    imageVector = Icons.Default.Refresh, 
                    contentDescription = "Refresh State",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Glassmorphic interactive action control panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    placeholder = { Text("Enter compilation commit message...", fontFamily = LexendFontFamily) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                    )
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            GitEngine.stageFiles(projectFile)
                            val result = GitEngine.commit(projectFile, commitMessage)
                            operationTerminalOutput = result.getOrNull() 
                                ?: "ERR: ${result.exceptionOrNull()?.localizedMessage}"
                            commitMessage = ""
                            refreshRepoState()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Commit All", fontFamily = LexendFontFamily, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = {
                            val result = GitEngine.push(projectFile)
                            operationTerminalOutput = result.getOrNull() 
                                ?: "ERR: ${result.exceptionOrNull()?.localizedMessage}"
                            refreshRepoState()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Push Changes", fontFamily = LexendFontFamily, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (operationTerminalOutput.isNotBlank()) {
            // Specialized cosmic deep-black terminal console space
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp)
                    .background(Color(0xFF07040F), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = operationTerminalOutput,
                    color = if (operationTerminalOutput.startsWith("ERR:")) Color(0xFFF87171) else Color(0xFF34D399),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }

        Text(
            text = "Changed Files (${changeList.size})", 
            fontFamily = LexendFontFamily,
            fontSize = 14.sp, 
            fontWeight = FontWeight.SemiBold, 
            color = MaterialTheme.colorScheme.primary
        )

        Box(modifier = Modifier.weight(0.5f)) {
            if (changeList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No unstaged modifications detected", 
                        fontFamily = LexendFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), 
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(changeList) { change ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = change.first, 
                                fontSize = 13.sp, 
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                            Text(
                                text = change.second,
                                color = when (change.second) {
                                    "M" -> Color(0xFFFBBF24) // Soft Amber
                                    "A" -> Color(0xFF34D399) // Soft Emerald
                                    "D" -> Color(0xFFF87171) // Soft Coral Red
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "Commit History Matrix", 
            fontFamily = LexendFontFamily,
            fontSize = 14.sp, 
            fontWeight = FontWeight.SemiBold, 
            color = MaterialTheme.colorScheme.primary
        )

        Box(modifier = Modifier.weight(0.5f)) {
            if (historyLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No commit records found in sandbox environment", 
                        fontFamily = LexendFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), 
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(historyLogs) { logLine ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp).padding(top = 2.dp)
                            )
                            Text(
                                text = logLine,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }
    }
}
