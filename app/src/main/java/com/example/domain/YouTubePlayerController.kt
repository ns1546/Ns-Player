package com.example.domain

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class YouTubePlayerController private constructor(private val appContext: Context) {

    companion object {
        @Volatile
        private var instance: YouTubePlayerController? = null

        fun getInstance(context: Context): YouTubePlayerController {
            return instance ?: synchronized(this) {
                instance ?: YouTubePlayerController(context.applicationContext).also { instance = it }
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var isPlayerReady = false
    private var currentVideoId: String = ""

    private var currentTitle: String = "YouTube Video"
    private var currentArtist: String = "YouTube"
    private var currentThumbnailUrl: String = ""

    // WakeLock & WifiLock to prevent sleep during background streaming
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    var onStateChange: ((Int) -> Unit)? = null
    var onProgress: ((Float, Float) -> Unit)? = null
    var onError: ((Int) -> Unit)? = null

    private val _lastErrorCode = MutableStateFlow<Int?>(null)
    val lastErrorCode: StateFlow<Int?> = _lastErrorCode.asStateFlow()

    private val _isBackgroundPlaybackActive = MutableStateFlow(false)
    val isBackgroundPlaybackActive: StateFlow<Boolean> = _isBackgroundPlaybackActive.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // KeepAlive runnable
    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            if (_isBackgroundPlaybackActive.value) {
                evaluate("if (window.player && window.player.getPlayerState && window.player.getPlayerState() === 1) { /* keepalive ok */ }")
                mainHandler.postDelayed(this, 2000)
            }
        }
    }

    init {
        audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        initWebView()
    }

    fun setTrackMetadata(title: String, artist: String, thumbnailUrl: String) {
        this.currentTitle = title
        this.currentArtist = artist
        this.currentThumbnailUrl = thumbnailUrl
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .build()

                audioManager?.requestAudioFocus(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        } catch (_: Exception) {}
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    private fun acquireLocks() {
        try {
            requestAudioFocus()

            if (wakeLock == null) {
                val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NSPlayer::YouTubeWakeLock")
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hours max
            }

            if (wifiLock == null) {
                val wifiManager = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "NSPlayer::YouTubeWifiLock")
            }
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
            _isBackgroundPlaybackActive.value = true

            // Trigger YouTube Foreground Service for persistent notification & lockscreen controls
            YouTubeAudioForegroundService.start(
                appContext,
                currentTitle,
                currentArtist,
                currentThumbnailUrl,
                isPlaying = true
            )

            mainHandler.removeCallbacks(keepAliveRunnable)
            mainHandler.post(keepAliveRunnable)
        } catch (_: Exception) {}
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
            _isBackgroundPlaybackActive.value = false
            mainHandler.removeCallbacks(keepAliveRunnable)

            // Update foreground service notification to paused
            YouTubeAudioForegroundService.start(
                appContext,
                currentTitle,
                currentArtist,
                currentThumbnailUrl,
                isPlaying = false
            )
        } catch (_: Exception) {}
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView(context: Context = appContext) {
        if (webView != null) return

        try {
            // Enable Cookies for YouTube playback session
            try {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
            } catch (_: Throwable) {}

            webView = WebView(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                try {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                } catch (_: Throwable) {}

                try {
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                } catch (_: Throwable) {
                    try {
                        setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                    } catch (_: Throwable) {}
                }

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    allowFileAccess = true
                    allowContentAccess = true
                    javaScriptCanOpenWindowsAutomatically = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    cacheMode = WebSettings.LOAD_DEFAULT
                    // Modern Android Chrome User-Agent
                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                }
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false
                }
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onReady() {
                            mainHandler.post {
                                isPlayerReady = true
                                _lastErrorCode.value = null
                            }
                        }

                        @JavascriptInterface
                        fun onStateChange(state: Int) {
                            mainHandler.post {
                                if (state == 1) { // Playing
                                    isPlayerReady = true
                                    _isPlaying.value = true
                                    _lastErrorCode.value = null
                                    acquireLocks()
                                } else if (state == 2 || state == 0) { // Paused or Ended
                                    _isPlaying.value = false
                                    releaseLocks()
                                }
                                onStateChange?.invoke(state)
                            }
                        }

                        @JavascriptInterface
                        fun onProgress(curr: Float, dur: Float) {
                            mainHandler.post { onProgress?.invoke(curr, dur) }
                        }

                        @JavascriptInterface
                        fun onError(code: Int) {
                            mainHandler.post {
                                _lastErrorCode.value = code
                                _isPlaying.value = false
                                releaseLocks()
                                onError?.invoke(code)
                            }
                        }
                    },
                    "AndroidBridge"
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("YouTubePlayerController", "Error initializing WebView", e)
        }
    }

    fun getWebView(context: Context = appContext): WebView {
        if (webView == null) {
            initWebView(context)
        }
        return webView!!
    }

    fun detachFromParent() {
        try {
            val wv = webView ?: return
            val parent = wv.parent as? ViewGroup
            parent?.removeView(wv)
        } catch (_: Exception) {}
    }

    fun loadTrack(videoId: String, startSeconds: Int = 0) {
        val wv = webView ?: return
        _lastErrorCode.value = null

        if (videoId == currentVideoId && isPlayerReady) {
            if (startSeconds > 0) {
                seekTo(startSeconds.toFloat())
            }
            play()
            return
        }

        if (isPlayerReady && currentVideoId.isNotBlank()) {
            currentVideoId = videoId
            evaluate("loadVideo('$videoId', $startSeconds); playVideo();")
            acquireLocks()
            return
        }

        currentVideoId = videoId
        isPlayerReady = false

        val html = buildHtml(videoId, startSeconds)
        wv.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
        acquireLocks()
    }

    fun play() {
        _isPlaying.value = true
        acquireLocks()
        evaluate("playVideo();")
    }

    fun pause() {
        _isPlaying.value = false
        releaseLocks()
        evaluate("pauseVideo();")
    }

    fun seekTo(seconds: Float) {
        evaluate("seekTo(${seconds.toInt()});")
    }

    fun setSpeed(rate: Float) {
        evaluate("setSpeed($rate);")
    }

    fun setVolume(volume: Int) {
        evaluate("setVolume(${volume.coerceIn(0, 100)});")
    }

    fun clearCache() {
        try {
            webView?.clearCache(true)
            webView?.clearHistory()
        } catch (_: Exception) {}
    }

    private fun evaluate(script: String) {
        mainHandler.post {
            try {
                webView?.evaluateJavascript(script, null)
            } catch (_: Exception) {}
        }
    }

    private fun buildHtml(videoId: String, startSeconds: Int): String {
        return """
<!DOCTYPE html>
<html>
<head>
<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  html, body { width: 100%; height: 100%; background: #000000; overflow: hidden; }
  #player { width: 100%; height: 100%; position: absolute; top: 0; left: 0; border: none; }
</style>
<script>
  // OVERRIDE BROWSER VISIBILITY API SO BACKGROUND / SCREEN-OFF DOES NOT PAUSE PLAYBACK
  try {
    Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
    Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
    Object.defineProperty(document, 'webkitHidden', { get: function() { return false; }, configurable: true });
    Object.defineProperty(document, 'webkitVisibilityState', { get: function() { return 'visible'; }, configurable: true });
  } catch(e) {}

  var blockEvts = ['visibilitychange', 'webkitvisibilitychange', 'blur', 'pagehide'];
  blockEvts.forEach(function(evtName) {
    window.addEventListener(evtName, function(e) {
      if (e) e.stopImmediatePropagation();
    }, true);
    document.addEventListener(evtName, function(e) {
      if (e) e.stopImmediatePropagation();
    }, true);
  });
</script>
</head>
<body>
<div id='player'></div>
<script>
  var tag = document.createElement('script');
  tag.src = 'https://www.youtube.com/iframe_api';
  var firstScriptTag = document.getElementsByTagName('script')[0];
  firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

  var player;
  var isReady = false;

  function onYouTubeIframeAPIReady() {
    player = new YT.Player('player', {
      height: '100%',
      width: '100%',
      videoId: '$videoId',
      host: 'https://www.youtube-nocookie.com',
      playerVars: {
        'autoplay': 1,
        'playsinline': 1,
        'controls': 1,
        'rel': 0,
        'modestbranding': 1,
        'enablejsapi': 1,
        'widget_referrer': 'https://www.youtube.com',
        'origin': 'https://www.youtube.com',
        'start': $startSeconds,
        'iv_load_policy': 3,
        'fs': 1
      },
      events: {
        'onReady': function(e) {
          isReady = true;
          if (window.AndroidBridge) {
            try { window.AndroidBridge.onReady(); } catch(err) {}
          }
          if ($startSeconds > 0) {
            try { e.target.seekTo($startSeconds, true); } catch(err) {}
          }
          try { e.target.playVideo(); } catch(err) {}
        },
        'onStateChange': function(e) {
          if (window.AndroidBridge) {
            try { window.AndroidBridge.onStateChange(e.data); } catch(err) {}
          }
        },
        'onError': function(e) {
          if (window.AndroidBridge) {
            try { window.AndroidBridge.onError(e.data); } catch(err) {}
          }
        }
      }
    });
  }

  function playVideo() { if (player && player.playVideo) player.playVideo(); }
  function pauseVideo() { if (player && player.pauseVideo) player.pauseVideo(); }
  function seekTo(sec) { if (player && player.seekTo) player.seekTo(sec, true); }
  function setSpeed(rate) { if (player && player.setPlaybackRate) player.setPlaybackRate(rate); }
  function setVolume(vol) { if (player && player.setVolume) player.setVolume(vol); }
  function loadVideo(id, startSec) {
    if (player && player.loadVideoById) {
      if (startSec && startSec > 0) {
        player.loadVideoById({ videoId: id, startSeconds: startSec });
      } else {
        player.loadVideoById(id);
      }
    }
  }

  setInterval(function() {
    if (player && player.getCurrentTime && window.AndroidBridge) {
      try {
        var c = player.getCurrentTime();
        var d = player.getDuration();
        window.AndroidBridge.onProgress(c, d);
      } catch(e) {}
    }
  }, 1000);
</script>
</body>
</html>
        """.trimIndent()
    }

    fun release() {
        try {
            abandonAudioFocus()
            releaseLocks()
            YouTubeAudioForegroundService.stop(appContext)
            detachFromParent()
            webView?.stopLoading()
            webView?.loadUrl("about:blank")
            webView?.clearHistory()
            webView?.removeAllViews()
            webView?.destroy()
            webView = null
            instance = null
        } catch (_: Exception) {}
    }
}
