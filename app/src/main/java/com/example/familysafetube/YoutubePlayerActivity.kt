package com.example.familysafetube

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class YoutubePlayerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var gestureDetector: GestureDetector
    private lateinit var usage: UsageTracker.Session
    private val windDown by lazy { WindDownMonitor(this, Policy.CAT_YOUTUBE) }

    // HUD views
    private lateinit var hudContainer: FrameLayout
    private lateinit var tvVideoCounter: TextView
    private lateinit var tvTimeLeft: TextView
    private lateinit var dotsContainer: LinearLayout

    private val timerHandler  = Handler(Looper.getMainLooper())
    private val hudHandler    = Handler(Looper.getMainLooper())
    private val checkInterval = 10_000L   // 10 s usage tick

    private var videoIds        = listOf<String>()
    private var shuffledIds     = listOf<String>()   // one shuffled copy per loop
    private var currentIndex    = 0
    private var isTransitioning = false
    private var hudVisible      = true

    // ── Usage ticker ────────────────────────────────────────────────────────
    // Measures real elapsed time (not fixed 10 s ticks) and re-checks bedtime, the global limit
    // and the YouTube limit every tick, so a session that is already open is stopped too.
    private val usageTicker = object : Runnable {
        override fun run() {
            usage.flush()
            windDown.check()
            updateHUD()
            val reason = Policy.blockReason(this@YoutubePlayerActivity, Policy.CAT_YOUTUBE)
            if (reason != null) {
                Toast.makeText(this@YoutubePlayerActivity, reason, Toast.LENGTH_LONG).show()
                cleanUpAndExit()
            } else {
                timerHandler.postDelayed(this, checkInterval)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Immersive.enter(this)
        usage = UsageTracker.Session(this, Policy.CAT_YOUTUBE)

        // ── Build layout programmatically ────────────────────────────────
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        webView = WebView(this)
        root.addView(webView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        hudContainer = buildHUD()
        root.addView(hudContainer)

        val overlay = MagicTouchOverlay(this)
        root.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)

        setupWebView()
        setupGestures(root)
        loadVideoList()
        scheduleHudAutoHide()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = cleanUpAndExit()
        })
    }

    // ── HUD builder ─────────────────────────────────────────────────────────
    private fun buildHUD(): FrameLayout {
        val dp = resources.displayMetrics.density

        val container = FrameLayout(this)

        // Top bar — semi-transparent dark gradient
        val topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.setPadding((12*dp).toInt(), (8*dp).toInt(), (12*dp).toInt(), (8*dp).toInt())
        topBar.setBackgroundColor(Color.parseColor("#CC000000"))

        tvVideoCounter = TextView(this)
        tvVideoCounter.setTextColor(Color.WHITE)
        tvVideoCounter.textSize = 14f
        tvVideoCounter.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        tvTimeLeft = TextView(this)
        tvTimeLeft.setTextColor(Color.parseColor("#FFD700"))  // gold
        tvTimeLeft.textSize = 14f

        topBar.addView(tvVideoCounter)
        topBar.addView(tvTimeLeft)

        val topBarParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        topBarParams.gravity = android.view.Gravity.TOP
        container.addView(topBar, topBarParams)

        // Swipe hint at bottom
        dotsContainer = LinearLayout(this)
        dotsContainer.orientation = LinearLayout.VERTICAL
        dotsContainer.gravity = android.view.Gravity.CENTER_HORIZONTAL
        dotsContainer.setPadding(0, 0, 0, (24*dp).toInt())
        dotsContainer.setBackgroundColor(Color.TRANSPARENT)

        val hintText = TextView(this)
        hintText.text = getString(R.string.yt_hint)
        hintText.setTextColor(Color.parseColor("#99FFFFFF"))
        hintText.textSize = 12f
        hintText.gravity = android.view.Gravity.CENTER
        dotsContainer.addView(hintText)

        val bottomParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        bottomParams.gravity = android.view.Gravity.BOTTOM
        container.addView(dotsContainer, bottomParams)

        return container
    }

    private fun scheduleHudAutoHide() {
        hudHandler.removeCallbacksAndMessages(null)
        hudHandler.postDelayed({
            hudContainer.animate().alpha(0f).setDuration(500).start()
            hudVisible = false
        }, 3_000)
    }

    private fun showHudBriefly() {
        hudContainer.animate().alpha(1f).setDuration(200).start()
        hudVisible = true
        scheduleHudAutoHide()
    }

    private fun updateHUD() {
        val leftMins = TimeRules.minutesLeft(Policy.globalLimit(this), Policy.globalElapsed(this))
        tvVideoCounter.text = "▶ ${currentIndex + 1} / ${shuffledIds.size}"
        tvTimeLeft.text     = getString(R.string.yt_time_left, leftMins.toInt())
    }

    // ── WebView ──────────────────────────────────────────────────────────────
    private fun setupWebView() {
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.settings.apply {
            javaScriptEnabled             = true
            domStorageEnabled             = true
            databaseEnabled               = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode              = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString               = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                injectCleanupCSS(view)
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val req = request ?: return true
                val url = req.url.toString()
                if (url == "familysafetube://next") {
                    playNext(animate = true)
                    return true
                }
                // Keep the child inside the curated list: block taps that leave it (channels,
                // recommendations, other sites). Returning true = "handled", i.e. blocked.
                val allowed = NavigationPolicy.isAllowed(url, req.hasGesture(), videoIds.toSet())
                if (!allowed) android.util.Log.w("YoutubePlayer", "Blocked navigation to $url")
                return !allowed
            }
        }
        webView.webChromeClient = WebChromeClient()
    }

    private fun injectCleanupCSS(view: WebView?) {
        // Hide ALL YouTube chrome — ads, headers, buttons, recommendations
        val css = """
            .ytp-chrome-top, .ytp-show-cards-title, .ytp-share-button,
            .ytp-pause-overlay, .ytp-youtube-button, .ytp-ce-element,
            .iv-click-tracking, .iv-promo, header, footer, #header-bar,
            .ytp-title-link, .ytp-watermark, .shorts-external-container,
            .reel-player-header-container, .reel-player-actions-container,
            .ytp-endscreen-content, ytd-watch-next-secondary-results-renderer,
            #related, .ytp-suggestion-set, #player-ads, .ad-showing,
            ytm-companion-ad-renderer, ytm-promoted-sparkles-web-renderer,
            .ytp-ad-overlay-container, .ytp-ad-text-overlay,
            #shorts-inner-container > ytm-shorts-lockup-view-model { display:none!important; }
            video { object-fit: cover!important; }
        """.trimIndent().replace("\n", " ")

        val js = """
            (function() {
                var s = document.createElement('style');
                s.innerHTML = '$css';
                document.head.appendChild(s);

                // Auto-advance when video ends
                setInterval(function() {
                    var v = document.querySelector('video');
                    if (v && v.ended) { window.location.href = 'familysafetube://next'; }
                }, 2000);

                // Remove injected ads dynamically
                var obs = new MutationObserver(function() {
                    document.querySelectorAll('.ad-showing, .ytp-ad-overlay-container').forEach(function(el) {
                        el.style.display = 'none';
                    });
                });
                obs.observe(document.body, { childList:true, subtree:true });
            })()
        """.trimIndent()

        view?.evaluateJavascript(js, null)
    }

    // ── Gestures ─────────────────────────────────────────────────────────────
    private fun setupGestures(root: View) {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val MIN_SWIPE_DIST = 80f
            private val MIN_SWIPE_VEL  = 80f

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                showHudBriefly()
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent,
                                 vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dy = e2.y - e1.y
                if (Math.abs(dy) > MIN_SWIPE_DIST && Math.abs(vy) > MIN_SWIPE_VEL) {
                    if (dy < 0) playNext(animate = true)
                    else        playPrevious(animate = true)
                    return true
                }
                return false
            }
        })

        // Route touches through gesture detector
        webView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    // ── Video list ───────────────────────────────────────────────────────────
    private fun loadVideoList() {
        val configFile = File(filesDir, "config_youtube.txt")
        if (!configFile.exists() || configFile.length() == 0L) {
            Toast.makeText(this, getString(R.string.yt_config_missing), Toast.LENGTH_LONG).show()
            finish(); return
        }

        videoIds = configFile.readText().lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { extractVideoId(it) }

        if (videoIds.isEmpty()) {
            Toast.makeText(this, getString(R.string.yt_list_empty), Toast.LENGTH_LONG).show()
            finish(); return
        }

        // Start first shuffled loop
        newShuffledLoop()
    }

    private fun extractVideoId(line: String): String? {
        return when {
            // Already a bare 11-char ID
            line.length == 11 && !line.contains("/") -> line
            line.contains("shorts/") ->
                line.substringAfter("shorts/").take(11).takeIf { it.length == 11 }
            line.contains("v=") ->
                line.substringAfter("v=").take(11).takeIf { it.length == 11 }
            line.contains("youtu.be/") ->
                line.substringAfter("youtu.be/").take(11).takeIf { it.length == 11 }
            else -> null
        }
    }

    // Create a new shuffled pass through all videos
    private fun newShuffledLoop() {
        shuffledIds = videoIds.shuffled()
        currentIndex = 0
        loadVideo(shuffledIds[currentIndex])
    }

    private fun playNext(animate: Boolean) {
        if (isTransitioning) return
        isTransitioning = true
        currentIndex++
        if (currentIndex >= shuffledIds.size) newShuffledLoop()
        else loadVideo(shuffledIds[currentIndex], slideUp = animate)
        showHudBriefly()
        updateHUD()
    }

    private fun playPrevious(animate: Boolean) {
        if (isTransitioning) return
        isTransitioning = true
        if (currentIndex > 0) {
            currentIndex--
            loadVideo(shuffledIds[currentIndex], slideUp = false)
        }
        showHudBriefly()
        updateHUD()
    }

    private fun loadVideo(videoId: String, slideUp: Boolean = false) {
        if (slideUp) {
            webView.animate().translationY(-80f).alpha(0.6f).setDuration(150)
                .withEndAction {
                    webView.animate().translationY(0f).alpha(1f).setDuration(150).start()
                }.start()
        }

        val headers = hashMapOf("Referer" to "https://www.youtube.com")
        // Use shorts URL — plays inline without full YouTube page chrome
        webView.loadUrl("https://www.youtube.com/shorts/$videoId", headers)

        Handler(Looper.getMainLooper()).postDelayed({ isTransitioning = false }, 400)
        updateHUD()
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────
    override fun onResume() {
        super.onResume()
        webView.onResume()
        // Bedtime / a limit may have started while the app was in the background.
        val reason = Policy.blockReason(this, Policy.CAT_YOUTUBE)
        if (reason != null) {
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        usage.start()
        timerHandler.postDelayed(usageTicker, checkInterval)
        updateHUD()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()          // stops audio/video when the child leaves the app
        usage.stop()
        timerHandler.removeCallbacks(usageTicker)
        hudHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    private fun cleanUpAndExit() {
        timerHandler.removeCallbacks(usageTicker)
        hudHandler.removeCallbacksAndMessages(null)
        finish()                   // onDestroy tears the WebView down exactly once
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Immersive.enter(this)
    }

}
