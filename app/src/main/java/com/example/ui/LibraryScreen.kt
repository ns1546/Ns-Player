package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: MainViewModel) {
    val songs by viewModel.filteredSongs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val lastSyncedTime by viewModel.lastSyncedTime.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    var songToAdd by remember { mutableStateOf<com.example.domain.LocalSong?>(null) }
    var songToRename by remember { mutableStateOf<com.example.domain.LocalSong?>(null) }
    var songToSetRingtone by remember { mutableStateOf<com.example.domain.LocalSong?>(null) }
    var songDetailsToShow by remember { mutableStateOf<com.example.domain.LocalSong?>(null) }
    var expandedFolder by remember { mutableStateOf<String?>(null) }
    val playlists by viewModel.playlists.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val intentSenderLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.triggerSync()
            android.widget.Toast.makeText(context, "Song deleted", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    LaunchedEffect(Unit) {
        if (lastSyncedTime == null) {
            viewModel.triggerSync()
        }
    }
    
    if (showThemeDialog) {
        ThemeDialog(viewModel = viewModel, onDismiss = { showThemeDialog = false })
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
            TopAppBar(
                title = {
                    Column {
                        Text("Library", style = MaterialTheme.typography.titleLarge)
                        if (lastSyncedTime != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Sync, contentDescription = "Syncing", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(lastSyncedTime!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showThemeDialog = true }) {
                        Icon(Icons.Default.Palette, contentDescription = "Theme Color")
                    }
                    IconButton(onClick = { viewModel.triggerSync() }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Now")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchSongs(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search songs...") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            
            var selectedTabIndex by remember { mutableIntStateOf(0) }
            val tabs = listOf("Songs", "Folders", "Artists", "Albums", "Favs", "Recent")
            
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { 
                            selectedTabIndex = index
                            when(index) {
                                0 -> viewModel.setSearchCategory(SearchCategory.ALL)
                                1 -> viewModel.setSearchCategory(SearchCategory.ALL)
                                2 -> viewModel.setSearchCategory(SearchCategory.ARTIST)
                                3 -> viewModel.setSearchCategory(SearchCategory.ALBUM)
                                4 -> viewModel.setSearchCategory(SearchCategory.ALL)
                                5 -> viewModel.setSearchCategory(SearchCategory.ALL)
                            }
                        },
                        text = { Text(title) }
                    )
                }
            }
            
            val favRows by viewModel.favorites.collectAsState()
            val recentRows by viewModel.recentSongs.collectAsState()
            
            val favUriSet = remember(favRows) { favRows.map { it.songUri }.toSet() }
            val displayedSongs = remember(songs, selectedTabIndex, favUriSet, recentRows, expandedFolder) {
                when (selectedTabIndex) {
                    1 -> {
                        if (expandedFolder != null) {
                            songs.filter { it.folderName == expandedFolder }
                        } else songs
                    }
                    4 -> songs.filter { favUriSet.contains(it.uri.toString()) }
                    5 -> {
                        val songMap = songs.associateBy { it.uri.toString() }
                        recentRows.mapNotNull { songMap[it.songUri] }
                    }
                    else -> songs
                }
            }

            if (selectedTabIndex == 1 && expandedFolder == null) {
                // Folder List View
                val folders = remember(songs) {
                    songs.groupBy { it.folderName }.toList().sortedByDescending { it.second.size }
                }

                if (folders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("No folders found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(folders, key = { it.first }) { (folderName, folderSongs) ->
                            Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedFolder = folderName }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folderName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                        Text(
                                            text = "${folderSongs.size} tracks",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (folderSongs.isNotEmpty()) {
                                                viewModel.playerManager.playSong(folderSongs.first(), folderSongs)
                                                viewModel.openFullScreenPlayer()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.PlayCircle,
                                            contentDescription = "Play Folder",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (displayedSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No songs found in this section", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = { viewModel.triggerSync() }) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan Media")
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (selectedTabIndex == 1 && expandedFolder != null) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(onClick = { expandedFolder = null }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Back to Folders")
                                }
                                Text(
                                    text = "$expandedFolder (${displayedSongs.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                        }
                    }

                    items(displayedSongs, key = { it.uri.toString() }) { song ->
                        val isFav = favUriSet.contains(song.uri.toString())
                        SongItem(
                            song = song,
                            isFavorite = isFav,
                            onToggleFavorite = { viewModel.toggleFavorite(song) },
                            onPlayNext = {
                                viewModel.playNext(song)
                                android.widget.Toast.makeText(context, "Playing next: ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onAddToQueue = {
                                viewModel.addToQueue(song)
                                android.widget.Toast.makeText(context, "Added to queue: ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onViewDetails = {
                                songDetailsToShow = song
                            },
                            onClick = { 
                                viewModel.playerManager.playSong(song, displayedSongs)
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
                                    viewModel.triggerSync() // Refresh library
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
        
        if (songDetailsToShow != null) {
            SongInfoDialog(song = songDetailsToShow!!, onDismiss = { songDetailsToShow = null })
        }
    }
}
