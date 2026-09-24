package com.example.domain

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class YouTubeAudioForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "yt_bg_audio_channel"
        const val NOTIFICATION_ID = 4040

        const val ACTION_START = "com.example.yt.ACTION_START"
        const val ACTION_PAUSE = "com.example.yt.ACTION_PAUSE"
        const val ACTION_PLAY = "com.example.yt.ACTION_PLAY"
        const val ACTION_NEXT = "com.example.yt.ACTION_NEXT"
        const val ACTION_PREV = "com.example.yt.ACTION_PREV"
        const val ACTION_STOP = "com.example.yt.ACTION_STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_THUMB = "extra_thumb"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        fun start(context: Context, title: String, artist: String, thumbUrl: String, isPlaying: Boolean = true) {
            val intent = Intent(context, YouTubeAudioForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_THUMB, thumbUrl)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("YTBGService", "Failed to start foreground service", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, YouTubeAudioForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var silentAudioTrack: android.media.AudioTrack? = null
    private var isAnchorRunning = false

    private var currentTitle = "YouTube Music"
    private var currentArtist = "Playing in Background"
    private var currentThumbUrl = ""
    private var isPlaying = true
    private var thumbBitmap: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireLocks()
        startSilentAudioAnchor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                currentTitle = intent?.getStringExtra(EXTRA_TITLE) ?: currentTitle
                currentArtist = intent?.getStringExtra(EXTRA_ARTIST) ?: currentArtist
                val newThumb = intent?.getStringExtra(EXTRA_THUMB) ?: ""
                isPlaying = intent?.getBooleanExtra(EXTRA_IS_PLAYING, true) ?: true

                if (newThumb.isNotBlank() && newThumb != currentThumbUrl) {
                    currentThumbUrl = newThumb
                    loadThumbnail(newThumb)
                }
                updateForegroundNotification()
                if (isPlaying) {
                    acquireLocks()
                    startSilentAudioAnchor()
                }
            }
            ACTION_PLAY -> {
                isPlaying = true
                acquireLocks()
                startSilentAudioAnchor()
                YouTubePlayerController.getInstance(this).play()
                updateForegroundNotification()
            }
            ACTION_PAUSE -> {
                isPlaying = false
                stopSilentAudioAnchor()
                YouTubePlayerController.getInstance(this).pause()
                updateForegroundNotification()
            }
            ACTION_NEXT -> {
                sendBroadcast(Intent("com.example.ACTION_YT_NEXT"))
            }
            ACTION_PREV -> {
                sendBroadcast(Intent("com.example.ACTION_YT_PREV"))
            }
            ACTION_STOP -> {
                isPlaying = false
                stopSilentAudioAnchor()
                releaseLocks()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return START_STICKY
    }

    private fun startSilentAudioAnchor() {
        if (isAnchorRunning) return
        isAnchorRunning = true
        try {
            val sampleRate = 44100
            val bufferSize = android.media.AudioTrack.getMinBufferSize(
                sampleRate,
                android.media.AudioFormat.CHANNEL_OUT_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(1024)

            silentAudioTrack = android.media.AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(android.media.AudioTrack.MODE_STREAM)
                .build()

            silentAudioTrack?.play()

            serviceScope.launch(Dispatchers.IO) {
                val silentBuffer = ByteArray(bufferSize)
                while (isAnchorRunning && isPlaying) {
                    try {
                        silentAudioTrack?.write(silentBuffer, 0, silentBuffer.size)
                        kotlinx.coroutines.delay(250)
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun stopSilentAudioAnchor() {
        isAnchorRunning = false
        try {
            silentAudioTrack?.stop()
            silentAudioTrack?.release()
            silentAudioTrack = null
        } catch (_: Exception) {}
    }

    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "NSPlayer:YouTubeAudioServiceLock"
                )
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hours
            }

            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                wifiLock = wifiManager?.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "NSPlayer:YouTubeAudioServiceWifiLock"
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
                            updateForegroundNotification()
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YouTube Background Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps YouTube streaming continuously in the background"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun updateForegroundNotification() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("YTBGService", "Error posting foreground notification", e)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, YouTubeAudioForegroundService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(
            this,
            1,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(this, YouTubeAudioForegroundService::class.java).apply {
            action = ACTION_PREV
        }
        val prevPendingIntent = PendingIntent.getService(
            this,
            2,
            prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, YouTubeAudioForegroundService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this,
            3,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, YouTubeAudioForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            4,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseText = if (isPlaying) "Pause" else "Play"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSubText("YouTube Background Stream")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(playPauseIcon, playPauseText, playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopPendingIntent)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$currentTitle\n$currentArtist • Playing in background")
            )

        if (thumbBitmap != null) {
            builder.setLargeIcon(thumbBitmap)
        }

        return builder.build()
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }
}
