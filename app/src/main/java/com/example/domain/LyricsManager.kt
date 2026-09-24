package com.example.domain

import android.content.Context
import com.example.data.PlaylistDao
import com.example.data.SongLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

class LyricsManager(
    private val context: Context,
    private val playlistDao: PlaylistDao
) {

    companion object {
        private val LRC_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.?(\\d{2,3})?\\](.*)")

        fun parseLrc(lrcContent: String): List<LyricLine> {
            if (lrcContent.isBlank()) return emptyList()
            val lines = mutableListOf<LyricLine>()
            
            lrcContent.lines().forEach { rawLine ->
                val trimmed = rawLine.trim()
                val matcher = LRC_PATTERN.matcher(trimmed)
                if (matcher.matches()) {
                    try {
                        val min = matcher.group(1)?.toLongOrNull() ?: 0L
                        val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                        val milliStr = matcher.group(3) ?: "0"
                        val ms = when (milliStr.length) {
                            1 -> milliStr.toLong() * 100L
                            2 -> milliStr.toLong() * 10L
                            else -> milliStr.take(3).toLong()
                        }
                        val timestamp = (min * 60L + sec) * 1000L + ms
                        val text = matcher.group(4)?.trim() ?: ""
                        if (text.isNotBlank()) {
                            lines.add(LyricLine(timestamp, text))
                        }
                    } catch (_: Exception) {}
                }
            }
            return lines.sortedBy { it.timestampMs }
        }
    }

    suspend fun getOrFetchLyrics(song: LocalSong): Pair<List<LyricLine>, String?> = withContext(Dispatchers.IO) {
        val songUriStr = song.uri.toString()

        // 1. Check local database cache
        // If already cached, parse and return
        // Note: We can check local .lrc file in storage if filePath is valid
        if (song.filePath.isNotBlank()) {
            try {
                val lrcFile = File(song.filePath.substringBeforeLast(".") + ".lrc")
                if (lrcFile.exists() && lrcFile.canRead()) {
                    val localLrc = lrcFile.readText()
                    val parsed = parseLrc(localLrc)
                    if (parsed.isNotEmpty()) {
                        playlistDao.saveLyrics(SongLyrics(songUriStr, localLrc, null, false))
                        return@withContext Pair(parsed, null)
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Fetch from free open LRCLIB database (no API key required)
        val onlineResult = fetchFromLrcLib(song.title, song.artist, song.duration / 1000L)
        if (onlineResult != null) {
            val (synced, plain) = onlineResult
            playlistDao.saveLyrics(SongLyrics(songUriStr, synced, plain, false))
            val parsed = if (!synced.isNullOrBlank()) parseLrc(synced) else emptyList()
            return@withContext Pair(parsed, plain)
        }

        Pair(emptyList(), null)
    }

    suspend fun searchAndSaveOnline(song: LocalSong): Boolean = withContext(Dispatchers.IO) {
        val onlineResult = fetchFromLrcLib(song.title, song.artist, song.duration / 1000L)
        if (onlineResult != null) {
            val (synced, plain) = onlineResult
            playlistDao.saveLyrics(SongLyrics(song.uri.toString(), synced, plain, false))
            return@withContext true
        }
        false
    }

    suspend fun saveCustomLyrics(songUri: String, rawText: String) = withContext(Dispatchers.IO) {
        val isSynced = rawText.contains("[") && rawText.contains("]")
        playlistDao.saveLyrics(
            SongLyrics(
                songUri = songUri,
                syncedLyrics = if (isSynced) rawText else null,
                plainLyrics = if (!isSynced) rawText else null,
                isCustom = true
            )
        )
    }

    private fun cleanQuery(text: String): String {
        return text.replace(Regex("\\(.*\\)|\\[.*\\]"), "")
            .replace(Regex("[_\\-\\.]"), " ")
            .trim()
    }

    private fun fetchFromLrcLib(title: String, artist: String, durationSec: Long): Pair<String?, String?>? {
        val cleanTitle = cleanQuery(title)
        val cleanArtist = if (artist.contains("unknown", ignoreCase = true)) "" else cleanQuery(artist)
        
        val urlString = buildString {
            append("https://lrclib.net/api/get?")
            append("track_name=").append(URLEncoder.encode(cleanTitle, "UTF-8"))
            if (cleanArtist.isNotBlank()) {
                append("&artist_name=").append(URLEncoder.encode(cleanArtist, "UTF-8"))
            }
            if (durationSec > 0) {
                append("&duration=").append(durationSec)
            }
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 6000
                setRequestProperty("User-Agent", "NSPlayerAndroid/1.0 (nstasin81@gmail.com)")
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                val syncedLyrics = json.optString("syncedLyrics").takeIf { it.isNotBlank() }
                val plainLyrics = json.optString("plainLyrics").takeIf { it.isNotBlank() }

                if (syncedLyrics != null || plainLyrics != null) {
                    return Pair(syncedLyrics, plainLyrics)
                }
            }
        } catch (_: Exception) {
            // Silently fallback if offline
        } finally {
            try { connection?.disconnect() } catch (_: Exception) {}
        }
        return null
    }
}
