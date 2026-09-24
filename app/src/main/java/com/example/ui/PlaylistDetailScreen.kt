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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.domain.LocalSong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: MainViewModel,
    navController: NavController,
    playlistId: Int,
    playlistName: String
) {
    val songsFlow = remember(playlistId) { viewModel.getSongsForPlaylist(playlistId) }
    val playlistSongs by songsFlow.collectAsState()
    
    // We need to convert PlaylistSong to LocalSong for SongItem
    val allLocalSongs by viewModel.songs.collectAsState()
    
    val currentSongs = remember(playlistSongs, allLocalSongs) {
        playlistSongs.mapNotNull { pSong ->
            allLocalSongs.find { it.uri.toString() == pSong.songUri } ?: LocalSong(
                id = -1,
                title = pSong.title,
                artist = pSong.artist,
                album = "Unknown",
                duration = pSong.duration,
                uri = Uri.parse(pSong.songUri),
                albumArtUri = null
            )
        }
    }

    val currentSong by viewModel.playerManager.currentSong.collectAsState()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsState()
    val favRows by viewModel.favorites.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlistName) },
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
                Text("Playlist is empty", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = padding,
                modifier = Modifier.fillMaxSize()
            ) {
                items(currentSongs) { song ->
                    val isFav = favRows.any { it.songUri == song.uri.toString() }
                    SongItem(
                        song = song,
                        isFavorite = isFav,
                        onToggleFavorite = { viewModel.toggleFavorite(song) },
                        onClick = {
                            viewModel.playerManager.playSong(song, currentSongs)
                            viewModel.openFullScreenPlayer()
                        },
                        onRemoveFromPlaylist = {
                            viewModel.removeSongFromPlaylist(playlistId, song.uri.toString())
                        }
                    )
                }
            }
        }
    }
}
