package com.example.domain

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class YouTubeMedia3PlaybackService : MediaSessionService() {

    companion object {
        const val CHANNEL_ID = "yt_media3_playback_channel"
        const val NOTIFICATION_ID = 5050

        const val ACTION_START = "com.example.yt.media3.ACTION_START"
        const val ACTION_PAUSE = "com.example.yt.media3.ACTION_PAUSE"
        const val ACTION_PLAY = "com.example.yt.media3.ACTION_PLAY"
        const val ACTION_NEXT = "com.example.yt.media3.ACTION_NEXT"
        const val ACTION_PREV = "com.example.yt.media3.ACTION_PREV"
        const val ACTION_STOP = "com.example.yt.media3.ACTION_STOP"

        fun startService(context: Context) {
            val intent = Intent(context, YouTubeMedia3PlaybackService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("YTMedia3Service", "Failed to start service", e)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, YouTubeMedia3PlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var thumbBitmap: Bitmap? = null
    private var lastThumbUrl: String = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireLocks()

        val ytPlayer = YouTubeExoPlayerManager.getInstance(this).player
        
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, ytPlayer)
            .setSessionActivity(pendingIntent)
            .build()

        ytPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateNotification()
                if (isPlaying) {
                    acquireLocks()
                } else {
                    releaseLocks()
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val title = mediaMetadata.title?.toString() ?: ""
                val artist = mediaMetadata.artist?.toString() ?: ""
                val artUri = mediaMetadata.artworkUri?.toString() ?: ""
                if (artUri.isNotBlank() && artUri != lastThumbUrl) {
                    lastThumbUrl = artUri
                    loadThumbnail(artUri)
                } else {
                    updateNotification()
                }
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action ?: ACTION_START
        val playerManager = YouTubeExoPlayerManager.getInstance(this)

        when (action) {
            ACTION_START -> {
                acquireLocks()
                updateNotification()
            }
            ACTION_PLAY -> {
                acquireLocks()
                playerManager.play()
                updateNotification()
            }
            ACTION_PAUSE -> {
                playerManager.pause()
                updateNotification()
            }
            ACTION_NEXT -> {
                sendBroadcast(Intent("com.example.ACTION_YT_NEXT"))
            }
            ACTION_PREV -> {
                sendBroadcast(Intent("com.example.ACTION_YT_PREV"))
            }
            ACTION_STOP -> {
                playerManager.pause()
                releaseLocks()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "NSPlayer:YouTubeMedia3WakeLock"
                )
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(6 * 60 * 60 * 1000L) // 6 hours
            }

            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                wifiLock = wifiManager?.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "NSPlayer:YouTubeMedia3WifiLock"
                )
            }
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        } catch (_: Exception) {}
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {}
    }

    private fun loadThumbnail(url: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.getInputStream().use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        withContext(Dispatchers.Main) {
                            thumbBitmap = bmp
                            updateNotification()
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    updateNotification()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YouTube Audio Stream",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media3 Foreground Service ensuring uninterrupted YouTube background playback"
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val ytPlayer = YouTubeExoPlayerManager.getInstance(this).player
        val isPlaying = ytPlayer.isPlaying
        val metadata = ytPlayer.mediaMetadata
        val title = metadata.title?.toString()?.ifBlank { "YouTube Audio" } ?: "YouTube Audio"
        val artist = metadata.artist?.toString()?.ifBlank { "NSPlayer Media3 Stream" } ?: "NSPlayer Media3 Stream"

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, YouTubeMedia3PlaybackService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(
            this,
            1,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(this, YouTubeMedia3PlaybackService::class.java).apply {
            action = ACTION_PREV
        }
        val prevPendingIntent = PendingIntent.getService(
            this,
            2,
            prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, YouTubeMedia3PlaybackService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this,
            3,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, YouTubeMedia3PlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            4,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseText = if (isPlaying) "Pause" else "Play"

        val notifBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText("YouTube Media3 Background Audio")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(playPauseIcon, playPauseText, playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopPendingIntent)
        if (mediaSession != null) {
            try {
                notifBuilder.setStyle(
                    androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(mediaSession!!)
                        .setShowActionsInCompactView(0, 1, 2)
                )
            } catch (_: Exception) {
                notifBuilder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("$title\n$artist")
                )
            }
        } else {
            notifBuilder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$title\n$artist")
            )
        }

        if (thumbBitmap != null) {
            notifBuilder.setLargeIcon(thumbBitmap)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notifBuilder.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notifBuilder.build())
            }
        } catch (e: Exception) {
            android.util.Log.e("YTMedia3Service", "Error posting notification", e)
        }
    }

    override fun onDestroy() {
        releaseLocks()
        serviceScope.cancel()
        mediaSession?.run {
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
