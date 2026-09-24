package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.data.DownloadedTrack
import com.example.domain.LocalSong
import com.example.domain.YouTubeTrack

fun DownloadedTrack.toYouTubeTrack(): YouTubeTrack = YouTubeTrack(
    videoId = videoId,
    title = title,
    author = author,
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeStreamScreen(viewModel: MainViewModel) {
    val query by viewModel.youTubeQuery.collectAsState()
    val searchTracks by viewModel.youTubeTracks.collectAsState()
    val isSearching by viewModel.isYouTubeSearching.collectAsState()
    val errorMessage by viewModel.youTubeError.collectAsState()
    val sleepTimerMs by viewModel.sleepTimerRemainingMs.collectAsState()

    val currentYTTrack by viewModel.currentYouTubeTrack.collectAsState()
    val isYTPlaying by viewModel.isYouTubePlaying.collectAsState()
    val isYTPlayerVisible by viewModel.isYouTubePlayerVisible.collectAsState()
    val ytCurrentTime by viewModel.youTubeCurrentTime.collectAsState()
    val ytDuration by viewModel.youTubeDuration.collectAsState()
    val isFullScreen by viewModel.isFullScreenVideo.collectAsState()
    val youTubeCommand by viewModel.youTubeCommand.collectAsState()

    val youTubeQueue by viewModel.youTubeQueue.collectAsState()
    val youTubeSpeed by viewModel.youTubePlaybackSpeed.collectAsState()
    val youTubeRepeat by viewModel.youTubeRepeatMode.collectAsState()
    val youTubeShuffle by viewModel.youTubeShuffleEnabled.collectAsState()
    val isAudioOnly by viewModel.isAudioOnlyMode.collectAsState()
    val playbackError by viewModel.youTubePlaybackError.collectAsState()
    val lastErrorCode by viewModel.youTubePlayerController.lastErrorCode.collectAsState()
    val selectedChannel by viewModel.selectedChannel.collectAsState()

    val downloadedTracks by viewModel.downloadedTracks.collectAsState()
    val downloadingTrackIds by viewModel.downloadingTrackIds.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val resumeNotice by viewModel.resumeNotice.collectAsState()

    val playlists by viewModel.playlists.collectAsState()
    var trackToAddToPlaylist by remember { mutableStateOf<YouTubeTrack?>(null) }
    var trackForDownloadOptions by remember { mutableStateOf<YouTubeTrack?>(null) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showCloseConfirmDialog by remember { mutableStateOf(false) }
    var isVideoExpanded by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf("All") }

    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val quickGenres = listOf(
        "All",
        "📥 Downloaded",
        "Top Music",
        "Pop Hits",
        "Lofi Hip Hop",
        "Rock Classics",
        "Relaxing Chill",
        "Workout Music",
        "EDM Dance"
    )

    BackHandler(enabled = isFullScreen) {
        viewModel.setFullScreenVideo(false)
    }

    if (showSpeedDialog) {
        SpeedSelectionDialog(
            currentSpeed = youTubeSpeed,
            onSelectSpeed = { viewModel.setYouTubePlaybackSpeed(it) },
            onDismiss = { showSpeedDialog = false }
        )
    }

    if (showQueueSheet) {
        QueueBottomSheet(
            queue = youTubeQueue,
            currentTrack = currentYTTrack,
            isPlaying = isYTPlaying,
            onTrackClick = { viewModel.playYouTubeTrack(it, youTubeQueue) },
            onDismiss = { showQueueSheet = false }
        )
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            remainingMs = sleepTimerMs,
            onSetTimer = { minutes ->
                viewModel.setSleepTimer(minutes)
                showSleepTimerDialog = false
            },
            onCancelTimer = {
                viewModel.setSleepTimer(0)
                showSleepTimerDialog = false
            },
            onDismiss = { showSleepTimerDialog = false }
        )
    }

    if (trackToAddToPlaylist != null) {
        AlertDialog(
            onDismissRequest = { trackToAddToPlaylist = null },
            title = { Text("Add YouTube Audio to Playlist") },
            text = {
                if (playlists.isEmpty()) {
                    Text("No playlists created yet. Create one in the Playlists tab.")
                } else {
                    LazyColumn {
                        items(playlists) { pl ->
                            TextButton(
                                onClick = {
                                    val t = trackToAddToPlaylist!!
                                    val song = LocalSong(
                                        id = t.videoId.hashCode().toLong(),
                                        title = t.title,
                                        artist = t.author,
                                        album = "YouTube Music",
                                        duration = t.durationSeconds * 1000L,
                                        uri = android.net.Uri.parse("yt://${t.videoId}"),
                                        albumArtUri = android.net.Uri.parse(t.thumbnailUrl)
                                    )
                                    viewModel.addSongToPlaylist(pl, song)
                                    Toast.makeText(context, "Added to ${pl.name}", Toast.LENGTH_SHORT).show()
                                    trackToAddToPlaylist = null
                                }
                            ) {
                                Text(pl.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { trackToAddToPlaylist = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCloseConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCloseConfirmDialog = false },
            icon = { Icon(Icons.Default.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Song is Currently Playing") },
            text = {
                Text("Do you want to minimize to keep listening in the background, or pause and save your position?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.minimizeYouTubePlayer()
                        showCloseConfirmDialog = false
                        Toast.makeText(context, "Playing in background (MiniPlayer)", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Minimize & Keep Playing")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        viewModel.closeYouTubePlayer(savePosition = true)
                        showCloseConfirmDialog = false
                        Toast.makeText(context, "Position saved. Tap song to resume anytime.", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Pause & Close")
                }
            }
        )
    }

    // Artist Channel Content BottomSheet
    if (selectedChannel != null) {
        ChannelDetailSheet(
            channel = selectedChannel!!,
            viewModel = viewModel,
            onDismiss = { viewModel.closeChannel() }
        )
    }

    // Download Format & Quality Selector Sheet
    if (trackForDownloadOptions != null) {
        YouTubeDownloadSheet(
            track = trackForDownloadOptions!!,
            viewModel = viewModel,
            onDismiss = { trackForDownloadOptions = null }
        )
    }

    // Determine which tracks to display
    val displayedTracks = if (selectedCategory == "📥 Downloaded") {
        downloadedTracks.map { it.toYouTubeTrack() }
    } else {
        searchTracks
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F14))) {
        Scaffold(
            containerColor = Color(0xFF0F0F14),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0F0F14),
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFFF0000)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "YouTube Music",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "High-fidelity audio stream • Channels • Downloads",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF9E9EA8)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSleepTimerDialog = true }) {
                            BadgedBox(
                                badge = {
                                    if (sleepTimerMs > 0) {
                                        Badge(containerColor = Color(0xFFFF0000)) { Text("${(sleepTimerMs / 60000)}m", color = Color.White) }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = "Sleep Timer",
                                    tint = if (sleepTimerMs > 0) Color(0xFFFF0000) else Color.White
                                )
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // ACTIVE YOUTUBE PLAYER SECTION (Full Audio & Video Controls)
                if (currentYTTrack != null && isYTPlayerVisible) {
                    val track = currentYTTrack!!
                    val isDownloaded = downloadedTracks.any { it.videoId == track.videoId }
                    val isDownloading = downloadingTrackIds.contains(track.videoId)
                    val isFav = favorites.any { it.songUri == "yt://${track.videoId}" }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF161622)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28283C)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column {
                            // Player Title Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = if (isAudioOnly) Color(0xFF00B0FF) else Color(0xFFFF0000),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (isAudioOnly) "AUDIO MODE" else "NOW PLAYING",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.openChannel(track.author) }
                                ) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = track.author,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFFF5252),
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Verified,
                                            contentDescription = "View Channel",
                                            tint = Color(0xFFFF0000),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }

                                // Audio Mode / Video Mode Switcher
                                IconButton(
                                    onClick = { viewModel.toggleAudioOnlyMode() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isAudioOnly) Icons.Default.OndemandVideo else Icons.Default.Headphones,
                                        contentDescription = if (isAudioOnly) "Switch to Video" else "Switch to Audio Mode",
                                        tint = if (isAudioOnly) Color(0xFF00B0FF) else Color(0xFFFF0000)
                                    )
                                }

                                // Expand / Collapse
                                IconButton(
                                    onClick = { isVideoExpanded = !isVideoExpanded },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isVideoExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isVideoExpanded) "Collapse" else "Expand",
                                        tint = Color.White
                                    )
                                }

                                // Fullscreen (for video mode)
                                if (!isAudioOnly) {
                                    IconButton(
                                        onClick = { viewModel.setFullScreenVideo(true) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Fullscreen, contentDescription = "Full Screen", tint = Color.White)
                                    }
                                }

                                // Minimize to background (keeps playing in MiniPlayer without interrupting!)
                                IconButton(
                                    onClick = {
                                        viewModel.minimizeYouTubePlayer()
                                        Toast.makeText(context, "Playing in background (MiniPlayer)", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize Player", tint = Color.White)
                                }

                                // Close Player with Accidental Tap Protection
                                IconButton(
                                    onClick = {
                                        if (isYTPlaying) {
                                            showCloseConfirmDialog = true
                                        } else {
                                            viewModel.closeYouTubePlayer()
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Player", tint = Color.White)
                                }
                            }

                            // EXPANDED MEDIA VIEW (Audio Cover Art vs Video WebView)
                            AnimatedVisibility(
                                visible = isVideoExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black)
                                ) {
                                    if (isAudioOnly) {
                                        // AUDIO-FIRST EXPERIENCE: Vinyl disc cover + Equalizer bars
                                        AudioModeView(
                                            track = track,
                                            isPlaying = isYTPlaying,
                                            onSwitchToVideo = { viewModel.toggleAudioOnlyMode() }
                                        )
                                        // Hidden background WebView ensuring continuous audio playback
                                        Box(modifier = Modifier.size(1.dp).alpha(0.01f)) {
                                            YouTubePlayerWebView(viewModel = viewModel)
                                        }
                                    } else {
                                        // VIDEO MODE: 16:9 Interactive YouTube Embed
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(16f / 9f)
                                        ) {
                                            YouTubePlayerWebView(viewModel = viewModel)
                                        }
                                    }
                                }
                            }

                            // CONTROLS BAR (All Normal Audio Controls)
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                // Error / Fallback Banner for embed-restricted videos
                                if (playbackError != null || (lastErrorCode != null && lastErrorCode in listOf(2, 5, 100, 101, 150, 152))) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    Icon(
                                                        Icons.Default.Info,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Embed Restricted Video",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.clearYouTubePlaybackError() },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Dismiss",
                                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = playbackError ?: "Owner disabled in-app embedding for this track.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = { viewModel.playAlternativeYouTubeTrack() },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.error
                                                    ),
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Play Audio Stream", style = MaterialTheme.typography.labelMedium)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        try {
                                                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=${track.videoId}")).apply {
                                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                            }
                                                            context.startActivity(intent)
                                                        } catch (_: Exception) {
                                                            Toast.makeText(context, "Could not launch YouTube", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Open YouTube", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    }
                                }

                                // Smart Resume Banner
                                if (resumeNotice != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.History,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = resumeNotice ?: "",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            TextButton(
                                                onClick = { viewModel.restartCurrentYouTubeTrack() },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                            ) {
                                                Text(
                                                    text = "Start from 0:00",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                // Progress Slider
                                val curSec = ytCurrentTime
                                val totalSec = if (ytDuration > 0f) ytDuration else (track.durationSeconds.toFloat().coerceAtLeast(1f))
                                val progress = (curSec / totalSec).coerceIn(0f, 1f)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formatTime(curSec.toLong()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFA0A0B0)
                                    )
                                    Slider(
                                        value = progress,
                                        onValueChange = { newProg ->
                                            val targetSec = newProg * totalSec
                                            viewModel.seekYouTube(targetSec)
                                        },
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFFFF0000),
                                            activeTrackColor = Color(0xFFFF0000),
                                            inactiveTrackColor = Color(0xFF2C2C3E)
                                        ),
                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                    )
                                    Text(
                                        text = formatTime(totalSec.toLong()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFA0A0B0)
                                    )
                                }

                                // Primary Playback Controls Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Shuffle
                                    IconButton(onClick = { viewModel.toggleYouTubeShuffle() }) {
                                        Icon(
                                            imageVector = Icons.Default.Shuffle,
                                            contentDescription = "Shuffle Queue",
                                            tint = if (youTubeShuffle) Color(0xFFFF0000) else Color(0xFFA0A0B0)
                                        )
                                    }

                                    // Previous Track
                                    IconButton(onClick = { viewModel.playPreviousYouTubeTrack() }) {
                                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Track", tint = Color.White)
                                    }

                                    // Restart from beginning
                                    IconButton(
                                        onClick = {
                                            viewModel.restartCurrentYouTubeTrack()
                                            Toast.makeText(context, "Restarted from 0:00", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.RestartAlt, contentDescription = "Restart from 0:00", tint = Color.White)
                                    }

                                    // Rewind 10s
                                    IconButton(onClick = {
                                        val newSec = (ytCurrentTime - 10f).coerceAtLeast(0f)
                                        viewModel.seekYouTube(newSec)
                                    }) {
                                        Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White)
                                    }

                                    // Play / Pause
                                    FilledIconButton(
                                        onClick = { viewModel.toggleYouTubePlayPause() },
                                        modifier = Modifier.size(54.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = Color(0xFFFF0000)
                                        )
                                    ) {
                                        Icon(
                                            imageVector = if (isYTPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isYTPlaying) "Pause" else "Play",
                                            modifier = Modifier.size(32.dp),
                                            tint = Color.White
                                        )
                                    }

                                    // Forward 10s
                                    IconButton(onClick = {
                                        val newSec = ytCurrentTime + 10f
                                        viewModel.seekYouTube(newSec)
                                    }) {
                                        Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White)
                                    }

                                    // Next Track
                                    IconButton(onClick = { viewModel.playNextYouTubeTrack() }) {
                                        Icon(Icons.Default.SkipNext, contentDescription = "Next Track", tint = Color.White)
                                    }

                                    // Repeat (Off, All, One)
                                    IconButton(onClick = { viewModel.toggleYouTubeRepeatMode() }) {
                                        Icon(
                                            imageVector = when (youTubeRepeat) {
                                                YouTubeRepeatMode.OFF -> Icons.Default.Repeat
                                                YouTubeRepeatMode.ALL -> Icons.Default.RepeatOn
                                                YouTubeRepeatMode.ONE -> Icons.Default.RepeatOne
                                            },
                                            contentDescription = "Repeat Mode",
                                            tint = if (youTubeRepeat != YouTubeRepeatMode.OFF) Color(0xFFFF0000) else Color(0xFFA0A0B0)
                                        )
                                    }
                                }

                                // Secondary Tools Row: Speed, Download, Favorite, Queue, Playlist, Share
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Playback Speed Button
                                    FilterChip(
                                        selected = youTubeSpeed != 1.0f,
                                        onClick = { showSpeedDialog = true },
                                        label = { Text("${youTubeSpeed}x", color = Color.White) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = Color(0xFF1E1E2C),
                                            selectedContainerColor = Color(0xFFFF0000)
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = youTubeSpeed != 1.0f,
                                            borderColor = Color(0xFF2C2C3E),
                                            selectedBorderColor = Color(0xFFFF0000)
                                        ),
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Speed,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )

                                    // Download / Offline Save Button
                                    Surface(
                                        onClick = {
                                            trackForDownloadOptions = track
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isDownloaded) Color(0xFF1B3822) else Color(0xFF1E1E2C),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDownloaded) Color(0xFF2E7D32) else Color(0xFF2C2C3E)),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isDownloading) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = Color(0xFFFF0000)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Saving...",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFFF5252)
                                                )
                                            } else if (isDownloaded) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = Color(0xFF4CAF50)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Saved",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF4CAF50)
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Download,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = Color.White
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Download",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }

                                    // Favorite Toggle
                                    IconButton(
                                        onClick = { viewModel.toggleYouTubeFavorite(track) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Favorite",
                                            tint = if (isFav) Color(0xFFFF1744) else Color(0xFFA0A0B0)
                                        )
                                    }

                                    // Up Next Queue Button
                                    IconButton(
                                        onClick = { showQueueSheet = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        BadgedBox(
                                            badge = {
                                                if (youTubeQueue.isNotEmpty()) {
                                                    Badge(containerColor = Color(0xFFFF0000)) { Text("${youTubeQueue.size}", color = Color.White) }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.QueueMusic,
                                                contentDescription = "Queue",
                                                tint = Color.White
                                            )
                                        }
                                    }

                                    // View Artist Channel Button
                                    IconButton(
                                        onClick = { viewModel.openChannel(track.author) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.AccountCircle,
                                            contentDescription = "View Channel",
                                            tint = Color(0xFFFF0000)
                                        )
                                    }

                                    // Add to Playlist
                                    IconButton(
                                        onClick = { trackToAddToPlaylist = track },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.PlaylistAdd,
                                            contentDescription = "Add to playlist",
                                            tint = Color(0xFFA0A0B0)
                                        )
                                    }

                                    // Share Link
                                    IconButton(
                                        onClick = { shareYouTubeLink(context, track) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = "Share link",
                                            tint = Color(0xFFA0A0B0)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // SEARCH BAR
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF181824),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28283C))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFFFF0000)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { viewModel.onYouTubeQueryChanged(it) },
                            placeholder = { Text("Search songs or paste YouTube link...", color = Color(0xFF888898)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFFFF0000),
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                viewModel.searchYouTube()
                            })
                        )
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.onYouTubeQueryChanged("")
                                viewModel.searchYouTube("")
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.White)
                            }
                        } else {
                            val clipboardText = clipboardManager.getText()?.text
                            if (!clipboardText.isNullOrBlank() && (clipboardText.contains("youtube.com") || clipboardText.contains("youtu.be"))) {
                                IconButton(onClick = {
                                    viewModel.onYouTubeQueryChanged(clipboardText)
                                    viewModel.searchYouTube(clipboardText)
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste YouTube link", tint = Color(0xFFFF0000))
                                }
                            }
                        }
                        IconButton(onClick = {
                            focusManager.clearFocus()
                            viewModel.searchYouTube()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Submit Search",
                                tint = Color(0xFFFF0000)
                            )
                        }
                    }
                }

                // QUICK CATEGORY CHIPS (Including "📥 Downloaded")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickGenres) { genre ->
                        val isSelected = (selectedCategory == genre)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedCategory = genre
                                if (genre == "📥 Downloaded") {
                                    // Local downloads shown immediately
                                } else if (genre == "All") {
                                    viewModel.searchYouTube("")
                                } else {
                                    viewModel.searchYouTube(genre)
                                }
                            },
                            label = { Text(genre, color = Color.White, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0xFF181824),
                                labelColor = Color.White,
                                selectedContainerColor = Color(0xFFFF0000),
                                selectedLabelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = Color(0xFF28283C),
                                selectedBorderColor = Color(0xFFFF0000)
                            ),
                            leadingIcon = {
                                if (genre == "📥 Downloaded") {
                                    Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (isSelected) Color.White else Color(0xFF4CAF50))
                                } else {
                                    Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (isSelected) Color.White else Color(0xFFFF5252))
                                }
                            }
                        )
                    }
                }

                // FEATURED ARTISTS & CHANNELS ROW
                if (selectedCategory != "📥 Downloaded" && query.isBlank()) {
                    val topArtists = remember { viewModel.getTopArtists() }
                    Column(modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Stars,
                                    contentDescription = null,
                                    tint = Color(0xFFFF0000),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Featured Artist Channels",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "Tap to explore",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF9E9EA8)
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(topArtists, key = { it.id }) { artist ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(82.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { viewModel.openChannel(artist.name) }
                                        .padding(vertical = 4.dp, horizontal = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(CircleShape)
                                            .border(2.dp, Color(0xFFFF0000), CircleShape)
                                            .background(Color(0xFF161622))
                                    ) {
                                        AsyncImage(
                                            model = artist.avatarUrl,
                                            contentDescription = artist.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = artist.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            imageVector = Icons.Default.Verified,
                                            contentDescription = "Verified",
                                            tint = Color(0xFFFF0000),
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                    Text(
                                        text = if (artist.subscribers.isNotBlank()) artist.subscribers.substringBefore(" ") else "Artist",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF9E9EA8),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // SEARCHING INDICATOR
                if (isSearching) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .height(3.dp),
                        color = Color(0xFFFF0000),
                        trackColor = Color(0xFF28283C)
                    )
                }

                // ERROR BANNER
                if (errorMessage != null && !isSearching && selectedCategory != "📥 Downloaded") {
                    Surface(
                        color = Color(0xFF38151D),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF0000).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFFF5252))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.searchYouTube() }) {
                                Text("Retry", color = Color(0xFFFF5252))
                            }
                        }
                    }
                }

                // TRACKS LIST
                if (selectedCategory == "📥 Downloaded" && downloadedTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.DownloadForOffline,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color(0xFFFF0000).copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No Downloaded YouTube Songs Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap the download icon (📥) on any song to save it for instant offline access and playback.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF9E9EA8),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (selectedCategory == "📥 Downloaded") "Downloaded YouTube Music"
                                           else if (query.isBlank()) "Featured & Trending Music"
                                           else "Search Results",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "${displayedTracks.size} tracks",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF9E9EA8)
                                )
                            }
                        }

                        items(displayedTracks) { track ->
                            val isThisPlaying = currentYTTrack?.videoId == track.videoId && isYTPlaying
                            val isTrackDl = downloadedTracks.any { it.videoId == track.videoId }
                            val isTrackDling = downloadingTrackIds.contains(track.videoId)
                            val isFavTrack = favorites.any { it.songUri == "yt://${track.videoId}" }

                            YouTubeTrackCard(
                                track = track,
                                isPlaying = isThisPlaying,
                                isDownloaded = isTrackDl,
                                isDownloading = isTrackDling,
                                isFavorite = isFavTrack,
                                onPlay = {
                                    val previous = currentYTTrack
                                    val wasPlaying = isYTPlaying
                                    viewModel.playYouTubeTrack(track, displayedTracks)
                                    if (previous != null && previous.videoId != track.videoId && wasPlaying) {
                                        coroutineScope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Playing \"${track.title.take(24)}\"",
                                                actionLabel = "Undo",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.restorePreviousYouTubeTrack()
                                            }
                                        }
                                    }
                                },
                                onOpenChannel = {
                                    viewModel.openChannel(track.author)
                                },
                                onDownload = {
                                    trackForDownloadOptions = track
                                },
                                onToggleFavorite = { viewModel.toggleYouTubeFavorite(track) },
                                onAddToPlaylist = { trackToAddToPlaylist = track },
                                onShare = { shareYouTubeLink(context, track) }
                            )
                        }
                    }
                }
            }
        }

        // FULLSCREEN VIDEO OVERLAY
        if (isFullScreen && currentYTTrack != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .zIndex(100f)
            ) {
                YouTubePlayerWebView(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )

                // Close Fullscreen Button
                FilledIconButton(
                    onClick = { viewModel.setFullScreenVideo(false) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.6f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Exit Fullscreen",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun AudioModeView(
    track: YouTubeTrack,
    isPlaying: Boolean,
    onSwitchToVideo: () -> Unit
) {
    // Vinyl Disc Rotation Animation
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF1A1A24),
                        Color(0xFF0F0F14)
                    )
                )
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            // Vinyl Record Background
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF111111))
                    .border(2.dp, Color(0xFF333333), CircleShape)
            )

            // Spinning Album Art
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .rotate(if (isPlaying) rotationAngle else 0f)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape)
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Center Spindle
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Animated Audio Waveform Bars
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.height(20.dp)
        ) {
            val barCount = 7
            for (i in 0 until barCount) {
                val barAnim by infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 350 + (i * 90),
                            easing = FastOutSlowInEasing
                        ),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bar_$i"
                )
                val heightFraction = if (isPlaying) barAnim else 0.25f
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight(heightFraction)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Normal High-Quality Audio Stream Active",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun YouTubePlayerWebView(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    AndroidView(
        modifier = modifier,
        factory = {
            viewModel.youTubePlayerController.detachFromParent()
            viewModel.youTubePlayerController.getWebView()
        },
        update = {
            // Managed seamlessly by youTubePlayerController
        }
    )
}

@Composable
fun YouTubeTrackCard(
    track: YouTubeTrack,
    isPlaying: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onOpenChannel: () -> Unit,
    onDownload: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit
) {
    Surface(
        onClick = onPlay,
        shape = RoundedCornerShape(14.dp),
        color = if (isPlaying) Color(0xFF25141B) else Color(0xFF161622),
        border = if (isPlaying) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF0000)) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF242436)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 86.dp, height = 58.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Duration / Audio badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (track.durationSeconds > 0) formatTime(track.durationSeconds) else "Audio",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Playing",
                            tint = Color(0xFFFF0000),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isPlaying) Color(0xFFFF5252) else Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Clickable Artist Channel Link with Verified Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onOpenChannel() }
                        .padding(vertical = 1.dp)
                ) {
                    Text(
                        text = track.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF6666),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "View Channel",
                        tint = Color(0xFFFF0000),
                        modifier = Modifier.size(12.dp)
                    )
                    if (track.viewCountText.isNotBlank()) {
                        Text(
                            text = " • ${track.viewCountText}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF9E9EA8)
                        )
                    }
                }
            }

            // Download Icon Button
            IconButton(
                onClick = onDownload,
                modifier = Modifier.size(36.dp)
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFFF0000)
                    )
                } else if (isDownloaded) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download Audio",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Favorite Icon Button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Color(0xFFFF1744) else Color(0xFFA0A0B0),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Play Icon Button
            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(36.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPlaying) Color(0xFFFF0000) else Color(0xFF28283C)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play Audio",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedSelectionDialog(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Playback Speed")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                speeds.forEach { spd ->
                    val isSelected = (spd == currentSpeed)
                    Surface(
                        onClick = {
                            onSelectSpeed(spd)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (spd == 1.0f) "1.0x (Normal)" else "${spd}x",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueBottomSheet(
    queue: List<YouTubeTrack>,
    currentTrack: YouTubeTrack?,
    isPlaying: Boolean,
    onTrackClick: (YouTubeTrack) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Up Next Queue",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${queue.size} tracks",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Queue is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(queue) { track ->
                        val isCurrent = currentTrack?.videoId == track.videoId
                        Surface(
                            onClick = {
                                onTrackClick(track)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp, 36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black)
                                ) {
                                    AsyncImage(
                                        model = track.thumbnailUrl,
                                        contentDescription = track.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (isCurrent && isPlaying) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.4f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.author,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = formatTime(track.durationSeconds),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SleepTimerDialog(
    remainingMs: Long,
    onSetTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val presets = listOf(15, 30, 45, 60, 90)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sleep Timer")
            }
        },
        text = {
            Column {
                if (remainingMs > 0) {
                    val minutesLeft = remainingMs / 60000
                    val secondsLeft = (remainingMs % 60000) / 1000
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Active Timer Running",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = String.format("%02d:%02d remaining", minutesLeft, secondsLeft),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Text(
                    text = "Automatically pause playback and fade audio after:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { minutes ->
                        OutlinedButton(
                            onClick = { onSetTimer(minutes) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$minutes Minutes")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (remainingMs > 0) {
                TextButton(onClick = onCancelTimer) {
                    Text("Turn Off Timer", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%d:%02d", m, s)
}

private fun shareYouTubeLink(context: Context, track: YouTubeTrack) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, track.title)
            putExtra(Intent.EXTRA_TEXT, "Listen to \"${track.title}\" by ${track.author} on YouTube: https://www.youtube.com/watch?v=${track.videoId}")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Track"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share link", Toast.LENGTH_SHORT).show()
    }
}
