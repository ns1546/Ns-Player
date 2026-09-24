package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMemoDialog(
    songTitle: String,
    songUri: String,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val existingMemo by viewModel.getMemoForSong(songUri).collectAsState(initial = null)
    var noteText by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf("") }

    LaunchedEffect(existingMemo) {
        existingMemo?.let {
            noteText = it.note
            selectedTag = it.keyTag
        }
    }

    val presetTags = listOf("Favorite Solo", "Gym PR", "Chords", "Lyrics", "Acoustic", "Road Trip", "Sleep Track")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Song Memo & Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(songTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Quick Tag:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presetTags) { tag ->
                        val isSelected = (selectedTag == tag)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedTag = if (isSelected) "" else tag
                            },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("Write personal timestamp, guitar chords, memories, or lyrics...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.saveSongMemo(songUri, noteText, selectedTag)
                    onDismiss()
                }
            ) {
                Text("Save Memo")
            }
        },
        dismissButton = {
            Row {
                if (existingMemo != null) {
                    TextButton(
                        onClick = {
                            viewModel.deleteSongMemo(songUri)
                            onDismiss()
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
