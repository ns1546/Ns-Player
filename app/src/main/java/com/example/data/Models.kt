package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorite_songs",
    indices = [Index(value = ["timestamp"])]
)
data class FavoriteSong(
    @PrimaryKey val songUri: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "recent_songs",
    indices = [Index(value = ["timestamp"])]
)
data class RecentSong(
    @PrimaryKey val songUri: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["timestamp"])]
)
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["songUri"])
    ]
)
data class PlaylistSong(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playlistId: Int,
    val songUri: String,
    val title: String,
    val artist: String,
    val duration: Long
)

@Entity(
    tableName = "downloaded_tracks",
    indices = [Index(value = ["downloadedAt"])]
)
data class DownloadedTrack(
    @PrimaryKey val videoId: String,
    val title: String,
    val author: String,
    val durationSeconds: Long,
    val thumbnailUrl: String,
    val downloadedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "song_memos",
    indices = [Index(value = ["updatedAt"])]
)
data class SongMemo(
    @PrimaryKey val songUri: String,
    val note: String,
    val keyTag: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "song_cues",
    indices = [
        Index(value = ["songUri"]),
        Index(value = ["timestampMs"])
    ]
)
data class SongCue(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val songUri: String,
    val timestampMs: Long,
    val label: String,
    val tagColor: Long = 0xFF6366F1
)

@Entity(
    tableName = "song_lyrics",
    indices = [Index(value = ["songUri"])]
)
data class SongLyrics(
    @PrimaryKey val songUri: String,
    val syncedLyrics: String?,
    val plainLyrics: String?,
    val isCustom: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
