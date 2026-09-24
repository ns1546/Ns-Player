package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.LocalSong
import java.io.File

@Composable
fun SongInfoDialog(
    song: LocalSong,
    onDismiss: () -> Unit
) {
    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }

    val fileExtension = rememberExtension(song.filePath, song.title)
    val fileSizeStr = rememberFileSize(song.filePath)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.AudioFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Track Details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoRow("Title", song.title)
                InfoRow("Artist", song.artist)
                InfoRow("Album", song.album)
                InfoRow("Duration", formatDuration(song.duration))
                InfoRow("Folder", song.folderName)
                InfoRow("Format", fileExtension.uppercase())
                if (fileSizeStr.isNotBlank()) {
                    InfoRow("File Size", fileSizeStr)
                }
                if (song.filePath.isNotBlank()) {
                    InfoRow("File Path", song.filePath)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun rememberExtension(path: String, title: String): String {
    if (path.contains(".")) {
        return path.substringAfterLast(".", "MP3")
    }
    if (title.contains(".")) {
        return title.substringAfterLast(".", "MP3")
    }
    return "MP3"
}

private fun rememberFileSize(path: String): String {
    if (path.isBlank()) return ""
    return try {
        val file = File(path)
        if (file.exists()) {
            val bytes = file.length()
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            String.format("%.2f MB", mb)
        } else ""
    } catch (_: Exception) { "" }
}
