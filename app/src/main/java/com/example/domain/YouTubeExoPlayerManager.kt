package com.example.domain

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class YouTubeExoPlayerManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: YouTubeExoPlayerManager? = null

        fun getInstance(context: Context): YouTubeExoPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: YouTubeExoPlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val playerScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        setHandleAudioBecomingNoisy(true)
    }

    private val _currentTrack = MutableStateFlow<YouTubeTrack?>(null)
    val currentTrack: StateFlow<YouTubeTrack?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionSec = MutableStateFlow(0f)
    val currentPositionSec: StateFlow<Float> = _currentPositionSec.asStateFlow()

    private val _durationSec = MutableStateFlow(0f)
    val durationSec: StateFlow<Float> = _durationSec.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    var onTrackEnded: (() -> Unit)? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                _isPlaying.value = isPlayingNow
                if (isPlayingNow) {
                    startProgressTracker()
                } else {
                    progressJob?.cancel()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _isBuffering.value = true
                    }
                    Player.STATE_READY -> {
                        _isBuffering.value = false
                        val durMs = player.duration
                        if (durMs > 0) {
                            _durationSec.value = durMs / 1000f
                        }
                    }
                    Player.STATE_ENDED -> {
                        _isBuffering.value = false
                        _isPlaying.value = false
                        progressJob?.cancel()
                        onTrackEnded?.invoke()
                    }
                    Player.STATE_IDLE -> {
                        _isBuffering.value = false
                    }
                }
            }

            override fun onPlayerError(playbackError: PlaybackException) {
                android.util.Log.e("YTExoPlayerManager", "Media3 playback error: ${playbackError.message}", playbackError)
                _error.value = playbackError.message
                _isPlaying.value = false
                progressJob?.cancel()
            }
        })
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = playerScope.launch {
            while (true) {
                val posMs = player.currentPosition
                val durMs = player.duration
                _currentPositionSec.value = (posMs / 1000f).coerceAtLeast(0f)
                if (durMs > 0) {
                    _durationSec.value = durMs / 1000f
                }
                delay(250)
            }
        }
    }

    fun playTrack(track: YouTubeTrack, startPositionSec: Float = 0f) {
        _currentTrack.value = track
        _error.value = null
        _currentPositionSec.value = startPositionSec
        _durationSec.value = track.durationSeconds.toFloat()

        playerScope.launch {
            val mediaUri = resolveMediaUri(track)
            
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.author)
                .setArtworkUri(if (track.thumbnailUrl.isNotBlank()) Uri.parse(track.thumbnailUrl) else null)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(mediaUri)
                .setMediaId("yt_${track.videoId}")
                .setMediaMetadata(metadata)
                .build()

            player.setMediaItem(mediaItem, (startPositionSec * 1000).toLong())
            player.prepare()
            player.play()

            // Start foreground service for persistent background audio
            YouTubeMedia3PlaybackService.startService(context)
        }
    }

    fun play() {
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
        }
        player.play()
        YouTubeMedia3PlaybackService.startService(context)
    }

    fun pause() {
        player.pause()
    }

    fun seekTo(seconds: Float) {
        _currentPositionSec.value = seconds
        player.seekTo((seconds * 1000).toLong())
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        player.playbackParameters = PlaybackParameters(speed)
    }

    fun setVolume(volume0to100: Int) {
        val v = (volume0to100 / 100f).coerceIn(0f, 1f)
        player.volume = v
    }

    private suspend fun resolveMediaUri(track: YouTubeTrack): Uri = withContext(Dispatchers.IO) {
        // 1. Check if previously downloaded in local offline storage
        try {
            val appSpecificDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val localAppFolder = File(appSpecificDir, "NSPlayer")
            if (localAppFolder.exists()) {
                val matching = localAppFolder.listFiles()?.firstOrNull { 
                    it.name.contains(track.videoId) || (it.name.contains(track.title.take(15)) && it.length() > 5000) 
                }
                if (matching != null) {
                    return@withContext Uri.fromFile(matching)
                }
            }
        } catch (_: Exception) {}

        // 2. Direct HTTPS audio stream endpoint via public audio relay
        val streamUrl = "https://pipedproxy.kavin.rocks/videoplayback?id=${track.videoId}&itag=140"
        return@withContext Uri.parse(streamUrl)
    }

    fun release() {
        progressJob?.cancel()
        player.stop()
        player.release()
        instance = null
    }
}
