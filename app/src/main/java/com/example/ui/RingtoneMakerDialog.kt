package com.example.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.LocalSong

@Composable
fun RingtoneMakerDialog(
    song: LocalSong,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val totalDurationMs = song.duration.coerceAtLeast(30_000L)

    var startSec by remember { mutableFloatStateOf(0f) }
    var snippetDurationSec by remember { mutableFloatStateOf(30f) }
    var isPreviewing by remember { mutableStateOf(false) }

    val startMs = (startSec * 1000).toLong()
    val endMs = ((startSec + snippetDurationSec) * 1000).toLong().coerceAtMost(totalDurationMs)

    fun formatTime(sec: Float): String {
        val totalS = sec.toInt()
        val m = totalS / 60
        val s = totalS % 60
        return String.format("%02d:%02d", m, s)
    }

    AlertDialog(
        onDismissRequest = {
            if (isPreviewing) {
                viewModel.playerManager.clearABLoop()
            }
            onDismiss()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Craft Ringtone / Alarm",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Snippet Range
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Start: ${formatTime(startSec)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "End: ${formatTime(endMs / 1000f)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Slider(
                    value = startSec,
                    onValueChange = {
                        startSec = it
                        if (isPreviewing) {
                            viewModel.playerManager.setPointA(startMs)
                            viewModel.playerManager.setPointB(endMs)
                            viewModel.playerManager.seekTo(startMs)
                        }
                    },
                    valueRange = 0f..(totalDurationMs / 1000f - snippetDurationSec).coerceAtLeast(1f),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Preset Duration",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15f, 30f, 45f).forEach { dur ->
                        FilterChip(
                            selected = snippetDurationSec == dur,
                            onClick = {
                                snippetDurationSec = dur
                                if (isPreviewing) {
                                    viewModel.playerManager.setPointB(endMs)
                                }
                            },
                            label = { Text("${dur.toInt()}s") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Preview Button
                FilledTonalButton(
                    onClick = {
                        if (!isPreviewing) {
                            viewModel.playSong(song)
                            viewModel.playerManager.setPointA(startMs)
                            viewModel.playerManager.setPointB(endMs)
                            viewModel.playerManager.seekTo(startMs)
                            isPreviewing = true
                        } else {
                            viewModel.playerManager.clearABLoop()
                            viewModel.playerManager.pause()
                            isPreviewing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isPreviewing) "Stop Snippet Preview" else "Listen to Loop Snippet")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isPreviewing) {
                        viewModel.playerManager.clearABLoop()
                    }
                    try {
                        // Open system ringtone picker or share audio
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "audio/*"
                            putExtra(Intent.EXTRA_STREAM, song.uri)
                            putExtra(Intent.EXTRA_SUBJECT, "Ringtone: ${song.title}")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Set / Share as Ringtone"))
                        Toast.makeText(context, "Ready to set as Ringtone / Alarm!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not share audio: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }
            ) {
                Text("Set / Export Ringtone")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (isPreviewing) {
                        viewModel.playerManager.clearABLoop()
                    }
                    onDismiss()
                }
            ) {
                Text("Cancel")
            }
        }
    )
}
