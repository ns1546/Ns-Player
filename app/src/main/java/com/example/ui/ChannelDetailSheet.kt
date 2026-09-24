package com.example.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.YouTubeArtistChannel
import com.example.domain.YouTubeTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelDetailSheet(
    channel: YouTubeArtistChannel,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentYTTrack by viewModel.currentYouTubeTrack.collectAsState()
    val isYTPlaying by viewModel.isYouTubePlaying.collectAsState()
    val downloadedTracks by viewModel.downloadedTracks.collectAsState()
    val downloadingTrackIds by viewModel.downloadingTrackIds.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val subscribedChannels by viewModel.subscribedChannelIds.collectAsState()

    val isSubscribed = subscribedChannels.contains(channel.id) || subscribedChannels.contains(channel.name)
    var selectedTab by remember { mutableIntStateOf(0) }
    var trackForDownloadOptions by remember { mutableStateOf<com.example.domain.YouTubeTrack?>(null) }

    if (trackForDownloadOptions != null) {
        YouTubeDownloadSheet(
            track = trackForDownloadOptions!!,
            viewModel = viewModel,
            onDismiss = { trackForDownloadOptions = null }
        )
    }
    var isBioExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF101018),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color(0xFF444455)
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .background(Color(0xFF101018))
        ) {
            // 1. HERO CHANNEL BANNER & HEADER
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    // Banner Image
                    AsyncImage(
                        model = if (channel.bannerUrl.isNotBlank()) channel.bannerUrl else channel.avatarUrl,
                        contentDescription = "Channel Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Gradient Scrim for contrast
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.3f),
                                        Color(0xFF101018)
                                    )
                                )
                            )
                    )

                    // Close Button Top End
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.7f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Channel",
                                tint = Color.White,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                    }

                    // Channel Avatar + Name Row over Banner
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Channel Avatar with YouTube Red Ring
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .border(2.5.dp, Color(0xFFFF0000), CircleShape)
                                .background(Color(0xFF161622))
                        ) {
                            AsyncImage(
                                model = channel.avatarUrl,
                                contentDescription = channel.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = channel.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                // Verified Official Badge
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Official Artist Channel",
                                    tint = Color(0xFFFF0000),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (channel.handle.isNotBlank()) {
                                Text(
                                    text = channel.handle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFB0B0C0)
                                )
                            }

                            Text(
                                text = if (channel.subscribers.isNotBlank()) channel.subscribers else "Official Music Channel",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFF5252),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 2. ACTION CONTROLS (Subscribe, Play All, Shuffle, Share)
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play All Button
                        Button(
                            onClick = {
                                viewModel.playChannelTracks(channel.topTracks, startIndex = 0, shuffle = false)
                                Toast.makeText(context, "Playing all songs by ${channel.name}", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF0000)
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play All", fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        // Shuffle Button
                        OutlinedButton(
                            onClick = {
                                viewModel.playChannelTracks(channel.topTracks, startIndex = 0, shuffle = true)
                                Toast.makeText(context, "Shuffling songs by ${channel.name}", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38384E)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Shuffle", fontWeight = FontWeight.SemiBold, color = Color.White)
                        }

                        // Subscribe Button
                        FilledTonalButton(
                            onClick = {
                                viewModel.toggleChannelSubscription(channel.id)
                                val msg = if (!isSubscribed) "Subscribed to ${channel.name}" else "Unsubscribed from ${channel.name}"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isSubscribed) Color(0xFF222232) else Color(0xFFFF0000).copy(alpha = 0.2f),
                                contentColor = if (isSubscribed) Color.White else Color(0xFFFF5252)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(
                                imageVector = if (isSubscribed) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isSubscribed) "Subscribed" else "Subscribe", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Artist Bio section
                    if (channel.bio.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF161622),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28283C)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isBioExpanded = !isBioExpanded }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "About the Artist",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF5252)
                                    )
                                    Icon(
                                        imageVector = if (isBioExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = channel.bio,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFB0B0C0),
                                    maxLines = if (isBioExpanded) 10 else 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 3. CHANNEL TABS (Top Songs, Albums, Videos)
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF101018),
                    contentColor = Color(0xFFFF0000),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (selectedTab == 0) Color(0xFFFF0000) else Color(0xFF9E9EA8))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Popular Songs (${channel.topTracks.size})", color = if (selectedTab == 0) Color.White else Color(0xFF9E9EA8), fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Album, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (selectedTab == 1) Color(0xFFFF0000) else Color(0xFF9E9EA8))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Albums & EPs", color = if (selectedTab == 1) Color.White else Color(0xFF9E9EA8), fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 4. TAB CONTENTS
            if (selectedTab == 0) {
                // POPULAR SONGS LIST
                if (channel.topTracks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No tracks found for this channel.",
                                color = Color(0xFF9E9EA8)
                            )
                        }
                    }
                } else {
                    itemsIndexed(channel.topTracks) { index, track ->
                        val isPlaying = currentYTTrack?.videoId == track.videoId && isYTPlaying
                        val isDl = downloadedTracks.any { it.videoId == track.videoId }
                        val isDling = downloadingTrackIds.contains(track.videoId)
                        val isFav = favorites.any { it.songUri == "yt://${track.videoId}" }

                        ChannelSongItem(
                            index = index + 1,
                            track = track,
                            isPlaying = isPlaying,
                            isDownloaded = isDl,
                            isDownloading = isDling,
                            isFavorite = isFav,
                            onPlay = {
                                viewModel.playYouTubeTrack(track, channel.topTracks)
                            },
                            onDownload = {
                                trackForDownloadOptions = track
                            },
                            onToggleFavorite = { viewModel.toggleYouTubeFavorite(track) },
                            onShare = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Listen to ${track.title} by ${track.author}: https://www.youtube.com/watch?v=${track.videoId}")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Song"))
                            }
                        )
                    }
                }
            } else {
                // ALBUMS & DISCOGRAPHY
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Albums & Discography",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (channel.albums.isEmpty()) {
                            Text(
                                text = "Discography releases available soon.",
                                color = Color(0xFF9E9EA8),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            channel.albums.forEach { albumName ->
                                Surface(
                                    onClick = {
                                        viewModel.searchYouTube("${channel.name} $albumName")
                                        Toast.makeText(context, "Searching for songs in $albumName", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF161622),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28283C)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF28283C)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Album,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = albumName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = channel.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF9E9EA8)
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Open Album",
                                            tint = Color(0xFFFF0000),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom space for scroll comfort
            item {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun ChannelSongItem(
    index: Int,
    track: YouTubeTrack,
    isPlaying: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit
) {
    Surface(
        onClick = onPlay,
        shape = RoundedCornerShape(12.dp),
        color = if (isPlaying) Color(0xFF25141B) else Color(0xFF161622),
        border = if (isPlaying) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF0000)) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF242436)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track Index Number or Playing Equalizer
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Playing",
                        tint = Color(0xFFFF0000),
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = "$index",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9E9EA8)
                    )
                }
            }

            // Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Title & View count
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isPlaying) Color(0xFFFF5252) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (track.viewCountText.isNotBlank()) {
                        Text(
                            text = track.viewCountText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF9E9EA8)
                        )
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF9E9EA8)
                        )
                    }
                    Text(
                        text = if (track.durationSeconds > 0) String.format("%d:%02d", track.durationSeconds / 60, track.durationSeconds % 60) else "Audio",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF9E9EA8)
                    )
                }
            }

            // Download Icon Button
            IconButton(
                onClick = onDownload,
                modifier = Modifier.size(32.dp)
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFFF0000)
                    )
                } else if (isDownloaded) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Favorite Icon Button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Color(0xFFFF1744) else Color(0xFFA0A0B0),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Play Icon Button
            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(34.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) Color(0xFFFF0000) else Color(0xFF28283C)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
