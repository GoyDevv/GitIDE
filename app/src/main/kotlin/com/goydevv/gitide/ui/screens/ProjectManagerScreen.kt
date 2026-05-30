package com.goydevv.gitide.ui.screens

import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goydevv.gitide.LexendFontFamily
import com.goydevv.gitide.engine.GitEngine
import com.goydevv.gitide.ui.components.FloatingVibrantShapesBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

data class LocalProject(val name: String, val path: String, val isGitHub: Boolean)
data class GitHubUser(val login: String, val avatarUrl: String, val name: String, val publicRepos: Int)
data class RemoteRepo(val name: String, val description: String, val cloneUrl: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectManagerScreen(
    tokenSetting: String,
    onTokenSave: (String) -> Unit,
    onProjectSelected: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var selectedTab by remember { mutableStateOf(0) }
    var token by remember { mutableStateOf(tokenSetting) }
    var githubUser by remember { mutableStateOf<GitHubUser?>(null) }
    var remoteRepos by remember { mutableStateOf<List<RemoteRepo>>(emptyList()) }
    var localProjects by remember { mutableStateOf<List<LocalProject>>(emptyList()) }
    
    var showImportDialog by remember { mutableStateOf(false) }
    var isCloning by remember { mutableStateOf(false) }
    var importUrl by remember { mutableStateOf("") }
    
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val baseDir = File(Environment.getExternalStorageDirectory(), "GoyDevvIDE/Projects")
    val githubDir = File(baseDir, "GitHub")

    fun loadLocalProjects() {
        val projects = mutableListOf<LocalProject>()
        if (baseDir.exists() && baseDir.isDirectory) {
            baseDir.listFiles()?.forEach { file ->
                if (file.isDirectory && file.name != "GitHub") {
                    projects.add(LocalProject(file.name, file.absolutePath, false))
                }
            }
        }
        if (githubDir.exists() && githubDir.isDirectory) {
            githubDir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    projects.add(LocalProject(file.name, file.absolutePath, true))
                }
            }
        }
        localProjects = projects
    }

    fun fetchGitHubData(targetToken: String) {
        if (targetToken.isBlank()) return
        scope.launch(Dispatchers.IO) {
            try {
                val userUrl = URL("https://api.github.com/user")
                val conn = userUrl.openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "token $targetToken")
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(text)
                    githubUser = GitHubUser(
                        login = json.getString("login"),
                        avatarUrl = json.getString("avatar_url"),
                        name = json.optString("name", json.getString("login")),
                        publicRepos = json.getInt("public_repos")
                    )

                    val reposUrl = URL("https://api.github.com/user/repos?per_page=100")
                    val reposConn = reposUrl.openConnection() as HttpURLConnection
                    reposConn.setRequestProperty("Authorization", "token $targetToken")
                    if (reposConn.responseCode == 200) {
                        val reposText = reposConn.inputStream.bufferedReader().use { it.readText() }
                        val reposArray = JSONArray(reposText)
                        val repoList = mutableListOf<RemoteRepo>()
                        for (i in 0 until reposArray.length()) {
                            val r = reposArray.getJSONObject(i)
                            repoList.add(
                                RemoteRepo(
                                    name = r.getString("name"),
                                    description = r.optString("description", "No description production metadata"),
                                    cloneUrl = r.getString("clone_url")
                                )
                            )
                        }
                        remoteRepos = repoList
                    }
                } else {
                    scope.launch { snackbarHostState.showSnackbar("GitHub Auth failed: HTTP ${conn.responseCode}") }
                }
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar("Network error: ${e.localizedMessage}") }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!baseDir.exists()) baseDir.mkdirs()
        if (!githubDir.exists()) githubDir.mkdirs()
        loadLocalProjects()
        if (token.isNotBlank()) {
            fetchGitHubData(token)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FloatingVibrantShapesBackground()

        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            text = "GoyDevv Workspace", 
                            fontFamily = LexendFontFamily, 
                            fontWeight = FontWeight.Bold, 
                            color = Color.White
                        ) 
                    },
                    actions = {
                        IconButton(onClick = { isSearching = !isSearching }) {
                            Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f))
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.8f)) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Local", fontFamily = LexendFontFamily, fontWeight = FontWeight.Medium) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Person, contentDescription = null) },
                        label = { Text("GitHub", fontFamily = LexendFontFamily, fontWeight = FontWeight.Medium) }
                    )
                }
            },
            floatingActionButton = {
                val fabInteractionSource = remember { MutableInteractionSource() }
                val isPressed by fabInteractionSource.collectIsPressedAsState()
                
                val fabRadius by animateDpAsState(targetValue = if (isPressed) 24.dp else 16.dp, label = "FABShape")
                val fabScale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, label = "FABScale")

                ExtendedFloatingActionButton(
                    onClick = { showImportDialog = true },
                    interactionSource = fabInteractionSource,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(fabRadius),
                    modifier = Modifier.scale(fabScale)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Repo", fontFamily = LexendFontFamily, fontWeight = FontWeight.SemiBold)
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(visible = isSearching) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search projects...", fontFamily = LexendFontFamily) },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                        )
                    )
                }

                if (selectedTab == 0) {
                    val filteredList = localProjects.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    if (filteredList.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No projects detected mapping target criteria", fontFamily = LexendFontFamily, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(filteredList) { project ->
                                val interactionSource = remember { MutableInteractionSource() }
                                val isCardPressed by interactionSource.collectIsPressedAsState()
                                val cardRadius by animateDpAsState(targetValue = if (isCardPressed) 24.dp else 16.dp, label = "CardShape")
                                val cardScale by animateFloatAsState(targetValue = if (isCardPressed) 0.98f else 1f, label = "CardScale")

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .scale(cardScale)
                                        .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { 
                                            onProjectSelected(project.path) 
                                        },
                                    shape = RoundedCornerShape(cardRadius),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(20.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = if (project.isGitHub) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Column {
                                            Text(project.name, fontFamily = LexendFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                                            Text(
                                                if (project.isGitHub) "Location: Projects/GitHub" else "Location: Projects/Local",
                                                fontFamily = LexendFontFamily,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (githubUser == null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text("Connect GitHub Subsystem", fontFamily = LexendFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                                OutlinedTextField(
                                    value = token,
                                    onValueChange = { token = it },
                                    label = { Text("Personal Access Token", fontFamily = LexendFontFamily) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                )
                                
                                val authInteraction = remember { MutableInteractionSource() }
                                val isAuthPressed by authInteraction.collectIsPressedAsState()
                                val authRadius by animateDpAsState(targetValue = if (isAuthPressed) 24.dp else 12.dp, label = "AuthRadius")

                                Button(
                                    onClick = {
                                        onTokenSave(token)
                                        fetchGitHubData(token)
                                    },
                                    interactionSource = authInteraction,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(authRadius),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                ) {
                                    Text("Authenticate Profile", fontFamily = LexendFontFamily, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f))
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f))
                                )
                                Column {
                                    Text(githubUser!!.name, fontFamily = LexendFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                                    Text("@${githubUser!!.login}", fontFamily = LexendFontFamily, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                    Text("Active Repos: ${githubUser!!.publicRepos}", fontFamily = LexendFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Remote Cloud Profiles", fontFamily = LexendFontFamily, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                        val filteredRepos = remoteRepos.filter { it.name.contains(searchQuery, ignoreCase = true) }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(filteredRepos) { repo ->
                                val repoInteraction = remember { MutableInteractionSource() }
                                val isRepoPressed by repoInteraction.collectIsPressedAsState()
                                val repoRadius by animateDpAsState(targetValue = if (isRepoPressed) 20.dp else 14.dp, label = "RepoRadius")

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(interactionSource = repoInteraction, indication = LocalIndication.current) {
                                            importUrl = repo.cloneUrl
                                            showImportDialog = true
                                        },
                                    shape = RoundedCornerShape(repoRadius),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                ) {
                                    Column(modifier = Modifier.padding(18.dp)) {
                                        Text(repo.name, fontFamily = LexendFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(repo.description, fontFamily = LexendFontFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 2)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCloning) showImportDialog = false },
            confirmButton = {
                Button(
                    enabled = !isCloning,
                    shape = RoundedCornerShape(10.dp),
                    onClick = {
                        if (importUrl.isNotBlank()) {
                            isCloning = true
                            scope.launch(Dispatchers.IO) {
                                val repoName = importUrl.substringAfterLast("/").substringBeforeLast(".git")
                                val destination = File(githubDir, repoName)
                                
                                val result = GitEngine.clone(importUrl, destination, token.ifBlank { null })
                                
                                withContext(Dispatchers.Main) {
                                    isCloning = false
                                    showImportDialog = false
                                    if (result.isSuccess) {
                                        loadLocalProjects()
                                        snackbarHostState.showSnackbar("Cloned successfully: $repoName")
                                    } else {
                                        snackbarHostState.showSnackbar("Clone failed: ${result.exceptionOrNull()?.localizedMessage}")
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("Clone Engine", fontFamily = LexendFontFamily)
                }
            },
            dismissButton = {
                TextButton(enabled = !isCloning, onClick = { showImportDialog = false }) {
                    Text("Cancel", fontFamily = LexendFontFamily)
                }
            },
            title = { Text("Clone Remote Cloud Matrix", fontFamily = LexendFontFamily, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = importUrl,
                        enabled = !isCloning,
                        onValueChange = { importUrl = it },
                        label = { Text("Git Target URI Location", fontFamily = LexendFontFamily) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (isCloning) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.5.dp)
                            Text("Running subsystem git clone...", fontFamily = LexendFontFamily, fontSize = 14.sp, color = Color.White)
                        }
                    }
                }
            }
        )
    }
}
