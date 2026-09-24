package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.LocalSong
import com.example.domain.LyricLine
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(
    viewModel: MainViewModel,
    song: LocalSong,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var syncedLyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var plainLyrics by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isEditing by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    var searchError by remember { mutableStateOf<String?>(null) }

    // Load lyrics from database or fetch online
    LaunchedEffect(song.uri) {
        isLoading = true
        searchError = null
        try {
            val res = viewModel.lyricsManager.getOrFetchLyrics(song)
            syncedLyrics = res.first
            plainLyrics = res.second
        } catch (e: Exception) {
            searchError = "Could not fetch lyrics."
        } finally {
            isLoading = false
        }
    }

    // Determine currently active lyric index
    val activeIndex = remember(syncedLyrics, currentPositionMs) {
        if (syncedLyrics.isEmpty()) -1
        else {
            val idx = syncedLyrics.indexOfLast { it.timestampMs <= currentPositionMs }
            if (idx >= 0) idx else 0
        }
    }

    // Auto-scroll in sync with current playing line
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && activeIndex < syncedLyrics.size && !isEditing) {
            val targetScroll = (activeIndex - 3).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = if (syncedLyrics.isNotEmpty()) "Synchronized Lyrics" else "Lyrics",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Manual edit / paste lyrics
                    IconButton(onClick = {
                        customText = syncedLyrics.joinToString("\n") { it.text }.ifBlank { plainLyrics ?: "" }
                        isEditing = !isEditing
                    }) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Close else Icons.Default.EditNote,
                            contentDescription = "Edit Lyrics"
                        )
                    }

                    // Online search / refresh
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                searchError = null
                                val success = viewModel.lyricsManager.searchAndSaveOnline(song)
                                if (success) {
                                    val res = viewModel.lyricsManager.getOrFetchLyrics(song)
                                    syncedLyrics = res.first
                                    plainLyrics = res.second
                                } else {
                                    searchError = "No lyrics found online."
                                }
                                isLoading = false
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Search Online")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Finding lyrics for ${song.title}...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (isEditing) {
                // Edit / Paste custom lyrics view
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Paste or edit lyrics below. Standard lines or [mm:ss.xx] timestamped lines are supported.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        placeholder = { Text("Enter lyrics here...") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { isEditing = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            coroutineScope.launch {
                                viewModel.lyricsManager.saveCustomLyrics(song.uri.toString(), customText)
                                val res = viewModel.lyricsManager.getOrFetchLyrics(song)
                                syncedLyrics = res.first
                                plainLyrics = res.second
                                isEditing = false
                            }
                        }) {
                            Text("Save Lyrics")
                        }
                    }
                }
            } else if (syncedLyrics.isNotEmpty()) {
                // Synchronized Karaoke-style scrolling view
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(syncedLyrics) { index, line ->
                        val isActive = index == activeIndex
                        val textColor by animateColorAsState(
                            targetValue = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            label = "lyricColor"
                        )

                        Text(
                            text = line.text,
                            style = if (isActive) MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                            else MaterialTheme.typography.titleMedium,
                            color = textColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSeekTo(line.timestampMs)
                                }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            } else if (!plainLyrics.isNullOrBlank()) {
                // Plain text view
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    item {
                        Text(
                            text = plainLyrics!!,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            lineHeight = 28.sp
                        )
                    }
                }
            } else {
                // Empty state with quick action
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Lyrics,
                            contentDescription = null,
                            modifier = Modifier.size(54.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = searchError ?: "No lyrics found for this song",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "You can paste or enter lyrics manually, or retry online search.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { isEditing = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Lyrics")
                            }
                            Button(onClick = {
                                coroutineScope.launch {
                                    isLoading = true
                                    searchError = null
                                    val success = viewModel.lyricsManager.searchAndSaveOnline(song)
                                    if (success) {
                                        val res = viewModel.lyricsManager.getOrFetchLyrics(song)
                                        syncedLyrics = res.first
                                        plainLyrics = res.second
                                    } else {
                                        searchError = "Could not find online lyrics for \"${song.title}\"."
                                    }
                                    isLoading = false
                                }
                            }) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Search Online")
                            }
                        }
                    }
                }
            }
        }
    }
}
