package com.lizongying.mytv0

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
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
            if (isUsingWebView && _binding != null) {
                binding.webView.alpha = 1f
                tvModel?.setErrInfo("")
                injectPlayerScript(binding.webView)
            }
        }
        handler.postDelayed(watchdogRunnable!!, 2000)
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
        setupWebView()
        updatePlayer()
        (activity as MainActivity).ready(TAG)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        webView.setBackgroundColor(android.graphics.Color.BLACK)
        webView.alpha = 0f
        webView.isFocusable = false
        webView.isFocusableInTouchMode = false
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
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
                handler.postDelayed({
                    if (isUsingWebView && _binding != null && binding.webView.alpha < 1f) {
                        onVideoPlaying()
                    }
                }, 1500)
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
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress > 25) {
                    injectPlayerScript(view)
                }
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
            video::-webkit-media-controls-fullscreen-button {
                display: none !important;
            }
            header, .header, .nav, footer, .footer, .recommend, .comment,
            .aside, .sidebar, .user-info, .head, .bottom, .guide, .download-bar,
            .share-box, .banner, .video-info, .player-control-top, .app-guide,
            #header, #footer, .top-nav, .nav-wrap, .login-btn, .logo, .poster-box,
            .txp_none, .txp_shadow, .txp_layer_title, .txp_layer_controls,
            .txp_bottom_shadow, .txp_top_shadow, .mod_player_head, .mod_player_side,
            .home-side-bar, .channel-side-bar, .live-channel-list, .side-bar-wrap,
            .ysp-home-sidebar, .sidebar-content, .cctv-side-bar, [class*="sidebar"], [class*="SideBar"] {
                display: none !important;
            }
            html, body, #app, #root, .app, .main-wrap, .page-wrap {
                background: #000 !important;
                background-color: #000 !important;
                margin: 0 !important;
                padding: 0 !important;
                overflow: hidden !important;
                width: 100vw !important;
                height: 100vh !important;
            }
            #player, .player, .player-container, .mod_player, .txp_player {
                position: fixed !important;
                top: 0 !important;
                left: 0 !important;
                width: 100vw !important;
                height: 100vh !important;
                z-index: 100000 !important;
                background: #000 !important;
            }
            video {
                position: fixed !important;
                top: 0 !important;
                left: 0 !important;
                width: 100vw !important;
                height: 100vh !important;
                z-index: 2147483647 !important;
                object-fit: contain !important;
                background: #000 !important;
            }
        """.trimIndent().replace("\n", " ").replace("'", "\\'")

        val jsCode = """
            (function() {
                var styleId = 'mytv-clean-style';
                var s = document.getElementById(styleId);
                if (!s) {
                    s = document.createElement('style');
                    s.id = styleId;
                    s.innerHTML = '$css';
                    (document.head || document.documentElement).appendChild(s);
                }

                function setupVideo(v) {
                    if (!v) return;
                    v.removeAttribute('controls');
                    v.controls = false;
                    v.setAttribute('poster', '');
                    v.poster = '';
                    v.muted = false;
                    if (v.paused) {
                        var p = v.play();
                        if (p && p.catch) {
                            p.catch(function() {
                                v.muted = true;
                                v.play().then(function() {
                                    setTimeout(function() { v.muted = false; }, 300);
                                }).catch(function(){});
                            });
                        }
                    }
                    if ((v.readyState >= 2 && v.currentTime > 0.05) || (!v.paused && v.currentTime > 0) || v.videoWidth > 0) {
                        if (window.AndroidBridge && window.AndroidBridge.onVideoReady) {
                            window.AndroidBridge.onVideoReady();
                        }
                    }
                    if (!v.__mytv_bound) {
                        v.__mytv_bound = true;
                        ['playing', 'timeupdate', 'canplay', 'loadeddata'].forEach(function(ev) {
                            v.addEventListener(ev, function() {
                                if (window.AndroidBridge && window.AndroidBridge.onVideoReady) {
                                    window.AndroidBridge.onVideoReady();
                                }
                            });
                        });
                    }
                }

                var cnt = 0;
                var poll = setInterval(function() {
                    cnt++;
                    var v = document.querySelector('video');
                    if (v) {
                        setupVideo(v);
                        if (v.currentTime > 0.5) {
                            clearInterval(poll);
                        }
                    }
                    if (cnt > 40) {
                        clearInterval(poll);
                    }
                }, 250);

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

        player = ExoPlayer.Builder(ctx)
            .setRenderersFactory(renderersFactory)
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

    @OptIn(UnstableApi::class)
    fun play(tvModel: TVModel) {
        this.tvModel = tvModel
        val url = tvModel.getVideoUrl() ?: return
        Log.i(TAG, "play url: $url")

        // If it's a web URL (e.g. yangshipin.cn, cctv.com), play via WebView
        val isWebUrl = (url.startsWith("http://") || url.startsWith("https://")) &&
                !url.contains(".m3u8") && !url.contains(".mp4") && !url.contains(".flv")

        if (isWebUrl) {
            isUsingWebView = true
            player?.stop()
            binding.playerView.visibility = View.GONE
            binding.webView.visibility = View.VISIBLE
            binding.webView.alpha = 0f
            cancelVideoWatchdog()
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
        binding.webView.stopLoading()
        binding.webView.evaluateJavascript(
            "try { document.querySelectorAll('video').forEach(function(v){ v.pause(); v.src = ''; }); } catch(e){}",
            null
        )
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
        if (isUsingWebView) {
            binding.webView.onPause()
        } else if (player?.isPlaying == true) {
            player?.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelVideoWatchdog()
        player?.release()
        try {
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