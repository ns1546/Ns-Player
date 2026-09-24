package com.example.ui

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.domain.LocalSong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: MainViewModel,
    navController: NavController
) {
    val favoriteSongs by viewModel.favorites.collectAsState()
    val allLocalSongs by viewModel.songs.collectAsState()
    
    val currentSongs = remember(favoriteSongs, allLocalSongs) {
        val songMap = allLocalSongs.associateBy { it.uri.toString() }
        favoriteSongs.mapNotNull { fSong ->
            songMap[fSong.songUri]
        }
    }

    val currentSong by viewModel.playerManager.currentSong.collectAsState()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favorites") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (currentSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No favorites yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = padding,
                modifier = Modifier.fillMaxSize()
            ) {
                items(currentSongs) { song ->
                    SongItem(
                        song = song,
                        isFavorite = true,
                        onToggleFavorite = { viewModel.toggleFavorite(song) },
                        onClick = {
                            viewModel.playerManager.playSong(song, currentSongs)
                            viewModel.openFullScreenPlayer()
                        }
                    )
                }
            }
        }
    }
}
