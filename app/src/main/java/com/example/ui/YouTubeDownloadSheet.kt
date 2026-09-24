package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.DownloadMediaType
import com.example.domain.DownloadOption
import com.example.domain.YouTubeDownloadHelper
import com.example.domain.YouTubeTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeDownloadSheet(
    track: YouTubeTrack,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedMediaType by remember { mutableStateOf(DownloadMediaType.AUDIO) }
    val audioOptions = remember(track) { YouTubeDownloadHelper.getAudioOptions(track) }
    val videoOptions = remember(track) { YouTubeDownloadHelper.getVideoOptions(track) }

    var selectedOptionId by remember(selectedMediaType) {
        mutableStateOf(
            if (selectedMediaType == DownloadMediaType.AUDIO) audioOptions.first().id else videoOptions[1].id
        )
    }

    val currentOption = remember(selectedMediaType, selectedOptionId) {
        if (selectedMediaType == DownloadMediaType.AUDIO) {
            audioOptions.find { it.id == selectedOptionId } ?: audioOptions.first()
        } else {
            videoOptions.find { it.id == selectedOptionId } ?: videoOptions.first()
        }
    }

    var isDownloadingLocally by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var downloadCompleted by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF12121A),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0xFF38384A))
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Title & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2B1116)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color(0xFFFF0000),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Download YouTube Track",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Select format & target quality",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9E9EB0)
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1E2C))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFFB0B0C0),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Track Preview Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1A1A26),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2E42))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black)
                    ) {
                        AsyncImage(
                            model = track.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB0B0C0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⏱ ${(track.durationSeconds / 60)}:${(track.durationSeconds % 60).toString().padStart(2, '0')}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8E8EA0)
                            )
                            if (track.viewCountText.isNotBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "• ${track.viewCountText}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF8E8EA0)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Active Download Progress indicator if running
            if (isDownloadingLocally) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF241420),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF0000).copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.5.dp,
                                    color = Color(0xFFFF0000)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Downloading to Device...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "$downloadProgress%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFF5252)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFFFF0000),
                            trackColor = Color(0xFF381C26)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Saving to /Downloads/NSPlayer • Notification active in status bar",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA0A0B0)
                        )
                    }
                }
            }

            // Media Type Tabs: Audio vs Video
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF181824))
                    .padding(4.dp)
            ) {
                // Audio Tab
                val isAudio = selectedMediaType == DownloadMediaType.AUDIO
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isAudio) Color(0xFFFF0000) else Color.Transparent)
                        .clickable {
                            selectedMediaType = DownloadMediaType.AUDIO
                            selectedOptionId = audioOptions.first().id
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = if (isAudio) Color.White else Color(0xFF9E9EB0),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Audio (MP3 / M4A / FLAC)",
                            fontWeight = if (isAudio) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (isAudio) Color.White else Color(0xFF9E9EB0)
                        )
                    }
                }

                // Video Tab
                val isVideo = selectedMediaType == DownloadMediaType.VIDEO
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isVideo) Color(0xFFFF0000) else Color.Transparent)
                        .clickable {
                            selectedMediaType = DownloadMediaType.VIDEO
                            selectedOptionId = videoOptions[1].id
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = if (isVideo) Color.White else Color(0xFF9E9EB0),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Video (1080p / 720p)",
                            fontWeight = if (isVideo) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (isVideo) Color.White else Color(0xFF9E9EB0)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Quality Selection List
            Text(
                text = if (selectedMediaType == DownloadMediaType.AUDIO) "Choose Audio Bitrate & Codec:" else "Choose Video Resolution:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFCBCBDC)
            )

            Spacer(modifier = Modifier.height(10.dp))

            val currentOptionsList = if (selectedMediaType == DownloadMediaType.AUDIO) audioOptions else videoOptions

            currentOptionsList.forEach { option ->
                val isSelected = option.id == selectedOptionId
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { selectedOptionId = option.id },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) Color(0xFF261922) else Color(0xFF161622),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) Color(0xFFFF0000) else Color(0xFF262638)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedOptionId = option.id },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFFFF0000),
                                unselectedColor = Color(0xFF55556E)
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = option.qualityLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color(0xFFE0E0EC)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color(0xFFFF0000).copy(alpha = 0.2f) else Color(0xFF222234))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = option.tag,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color(0xFFFF5252) else Color(0xFFAAAAAF)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Format: ${option.formatName} • Est. Size: ${option.estimatedSize}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8E8EA0)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Download Button (Direct Device Storage + Notification)
            Button(
                onClick = {
                    if (!isDownloadingLocally) {
                        isDownloadingLocally = true
                        downloadProgress = 0
                        YouTubeDownloadHelper.startFastDownload(
                            context = context,
                            track = track,
                            option = currentOption,
                            onProgress = { prog ->
                                downloadProgress = prog
                            },
                            onComplete = { _ ->
                                isDownloadingLocally = false
                                downloadCompleted = true
                                viewModel.downloadYouTubeTrack(track)
                            },
                            onError = { _ ->
                                isDownloadingLocally = false
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !isDownloadingLocally,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF0000),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF552222),
                    disabledContentColor = Color(0xFFAAAAAA)
                )
            ) {
                if (isDownloadingLocally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Downloading ($downloadProgress%)...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                } else if (downloadCompleted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Downloaded! Tap to Close",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Download to Device (${currentOption.qualityLabel.substringBefore(" ")})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Option: Open direct download engine in Google Chrome
            OutlinedButton(
                onClick = {
                    YouTubeDownloadHelper.openInChrome(context, track, currentOption)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383850))
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Download in Google Chrome (Web Downloader)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Instant Offline Library Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.downloadYouTubeTrack(track)
                        Toast.makeText(context, "Saved \"${track.title}\" to Offline Music Library!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint = Color(0xFF9E9EB0),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Save directly to NSPlayer Offline Library",
                    fontSize = 12.sp,
                    color = Color(0xFF9E9EB0)
                )
            }
        }
    }
}
