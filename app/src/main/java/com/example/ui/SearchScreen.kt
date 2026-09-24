package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: MainViewModel) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.filteredSongs.collectAsState()
    val selectedCategory by viewModel.searchCategory.collectAsState()
    
    val playlists by viewModel.playlists.collectAsState()
    val favRows by viewModel.favorites.collectAsState()
    var songToAdd by remember { mutableStateOf<com.example.domain.LocalSong?>(null) }
    var songToRename by remember { mutableStateOf<com.example.domain.LocalSong?>(null) }
    var songToSetRingtone by remember { mutableStateOf<com.example.domain.LocalSong?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val intentSenderLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.triggerSync()
            android.widget.Toast.makeText(context, "Song deleted", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    if (songToAdd != null) {
        AlertDialog(
            onDismissRequest = { songToAdd = null },
            title = { Text("Add to Playlist") },
            text = {
                if (playlists.isEmpty()) {
                    Text("No playlists available.")
                } else {
                    LazyColumn {
                        items(playlists) { playlist ->
                            TextButton(
                                onClick = {
                                    viewModel.addSongToPlaylist(playlist, songToAdd!!)
                                    android.widget.Toast.makeText(context, "Added to ${playlist.name}", android.widget.Toast.LENGTH_SHORT).show()
                                    songToAdd = null
                                }
                            ) {
                                Text(playlist.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { songToAdd = null }) { Text("Close") }
            }
        )
    }

    if (songToRename != null) {
        var newTitle by remember { mutableStateOf(songToRename!!.title) }
        AlertDialog(
            onDismissRequest = { songToRename = null },
            title = { Text("Rename Song") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    label = { Text("New Title") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val values = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Audio.Media.TITLE, newTitle)
                            put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, newTitle)
                        }
                        context.contentResolver.update(songToRename!!.uri, values, null, null)
                        viewModel.triggerSync()
                        android.widget.Toast.makeText(context, "Successfully renamed!", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Cannot rename file, permission required", android.widget.Toast.LENGTH_LONG).show()
                    }
                    songToRename = null
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { songToRename = null }) { Text("Cancel") }
            }
        )
    }

    if (songToSetRingtone != null) {
        AlertDialog(
            onDismissRequest = { songToSetRingtone = null },
            title = { Text("Set as Ringtone/Alarm") },
            text = {
                Column {
                    TextButton(onClick = { 
                        MediaHelper.setRingtone(context, songToSetRingtone!!, android.media.RingtoneManager.TYPE_RINGTONE)
                        songToSetRingtone = null
                    }) { Text("Phone Ringtone") }
                    TextButton(onClick = { 
                        MediaHelper.setRingtone(context, songToSetRingtone!!, android.media.RingtoneManager.TYPE_ALARM)
                        songToSetRingtone = null
                    }) { Text("Alarm") }
                    TextButton(onClick = { 
                        MediaHelper.setRingtone(context, songToSetRingtone!!, android.media.RingtoneManager.TYPE_NOTIFICATION)
                        songToSetRingtone = null
                    }) { Text("Notification") }
                }
            },
            confirmButton = {
                TextButton(onClick = { songToSetRingtone = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { 
                    OutlinedTextField(
                        value = query,
                        onValueChange = { viewModel.searchSongs(it) },
                        placeholder = { Text("Search songs, artists...") },
                        modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                })
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SearchCategory.entries) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { viewModel.setSearchCategory(category) },
                            label = { Text(category.name) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            items(results) { song ->
                val isFav = favRows.any { it.songUri == song.uri.toString() }
                SongItem(
                    song = song, 
                    isFavorite = isFav,
                    onToggleFavorite = { viewModel.toggleFavorite(song) },
                    onClick = { 
                        viewModel.playerManager.playSong(song, results)
                        viewModel.openFullScreenPlayer()
                    }, onShare = {
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "audio/*"
                        putExtra(android.content.Intent.EXTRA_STREAM, song.uri)
                        putExtra(android.content.Intent.EXTRA_TEXT, "Check out this song: ${song.title}")
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Song"))
                }, onAddToPlaylist = {
                    songToAdd = song
                }, onRename = {
                    songToRename = song
                }, onSetRingtone = {
                    songToSetRingtone = song
                }, onDelete = {
                    try {
                        context.contentResolver.delete(song.uri, null, null)
                        viewModel.triggerSync()
                        android.widget.Toast.makeText(context, "Song deleted", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: SecurityException) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            try {
                                val pendingIntent = android.provider.MediaStore.createDeleteRequest(context.contentResolver, listOf(song.uri))
                                intentSenderLauncher.launch(
                                    androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                )
                            } catch (innerE: Exception) {
                                android.widget.Toast.makeText(context, "Could not delete song.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            val recoverableSecurityException = e as? android.app.RecoverableSecurityException
                            if (recoverableSecurityException != null) {
                                intentSenderLauncher.launch(
                                    androidx.activity.result.IntentSenderRequest.Builder(recoverableSecurityException.userAction.actionIntent.intentSender).build()
                                )
                            } else {
                                android.widget.Toast.makeText(context, "Could not delete song. Permission required.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        } else {
                            android.widget.Toast.makeText(context, "Could not delete song. Permission required.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                })
            }
        }
    }
}
