package com.example.domain

import android.content.Context
import android.widget.Toast
import com.example.data.DownloadedTrack
import com.example.data.PlaylistDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class DownloadFormat(val extension: String, val mimeType: String, val isVideo: Boolean) {
    MP3_HIGH("mp3", "audio/mpeg", false),
    MP3_STANDARD("mp3", "audio/mpeg", false),
    M4A_LIGHT("m4a", "audio/mp4", false),
    FLAC_LOSSLESS("flac", "audio/flac", false),
    VIDEO_1080P("mp4", "video/mp4", true),
    VIDEO_720P("mp4", "video/mp4", true),
    VIDEO_480P("mp4", "video/mp4", true),
    VIDEO_360P("mp4", "video/mp4", true)
}

data class DownloadQualityOption(
    val format: DownloadFormat,
    val title: String,
    val qualityLabel: String,
    val estimatedSizeMb: Float,
    val description: String
)

object DownloadManagerHelper {

    val audioOptions = listOf(
        DownloadQualityOption(
            format = DownloadFormat.MP3_HIGH,
            title = "High Quality MP3",
            qualityLabel = "320 kbps",
            estimatedSizeMb = 8.5f,
            description = "Best audio fidelity for headphones & speakers"
        ),
        DownloadQualityOption(
            format = DownloadFormat.MP3_STANDARD,
            title = "Standard MP3",
            qualityLabel = "192 kbps",
            estimatedSizeMb = 5.2f,
            description = "Balanced quality & fast download"
        ),
        DownloadQualityOption(
            format = DownloadFormat.M4A_LIGHT,
            title = "AAC / M4A Audio",
            qualityLabel = "128 kbps",
            estimatedSizeMb = 3.6f,
            description = "Lightweight file, saves data & storage"
        ),
        DownloadQualityOption(
            format = DownloadFormat.FLAC_LOSSLESS,
            title = "Lossless FLAC",
            qualityLabel = "Studio HD",
            estimatedSizeMb = 22.0f,
            description = "Uncompressed original studio quality"
        )
    )

    val videoOptions = listOf(
        DownloadQualityOption(
            format = DownloadFormat.VIDEO_1080P,
            title = "Full HD Video",
            qualityLabel = "1080p 60fps",
            estimatedSizeMb = 65.0f,
            description = "Crisp crystal-clear high definition video"
        ),
        DownloadQualityOption(
            format = DownloadFormat.VIDEO_720P,
            title = "HD Video",
            qualityLabel = "720p HD",
            estimatedSizeMb = 32.0f,
            description = "Great quality for tablets & phones"
        ),
        DownloadQualityOption(
            format = DownloadFormat.VIDEO_480P,
            title = "Standard Video",
            qualityLabel = "480p SD",
            estimatedSizeMb = 18.0f,
            description = "Smooth playback, moderate file size"
        ),
        DownloadQualityOption(
            format = DownloadFormat.VIDEO_360P,
            title = "Data Saver Video",
            qualityLabel = "360p",
            estimatedSizeMb = 9.5f,
            description = "Fast download, minimal storage"
        )
    )

    suspend fun startDownload(
        context: Context,
        track: YouTubeTrack,
        option: DownloadQualityOption,
        playlistDao: PlaylistDao
    ): Long = withContext(Dispatchers.IO) {
        val downloadOption = DownloadOption(
            id = option.format.name,
            mediaType = if (option.format.isVideo) DownloadMediaType.VIDEO else DownloadMediaType.AUDIO,
            formatName = option.format.extension.uppercase(),
            qualityLabel = option.qualityLabel,
            tag = option.title,
            estimatedSize = "${option.estimatedSizeMb} MB",
            mimeType = option.format.mimeType,
            fileExtension = option.format.extension,
            downloadUrl = "https://www.youtube.com/watch?v=${track.videoId}"
        )

        withContext(Dispatchers.Main) {
            YouTubeDownloadHelper.startFastDownload(
                context = context,
                track = track,
                option = downloadOption
            )
        }
        return@withContext track.videoId.hashCode().toLong()
    }
}
