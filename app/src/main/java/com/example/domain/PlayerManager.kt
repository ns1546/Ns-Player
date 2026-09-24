package com.example.domain

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Visualizer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerManager private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var instance: PlayerManager? = null

        fun getInstance(context: Context): PlayerManager {
            return instance ?: synchronized(this) {
                instance ?: PlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)

    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
    }
    
    private val _currentSong = MutableStateFlow<LocalSong?>(null)
    val currentSong: StateFlow<LocalSong?> = _currentSong.asStateFlow()
    
    private val _currentArtworkData = MutableStateFlow<ByteArray?>(null)
    val currentArtworkData: StateFlow<ByteArray?> = _currentArtworkData.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled = _shuffleModeEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition = _playbackPosition.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _playbackPitch = MutableStateFlow(1.0f)
    val playbackPitch: StateFlow<Float> = _playbackPitch.asStateFlow()

    private val _pitchSemitones = MutableStateFlow(0)
    val pitchSemitones: StateFlow<Int> = _pitchSemitones.asStateFlow()

    // A-B Looper Studio
    private val _loopPointA = MutableStateFlow<Long?>(null)
    val loopPointA: StateFlow<Long?> = _loopPointA.asStateFlow()

    private val _loopPointB = MutableStateFlow<Long?>(null)
    val loopPointB: StateFlow<Long?> = _loopPointB.asStateFlow()

    private val _isABLoopEnabled = MutableStateFlow(false)
    val isABLoopEnabled: StateFlow<Boolean> = _isABLoopEnabled.asStateFlow()

    fun setPointA(pos: Long? = null) {
        val target = pos ?: player.currentPosition
        _loopPointA.value = target
        // If B is set and earlier than A, reset B
        if (_loopPointB.value != null && _loopPointB.value!! <= target) {
            _loopPointB.value = null
            _isABLoopEnabled.value = false
        }
    }

    fun setPointB(pos: Long? = null) {
        val target = pos ?: player.currentPosition
        val a = _loopPointA.value ?: 0L
        if (target > a) {
            _loopPointB.value = target
            _isABLoopEnabled.value = true
        }
    }

    fun toggleABLoop() {
        if (_loopPointA.value != null && _loopPointB.value != null) {
            _isABLoopEnabled.value = !_isABLoopEnabled.value
        }
    }

    fun clearABLoop() {
        _loopPointA.value = null
        _loopPointB.value = null
        _isABLoopEnabled.value = false
    }

    fun setLoopPoints(startMs: Long, endMs: Long) {
        _loopPointA.value = startMs
        _loopPointB.value = endMs
        _isABLoopEnabled.value = true
    }

    fun nudge(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs).coerceIn(0L, player.duration.coerceAtLeast(0L))
        seekTo(target)
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        updatePlaybackParameters()
    }

    fun setPlaybackPitch(pitch: Float) {
        _playbackPitch.value = pitch
        updatePlaybackParameters()
    }

    fun setPitchSemitones(semitones: Int) {
        _pitchSemitones.value = semitones
        val ratio = Math.pow(2.0, semitones / 12.0).toFloat()
        _playbackPitch.value = ratio
        updatePlaybackParameters()
    }

    // 1. Intro / Silence Skip
    private val _introSkipSeconds = MutableStateFlow(prefs.getInt("pref_intro_skip_sec", 0))
    val introSkipSeconds = _introSkipSeconds.asStateFlow()

    fun setIntroSkipSeconds(seconds: Int) {
        _introSkipSeconds.value = seconds
        prefs.edit().putInt("pref_intro_skip_sec", seconds).apply()
    }

    // 2. EarCare Hearing Guard / Volume Spike Protector
    private val _hearingGuardEnabled = MutableStateFlow(prefs.getBoolean("pref_hearing_guard", false))
    val hearingGuardEnabled = _hearingGuardEnabled.asStateFlow()

    fun setHearingGuardEnabled(enabled: Boolean) {
        _hearingGuardEnabled.value = enabled
        prefs.edit().putBoolean("pref_hearing_guard", enabled).apply()
        applyVolumeSettings()
    }

    // 3. Smart Rewind on Interruption
    private val _smartRewindSeconds = MutableStateFlow(prefs.getInt("pref_smart_rewind_sec", 5))
    val smartRewindSeconds = _smartRewindSeconds.asStateFlow()

    fun setSmartRewindSeconds(sec: Int) {
        _smartRewindSeconds.value = sec
        prefs.edit().putInt("pref_smart_rewind_sec", sec).apply()
    }

    private var lastPauseTimestamp = 0L

    // 4. Ear Fatigue Auto-Shield & Hearing Dose
    private val _earFatigueShieldEnabled = MutableStateFlow(prefs.getBoolean("pref_ear_fatigue_shield", true))
    val earFatigueShieldEnabled = _earFatigueShieldEnabled.asStateFlow()

    private val _isEarFatigueActive = MutableStateFlow(false)
    val isEarFatigueActive = _isEarFatigueActive.asStateFlow()

    private val _sessionListeningSeconds = MutableStateFlow(0L)
    val sessionListeningSeconds = _sessionListeningSeconds.asStateFlow()

    private val _sessionDosePercent = MutableStateFlow(0f)
    val sessionDosePercent = _sessionDosePercent.asStateFlow()

    fun setEarFatigueShieldEnabled(enabled: Boolean) {
        _earFatigueShieldEnabled.value = enabled
        prefs.edit().putBoolean("pref_ear_fatigue_shield", enabled).apply()
        if (!enabled) {
            _isEarFatigueActive.value = false
            applyEarFatigueRelief(false)
        }
    }

    fun resetSessionDose() {
        _sessionListeningSeconds.value = 0L
        _sessionDosePercent.value = 0f
        _isEarFatigueActive.value = false
        applyEarFatigueRelief(false)
    }

    private fun applyEarFatigueRelief(enable: Boolean) {
        try {
            val eq = equalizer ?: return
            val numBands = eq.numberOfBands.toInt()
            val minEqLevel = eq.bandLevelRange[0]
            if (enable) {
                // Tame harsh high frequencies (> 3.5kHz) by -3dB (-300 milliBels)
                for (i in 0 until numBands) {
                    val freq = eq.getCenterFreq(i.toShort()) / 1000
                    if (freq >= 3500) {
                        eq.setBandLevel(i.toShort(), (minEqLevel * 0.35f).toInt().toShort())
                    }
                }
            } else if (!_karaokeModeEnabled.value) {
                for (i in 0 until numBands) {
                    eq.setBandLevel(i.toShort(), 0)
                }
            }
        } catch (_: Exception) {}
    }

    // 5. Night Guard & Volume Spike Protection
    private val _nightGuardEnabled = MutableStateFlow(prefs.getBoolean("pref_night_guard", false))
    val nightGuardEnabled = _nightGuardEnabled.asStateFlow()

    fun setNightGuardEnabled(enabled: Boolean) {
        _nightGuardEnabled.value = enabled
        prefs.edit().putBoolean("pref_night_guard", enabled).apply()
        applyVolumeSettings()
    }

    private val _volumeSpikeProtectionEnabled = MutableStateFlow(prefs.getBoolean("pref_spike_protection", true))
    val volumeSpikeProtectionEnabled = _volumeSpikeProtectionEnabled.asStateFlow()

    fun setVolumeSpikeProtectionEnabled(enabled: Boolean) {
        _volumeSpikeProtectionEnabled.value = enabled
        prefs.edit().putBoolean("pref_spike_protection", enabled).apply()
    }

    fun getTargetMaxVolume(): Float {
        var maxVol = 1.0f
        if (_hearingGuardEnabled.value) {
            maxVol = maxVol.coerceAtMost(0.82f)
        }
        if (_nightGuardEnabled.value) {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            if (hour >= 22 || hour < 7) {
                maxVol = maxVol.coerceAtMost(0.70f)
            }
        }
        if (_isEarFatigueActive.value) {
            maxVol = maxVol.coerceAtMost(0.88f)
        }
        return maxVol
    }

    // 6. Karaoke Vocal Attenuator Mode
    private val _karaokeModeEnabled = MutableStateFlow(false)
    val karaokeModeEnabled = _karaokeModeEnabled.asStateFlow()

    fun toggleKaraokeMode() {
        val newMode = !_karaokeModeEnabled.value
        _karaokeModeEnabled.value = newMode
        applyKaraokeEqualizer(newMode)
    }

    private fun applyKaraokeEqualizer(enable: Boolean) {
        try {
            val eq = equalizer ?: return
            val numBands = eq.numberOfBands.toInt()
            val minEqLevel = eq.bandLevelRange[0]
            val maxEqLevel = eq.bandLevelRange[1]
            
            if (enable) {
                // Dip mid frequencies (1kHz - 3kHz) where human vocal presence resides
                for (i in 0 until numBands) {
                    val freq = eq.getCenterFreq(i.toShort()) / 1000
                    if (freq in 800..3500) {
                        eq.setBandLevel(i.toShort(), (minEqLevel * 0.75f).toInt().toShort())
                    } else {
                        eq.setBandLevel(i.toShort(), (maxEqLevel * 0.25f).toInt().toShort())
                    }
                }
            } else {
                for (i in 0 until numBands) {
                    eq.setBandLevel(i.toShort(), 0)
                }
            }
        } catch (_: Exception) {}
    }

    fun applyVolumeSettings() {
        if (!isSleepTimerFading) {
            player.volume = getTargetMaxVolume()
        }
    }

    private fun updatePlaybackParameters() {
        player.playbackParameters = androidx.media3.common.PlaybackParameters(_playbackSpeed.value, _playbackPitch.value)
    }

    private val _currentPlaylist = MutableStateFlow<List<LocalSong>>(emptyList())
    val currentPlaylist = _currentPlaylist.asStateFlow()
    
    var equalizer: Equalizer? = null
        private set

    var bassBoost: BassBoost? = null
        private set

    var loudnessEnhancer: LoudnessEnhancer? = null
        private set

    private val _visualizerData = MutableStateFlow<ByteArray>(ByteArray(0))
    val visualizerData: StateFlow<ByteArray> = _visualizerData.asStateFlow()

    private var visualizer: Visualizer? = null

    private var sleepTimerJob: Job? = null
    
    private val _sleepTimerRemainingMs = MutableStateFlow(0L)
    val sleepTimerRemainingMs: StateFlow<Long> = _sleepTimerRemainingMs.asStateFlow()
    
    private var isSleepTimerFading = false

    private val _loudnessEnhancerEnabled = MutableStateFlow(prefs.getBoolean("loudness_enhancer_enabled", false))
    val loudnessEnhancerEnabled = _loudnessEnhancerEnabled.asStateFlow()

    private val playerScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private fun startProgressUpdates() {
        if (progressJob?.isActive == true) return
        progressJob = playerScope.launch {
            var loopTickCount = 0
            while (player.isPlaying) {
                val position = player.currentPosition
                val duration = player.duration
                loopTickCount++

                // Every ~1 second (1000ms / 150ms ~ 7 ticks)
                if (loopTickCount >= 7) {
                    loopTickCount = 0
                    _sessionListeningSeconds.value += 1L
                    val currentVol = player.volume
                    val doseInc = if (currentVol > 0.8f) 0.035f else if (currentVol > 0.5f) 0.015f else 0.005f
                    _sessionDosePercent.value = (_sessionDosePercent.value + doseInc).coerceIn(0f, 150f)

                    if (_earFatigueShieldEnabled.value && !_isEarFatigueActive.value) {
                        if (_sessionListeningSeconds.value >= 45 * 60L || _sessionDosePercent.value >= 75f) {
                            _isEarFatigueActive.value = true
                            applyEarFatigueRelief(true)
                        }
                    }
                }

                val maxVol = getTargetMaxVolume()
                if (duration > 0 && !isSleepTimerFading) {
                    val remaining = duration - position
                    val crossfadeMs = 3000L
                    val rampUpMs = if (_volumeSpikeProtectionEnabled.value) 700L else 300L

                    if (position in 0..rampUpMs) {
                        // Anti-jump-scare micro-fade ramp-up
                        val factor = (position.toFloat() / rampUpMs).coerceIn(0.15f, 1f)
                        player.volume = maxVol * factor
                    } else if (remaining in 1..crossfadeMs) {
                        val factor = (remaining.toFloat() / crossfadeMs).coerceIn(0.1f, 1f)
                        player.volume = maxVol * factor
                    } else {
                        player.volume = maxVol
                    }
                } else if (!isSleepTimerFading) {
                    player.volume = maxVol
                }

                // A-B Looper check
                if (_isABLoopEnabled.value) {
                    val a = _loopPointA.value
                    val b = _loopPointB.value
                    if (a != null && b != null && b > a) {
                        if (position >= b) {
                            player.seekTo(a)
                            _playbackPosition.value = a
                        }
                    }
                }

                _playbackPosition.value = position
                delay(150)
            }
        }
    }

    init {
        // Restore last known song immediately on startup
        val lastUri = prefs.getString("last_song_uri", null)
        val lastTitle = prefs.getString("last_song_title", null)
        if (lastUri != null && lastTitle != null) {
            val lastArtist = prefs.getString("last_song_artist", "Unknown Artist") ?: "Unknown Artist"
            val lastAlbum = prefs.getString("last_song_album", "Unknown Album") ?: "Unknown Album"
            val lastDuration = prefs.getLong("last_song_duration", 0L)
            val lastArtUri = prefs.getString("last_song_art_uri", null)
            val lastPos = prefs.getLong("last_position", 0L)
            val restoredSong = LocalSong(
                id = -1,
                title = lastTitle,
                artist = lastArtist,
                album = lastAlbum,
                duration = lastDuration,
                uri = android.net.Uri.parse(lastUri),
                albumArtUri = if (lastArtUri != null) android.net.Uri.parse(lastArtUri) else null
            )
            _currentSong.value = restoredSong
            _playbackPosition.value = lastPos
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startProgressUpdates()
                } else {
                    progressJob?.cancel()
                    _currentSong.value?.let { saveSongToPrefs(it, player.currentPosition) }
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                if (_sleepAfterCurrentTrack.value) {
                    _sleepAfterCurrentTrack.value = false
                    _sleepTimerRemainingMs.value = 0L
                    player.pause()
                    onSleepTimerFinished?.invoke()
                    return
                }
                val currentMediaId = mediaItem?.mediaId
                val fullList = _currentPlaylist.value
                if (currentMediaId != null) {
                    val found = fullList.find { it.uri.toString() == currentMediaId }
                    if (found != null) {
                        _currentSong.value = found
                        saveSongToPrefs(found, 0L)

                        // If approaching end of ExoPlayer window in large collections, load next batch
                        if (fullList.size > 250) {
                            val currentIndexInPlayer = player.currentMediaItemIndex
                            val remainingInPlayer = player.mediaItemCount - currentIndexInPlayer - 1
                            if (remainingInPlayer < 8) {
                                val currentFullIdx = fullList.indexOfFirst { it.uri == found.uri }
                                if (currentFullIdx >= 0 && currentFullIdx + remainingInPlayer + 1 < fullList.size) {
                                    val startIdx = currentFullIdx + remainingInPlayer + 1
                                    val endIdx = (startIdx + 50).coerceAtMost(fullList.size)
                                    val nextBatch = fullList.subList(startIdx, endIdx)
                                    if (nextBatch.isNotEmpty()) {
                                        player.addMediaItems(nextBatch.map { it.toMediaItem() })
                                    }
                                }
                            }
                        }
                        return
                    }
                }
                val index = player.currentMediaItemIndex
                if (index in fullList.indices) {
                    val s = fullList[index]
                    _currentSong.value = s
                    saveSongToPrefs(s, 0L)
                }
            }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                super.onMediaMetadataChanged(mediaMetadata)
                _currentArtworkData.value = mediaMetadata.artworkData
            }
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                super.onAudioSessionIdChanged(audioSessionId)
                setupAudioEffects()
            }
            private var consecutiveErrorCount = 0
            private var lastErrorTimestamp = 0L

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                super.onPlayerError(error)
                android.util.Log.e("PlayerManager", "ExoPlayer playback error: ${error.message}", error)
                
                val now = System.currentTimeMillis()
                if (now - lastErrorTimestamp < 3000L) {
                    consecutiveErrorCount++
                } else {
                    consecutiveErrorCount = 1
                }
                lastErrorTimestamp = now

                // Auto-recover cleanly if a file is missing/moved, but break infinite loop if multiple fail
                if (consecutiveErrorCount <= 3) {
                    playerScope.launch {
                        try {
                            delay(300)
                            skipNext()
                        } catch (e: Exception) {
                            try { player.prepare() } catch (_: Exception) {}
                        }
                    }
                } else {
                    android.util.Log.w("PlayerManager", "Repeated playback errors encountered. Halting auto-advance to prevent freeze.")
                    _isPlaying.value = false
                    try { player.pause() } catch (_: Exception) {}
                }
            }
        })
    }

    private fun saveSongToPrefs(song: LocalSong, position: Long) {
        prefs.edit()
            .putString("last_song_uri", song.uri.toString())
            .putString("last_song_title", song.title)
            .putString("last_song_artist", song.artist)
            .putString("last_song_album", song.album)
            .putLong("last_song_duration", song.duration)
            .putString("last_song_art_uri", song.albumArtUri?.toString())
            .putLong("last_position", position)
            .apply()
    }
    
    private fun LocalSong.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(this.title)
            .setArtist(this.artist)
            .setArtworkUri(this.albumArtUri)
            .build()
        return MediaItem.Builder()
            .setUri(this.uri)
            .setMediaId(this.uri.toString())
            .setMediaMetadata(metadata)
            .build()
    }

    fun restoreLastState(songs: List<LocalSong>) {
        if (songs.isEmpty()) return
        val lastUriStr = prefs.getString("last_song_uri", null) ?: return
        val position = prefs.getLong("last_position", 0L)
        val song = songs.find { it.uri.toString() == lastUriStr } ?: return
        
        _currentPlaylist.value = songs
        _currentSong.value = song
        
        if (player.mediaItemCount == 0) {
            val startIndex = songs.indexOfFirst { it.uri == song.uri }.coerceAtLeast(0)
            val windowList = if (songs.size > 200) {
                val start = (startIndex - 25).coerceAtLeast(0)
                val end = (startIndex + 75).coerceAtMost(songs.size)
                songs.subList(start, end)
            } else {
                songs
            }
            val windowStartIndex = windowList.indexOfFirst { it.uri == song.uri }.coerceAtLeast(0)
            player.setMediaItems(windowList.map { it.toMediaItem() }, windowStartIndex, position)
            player.prepare()
        }
        _playbackPosition.value = position
    }
    
    fun playSong(song: LocalSong, playlist: List<LocalSong> = listOf(song)) {
        val list = if (playlist.isEmpty()) listOf(song) else playlist
        _currentPlaylist.value = list
        _currentSong.value = song
        
        if (player.mediaItemCount > 0 && player.currentMediaItem?.mediaId == song.uri.toString()) {
            if (!player.isPlaying) {
                player.play()
            }
            saveSongToPrefs(song, player.currentPosition)
            return
        }

        val startIndex = list.indexOfFirst { it.uri == song.uri }.coerceAtLeast(0)
        // Scalable windowing to prevent UI thread lockup on huge collections (thousands of songs)
        val windowList = if (list.size > 250) {
            val start = (startIndex - 30).coerceAtLeast(0)
            val end = (startIndex + 100).coerceAtMost(list.size)
            list.subList(start, end)
        } else {
            list
        }
        val windowStartIndex = windowList.indexOfFirst { it.uri == song.uri }.coerceAtLeast(0)

        val startPos = if (_introSkipSeconds.value > 0 && song.duration > 35_000L) {
            _introSkipSeconds.value * 1000L
        } else {
            0L
        }

        player.clearMediaItems()
        player.setMediaItems(windowList.map { it.toMediaItem() }, windowStartIndex, startPos)
        player.prepare()
        applyVolumeSettings()
        player.play()

        try {
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                android.content.Intent(context, PlaybackService::class.java)
            )
        } catch (_: Exception) {}
        
        saveSongToPrefs(song, startPos)
        setupAudioEffects()
    }

    fun playNext(song: LocalSong) {
        val list = _currentPlaylist.value.toMutableList()
        val cur = _currentSong.value
        val curIndex = if (cur != null) list.indexOfFirst { it.uri == cur.uri } else -1
        val insertIndex = if (curIndex >= 0) curIndex + 1 else 0
        list.add(insertIndex.coerceAtMost(list.size), song)
        _currentPlaylist.value = list

        val nextPlayerIdx = player.currentMediaItemIndex + 1
        if (nextPlayerIdx <= player.mediaItemCount) {
            player.addMediaItem(nextPlayerIdx, song.toMediaItem())
        } else {
            player.addMediaItem(song.toMediaItem())
        }
    }

    fun addToQueue(song: LocalSong) {
        val list = _currentPlaylist.value.toMutableList()
        list.add(song)
        _currentPlaylist.value = list
        player.addMediaItem(song.toMediaItem())
    }

    fun removeFromQueue(songUri: String) {
        val list = _currentPlaylist.value.toMutableList()
        val index = list.indexOfFirst { it.uri.toString() == songUri }
        if (index >= 0) {
            list.removeAt(index)
            _currentPlaylist.value = list
        }
        for (i in 0 until player.mediaItemCount) {
            if (player.getMediaItemAt(i).mediaId == songUri && i != player.currentMediaItemIndex) {
                player.removeMediaItem(i)
                break
            }
        }
    }

    fun clearUpcomingQueue() {
        val cur = _currentSong.value ?: return
        _currentPlaylist.value = listOf(cur)
        val curIdx = player.currentMediaItemIndex
        while (player.mediaItemCount > curIdx + 1) {
            player.removeMediaItem(curIdx + 1)
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val list = _currentPlaylist.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _currentPlaylist.value = list
        }
    }
    
    fun togglePlayPause() {
        if (player.mediaItemCount == 0 && _currentSong.value != null) {
            val song = _currentSong.value!!
            val playlist = if (_currentPlaylist.value.isNotEmpty()) _currentPlaylist.value else listOf(song)
            val pos = _playbackPosition.value
            _currentPlaylist.value = playlist
            val startIndex = playlist.indexOfFirst { it.uri == song.uri }.coerceAtLeast(0)
            val windowList = if (playlist.size > 250) {
                val start = (startIndex - 30).coerceAtLeast(0)
                val end = (startIndex + 100).coerceAtMost(playlist.size)
                playlist.subList(start, end)
            } else {
                playlist
            }
            val windowStartIndex = windowList.indexOfFirst { it.uri == song.uri }.coerceAtLeast(0)
            player.clearMediaItems()
            player.setMediaItems(windowList.map { it.toMediaItem() }, windowStartIndex, pos)
            player.prepare()
            applyVolumeSettings()
            player.play()
        } else {
            if (player.isPlaying) {
                lastPauseTimestamp = System.currentTimeMillis()
                player.pause()
            } else {
                // Smart rewind on resume after interruption (>10s)
                if (lastPauseTimestamp > 0L && _smartRewindSeconds.value > 0) {
                    val pausedDuration = System.currentTimeMillis() - lastPauseTimestamp
                    if (pausedDuration >= 10_000L) {
                        val rewindMs = _smartRewindSeconds.value * 1000L
                        val newPos = (player.currentPosition - rewindMs).coerceAtLeast(0L)
                        player.seekTo(newPos)
                        _playbackPosition.value = newPos
                    }
                }
                lastPauseTimestamp = 0L
                applyVolumeSettings()
                player.play()
            }
        }
    }

    fun pause() {
        if (player.isPlaying) {
            lastPauseTimestamp = System.currentTimeMillis()
            player.pause()
        }
    }

    fun toggleShuffle() {
        val newMode = !player.shuffleModeEnabled
        player.shuffleModeEnabled = newMode
        _shuffleModeEnabled.value = newMode
    }

    fun toggleRepeat() {
        val newMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = newMode
        _repeatMode.value = newMode
    }

    fun stopAndClear() {
        player.pause()
        player.seekTo(0L)
        _playbackPosition.value = 0L
        _isPlaying.value = false
        prefs.edit().putLong("last_position", 0L).apply()
    }

    fun setLoudnessEnhancerEnabled(enabled: Boolean) {
        _loudnessEnhancerEnabled.value = enabled
        prefs.edit().putBoolean("loudness_enhancer_enabled", enabled).apply()
        try {
            loudnessEnhancer?.enabled = enabled
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun skipNext() {
        val list = _currentPlaylist.value
        val cur = _currentSong.value
        if (list.isNotEmpty()) {
            val curIndex = if (cur != null) list.indexOfFirst { it.uri == cur.uri } else -1
            val nextIndex = if (curIndex >= 0 && curIndex + 1 < list.size) curIndex + 1 else 0
            val targetSong = list[nextIndex]

            // Check if next song is already loaded in ExoPlayer queue for instantaneous gapless transition
            if (player.hasNextMediaItem()) {
                val nextMediaItem = player.getMediaItemAt(player.currentMediaItemIndex + 1)
                if (nextMediaItem.mediaId == targetSong.uri.toString()) {
                    player.seekToNextMediaItem()
                    return
                }
            }
            playSong(targetSong, list)
        } else if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        }
    }

    fun skipPrevious() {
        if (player.currentPosition > 3000L) {
            seekTo(0L)
            return
        }
        val list = _currentPlaylist.value
        val cur = _currentSong.value
        if (list.isNotEmpty()) {
            val curIndex = if (cur != null) list.indexOfFirst { it.uri == cur.uri } else -1
            val prevIndex = if (curIndex > 0) curIndex - 1 else list.size - 1
            playSong(list[prevIndex], list)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            seekTo(0L)
        }
    }
    
    fun skipForward10s() {
        seekTo(player.currentPosition + 10_000)
    }

    fun skipBackward10s() {
        seekTo(maxOf(0, player.currentPosition - 10_000))
    }
    
    fun seekTo(position: Long) {
        player.seekTo(position)
        _playbackPosition.value = position
    }
    
    private var lastAudioSessionId = -1

    private fun setupAudioEffects() {
        val sessionId = player.audioSessionId
        if (sessionId <= 0 || sessionId == lastAudioSessionId) return
        lastAudioSessionId = sessionId

        try {
            equalizer?.release()
        } catch (_: Throwable) {}
        try {
            equalizer = Equalizer(0, sessionId).apply { enabled = true }
        } catch (_: Throwable) {
            equalizer = null
        }

        try {
            bassBoost?.release()
        } catch (_: Throwable) {}
        try {
            bassBoost = BassBoost(0, sessionId).apply { enabled = true }
        } catch (_: Throwable) {
            bassBoost = null
        }

        try {
            loudnessEnhancer?.release()
        } catch (_: Throwable) {}
        try {
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply { enabled = _loudnessEnhancerEnabled.value }
        } catch (_: Throwable) {
            loudnessEnhancer = null
        }
    }
    
    var onSleepTimerFinished: (() -> Unit)? = null
    var onSleepTimerFade: ((Float) -> Unit)? = null

    private val _sleepAfterCurrentTrack = MutableStateFlow(false)
    val sleepAfterCurrentTrack = _sleepAfterCurrentTrack.asStateFlow()

    fun setSleepAfterCurrentTrack(enabled: Boolean) {
        _sleepAfterCurrentTrack.value = enabled
        if (enabled) {
            sleepTimerJob?.cancel()
            _sleepTimerRemainingMs.value = 0L
        }
    }

    fun setSleepTimer(minutes: Int, stopAfterCurrent: Boolean = false) {
        sleepTimerJob?.cancel()
        _sleepTimerRemainingMs.value = 0L
        _sleepAfterCurrentTrack.value = stopAfterCurrent
        player.volume = 1.0f // ensure volume is normal
        if (stopAfterCurrent) {
            return
        }
        if (minutes > 0) {
            sleepTimerJob = playerScope.launch {
                var totalDelayMs = minutes * 60 * 1000L
                val fadeOutMs = 10000L // 10 seconds fade out
                
                while (totalDelayMs > fadeOutMs) {
                    _sleepTimerRemainingMs.value = totalDelayMs
                    delay(1000L)
                    totalDelayMs -= 1000L
                }
                
                // Fade out loop
                isSleepTimerFading = true
                val steps = (fadeOutMs / 1000).toInt()
                for (i in steps downTo 1) {
                    _sleepTimerRemainingMs.value = (i * 1000).toLong()
                    val vol = i.toFloat() / steps
                    if (player.isPlaying) {
                        player.volume = vol
                    }
                    onSleepTimerFade?.invoke(vol)
                    delay(1000L)
                }
                
                _sleepTimerRemainingMs.value = 0L
                player.pause()
                player.volume = 1.0f // reset for next time
                isSleepTimerFading = false
                onSleepTimerFinished?.invoke()
            }
        }
    }
    
    fun release() {
        try { playerScope.cancel() } catch (_: Throwable) {}
        progressJob?.cancel()
        sleepTimerJob?.cancel()
        try { equalizer?.release() } catch (_: Throwable) {}
        try { bassBoost?.release() } catch (_: Throwable) {}
        try { loudnessEnhancer?.release() } catch (_: Throwable) {}
        try { visualizer?.release() } catch (_: Throwable) {}
        equalizer = null
        bassBoost = null
        loudnessEnhancer = null
        visualizer = null
        try { player.release() } catch (_: Throwable) {}
    }
}
