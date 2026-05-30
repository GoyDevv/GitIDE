package com.goydevv.gitide

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.goydevv.gitide.engine.GitEngine
import com.goydevv.gitide.ui.screens.ProjectManagerScreen
import com.goydevv.gitide.ui.screens.SetupWizardScreen
import com.goydevv.gitide.ui.screens.WorkspaceScreen

// Lexend Custom Typography Tokens
val LexendFontFamily = FontFamily(
    Font(R.font.lexend_regular, FontWeight.Normal),
    Font(R.font.lexend_medium, FontWeight.Medium),
    Font(R.font.lexend_semibold, FontWeight.SemiBold),
    Font(R.font.lexend_bold, FontWeight.Bold)
)

// Premium Deep Violet Dark Palette
val VioletColorScheme = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF2E1065),
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFDDD6FE),
    secondary = Color(0xFFC084FC),
    onSecondary = Color(0xFF3B0764),
    background = Color(0xFF090514),
    surface = Color(0xFF140F26),
    surfaceVariant = Color(0xFF1E1638),
    surfaceContainerLow = Color(0xFF0F0A1C),
    surfaceContainerHigh = Color(0xFF251C42),
    outline = Color(0xFF7C3AED),
    error = Color(0xFFF87171)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Critical: Initialize execution paths for the subsystem environment
        GitEngine.initialize(filesDir)
        
        val sharedPrefs = getSharedPreferences("gitide_prefs", Context.MODE_PRIVATE)
        var isSetupComplete by mutableStateOf(sharedPrefs.getBoolean("setup_complete", false))
        var currentProjectPath by mutableStateOf<String?>(null)

        setContent {
            MaterialTheme(
                colorScheme = VioletColorScheme,
                typography = Typography(
                    displayLarge = MaterialTheme.typography.displayLarge.copy(fontFamily = LexendFontFamily),
                    headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontFamily = LexendFontFamily),
                    titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = LexendFontFamily),
                    bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = LexendFontFamily),
                    labelLarge = MaterialTheme.typography.labelLarge.copy(fontFamily = LexendFontFamily)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!isSetupComplete) {
                        SetupWizardScreen(
                            context = this,
                            onSetupComplete = {
                                sharedPrefs.edit().putBoolean("setup_complete", true).apply()
                                isSetupComplete = true
                            }
                        )
                    } else if (currentProjectPath == null) {
                        ProjectManagerScreen(
                            tokenSetting = sharedPrefs.getString("github_token", "") ?: "",
                            onTokenSave = { newToken -> 
                                sharedPrefs.edit().putString("github_token", newToken).apply() 
                            },
                            onProjectSelected = { path -> 
                                currentProjectPath = path 
                            }
                        )
                    } else {
                        WorkspaceScreen(
                            projectPath = currentProjectPath!!,
                            onBackToManager = { currentProjectPath = null }
                        )
                    }
                }
            }
        }
    }
}
