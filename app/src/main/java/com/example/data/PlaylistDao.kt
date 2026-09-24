package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY timestamp DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: Int)

    @Query("UPDATE playlists SET name = :newName WHERE id = :id")
    suspend fun renamePlaylist(id: Int, newName: String)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId")
    fun getSongsForPlaylist(playlistId: Int): Flow<List<PlaylistSong>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongToPlaylist(song: PlaylistSong)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songUri = :songUri")
    suspend fun removeSongFromPlaylist(playlistId: Int, songUri: String)
    @Query("SELECT * FROM favorite_songs ORDER BY timestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteSong>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favoriteSong: FavoriteSong)

    @Query("DELETE FROM favorite_songs WHERE songUri = :songUri")
    suspend fun deleteFavorite(songUri: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE songUri = :songUri)")
    fun isFavorite(songUri: String): Flow<Boolean>

    @Query("SELECT * FROM recent_songs ORDER BY timestamp DESC LIMIT 50")
    fun getRecentSongs(): Flow<List<RecentSong>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(recentSong: RecentSong)

    @Query("DELETE FROM recent_songs WHERE timestamp < (SELECT timestamp FROM recent_songs ORDER BY timestamp DESC LIMIT 1 OFFSET 100)")
    suspend fun pruneRecentSongs()

    @Query("DELETE FROM playlist_songs WHERE playlistId NOT IN (SELECT id FROM playlists)")
    suspend fun deleteOrphanedPlaylistSongs()

    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC")
    fun getAllDownloadedTracks(): Flow<List<DownloadedTrack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadedTrack(track: DownloadedTrack)

    @Query("DELETE FROM downloaded_tracks WHERE videoId = :videoId")
    suspend fun deleteDownloadedTrack(videoId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_tracks WHERE videoId = :videoId)")
    fun isTrackDownloaded(videoId: String): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM playlist_songs")
    suspend fun getPlaylistSongsCount(): Int

    @Query("SELECT * FROM song_memos WHERE songUri = :songUri")
    fun getMemoForSong(songUri: String): Flow<SongMemo?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSongMemo(memo: SongMemo)

    @Query("DELETE FROM song_memos WHERE songUri = :songUri")
    suspend fun deleteSongMemo(songUri: String)

    @Query("SELECT * FROM song_memos ORDER BY updatedAt DESC")
    fun getAllSongMemos(): Flow<List<SongMemo>>

    @Query("SELECT * FROM song_cues WHERE songUri = :songUri ORDER BY timestampMs ASC")
    fun getCuesForSong(songUri: String): Flow<List<SongCue>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCue(cue: SongCue): Long

    @Query("DELETE FROM song_cues WHERE id = :id")
    suspend fun deleteCue(id: Int)

    @Query("DELETE FROM song_cues WHERE songUri = :songUri")
    suspend fun deleteAllCuesForSong(songUri: String)

    @Query("SELECT * FROM song_lyrics WHERE songUri = :songUri")
    fun getLyricsForSong(songUri: String): Flow<SongLyrics?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLyrics(lyrics: SongLyrics)

    @Query("DELETE FROM song_lyrics WHERE songUri = :songUri")
    suspend fun deleteLyrics(songUri: String)
}
