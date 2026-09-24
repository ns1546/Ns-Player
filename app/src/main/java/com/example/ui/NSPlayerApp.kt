package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.ViewModelProvider
import com.example.domain.LocalSong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NSPlayerApp(factory: ViewModelProvider.Factory) {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel(factory = factory)
    val showFullScreenPlayer by viewModel.showFullScreenPlayer.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadSongs()
    }
    
    BackHandler(enabled = showFullScreenPlayer) {
        viewModel.closeFullScreenPlayer()
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    MiniPlayer(
                        viewModel = viewModel,
                        onExpand = {
                            if (viewModel.isYouTubePlaying.value || (viewModel.currentYouTubeTrack.value != null && viewModel.isYouTubePlayerVisible.value)) {
                                navController.navigate("youtube") {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } else {
                                viewModel.openFullScreenPlayer()
                            }
                        }
                    )
                    
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.border(1.dp, androidx.compose.ui.graphics.Color(0x0DFFFFFF))
                    ) {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route
                        
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                            label = { Text("Library") },
                            selected = currentRoute == "library",
                            onClick = { 
                                navController.navigate("library") { 
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Playlists") },
                            label = { Text("Playlists") },
                            selected = currentRoute == "playlists",
                            onClick = { 
                                navController.navigate("playlists") { 
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            label = { Text("Search") },
                            selected = currentRoute == "search",
                            onClick = { 
                                navController.navigate("search") { 
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.PlayCircle, contentDescription = "YouTube Audio") },
                            label = { Text("YouTube") },
                            selected = currentRoute == "youtube",
                            onClick = { 
                                navController.navigate("youtube") { 
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") },
                            selected = currentRoute == "settings",
                            onClick = { 
                                navController.navigate("settings") { 
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(navController = navController, startDestination = "library", modifier = Modifier.padding(innerPadding)) {
                composable("library") { LibraryScreen(viewModel) }
                composable("playlists") { PlaylistsScreen(viewModel, navController) }
                composable("search") { SearchScreen(viewModel) }
                composable("youtube") { YouTubeStreamScreen(viewModel) }
                composable("settings") { SettingsScreen(viewModel) }
                composable("playlist/{playlistId}/{playlistName}") { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getString("playlistId")?.toIntOrNull() ?: 0
                    val rawName = backStackEntry.arguments?.getString("playlistName") ?: "Playlist"
                    val playlistName = android.net.Uri.decode(rawName)
                    PlaylistDetailScreen(viewModel, navController, playlistId, playlistName)
                }
                composable("favorites") { FavoritesScreen(viewModel, navController) }
            }
        }
        
        AnimatedVisibility(
            visible = showFullScreenPlayer,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                FullPlayerContent(viewModel = viewModel, onClose = { viewModel.closeFullScreenPlayer() })
            }
        }

        val isDriveMode by viewModel.isDriveMode.collectAsState()
        AnimatedVisibility(
            visible = isDriveMode,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            DriveModeScreen(viewModel = viewModel, onExit = { viewModel.setDriveMode(false) })
        }

        // Persistent background host: when user is on other tabs (Library, Playlists, etc.), keeps YouTube audio playing without interruption
        val currentYTTrack by viewModel.currentYouTubeTrack.collectAsState()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        if (currentYTTrack != null && currentRoute != "youtube") {
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0.001f)
            ) {
                AndroidView(
                    factory = {
                        viewModel.youTubePlayerController.detachFromParent()
                        viewModel.youTubePlayerController.getWebView()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
