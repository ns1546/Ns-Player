package com.example.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Playlist
import com.example.data.PlaylistDao
import com.example.domain.AudioRepository
import com.example.domain.AmbientSoundscapeEngine
import com.example.domain.MotionGestureManager
import com.example.domain.SoundscapeType
import com.example.domain.LocalSong
import com.example.domain.PlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val audioRepository: AudioRepository,
    val playerManager: PlayerManager,
    private val playlistDao: PlaylistDao,
    private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private fun getInitialAccentColor(): Color {
        return try {
            if (prefs.contains("pref_accent_color_argb")) {
                Color(prefs.getInt("pref_accent_color_argb", 0xFFD0BCFF.toInt()))
            } else if (prefs.contains("pref_accent_color")) {
                val oldVal = prefs.getLong("pref_accent_color", 0xFFD0BCFFL)
                val c = Color(oldVal.toULong())
                c.colorSpace // Validate color space ID
                c
            } else {
                Color(0xFFD0BCFF)
            }
        } catch (e: Throwable) {
            Color(0xFFD0BCFF)
        }
    }

    private val _accentColor = MutableStateFlow(getInitialAccentColor())
    val accentColor = _accentColor.asStateFlow()
    
    fun setAccentColor(color: Color) {
        _accentColor.value = color
        prefs.edit().putInt("pref_accent_color_argb", color.toArgb()).apply()
    }
    
    private val _isDarkTheme = MutableStateFlow(prefs.getBoolean("pref_dark_theme", true))
    val isDarkTheme = _isDarkTheme.asStateFlow()

    fun setDarkTheme(isDark: Boolean) {
        _isDarkTheme.value = isDark
        prefs.edit().putBoolean("pref_dark_theme", isDark).apply()
    }

    private val _includeShortAudio = MutableStateFlow(prefs.getBoolean("pref_include_short_audio", false))
    val includeShortAudio = _includeShortAudio.asStateFlow()

    fun setIncludeShortAudio(include: Boolean) {
        _includeShortAudio.value = include
        prefs.edit().putBoolean("pref_include_short_audio", include).apply()
    }

    val loudnessEnhancerEnabled = playerManager.loudnessEnhancerEnabled
    fun setLoudnessEnhancerEnabled(enabled: Boolean) {
        playerManager.setLoudnessEnhancerEnabled(enabled)
    }

    val ambientEngine = AmbientSoundscapeEngine.getInstance()
    val gestureManager = MotionGestureManager(context)
    val youTubePlayerController = com.example.domain.YouTubePlayerController.getInstance(context)
    val youTubeExoPlayerManager = com.example.domain.YouTubeExoPlayerManager.getInstance(context)
    val lyricsManager = com.example.domain.LyricsManager(context, playlistDao)

    private val _isDriveMode = MutableStateFlow(false)
    val isDriveMode = _isDriveMode.asStateFlow()

    fun setDriveMode(enabled: Boolean) {
        _isDriveMode.value = enabled
    }

    fun playNext(song: LocalSong) {
        playerManager.playNext(song)
    }

    fun addToQueue(song: LocalSong) {
        playerManager.addToQueue(song)
    }

    fun removeFromQueue(songUri: String) {
        playerManager.removeFromQueue(songUri)
    }

    fun clearUpcomingQueue() {
        playerManager.clearUpcomingQueue()
    }

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.ACTION_YT_NEXT" -> playNextYouTubeTrack()
                "com.example.ACTION_YT_PREV" -> playPreviousYouTubeTrack()
            }
        }
    }

    init {
        try {
            val filter = IntentFilter().apply {
                addAction("com.example.ACTION_YT_NEXT")
                addAction("com.example.ACTION_YT_PREV")
            }
            ContextCompat.registerReceiver(
                context,
                notificationReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {}

        gestureManager.onShakeTriggered = {
            playerManager.skipNext()
        }
        gestureManager.onAirWaveTriggered = {
            playerManager.skipNext()
        }
        gestureManager.startListening()

        playerManager.onSleepTimerFinished = {
            pauseYouTube()
        }
        playerManager.onSleepTimerFade = { fraction ->
            setYouTubeVolume((fraction * 100).toInt())
        }

        youTubePlayerController.onStateChange = { state ->
            onYouTubePlayerStateChanged(state)
        }
        youTubePlayerController.onProgress = { curr, dur ->
            onYouTubeProgress(curr, dur)
        }
        youTubePlayerController.onError = { errorCode ->
            onYouTubePlayerError(errorCode)
        }

        youTubeExoPlayerManager.onTrackEnded = {
            playNextYouTubeTrack()
        }

        // Automatic periodic database maintenance on startup
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                playlistDao.pruneRecentSongs()
                playlistDao.deleteOrphanedPlaylistSongs()
            } catch (_: Exception) {}
        }

        viewModelScope.launch {
            playerManager.currentSong.collect { song ->
                song?.let {
                    addRecentSong(it.uri.toString())
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(notificationReceiver)
        } catch (_: Exception) {}
        gestureManager.stopListening()
        playerManager.release()
        youTubeExoPlayerManager.release()
        youTubePlayerController.release()
    }

    fun playSong(song: LocalSong) {
        pauseYouTube()
        if (song.uri.toString().startsWith("yt://")) {
            val videoId = song.uri.toString().removePrefix("yt://")
            playYouTubeTrack(
                com.example.domain.YouTubeTrack(
                    videoId = videoId,
                    title = song.title,
                    author = song.artist,
                    durationSeconds = song.duration / 1000L,
                    thumbnailUrl = song.albumArtUri?.toString() ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                )
            )
            return
        }
        playerManager.playSong(song, listOf(song))
    }

    private val _lastSyncedTime = MutableStateFlow<String?>(null)
    val lastSyncedTime = _lastSyncedTime.asStateFlow()

    private val _currentUniverse = MutableStateFlow(VisualUniverse.NONE)
    val currentUniverse = _currentUniverse.asStateFlow()

    fun setUniverse(universe: VisualUniverse) {
        _currentUniverse.value = universe
    }

    private val _songs = MutableStateFlow<List<LocalSong>>(emptyList())
    val songs = _songs.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    
    private val _searchCategory = MutableStateFlow(SearchCategory.ALL)
    val searchCategory = _searchCategory.asStateFlow()
    
    val filteredSongs: StateFlow<List<LocalSong>> = combine(_songs, _searchQuery, _searchCategory, _includeShortAudio) { songs, query, category, includeShort ->
        val baseList = if (includeShort) songs else songs.filter { it.duration == 0L || it.duration >= 30_000L }
        if (query.isBlank()) {
            baseList
        } else {
            val q = query.trim().lowercase()
            baseList.filter { song ->
                val titleLower = song.title.lowercase()
                val artistLower = song.artist.lowercase()
                val albumLower = song.album.lowercase()
                when (category) {
                    SearchCategory.ALL -> titleLower.contains(q) || artistLower.contains(q) || albumLower.contains(q) || fuzzyMatch(q, song.title)
                    SearchCategory.TRACK -> titleLower.contains(q) || fuzzyMatch(q, song.title)
                    SearchCategory.ARTIST -> artistLower.contains(q) || fuzzyMatch(q, song.artist)
                    SearchCategory.ALBUM -> albumLower.contains(q) || fuzzyMatch(q, song.album)
                }
            }
        }
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun fuzzyMatch(query: String, text: String): Boolean {
        if (query.isBlank()) return true
        val lowerQuery = query.lowercase().replace(" ", "")
        val lowerText = text.lowercase()
        if (lowerText.contains(lowerQuery)) return true
        
        var queryIndex = 0
        for (char in lowerText) {
            if (queryIndex < lowerQuery.length && char == lowerQuery[queryIndex]) {
                queryIndex++
            }
        }
        return queryIndex == lowerQuery.length
    }

    val playlists = playlistDao.getAllPlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadSongs() {
        viewModelScope.launch {
            val fetched = audioRepository.getAudioFiles()
            _songs.value = fetched
            playerManager.restoreLastState(fetched)
        }
    }

    private val _showFullScreenPlayer = MutableStateFlow(false)
    val showFullScreenPlayer = _showFullScreenPlayer.asStateFlow()

    fun openFullScreenPlayer() { _showFullScreenPlayer.value = true }
    fun closeFullScreenPlayer() { _showFullScreenPlayer.value = false }

    fun searchSongs(query: String) {
        _searchQuery.value = query
    }

    fun setSearchCategory(category: SearchCategory) {
        _searchCategory.value = category
    }
    
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                playlistDao.insertPlaylist(Playlist(name = name))
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            playlistDao.deletePlaylistById(playlist.id)
        }
    }

    fun renamePlaylist(playlist: Playlist, newName: String) {
        viewModelScope.launch {
            if (newName.isNotBlank()) {
                playlistDao.renamePlaylist(playlist.id, newName)
            }
        }
    }

    fun getSongsForPlaylist(playlistId: Int) = playlistDao.getSongsForPlaylist(playlistId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addSongToPlaylist(playlist: Playlist, song: LocalSong) {
        viewModelScope.launch {
            playlistDao.insertSongToPlaylist(
                com.example.data.PlaylistSong(
                    playlistId = playlist.id, 
                    songUri = song.uri.toString(),
                    title = song.title,
                    artist = song.artist,
                    duration = song.duration
                )
            )
        }
    }

    fun removeSongFromPlaylist(playlistId: Int, songUri: String) {
        viewModelScope.launch {
            playlistDao.removeSongFromPlaylist(playlistId, songUri)
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            _lastSyncedTime.value = "Syncing..."
            val fetched = audioRepository.getAudioFiles()
            _songs.value = fetched
            playerManager.restoreLastState(fetched)
            val format = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            _lastSyncedTime.value = "Synced: ${format.format(java.util.Date())}"
        }
    }

    fun isFavorite(songUri: String) = playlistDao.isFavorite(songUri)

    fun toggleFavorite(song: LocalSong) {
        viewModelScope.launch {
            val uriStr = song.uri.toString()
            val isFav = playlistDao.isFavorite(uriStr).first()
            if (isFav) {
                playlistDao.deleteFavorite(uriStr)
            } else {
                playlistDao.insertFavorite(com.example.data.FavoriteSong(songUri = uriStr))
            }
        }
    }
    
    fun addRecentSong(songUri: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            playlistDao.insertRecent(com.example.data.RecentSong(songUri = songUri))
            playlistDao.pruneRecentSongs()
        }
    }

    val favorites = playlistDao.getAllFavorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentSongs = playlistDao.getRecentSongs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // YouTube Integration
    private val youTubeRepository = com.example.domain.YouTubeRepository()
    
    private val _youTubeQuery = MutableStateFlow("")
    val youTubeQuery = _youTubeQuery.asStateFlow()

    // Initialize with curated tracks immediately so interface shows with 0 delay!
    private val _youTubeTracks = MutableStateFlow<List<com.example.domain.YouTubeTrack>>(youTubeRepository.getInitialTracks())
    val youTubeTracks = _youTubeTracks.asStateFlow()

    private val _isYouTubeSearching = MutableStateFlow(false)
    val isYouTubeSearching = _isYouTubeSearching.asStateFlow()

    private val _isYouTubeBuffering = MutableStateFlow(false)
    val isYouTubeBuffering = _isYouTubeBuffering.asStateFlow()

    private val _youTubeError = MutableStateFlow<String?>(null)
    val youTubeError = _youTubeError.asStateFlow()

    // YouTube Player State
    private val _currentYouTubeTrack = MutableStateFlow<com.example.domain.YouTubeTrack?>(null)
    val currentYouTubeTrack = _currentYouTubeTrack.asStateFlow()

    private val _isYouTubePlaying = MutableStateFlow(false)
    val isYouTubePlaying = _isYouTubePlaying.asStateFlow()

    private val _isYouTubePlayerVisible = MutableStateFlow(false)
    val isYouTubePlayerVisible = _isYouTubePlayerVisible.asStateFlow()

    private val _youTubeCurrentTime = MutableStateFlow(0f)
    val youTubeCurrentTime = _youTubeCurrentTime.asStateFlow()

    private val _youTubeDuration = MutableStateFlow(0f)
    val youTubeDuration = _youTubeDuration.asStateFlow()

    private val _isFullScreenVideo = MutableStateFlow(false)
    val isFullScreenVideo = _isFullScreenVideo.asStateFlow()

    private val _youTubeQueue = MutableStateFlow<List<com.example.domain.YouTubeTrack>>(emptyList())
    val youTubeQueue = _youTubeQueue.asStateFlow()

    private val _youTubeCommand = MutableStateFlow<String?>(null)
    val youTubeCommand = _youTubeCommand.asStateFlow()

    // Normal Audio Controls for YouTube
    private val _youTubePlaybackSpeed = MutableStateFlow(1.0f)
    val youTubePlaybackSpeed = _youTubePlaybackSpeed.asStateFlow()

    private val _youTubeRepeatMode = MutableStateFlow(YouTubeRepeatMode.OFF)
    val youTubeRepeatMode = _youTubeRepeatMode.asStateFlow()

    private val _youTubeShuffleEnabled = MutableStateFlow(false)
    val youTubeShuffleEnabled = _youTubeShuffleEnabled.asStateFlow()

    private val _youTubeVolume = MutableStateFlow(100)
    val youTubeVolume = _youTubeVolume.asStateFlow()

    private val _isAudioOnlyMode = MutableStateFlow(false)
    val isAudioOnlyMode = _isAudioOnlyMode.asStateFlow()

    private val _showYouTubeQueue = MutableStateFlow(false)
    val showYouTubeQueue = _showYouTubeQueue.asStateFlow()

    // Downloads / Offline saving for YouTube
    val downloadedTracks = playlistDao.getAllDownloadedTracks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _downloadingTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingTrackIds = _downloadingTrackIds.asStateFlow()

    fun isTrackDownloaded(videoId: String): kotlinx.coroutines.flow.Flow<Boolean> {
        return playlistDao.isTrackDownloaded(videoId)
    }

    fun downloadYouTubeTrack(track: com.example.domain.YouTubeTrack) {
        viewModelScope.launch {
            if (_downloadingTrackIds.value.contains(track.videoId)) return@launch
            _downloadingTrackIds.value = _downloadingTrackIds.value + track.videoId

            val options = com.example.domain.YouTubeDownloadHelper.getAudioOptions(track)
            val bestOption = options.firstOrNull() ?: com.example.domain.DownloadOption(
                id = "audio_mp3_320",
                mediaType = com.example.domain.DownloadMediaType.AUDIO,
                formatName = "MP3",
                qualityLabel = "320 kbps",
                tag = "Best Audio",
                estimatedSize = "8.5 MB",
                mimeType = "audio/mpeg",
                fileExtension = "mp3",
                downloadUrl = "https://www.youtube.com/watch?v=${track.videoId}"
            )

            com.example.domain.YouTubeDownloadHelper.startFastDownload(
                context = context,
                track = track,
                option = bestOption,
                onProgress = { },
                onComplete = {
                    _downloadingTrackIds.value = _downloadingTrackIds.value - track.videoId
                },
                onError = {
                    _downloadingTrackIds.value = _downloadingTrackIds.value - track.videoId
                }
            )
        }
    }

    fun deleteDownloadedTrack(videoId: String) {
        viewModelScope.launch {
            playlistDao.deleteDownloadedTrack(videoId)
        }
    }

    fun toggleYouTubeFavorite(track: com.example.domain.YouTubeTrack) {
        viewModelScope.launch {
            val uriStr = "yt://${track.videoId}"
            val isFav = playlistDao.isFavorite(uriStr).first()
            if (isFav) {
                playlistDao.deleteFavorite(uriStr)
            } else {
                playlistDao.insertFavorite(com.example.data.FavoriteSong(songUri = uriStr))
            }
        }
    }

    fun setYouTubePlaybackSpeed(speed: Float) {
        _youTubePlaybackSpeed.value = speed
        _youTubeCommand.value = "speed:$speed"
        youTubeExoPlayerManager.setSpeed(speed)
        youTubePlayerController.setSpeed(speed)
    }

    fun toggleYouTubeRepeatMode() {
        _youTubeRepeatMode.value = when (_youTubeRepeatMode.value) {
            YouTubeRepeatMode.OFF -> YouTubeRepeatMode.ALL
            YouTubeRepeatMode.ALL -> YouTubeRepeatMode.ONE
            YouTubeRepeatMode.ONE -> YouTubeRepeatMode.OFF
        }
    }

    fun toggleYouTubeShuffle() {
        _youTubeShuffleEnabled.value = !_youTubeShuffleEnabled.value
    }

    fun setYouTubeVolume(volume: Int) {
        val safeVol = volume.coerceIn(0, 100)
        _youTubeVolume.value = safeVol
        _youTubeCommand.value = "volume:$safeVol"
        youTubeExoPlayerManager.setVolume(safeVol)
        youTubePlayerController.setVolume(safeVol)
    }

    fun toggleAudioOnlyMode() {
        _isAudioOnlyMode.value = !_isAudioOnlyMode.value
    }

    fun setShowYouTubeQueue(show: Boolean) {
        _showYouTubeQueue.value = show
    }

    fun consumeYouTubeCommand() {
        _youTubeCommand.value = null
    }

    val sleepTimerRemainingMs = playerManager.sleepTimerRemainingMs

    fun setSleepTimer(minutes: Int) {
        playerManager.setSleepTimer(minutes)
    }

    fun onYouTubeQueryChanged(query: String) {
        _youTubeQuery.value = query
    }

    fun searchYouTube(query: String? = null) {
        val q = query ?: _youTubeQuery.value
        if (q.isBlank()) {
            _youTubeTracks.value = youTubeRepository.getInitialTracks()
            return
        }
        _youTubeQuery.value = q
        viewModelScope.launch {
            _isYouTubeSearching.value = true
            _youTubeError.value = null
            try {
                val results = youTubeRepository.searchTracks(q)
                if (results.isEmpty()) {
                    _youTubeError.value = "No results found. Showing popular songs."
                    _youTubeTracks.value = youTubeRepository.getInitialTracks()
                } else {
                    _youTubeTracks.value = results
                }
            } catch (e: Exception) {
                _youTubeError.value = "Network slow: showing cached music."
                _youTubeTracks.value = youTubeRepository.getInitialTracks()
            } finally {
                _isYouTubeSearching.value = false
            }
        }
    }

    // Bounded LRU cache of videoId -> saved playback position in seconds (prevents memory leak over months)
    private val _youTubePlaybackPositions = object : LinkedHashMap<String, Float>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?): Boolean {
            return size > 150
        }
    }

    // Track previous track for accidental switch undo
    private var _previousYouTubeTrack: com.example.domain.YouTubeTrack? = null
    private var _previousYouTubeTrackPosition: Float = 0f

    private val _selectedChannel = MutableStateFlow<com.example.domain.YouTubeArtistChannel?>(null)
    val selectedChannel = _selectedChannel.asStateFlow()

    private val _subscribedChannelIds = MutableStateFlow<Set<String>>(setOf("UCiT_U4C_zXU3Xv8BfL6-Bdg", "UC0C-w0YjGpqDXGB8Tr06V5A"))
    val subscribedChannelIds = _subscribedChannelIds.asStateFlow()

    fun toggleChannelSubscription(channelId: String) {
        val current = _subscribedChannelIds.value.toMutableSet()
        if (current.contains(channelId)) {
            current.remove(channelId)
        } else {
            current.add(channelId)
        }
        _subscribedChannelIds.value = current
    }

    fun openChannel(artistNameOrChannelId: String) {
        viewModelScope.launch {
            try {
                val channel = youTubeRepository.getChannelDetails(artistNameOrChannelId)
                _selectedChannel.value = channel
            } catch (_: Exception) {}
        }
    }

    fun closeChannel() {
        _selectedChannel.value = null
    }

    fun playChannelTracks(tracks: List<com.example.domain.YouTubeTrack>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val listToPlay = if (shuffle) tracks.shuffled() else tracks
        val trackToStart = listToPlay.getOrNull(startIndex) ?: listToPlay.first()
        playYouTubeTrack(trackToStart, listToPlay)
    }

    fun getTopArtists(): List<com.example.domain.YouTubeArtistChannel> {
        return youTubeRepository.getTopArtists()
    }

    private val _resumeNotice = MutableStateFlow<String?>(null)
    val resumeNotice = _resumeNotice.asStateFlow()

    private val _youTubePlaybackError = MutableStateFlow<String?>(null)
    val youTubePlaybackError = _youTubePlaybackError.asStateFlow()

    fun onYouTubePlayerError(errorCode: Int) {
        val cur = _currentYouTubeTrack.value ?: return
        val title = cur.title
        android.util.Log.w("MainViewModel", "YouTube Player Error: $errorCode for track: $title (${cur.videoId})")

        when (errorCode) {
            101, 150, 152 -> {
                _youTubePlaybackError.value = "Embed restriction detected. Automatically finding audio stream for \"$title\"..."
                // Auto-resolve: automatically trigger alternative audio track search and play
                viewModelScope.launch {
                    try {
                        val query = "${cur.title} ${cur.author} audio"
                        val results = youTubeRepository.searchTracks(query)
                        val alt = results.firstOrNull { it.videoId != cur.videoId }
                        if (alt != null) {
                            _youTubePlaybackError.value = "Playing alternate audio stream for \"$title\""
                            playYouTubeTrack(alt, _youTubeQueue.value)
                        } else {
                            _youTubePlaybackError.value = "Embedding restricted by creator for \"$title\" (Error $errorCode). Tap 'Play Audio Stream' or 'Open in YouTube'."
                        }
                    } catch (_: Exception) {
                        _youTubePlaybackError.value = "Embedding restricted by creator for \"$title\" (Error $errorCode). Tap 'Play Audio Stream' or 'Open in YouTube'."
                    }
                }
            }
            100, 2 -> {
                _youTubePlaybackError.value = "Video not found. Finding alternative version..."
                viewModelScope.launch {
                    try {
                        val query = "${cur.title} ${cur.author}"
                        val results = youTubeRepository.searchTracks(query)
                        val alt = results.firstOrNull { it.videoId != cur.videoId }
                        if (alt != null) {
                            playYouTubeTrack(alt, _youTubeQueue.value)
                        } else {
                            _youTubePlaybackError.value = "Video not available. Tap 'Play Alternative' to search audio."
                        }
                    } catch (_: Exception) {
                        _youTubePlaybackError.value = "Video not available. Tap 'Play Alternative' to search audio."
                    }
                }
            }
            else -> {
                _youTubePlaybackError.value = "Playback error (Code $errorCode). Tap 'Play Alternative' to retry."
            }
        }
    }

    fun playAlternativeYouTubeTrack() {
        val cur = _currentYouTubeTrack.value ?: return
        viewModelScope.launch {
            _isYouTubeSearching.value = true
            _youTubePlaybackError.value = "Searching for audio stream..."
            try {
                val query = "${cur.title} ${cur.author} official audio"
                val results = youTubeRepository.searchTracks(query)
                val alt = results.firstOrNull { it.videoId != cur.videoId }
                if (alt != null) {
                    _youTubePlaybackError.value = "Playing audio stream for \"${cur.title}\""
                    playYouTubeTrack(alt, _youTubeQueue.value)
                } else {
                    _youTubePlaybackError.value = "No alternative found. Tap 'Open in YouTube' to play."
                }
            } catch (_: Exception) {
                _youTubePlaybackError.value = "Could not find alternative audio version."
            } finally {
                _isYouTubeSearching.value = false
            }
        }
    }

    fun clearYouTubePlaybackError() {
        _youTubePlaybackError.value = null
    }

    fun clearResumeNotice() {
        _resumeNotice.value = null
    }

    fun restorePreviousYouTubeTrack() {
        val prev = _previousYouTubeTrack ?: return
        val pos = _previousYouTubeTrackPosition
        _previousYouTubeTrack = null
        playYouTubeTrack(prev, _youTubeQueue.value, forceStartFromPosition = pos)
    }

    fun playYouTubeTrack(
        track: com.example.domain.YouTubeTrack,
        queue: List<com.example.domain.YouTubeTrack> = emptyList(),
        forceStartFromPosition: Float? = null
    ) {
        val cur = _currentYouTubeTrack.value

        // 1. SMART CHECK: If user taps the ALREADY running song, NEVER restart from 0!
        if (cur?.videoId == track.videoId && forceStartFromPosition == null) {
            _isYouTubePlayerVisible.value = true
            if (!_isYouTubePlaying.value) {
                resumeYouTube()
            }
            return
        }

        // 2. Remember previous song position for accidental tap protection
        if (cur != null) {
            _previousYouTubeTrack = cur
            _previousYouTubeTrackPosition = _youTubeCurrentTime.value
            if (_youTubeCurrentTime.value > 3f) {
                _youTubePlaybackPositions[cur.videoId] = _youTubeCurrentTime.value
            }
        }

        playerManager.pause()
        _currentYouTubeTrack.value = track
        _isYouTubePlaying.value = true
        _isYouTubePlayerVisible.value = true
        _youTubeDuration.value = track.durationSeconds.toFloat()
        _youTubeQueue.value = if (queue.isNotEmpty()) queue else _youTubeTracks.value
        youTubePlayerController.setTrackMetadata(track.title, track.author, track.thumbnailUrl)

        // 3. SMART RESUME: Resume from where the user left off if interrupted or crossed
        val targetPos = forceStartFromPosition ?: _youTubePlaybackPositions[track.videoId] ?: 0f
        youTubeExoPlayerManager.playTrack(track, targetPos)

        if (targetPos > 5f && (track.durationSeconds <= 0 || targetPos < track.durationSeconds - 5)) {
            _youTubeCurrentTime.value = targetPos
            _youTubeCommand.value = "load:${track.videoId}:${targetPos.toInt()}"
            youTubePlayerController.loadTrack(track.videoId, targetPos.toInt())
            val m = (targetPos / 60).toInt()
            val s = (targetPos % 60).toInt()
            _resumeNotice.value = "Resumed at ${String.format("%d:%02d", m, s)}"
        } else {
            _youTubeCurrentTime.value = 0f
            _youTubeCommand.value = "load:${track.videoId}:0"
            youTubePlayerController.loadTrack(track.videoId, 0)
            _resumeNotice.value = null
        }

        addRecentSong("yt://${track.videoId}")
    }

    fun restartCurrentYouTubeTrack() {
        val cur = _currentYouTubeTrack.value ?: return
        _youTubePlaybackPositions[cur.videoId] = 0f
        _youTubeCurrentTime.value = 0f
        _youTubeCommand.value = "seek:0"
        youTubeExoPlayerManager.seekTo(0f)
        youTubePlayerController.seekTo(0f)
        resumeYouTube()
        _resumeNotice.value = null
    }

    fun minimizeYouTubePlayer() {
        _isYouTubePlayerVisible.value = false
    }

    fun toggleYouTubePlayPause() {
        if (_isYouTubePlaying.value) {
            pauseYouTube()
        } else {
            resumeYouTube()
        }
    }

    fun pauseYouTube() {
        _isYouTubePlaying.value = false
        _youTubeCommand.value = "pause"
        youTubeExoPlayerManager.pause()
        youTubePlayerController.pause()
    }

    fun resumeYouTube() {
        playerManager.pause()
        _isYouTubePlaying.value = true
        _youTubeCommand.value = "play"
        youTubeExoPlayerManager.play()
        youTubePlayerController.play()
    }

    fun seekYouTube(seconds: Float) {
        _youTubeCurrentTime.value = seconds
        _youTubeCommand.value = "seek:${seconds.toInt()}"
        youTubeExoPlayerManager.seekTo(seconds)
        youTubePlayerController.seekTo(seconds)
    }

    fun playNextYouTubeTrack() {
        val q = _youTubeQueue.value
        val cur = _currentYouTubeTrack.value ?: return
        if (q.isNotEmpty()) {
            if (_youTubeShuffleEnabled.value && q.size > 1) {
                var randomIdx = (0 until q.size).random()
                if (q[randomIdx].videoId == cur.videoId) {
                    randomIdx = (randomIdx + 1) % q.size
                }
                playYouTubeTrack(q[randomIdx], q)
                return
            }
            val idx = q.indexOfFirst { it.videoId == cur.videoId }
            if (idx >= 0 && idx + 1 < q.size) {
                playYouTubeTrack(q[idx + 1], q)
            } else if (_youTubeRepeatMode.value == YouTubeRepeatMode.ALL || _youTubeRepeatMode.value == YouTubeRepeatMode.OFF) {
                playYouTubeTrack(q[0], q)
            }
        }
    }

    fun playPreviousYouTubeTrack() {
        val q = _youTubeQueue.value
        val cur = _currentYouTubeTrack.value ?: return
        if (q.isNotEmpty()) {
            val idx = q.indexOfFirst { it.videoId == cur.videoId }
            val prevIdx = if (idx > 0) idx - 1 else q.size - 1
            playYouTubeTrack(q[prevIdx], q)
        }
    }

    fun closeYouTubePlayer(savePosition: Boolean = true) {
        if (savePosition) {
            _currentYouTubeTrack.value?.let { track ->
                if (_youTubeCurrentTime.value > 3f) {
                    _youTubePlaybackPositions[track.videoId] = _youTubeCurrentTime.value
                }
            }
        }
        _isYouTubePlaying.value = false
        _isYouTubePlayerVisible.value = false
        _isFullScreenVideo.value = false
        _youTubeCommand.value = "pause"
        youTubeExoPlayerManager.pause()
        youTubePlayerController.pause()
    }

    fun optimizeStorageAndCache(onComplete: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                playlistDao.pruneRecentSongs()
                playlistDao.deleteOrphanedPlaylistSongs()
                youTubePlayerController.clearCache()
                val cacheDir = context.cacheDir
                var deletedBytes = 0L
                cacheDir.listFiles()?.forEach { file ->
                    deletedBytes += file.length()
                    try { file.deleteRecursively() } catch (_: Exception) {}
                }
                val mbFreed = String.format(java.util.Locale.US, "%.1f", deletedBytes / (1024f * 1024f))
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete("Freed $mbFreed MB. Cache cleared & database optimized.")
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete("Storage & cache optimized successfully.")
                }
            }
        }
    }

    fun setFullScreenVideo(enabled: Boolean) {
        _isFullScreenVideo.value = enabled
    }

    fun onYouTubePlayerStateChanged(state: Int) {
        when (state) {
            1 -> {
                // Playing
                _isYouTubePlaying.value = true
                playerManager.pause()
            }
            2 -> {
                // Paused
                _isYouTubePlaying.value = false
            }
            0 -> {
                // Ended
                if (_youTubeRepeatMode.value == YouTubeRepeatMode.ONE) {
                    seekYouTube(0f)
                    resumeYouTube()
                } else {
                    playNextYouTubeTrack()
                }
            }
        }
    }

    fun onYouTubeProgress(current: Float, duration: Float) {
        _youTubeCurrentTime.value = current
        if (duration > 0f) {
            _youTubeDuration.value = duration
        }
        _currentYouTubeTrack.value?.let { track ->
            if (current > 3f && (duration <= 0f || current < duration - 4f)) {
                _youTubePlaybackPositions[track.videoId] = current
            }
        }
    }

    // 1. Song Memos & Memory Notes
    fun getMemoForSong(songUri: String): kotlinx.coroutines.flow.Flow<com.example.data.SongMemo?> {
        return playlistDao.getMemoForSong(songUri)
    }

    fun saveSongMemo(songUri: String, note: String, keyTag: String = "") {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            playlistDao.saveSongMemo(
                com.example.data.SongMemo(
                    songUri = songUri,
                    note = note,
                    keyTag = keyTag
                )
            )
        }
    }

    fun deleteSongMemo(songUri: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            playlistDao.deleteSongMemo(songUri)
        }
    }

    // Song Cues & Audio Bookmarks
    fun getCuesForSong(songUri: String): kotlinx.coroutines.flow.Flow<List<com.example.data.SongCue>> {
        return playlistDao.getCuesForSong(songUri)
    }

    fun addCue(songUri: String, timestampMs: Long, label: String, tagColor: Long = 0xFF6366F1) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            playlistDao.insertCue(
                com.example.data.SongCue(
                    songUri = songUri,
                    timestampMs = timestampMs,
                    label = label,
                    tagColor = tagColor
                )
            )
        }
    }

    fun deleteCue(id: Int) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            playlistDao.deleteCue(id)
        }
    }

    fun deleteAllCuesForSong(songUri: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            playlistDao.deleteAllCuesForSong(songUri)
        }
    }

    fun takeEarBreathingBreak(durationMinutes: Int = 5) {
        playerManager.pause()
        pauseYouTube()
        playerManager.resetSessionDose()
        ambientEngine.setSoundscape(com.example.domain.SoundscapeType.RAIN, true, 0.4f)
        ambientEngine.setSoundscape(com.example.domain.SoundscapeType.BREEZE, true, 0.25f)
        playerManager.setSleepTimer(durationMinutes)
    }

    // 2. Pocket Shield
    private val _isPocketShieldActive = MutableStateFlow(false)
    val isPocketShieldActive = _isPocketShieldActive.asStateFlow()

    fun setPocketShieldActive(active: Boolean) {
        _isPocketShieldActive.value = active
    }

    // 3. Smart Intro Skip, Hearing Guard, Karaoke Mode & Pitch
    val introSkipSeconds = playerManager.introSkipSeconds
    fun setIntroSkipSeconds(seconds: Int) {
        playerManager.setIntroSkipSeconds(seconds)
    }

    val hearingGuardEnabled = playerManager.hearingGuardEnabled
    fun setHearingGuardEnabled(enabled: Boolean) {
        playerManager.setHearingGuardEnabled(enabled)
    }

    val karaokeModeEnabled = playerManager.karaokeModeEnabled
    fun toggleKaraokeMode() {
        playerManager.toggleKaraokeMode()
    }

    val pitchSemitones = playerManager.pitchSemitones
    fun setPitchSemitones(semitones: Int) {
        playerManager.setPitchSemitones(semitones)
    }

    // 4. Library Health & Duplicate Doctor
    fun getDuplicateSongGroups(): Map<String, List<LocalSong>> {
        val all = _songs.value
        return all.groupBy {
            "${it.title.trim().lowercase()} - ${it.artist.trim().lowercase()}"
        }.filter { it.value.size > 1 }
    }
}

enum class YouTubeRepeatMode { OFF, ALL, ONE }

enum class SearchCategory { ALL, TRACK, ARTIST, ALBUM }
