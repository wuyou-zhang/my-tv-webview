package com.lizongying.mytv0

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import com.lizongying.mytv0.data.SourceType
import com.lizongying.mytv0.databinding.PlayerBinding
import com.lizongying.mytv0.models.TVModel


class PlayerFragment : Fragment() {
    private var audioManager: AudioManager? = null

    private fun requestAudioFocus(): Boolean {
        return true
    }

    private fun abandonAudioFocus() {
    }

    private var _binding: PlayerBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    private var isUsingWebView = false

    private var tvModel: TVModel? = null
    private val aspectRatio = 16f / 9f

    private val handler = Handler(Looper.myLooper()!!)
    private val delayHideVolume = 2 * 1000L

    inner class AndroidBridge {
        @JavascriptInterface
        fun onVideoReady() {
            activity?.runOnUiThread {
                onVideoPlaying()
            }
        }
    }

    private var watchdogRunnable: Runnable? = null

    fun onVideoPlaying() {
        cancelVideoWatchdog()
        if (isUsingWebView && _binding != null) {
            binding.webView.alpha = 1f
        }
        tvModel?.setErrInfo("")
    }

    private fun startVideoWatchdog() {
        cancelVideoWatchdog()
        watchdogRunnable = Runnable {
            if (isUsingWebView && _binding != null && binding.webView.alpha < 1f) {
                Log.w(TAG, "Watchdog timeout: forcing video play and making WebView visible")
                binding.webView.evaluateJavascript(
                    "try { var v = document.querySelector('video'); if (v) { v.muted=false; v.volume=1; v.play(); } } catch(e){}",
                    null
                )
                onVideoPlaying()
            }
        }
        handler.postDelayed(watchdogRunnable!!, 5000)
    }

    private fun cancelVideoWatchdog() {
        watchdogRunnable?.let { handler.removeCallbacks(it) }
        watchdogRunnable = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (binding.root as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        setupWebView()
        updatePlayer()
        (activity as MainActivity).ready(TAG)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        WebView.setWebContentsDebuggingEnabled(true)
        val webView = binding.webView
        webView.setBackgroundColor(android.graphics.Color.BLACK)
        webView.alpha = 0f
        webView.isFocusable = false
        webView.isFocusableInTouchMode = false
        webView.setOnTouchListener { _, _ -> true }
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadsImagesAutomatically = true
        settings.blockNetworkImage = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.allowFileAccess = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        // Use Chrome Desktop User-Agent for Yangshipin to load video player directly
        settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                injectPlayerScript(view)
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                injectPlayerScript(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectPlayerScript(view)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    Log.e(TAG, "WebView error: ${error?.description}")
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            private var customView: View? = null
            private var customViewCallback: CustomViewCallback? = null

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                binding.fullscreenContainer.addView(view, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                ))
                binding.fullscreenContainer.visibility = View.VISIBLE
            }

            override fun onHideCustomView() {
                if (customView == null) return
                binding.fullscreenContainer.removeView(customView)
                binding.fullscreenContainer.visibility = View.GONE
                customViewCallback?.onCustomViewHidden()
                customView = null
                customViewCallback = null
            }
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress > 25) {
                    injectPlayerScript(view)
                }
            }

            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                Log.i("WebConsole", "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()}")
                return true
            }
        }
    }

    private fun injectPlayerScript(view: WebView?) {
        if (view == null) return

        val css = """
            video::-webkit-media-controls,
            video::-webkit-media-controls-panel,
            video::-webkit-media-controls-enclosure,
            video::-webkit-media-controls-overlay-play-button,
            video::-webkit-media-controls-start-playback-button,
            video::-webkit-media-controls-play-button,
            video::-webkit-media-controls-timeline,
            video::-webkit-media-controls-current-time-display,
            video::-webkit-media-controls-time-remaining-display,
            video::-webkit-media-controls-mute-button,
            video::-webkit-media-controls-volume-slider,
            video::-webkit-media-controls-fullscreen-button,
            .txp_none, .txp_shadow, .txp_layer_title, .txp_layer_controls,
            .txp_bottom_shadow, .txp_top_shadow, .mod_player_head, .mod_player_side,
            .txp_overlay_play, .txp_center_play, .txp_big_play, .player_icon_play,
            .txp_pop, .txp_toast, .txp_watermark,
            .vjs-control-bar, .vjs-big-play-button,
            .channel-detail-right, [class*="channel-detail-right"], [class*="epg"], [class*="schedule"] {
                display: none !important;
                opacity: 0 !important;
                visibility: hidden !important;
            }
            html, body {
                background: #000 !important;
                background-color: #000 !important;
                margin: 0 !important;
                padding: 0 !important;
                overflow: hidden !important;
                width: 100vw !important;
                height: 100vh !important;
            }
            .channel-detail-left, [class*="channel-detail-left"] {
                width: 100vw !important;
                height: 100vh !important;
                max-width: 100vw !important;
                max-height: 100vh !important;
                margin: 0 !important;
                padding: 0 !important;
                position: fixed !important;
                top: 0 !important;
                left: 0 !important;
                z-index: 100000 !important;
            }
            video {
                display: block !important;
                visibility: visible !important;
                opacity: 1 !important;
                position: fixed !important;
                top: 0 !important;
                left: 0 !important;
                right: 0 !important;
                bottom: 0 !important;
                width: 100vw !important;
                height: 100vh !important;
                max-width: 100vw !important;
                max-height: 100vh !important;
                transform: none !important;
                -webkit-transform: none !important;
                margin: 0 !important;
                padding: 0 !important;
                border: none !important;
                outline: none !important;
                z-index: 2147483647 !important;
                object-fit: contain !important;
                background: #000 !important;
                background-color: #000 !important;
            }
        """.trimIndent().replace("\n", " ").replace("'", "\\'")

        val jsCode = """
            (function() {
                var styleId = 'mytv-clean-style';
                var s = document.getElementById(styleId);
                if (!s) {
                    var targetHead = document.head || document.documentElement || document.body;
                    if (targetHead) {
                        s = document.createElement('style');
                        s.id = styleId;
                        s.innerHTML = '$css';
                        targetHead.appendChild(s);
                    }
                }

                function getParam(name) {
                    try {
                        var params = new URLSearchParams(window.location.search);
                        var val = params.get(name);
                        if (val) return val;
                        var reg = new RegExp('[?&]' + name + '=([^&#]*)');
                        var match = window.location.href.match(reg);
                        return match ? decodeURIComponent(match[1]) : null;
                    } catch(e) { return null; }
                }

                var c1 = getParam('watch_video_click');
                var c2 = getParam('watch_video_click2');
                var hasClick = !!(c1 || c2);

                if (typeof window.__mytv_click_done === 'undefined') {
                    window.__mytv_click_done = !hasClick;
                }

                function findBestVideo() {
                    var videos = document.querySelectorAll('video');
                    if (!videos || !videos.length) return null;
                    if (videos.length === 1) return videos[0];
                    for (var i = 0; i < videos.length; i++) {
                        if (!videos[i].paused && videos[i].videoWidth > 0) return videos[i];
                    }
                    for (var j = 0; j < videos.length; j++) {
                        if (videos[j].readyState >= 2 || videos[j].src || videos[j].currentSrc) return videos[j];
                    }
                    return videos[0];
                }

                function makeVideoFullscreen(v) {
                    if (!v) return;
                    try {
                        v.style.setProperty('display', 'block', 'important');
                        v.style.setProperty('visibility', 'visible', 'important');
                        v.style.setProperty('opacity', '1', 'important');
                        v.style.setProperty('position', 'fixed', 'important');
                        v.style.setProperty('top', '0', 'important');
                        v.style.setProperty('left', '0', 'important');
                        v.style.setProperty('right', '0', 'important');
                        v.style.setProperty('bottom', '0', 'important');
                        v.style.setProperty('width', '100vw', 'important');
                        v.style.setProperty('height', '100vh', 'important');
                        v.style.setProperty('max-width', '100vw', 'important');
                        v.style.setProperty('max-height', '100vh', 'important');
                        v.style.setProperty('margin', '0', 'important');
                        v.style.setProperty('padding', '0', 'important');
                        v.style.setProperty('border', 'none', 'important');
                        v.style.setProperty('outline', 'none', 'important');
                        v.style.setProperty('transform', 'none', 'important');
                        v.style.setProperty('-webkit-transform', 'none', 'important');
                        v.style.setProperty('z-index', '2147483647', 'important');
                        v.style.setProperty('object-fit', 'contain', 'important');
                        v.style.setProperty('background', '#000', 'important');
                        v.style.setProperty('background-color', '#000', 'important');

                        var curr = v;
                        var p = v.parentElement;
                        var canHideSiblings = !!(window.__mytv_click_done && (!v.paused || v.readyState >= 2));
                        while (p && p !== document.documentElement) {
                            p.style.setProperty('visibility', 'visible', 'important');
                            p.style.setProperty('opacity', '1', 'important');
                            p.style.setProperty('transform', 'none', 'important');
                            p.style.setProperty('-webkit-transform', 'none', 'important');
                            p.style.setProperty('filter', 'none', 'important');
                            p.style.setProperty('perspective', 'none', 'important');
                            p.style.setProperty('contain', 'none', 'important');
                            p.style.setProperty('clip-path', 'none', 'important');
                            p.style.setProperty('overflow', 'visible', 'important');
                            p.style.setProperty('z-index', '2147483647', 'important');
                            p.style.setProperty('border', 'none', 'important');
                            p.style.setProperty('outline', 'none', 'important');

                            if (canHideSiblings) {
                                var children = p.children;
                                for (var i = 0; i < children.length; i++) {
                                    var sib = children[i];
                                    if (sib !== curr && !sib.contains(v) && sib.tagName !== 'STYLE' && sib.tagName !== 'SCRIPT') {
                                        sib.style.setProperty('opacity', '0', 'important');
                                        sib.style.setProperty('visibility', 'hidden', 'important');
                                        sib.style.setProperty('pointer-events', 'none', 'important');
                                    }
                                }
                            }
                            curr = p;
                            p = p.parentElement;
                        }
                    } catch(e) {}
                }

                function enforceUnmute(v) {
                    if (!v) return;
                    if (!window.__mytv_click_done) {
                        try { v.muted = true; } catch(e) {}
                        return;
                    }
                    try {
                        if (v.muted || v.defaultMuted || v.hasAttribute('muted')) {
                            v.muted = false;
                            v.defaultMuted = false;
                            v.removeAttribute('muted');
                        }
                        if (v.volume < 1) {
                            v.volume = 1;
                        }
                    } catch(e) {}
                }

                var isReadyNotified = false;
                function notifyReady() {
                    if (isReadyNotified) return;
                    if (!window.__mytv_click_done) return;
                    isReadyNotified = true;
                    if (window.AndroidBridge && window.AndroidBridge.onVideoReady) {
                        window.AndroidBridge.onVideoReady();
                    }
                }

                function checkAndNotify(v) {
                    if (!v) return;
                    if (!window.__mytv_click_done) return;
                    if (!v.paused && (v.currentTime > 0.05 || v.readyState >= 2 || v.videoWidth > 0)) {
                        makeVideoFullscreen(v);
                        enforceUnmute(v);
                        notifyReady();
                    }
                }

                function setupVideo(v) {
                    if (!v) return;
                    try {
                        v.removeAttribute('controls');
                        v.controls = false;
                        v.setAttribute('poster', '');
                        v.poster = '';
                    } catch(e) {}

                    if (!window.__mytv_click_done) {
                        enforceUnmute(v);
                        return;
                    }

                    makeVideoFullscreen(v);
                    enforceUnmute(v);

                    if (v.paused) {
                        var p = v.play();
                        if (p && p.catch) {
                            p.catch(function() {
                                v.muted = true;
                                v.play().then(function() {
                                    setTimeout(function() { enforceUnmute(v); }, 300);
                                }).catch(function(){});
                            });
                        }
                    }

                    checkAndNotify(v);

                    if (!v.__mytv_bound) {
                        v.__mytv_bound = true;
                        ['timeupdate', 'playing', 'canplay', 'loadeddata', 'loadedmetadata'].forEach(function(ev) {
                            v.addEventListener(ev, function() {
                                makeVideoFullscreen(v);
                                enforceUnmute(v);
                                checkAndNotify(v);
                            });
                        });
                        v.addEventListener('volumechange', function() {
                            enforceUnmute(v);
                        });
                    }
                }

                // Handle watch_video_click
                if (hasClick && !window.__mytv_click_started) {
                    window.__mytv_click_started = true;

                    function processClicks(encoded, onComplete) {
                        if (!encoded) { if (onComplete) onComplete(); return; }
                        try {
                            var decoded = atob(decodeURIComponent(encoded));
                            var list = JSON.parse(decoded);
                            if (!list || !list.length) { if (onComplete) onComplete(); return; }
                            var idx = 0;
                            function nextItem() {
                                if (idx >= list.length) {
                                    if (onComplete) onComplete();
                                    return;
                                }
                                var item = list[idx++];
                                var sel = item.selector;
                                var delay = item.delay || 300;
                                var tries = 0;
                                var timer = setInterval(function() {
                                    tries++;
                                    var el = document.querySelector(sel);
                                    if (el) {
                                        clearInterval(timer);
                                        setTimeout(function() {
                                            try {
                                                el.scrollIntoView({ behavior: 'instant', block: 'center' });
                                                el.click();
                                                el.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
                                                el.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true }));
                                                el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
                                                console.log('[mytv] auto-clicked target: ' + sel);
                                            } catch(e) {}
                                            nextItem();
                                        }, delay);
                                    } else if (tries > 60) {
                                        clearInterval(timer);
                                        nextItem();
                                    }
                                }, 250);
                            }
                            nextItem();
                        } catch(e) {
                            if (onComplete) onComplete();
                        }
                    }

                    function onTargetChannelClicked() {
                        setTimeout(function() {
                            window.__mytv_click_done = true;
                            document.documentElement.classList.add('__mytv_fullscreen_active');
                            var waitPoll = setInterval(function() {
                                var v = findBestVideo();
                                if (v) {
                                    setupVideo(v);
                                    if (!v.paused && (v.currentTime > 0.1 || v.readyState >= 2 || v.videoWidth > 0)) {
                                        clearInterval(waitPoll);
                                        notifyReady();
                                    }
                                }
                            }, 200);
                            setTimeout(function() {
                                clearInterval(waitPoll);
                                var v = findBestVideo();
                                if (v) {
                                    setupVideo(v);
                                    notifyReady();
                                }
                            }, 6000);
                        }, 800);
                    }

                    processClicks(c1, function() {
                        if (c2) {
                            processClicks(c2, function() { onTargetChannelClicked(); });
                        } else {
                            onTargetChannelClicked();
                        }
                    });
                }

                var cnt = 0;
                var poll = setInterval(function() {
                    cnt++;
                    var v = findBestVideo();
                    if (v) {
                        setupVideo(v);
                    }
                    if (cnt > 60 && isReadyNotified) {
                        clearInterval(poll);
                    }
                    if (cnt > 100) {
                        clearInterval(poll);
                    }
                }, 250);

                if (!window.__mytv_heartbeat) {
                    window.__mytv_heartbeat = setInterval(function() {
                        if (!window.__mytv_click_done) return;
                        var v = findBestVideo();
                        if (v) {
                            enforceUnmute(v);
                            makeVideoFullscreen(v);
                            if (v.paused) {
                                try { v.play().catch(function(){}); } catch(e) {}
                            }
                        }
                    }, 800);
                }

                document.addEventListener('playing', function(e) {
                    if (e.target && e.target.tagName === 'VIDEO') {
                        setupVideo(e.target);
                    }
                }, true);
            })();
        """.trimIndent()

        view.evaluateJavascript(jsCode, null)
    }

    @OptIn(UnstableApi::class)
    fun updatePlayer() {
        if (context == null) {
            Log.e(TAG, "context == null")
            return
        }

        val ctx = requireContext()
        val playerView = binding.playerView

        val renderersFactory = DefaultRenderersFactory(ctx)
        val playerMediaCodecSelector = PlayerMediaCodecSelector()
        renderersFactory.setMediaCodecSelector(playerMediaCodecSelector)
        renderersFactory.setExtensionRendererMode(
            if (SP.softDecode) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
        )

        player?.release()

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(ctx)
            .setRenderersFactory(renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .build()
        player?.repeatMode = REPEAT_MODE_ALL
        player?.playWhenReady = true
        player?.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val ratio = playerView.measuredWidth.div(playerView.measuredHeight)
                val layoutParams = playerView.layoutParams
                if (ratio < aspectRatio) {
                    layoutParams?.height =
                        (playerView.measuredWidth.div(aspectRatio)).toInt()
                    playerView.layoutParams = layoutParams
                } else if (ratio > aspectRatio) {
                    layoutParams?.width =
                        (playerView.measuredHeight.times(aspectRatio)).toInt()
                    playerView.layoutParams = layoutParams
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                if (playbackState == Player.STATE_ENDED) {
                    val model = tvModel
                    if (model != null && model.isLocalVideo()) {
                        val currentEp = model.videoIndexValue
                        val nextEp = (currentEp + 1) % model.tv.uris.size
                        Log.i(TAG, "Local video ended, advancing from $currentEp to $nextEp")
                        model.setVideoIndex(nextEp)
                        val key = model.getLocalProgressKey()
                        SP.setLocalEpisode(key, nextEp)
                        SP.setLocalPosition(key, 0L)
                        play(model)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)

                if (tvModel == null) {
                    Log.e(TAG, "tvModel == null")
                    return
                }

                val tv = tvModel!!

                if (isPlaying) {
                    tv.confirmSourceType()
                    tv.confirmVideoIndex()
                    tv.setErrInfo("")
                    tv.retryTimes = 0
                } else {
                    Log.i(TAG, "${tv.tv.title} 播放停止")
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    (activity as MainActivity).onPlayEnd()
                }
                super.onPositionDiscontinuity(oldPosition, newPosition, reason)
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)

                if (tvModel == null) {
                    Log.e(TAG, "tvModel == null")
                    return
                }

                val tv = tvModel!!

                if (tv.retryTimes < tv.retryMaxTimes) {
                    var last = true
                    if (tv.getSourceTypeDefault() == SourceType.UNKNOWN) {
                        last = tv.nextSourceType()
                    }
                    tv.setReady(true)
                    if (last) {
                        tv.retryTimes++
                    }
                    Log.i(
                        TAG,
                        "retry ${tv.videoIndex.value} ${tv.getSourceTypeCurrent()} ${tv.retryTimes}/${tv.retryMaxTimes}"
                    )
                } else {
                    if (!tv.isLastVideo()) {
                        tv.nextVideo()
                        tv.setReady(true)
                        tv.retryTimes = 0
                    } else {
                        tv.setErrInfo(R.string.play_error.getString())
                    }
                }
            }
        })

        playerView.player = player
        tvModel?.let {
            play(it)
        }
    }

    fun saveCurrentLocalProgress() {
        val model = tvModel ?: return
        if (model.isLocalVideo()) {
            val pos = player?.currentPosition ?: 0L
            val ep = model.videoIndexValue
            val key = model.getLocalProgressKey()
            SP.setLocalEpisode(key, ep)
            SP.setLocalPosition(key, pos)
            Log.i(TAG, "Saved local progress for $key: ep=$ep, pos=$pos ms")
        }
    }

    @OptIn(UnstableApi::class)
    fun play(tvModel: TVModel) {
        if (this.tvModel != tvModel) {
            saveCurrentLocalProgress()
        }
        this.tvModel = tvModel


        if (tvModel.isLocalVideo()) {
            val key = tvModel.getLocalProgressKey()
            val savedEp = SP.getLocalEpisode(key)
            if (savedEp in 0 until tvModel.tv.uris.size && tvModel.videoIndexValue != savedEp) {
                tvModel.setVideoIndex(savedEp)
            }
        } else {
            val savedIndex = SP.getChannelSourceIndex(tvModel.tv.id)
            if (savedIndex in 0 until tvModel.tv.uris.size && tvModel.videoIndexValue != savedIndex) {
                tvModel.setVideoIndex(savedIndex)
            }
        }
        requestAudioFocus()


        val url = tvModel.getVideoUrl() ?: return
        Log.i(TAG, "play url: $url")

        // If it's a web URL (e.g. yangshipin.cn, cctv.com), play via WebView
        val isWebUrl = (url.startsWith("http://") || url.startsWith("https://")) &&
                !url.contains(".m3u8") && !url.contains(".mp4") && !url.contains(".flv")

        if (isWebUrl) {
            isUsingWebView = true
            player?.stop()
            binding.fullscreenContainer.removeAllViews()
            binding.fullscreenContainer.visibility = View.GONE
            binding.playerView.visibility = View.GONE
            binding.webView.visibility = View.VISIBLE
            binding.webView.alpha = 0f
            cancelVideoWatchdog()
            binding.webView.onResume()
            binding.webView.stopLoading()
            binding.webView.evaluateJavascript(
                "try { document.querySelectorAll('video').forEach(function(v){ v.pause(); v.src = ''; }); } catch(e){}",
                null
            )
            binding.webView.loadUrl(url)
            startVideoWatchdog()
            return
        }

        // Direct stream -> ExoPlayer
        isUsingWebView = false
        cancelVideoWatchdog()
        binding.webView.evaluateJavascript(
            "try { document.querySelectorAll('video, audio').forEach(function(v){ v.pause(); v.muted = true; v.volume = 0; v.removeAttribute('src'); v.load(); }); } catch(e){}",
            null
        )
        binding.webView.stopLoading()
        binding.webView.loadUrl("about:blank")
        binding.webView.onPause()
        binding.webView.visibility = View.GONE
        binding.playerView.visibility = View.VISIBLE

        player?.run {
            while (true) {
                val last = tvModel.isLastVideo()
                val mediaItem = tvModel.getMediaItem()
                if (mediaItem == null) {
                    if (last) {
                        tvModel.setErrInfo(R.string.play_error.getString())
                        break
                    }
                    tvModel.nextVideo()
                    continue
                }
                val mediaSource = tvModel.getMediaSource()
                if (mediaSource != null) {
                    setMediaSource(mediaSource)
                } else {
                    setMediaItem(mediaItem)
                }
                if (tvModel.isLocalVideo()) {
                    repeatMode = Player.REPEAT_MODE_OFF
                    val key = tvModel.getLocalProgressKey()
                    val savedPos = SP.getLocalPosition(key)
                    if (savedPos > 0) {
                        seekTo(savedPos)
                        Log.i(TAG, "Restored local video $key to pos $savedPos ms")
                    }
                } else {
                    repeatMode = REPEAT_MODE_ALL
                }
                prepare()
                break
            }
        }
    }

    @OptIn(UnstableApi::class)
    class PlayerMediaCodecSelector : MediaCodecSelector {
        override fun getDecoderInfos(
            mimeType: String,
            requiresSecureDecoder: Boolean,
            requiresTunnelingDecoder: Boolean
        ): MutableList<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> {
            val infos = MediaCodecUtil.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            )
            if (mimeType == MimeTypes.VIDEO_H265 && !requiresSecureDecoder && !requiresTunnelingDecoder) {
                if (infos.isNotEmpty()) {
                    val infosNew = infos.find { it.name == "c2.android.hevc.decoder" }
                        ?.let { mutableListOf(it) }
                    if (infosNew != null) {
                        return infosNew
                    }
                }
            }
            return infos
        }
    }

    fun showVolume(visibility: Int) {
        binding.icon.visibility = visibility
        binding.volume.visibility = visibility
        hideVolume()
    }

    fun setVolumeMax(volume: Int) {
        binding.volume.max = volume
    }

    fun setVolume(progress: Int, volume: Boolean = false) {
        val context = requireContext()
        binding.volume.progress = progress
        binding.icon.setImageDrawable(
            ContextCompat.getDrawable(
                context,
                if (volume) {
                    if (progress > 0) R.drawable.volume_up_24px else R.drawable.volume_off_24px
                } else {
                    R.drawable.light_mode_24px
                }
            )
        )
    }

    fun hideVolume() {
        handler.removeCallbacks(hideVolumeRunnable)
        handler.postDelayed(hideVolumeRunnable, delayHideVolume)
    }

    fun hideVolumeNow() {
        handler.removeCallbacks(hideVolumeRunnable)
        handler.postDelayed(hideVolumeRunnable, 0)
    }

    private val hideVolumeRunnable = Runnable {
        binding.icon.visibility = View.GONE
        binding.volume.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        if (isUsingWebView) {
            binding.webView.onResume()
        } else if (player?.isPlaying == false) {
            player?.prepare()
            player?.play()
        }
    }

    override fun onPause() {
        super.onPause()
        saveCurrentLocalProgress()
        abandonAudioFocus()
        if (isUsingWebView) {
            binding.webView.onPause()
        } else if (player?.isPlaying == true) {
            player?.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        saveCurrentLocalProgress()
        cancelVideoWatchdog()
        abandonAudioFocus()
        player?.release()
        try {
            binding.webView.stopLoading()
            binding.webView.loadUrl("about:blank")
            binding.webView.clearHistory()
            binding.webView.removeAllViews()
            binding.webView.destroy()
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancelVideoWatchdog()
        _binding = null
    }

    companion object {
        private const val TAG = "PlayerFragment"
    }
}