package com.example.domain

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioRepository(private val context: Context) {
    suspend fun getAudioFiles(): List<LocalSong> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<LocalSong>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA
        )
        // Only select files that have content (> 100 bytes)
        val selection = "${MediaStore.Audio.Media.SIZE} > 100"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        
        try {
            context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                
                while (cursor.moveToNext()) {
                    try {
                        val id = if (idColumn >= 0) cursor.getLong(idColumn) else -1L
                        if (id == -1L) continue
                        val title = (if (titleColumn >= 0) cursor.getString(titleColumn) else null)?.takeIf { it.isNotBlank() } ?: "Unknown Title"
                        val artist = (if (artistColumn >= 0) cursor.getString(artistColumn) else null)?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
                        val album = (if (albumColumn >= 0) cursor.getString(albumColumn) else null)?.takeIf { it.isNotBlank() } ?: "Unknown Album"
                        val duration = if (durationColumn >= 0) cursor.getLong(durationColumn) else 0L
                        val albumId = if (albumIdColumn >= 0) cursor.getLong(albumIdColumn) else -1L
                        val path = if (dataColumn >= 0) cursor.getString(dataColumn) ?: "" else ""
                        val folder = if (path.isNotBlank()) {
                            try {
                                java.io.File(path).parentFile?.name ?: "Music"
                            } catch (_: Exception) { "Music" }
                        } else "Music"
                        
                        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        val albumArtUri = if (albumId >= 0) Uri.parse("content://media/external/audio/albumart/$albumId") else null
                        
                        songs.add(LocalSong(id, title, artist, album, duration, uri, albumArtUri, folder, path))
                    } catch (_: Exception) {
                        // Safely skip any corrupted single row without failing the scan
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioRepository", "Error querying MediaStore", e)
        }
        songs
    }
}
