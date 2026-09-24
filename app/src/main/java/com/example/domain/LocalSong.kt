package com.example.domain

import android.net.Uri

data class LocalSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val albumArtUri: Uri?,
    val folderName: String = "Internal",
    val filePath: String = ""
)
