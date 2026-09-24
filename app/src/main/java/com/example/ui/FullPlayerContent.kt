package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun FullPlayerContent(viewModel: MainViewModel, onClose: () -> Unit = {}) {
    val currentSong by viewModel.playerManager.currentSong.collectAsState()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsState()
    val position by viewModel.playerManager.playbackPosition.collectAsState()
    val shuffleMode by viewModel.playerManager.shuffleModeEnabled.collectAsState()
    val repeatMode by viewModel.playerManager.repeatMode.collectAsState()
    val artworkData by viewModel.playerManager.currentArtworkData.collectAsState()
    
    var showSleepTimer by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showMemoDialog by remember { mutableStateOf(false) }
    var showSingerKeyDialog by remember { mutableStateOf(false) }
    var showEarShieldDialog by remember { mutableStateOf(false) }
    var showCuesDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    if (currentSong == null) return

    val isFatigueActive by viewModel.playerManager.isEarFatigueActive.collectAsState()
    val sessionDose by viewModel.playerManager.sessionDosePercent.collectAsState()
    val songCues by remember(currentSong?.uri) {
        viewModel.getCuesForSong(currentSong?.uri?.toString() ?: "")
    }.collectAsState(initial = emptyList())

    val currentMemo by remember(currentSong?.uri) {
        viewModel.getMemoForSong(currentSong?.uri?.toString() ?: "")
    }.collectAsState(initial = null)
    val isKaraokeMode by viewModel.karaokeModeEnabled.collectAsState()

    var dragAmountSum by remember { mutableFloatStateOf(0f) }

    // Hardware-accelerated Rotation Animation for Album Art
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin")
    val spinningRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val currentRotation = if (isPlaying) spinningRotation else 0f

    val currentUniverse by viewModel.currentUniverse.collectAsState()
    var showUniverseDialog by remember { mutableStateOf(false) }
    var showSanctuarySheet by remember { mutableStateOf(false) }
    var showStudioLooperSheet by remember { mutableStateOf(false) }

    val isAnyAmbientActive by viewModel.ambientEngine.isAnyActive.collectAsState()
    val isABLoopEnabled by viewModel.playerManager.isABLoopEnabled.collectAsState()
    val loopPointA by viewModel.playerManager.loopPointA.collectAsState()
    val loopPointB by viewModel.playerManager.loopPointB.collectAsState()

    if (showSanctuarySheet) {
        AmbientSanctuarySheet(viewModel = viewModel, onDismiss = { showSanctuarySheet = false })
    }

    if (showStudioLooperSheet) {
        StudioLooperSheet(playerManager = viewModel.playerManager, onDismiss = { showStudioLooperSheet = false })
    }

    if (showUniverseDialog) {
        AlertDialog(
            onDismissRequest = { showUniverseDialog = false },
            title = { Text("Select Visual Universe") },
            text = {
                LazyColumn {
                    items(VisualUniverse.values()) { universe ->
                        TextButton(
                            onClick = {
                                viewModel.setUniverse(universe)
                                showUniverseDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(universe.title, color = if (currentUniverse == universe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showUniverseDialog = false }) { Text("Close") } }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (currentUniverse != VisualUniverse.NONE) {
            VisualUniverseBackground(currentUniverse, isPlaying)
        } else {
            // Blurred Background
            if (artworkData != null) {
                AsyncImage(
                    model = artworkData,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.2f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0xCC1A1A1A), Color(0xF2000000))))
                )
            } else if (currentSong?.albumArtUri != null) {
                AsyncImage(
                    model = currentSong?.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.2f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0xCC1A1A1A), Color(0xF2000000))))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0xFF1E1E1E), Color(0xFF000000))))
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { viewModel.playerManager.togglePlayPause() }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragAmountSum > 100) {
                                viewModel.playerManager.skipPrevious()
                            } else if (dragAmountSum < -100) {
                                viewModel.playerManager.skipNext()
                            }
                            dragAmountSum = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            dragAmountSum += dragAmount
                            change.consume()
                        }
                    )
                }
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Dismiss Button and top controls
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showEarShieldDialog = true }) {
                        Icon(
                            imageVector = if (isFatigueActive) Icons.Default.Shield else Icons.Default.Hearing,
                            contentDescription = "Ear Fatigue & Health Guard",
                            tint = when {
                                isFatigueActive -> MaterialTheme.colorScheme.primary
                                sessionDose >= 75f -> Color(0xFFFF9800)
                                else -> Color.White
                            }
                        )
                    }
                    IconButton(onClick = { showSanctuarySheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Spa,
                            contentDescription = "Audio Sanctuary",
                            tint = if (isAnyAmbientActive) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                    IconButton(onClick = {
                        viewModel.setDriveMode(true)
                        onClose()
                    }) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "Drive Mode",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { showUniverseDialog = true }) {
                        Icon(Icons.Default.Visibility, contentDescription = "Visual Universe", tint = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Album Art
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
                    .padding(8.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
            ) {
                if (artworkData != null) {
                    AsyncImage(
                        model = artworkData,
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(currentRotation)
                    )
                } else if (currentSong!!.albumArtUri != null) {
                    AsyncImage(
                        model = currentSong!!.albumArtUri,
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(currentRotation)
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().rotate(currentRotation),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(80.dp), tint = Color.Gray)
                    }
                }
                // Center hole for vinyl look
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF121212))
                        .align(Alignment.Center)
                )
            }

            Spacer(modifier = Modifier.height(56.dp))

            // Title and Artist
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong!!.title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentSong!!.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                val isFav by remember(currentSong!!.uri) { viewModel.isFavorite(currentSong!!.uri.toString()) }.collectAsState(initial = false)
                IconButton(onClick = { viewModel.toggleFavorite(currentSong!!) }) {
                    Icon(
                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFav) Color.Red else Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            fun formatTime(ms: Long): String {
                val totalSeconds = ms / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                return String.format("%02d:%02d", minutes, seconds)
            }

            // Clickable Cue Chips above Seekbar
            if (songCues.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(songCues) { cue ->
                        Surface(
                            onClick = { viewModel.playerManager.seekTo(cue.timestampMs) },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            color = Color(cue.tagColor).copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, Color(cue.tagColor).copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(cue.tagColor), androidx.compose.foundation.shape.CircleShape)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "${cue.label} (${formatTime(cue.timestampMs)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Progress Bar
            Slider(
                value = position.toFloat(),
                onValueChange = { viewModel.playerManager.seekTo(it.toLong()) },
                valueRange = 0f..currentSong!!.duration.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(position), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { showCuesDialog = true },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            Icons.Default.BookmarkBorder,
                            contentDescription = "Audio Cues & Bookmarks",
                            tint = if (songCues.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                Text(formatTime(currentSong!!.duration), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.playerManager.toggleShuffle() }) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(28.dp), tint = if (shuffleMode) MaterialTheme.colorScheme.primary else Color.White)
                }
                IconButton(onClick = { viewModel.playerManager.skipPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(48.dp), tint = Color.White)
                }
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { viewModel.playerManager.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(48.dp),
                        tint = Color.Black
                    )
                }
                IconButton(onClick = { viewModel.playerManager.skipNext() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(48.dp), tint = Color.White)
                }
                IconButton(onClick = { viewModel.playerManager.toggleRepeat() }) {
                    val icon = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                    val tint = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF) Color.White else MaterialTheme.colorScheme.primary
                    Icon(icon, contentDescription = "Repeat", modifier = Modifier.size(28.dp), tint = tint)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Premium Features Row
            var showLyrics by remember { mutableStateOf(false) }

            if (showLyrics) {
                AlertDialog(
                    onDismissRequest = { showLyrics = false },
                    title = { Text("Lyrics") },
                    text = { 
                        LazyColumn {
                            item {
                                Text("No lyrics available for this local file.", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showLyrics = false }) { Text("Close") } }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showEqualizer = true }.padding(8.dp)) {
                    Icon(Icons.Default.Tune, "Equalizer", tint = if (showEqualizer) MaterialTheme.colorScheme.primary else Color.White)
                    Text("EQ", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showSpeedDialog = true }.padding(8.dp)) {
                    val speed by viewModel.playerManager.playbackSpeed.collectAsState()
                    Icon(Icons.Default.Speed, "Speed", tint = if (speed != 1.0f) MaterialTheme.colorScheme.primary else Color.White)
                    Text("${speed}x", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                }
                val timerMs by viewModel.playerManager.sleepTimerRemainingMs.collectAsState()
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showSleepTimer = true }.padding(8.dp)) {
                    val timerText = if (timerMs > 0) {
                        String.format("%02d:%02d", timerMs / 60000, (timerMs / 1000) % 60)
                    } else "Timer"
                    Icon(Icons.Default.Timer, "Sleep Timer", tint = if (timerMs > 0) MaterialTheme.colorScheme.primary else Color.White)
                    Text(timerText, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showLyrics = true }.padding(8.dp)) {
                    Icon(Icons.Default.Lyrics, "Lyrics", tint = Color.White)
                    Text("Lyrics", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showQueueSheet = true }.padding(8.dp)) {
                    Icon(Icons.Default.QueueMusic, "Queue", tint = Color.White)
                    Text("Queue", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showStudioLooperSheet = true }.padding(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.AllInclusive,
                        contentDescription = "Studio Looper",
                        tint = if (isABLoopEnabled) MaterialTheme.colorScheme.primary else Color.White
                    )
                    Text("Studio", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showSleepTimer) {
            SleepTimerDialog(
                viewModel = viewModel,
                onDismiss = { showSleepTimer = false },
                onSetTimer = { mins ->
                    viewModel.playerManager.setSleepTimer(mins)
                    showSleepTimer = false
                }
            )
        }

        if (showEqualizer) {
            EqualizerDialog(viewModel = viewModel, onDismiss = { showEqualizer = false })
        }

        if (showSpeedDialog) {
            SpeedDialog(viewModel = viewModel, onDismiss = { showSpeedDialog = false })
        }

        if (showEarShieldDialog) {
            EarShieldDialog(viewModel = viewModel, onDismiss = { showEarShieldDialog = false })
        }

        if (showCuesDialog && currentSong != null) {
            SongCuesDialog(
                viewModel = viewModel,
                songUri = currentSong!!.uri.toString(),
                currentPositionMs = position,
                songDurationMs = currentSong!!.duration,
                onSeekTo = { viewModel.playerManager.seekTo(it) },
                onDismiss = { showCuesDialog = false }
            )
        }

        if (showQueueSheet) {
            QueueSheet(
                viewModel = viewModel,
                onDismiss = { showQueueSheet = false }
            )
        }

        if (showLyrics && currentSong != null) {
            LyricsSheet(
                viewModel = viewModel,
                song = currentSong!!,
                currentPositionMs = position,
                onSeekTo = { viewModel.playerManager.seekTo(it) },
                onDismiss = { showLyrics = false }
            )
        }
    }
}

@Composable
fun SpeedDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val speed by viewModel.playerManager.playbackSpeed.collectAsState()
    val pitch by viewModel.playerManager.playbackPitch.collectAsState()
    var tapTimestamps by remember { mutableStateOf(listOf<Long>()) }
    var detectedBpm by remember { mutableIntStateOf(0) }
    var baseBpm by remember { mutableIntStateOf(120) }

    fun registerTap() {
        val now = System.currentTimeMillis()
        val recentTaps = (tapTimestamps + now).takeLast(6).filter { now - it < 3000L }
        tapTimestamps = recentTaps
        if (recentTaps.size >= 2) {
            val intervals = (1 until recentTaps.size).map { recentTaps[it] - recentTaps[it - 1] }
            val avgInterval = intervals.average()
            if (avgInterval > 0) {
                detectedBpm = (60_000.0 / avgInterval).toInt().coerceIn(50, 220)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Speed, Pitch & Cadence")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Playback Speed (${String.format("%.2f", speed)}x)", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(listOf(0.5f, 0.75f, 0.9f, 1.0f, 1.15f, 1.25f, 1.5f, 2.0f)) { s ->
                        FilterChip(
                            selected = kotlin.math.abs(speed - s) < 0.02f,
                            onClick = { viewModel.playerManager.setPlaybackSpeed(s) },
                            label = { Text("${s}x") }
                        )
                    }
                }

                Text("Pitch Adjustment (${String.format("%.2f", pitch)}x)", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(listOf(0.8f, 0.9f, 1.0f, 1.1f, 1.2f)) { p ->
                        FilterChip(
                            selected = kotlin.math.abs(pitch - p) < 0.02f,
                            onClick = { viewModel.playerManager.setPlaybackPitch(p) },
                            label = { Text("${p}x") }
                        )
                    }
                }

                HorizontalDivider()

                // Tap-to-BPM Workout Cadence Matcher
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Workout Cadence & Tap-to-BPM",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap rhythm of your steps or workout to sync tempo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { registerTap() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.DirectionsRun, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (detectedBpm > 0) "Tapped: $detectedBpm BPM (Tap to update)" else "Tap Beat / Stride Rhythm")
                        }

                        if (detectedBpm > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val targetSpeed = (detectedBpm.toFloat() / baseBpm.toFloat()).coerceIn(0.6f, 1.8f)
                            TextButton(
                                onClick = {
                                    viewModel.playerManager.setPlaybackSpeed(targetSpeed)
                                }
                            ) {
                                Text("Snap Speed to ${detectedBpm} BPM (${String.format("%.2f", targetSpeed)}x)")
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

@Composable
fun SleepTimerDialog(viewModel: MainViewModel, onDismiss: () -> Unit, onSetTimer: (Int) -> Unit) {
    val remainingMs by viewModel.playerManager.sleepTimerRemainingMs.collectAsState()
    val stopAfterCurrent by viewModel.playerManager.sleepAfterCurrentTrack.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sleep Timer")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (remainingMs > 0) {
                    val mm = (remainingMs / 1000) / 60
                    val ss = (remainingMs / 1000) % 60
                    Text(
                        text = String.format("Time Remaining: %02d:%02d", mm, ss),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    TextButton(onClick = { 
                        onSetTimer(0)
                        viewModel.playerManager.setSleepAfterCurrentTrack(false)
                    }) {
                        Text("Cancel Active Timer", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Stop After Current Track Option
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (stopAfterCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth().clickable {
                        viewModel.playerManager.setSleepAfterCurrentTrack(!stopAfterCurrent)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = stopAfterCurrent,
                            onCheckedChange = { viewModel.playerManager.setSleepAfterCurrentTrack(it) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Stop after current track ends",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (stopAfterCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Or choose duration:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5, 15, 30, 60).forEach { mins ->
                        FilterChip(
                            selected = false,
                            onClick = { onSetTimer(mins) },
                            label = { Text("${mins}m") }
                        )
                    }
                }

                if (remainingMs > 0 || stopAfterCurrent) {
                    TextButton(onClick = { 
                        onSetTimer(0)
                        viewModel.playerManager.setSleepAfterCurrentTrack(false)
                    }) {
                        Text("Turn Off Timer", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

