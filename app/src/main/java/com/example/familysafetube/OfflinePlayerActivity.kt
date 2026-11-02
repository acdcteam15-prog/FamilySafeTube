package com.example.familysafetube

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.viewpager2.widget.ViewPager2
import java.io.File

class OfflinePlayerActivity : AppCompatActivity(), VideoAdapter.Host {

    private lateinit var viewPager   : ViewPager2
    private lateinit var usage       : UsageTracker.Session
    private val windDown by lazy { WindDownMonitor(this, Policy.CAT_VIDEOS) }
    private lateinit var tvTimeLeft  : TextView
    private lateinit var tvVideoNum  : TextView
    private lateinit var hudRoot     : FrameLayout
    private var videoAdapter         : VideoAdapter? = null
    private val timerHandler         = Handler(Looper.getMainLooper())
    private val hudHandler           = Handler(Looper.getMainLooper())
    private val checkIntervalMs      = 10_000L
    private var isTouchLocked        = false
    private var lockBtn              : ImageButton? = null

    // ── Shared player: exactly ONE ExoPlayer (one hardware decoder) for the whole session ──
    private var player               : ExoPlayer? = null
    private var activeHolder         : VideoAdapter.VideoViewHolder? = null
    private var files                : List<String> = emptyList()
    private var autoplayNext         = false
    private var loadedIndex          = -1
    private var userPaused           = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            activeHolder?.showBuffering(state == Player.STATE_BUFFERING)
            if (state == Player.STATE_ENDED && autoplayNext && files.size > 1) advanceToNextVideo(files.size)
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // Keep the screen awake only while a video is actually playing.
            if (isPlaying) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else           window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        override fun onPlayerError(error: PlaybackException) {
            Log.e("OfflinePlayer", "ExoPlayer error: ${error.message}")
            activeHolder?.showBuffering(false)
        }
    }

    // ── Usage ticker ──────────────────────────────────────────────────────
    // Real elapsed time + re-check of bedtime / global / per-category limits on every tick.
    private val usageTicker = object : Runnable {
        override fun run() {
            usage.flush()
            windDown.check()
            val left = TimeRules.minutesLeft(Policy.globalLimit(this@OfflinePlayerActivity),
                                             Policy.globalElapsed(this@OfflinePlayerActivity)).toInt()
            tvTimeLeft.text = "⏱ ${left}m"
            tvTimeLeft.setTextColor(when {
                left <= 5  -> Color.parseColor("#FF5252")
                left <= 10 -> Color.parseColor("#FFB300")
                else       -> Color.parseColor("#00E676")
            })
            val reason = Policy.blockReason(this@OfflinePlayerActivity, Policy.CAT_VIDEOS)
            if (reason != null) {
                Toast.makeText(this@OfflinePlayerActivity, reason, Toast.LENGTH_LONG).show()
                cleanUpAndExit()
            } else {
                timerHandler.postDelayed(this, checkIntervalMs)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Full screen ───────────────────────────────────────────────────
        Immersive.enter(this)

        usage = UsageTracker.Session(this, Policy.CAT_VIDEOS)

        // ── Root layout ───────────────────────────────────────────────────
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        viewPager = ViewPager2(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = ViewPager2.ORIENTATION_VERTICAL
        }
        root.addView(viewPager)

        // ── Magic touch overlay ───────────────────────────────────────────
        val overlay = MagicTouchOverlay(this)
        root.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        viewPager.setOnTouchListener { _, event -> overlay.handleTouch(event); false }

        // ── HUD ───────────────────────────────────────────────────────────
        hudRoot = buildHUD(root)
        root.addView(hudRoot)

        setContentView(root)

        // ── Load videos ───────────────────────────────────────────────────
        files = SyncEngine.getLocalFiles(this, "videos")
            .filter {
                it.extension.lowercase() in listOf("mp4", "mkv", "mov", "webm")
                && !it.name.endsWith(".tmp")   // exclude interrupted partial downloads
                && it.length() > 1024L         // exclude zero/near-zero byte corrupt files
            }
            .sortedBy { it.name }
            .map { it.absolutePath }

        if (files.isEmpty()) {
            Toast.makeText(this, getString(R.string.off_no_videos), Toast.LENGTH_LONG).show()
            finish(); return
        }

        autoplayNext = AppGraph.settings.getOr(Keys.AUTOPLAY_NEXT, false)
        videoAdapter = VideoAdapter(files, this)
        viewPager.adapter = videoAdapter
        // MEDIACODEC FIX: do NOT preload offscreen pages — each bound page would create an
        // ExoPlayer in play(); with offscreenPageLimit=1 that means 2 hardware decoders open
        // simultaneously, exhausting the codec slots and causing SIGABRT.
        // ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT (= -1) only keeps the current page alive.
        viewPager.offscreenPageLimit = androidx.viewpager2.widget.ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                playIndex(position)
                tvVideoNum.text = "${position + 1} / ${files.size}"
                showHudBriefly()
            }
        })

        // Playback starts in onResume (after the first layout), so a single code path handles
        // first launch and returning from the background.
        tvVideoNum.text = "1 / ${files.size}"

        // ── Back button ───────────────────────────────────────────────────
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isTouchLocked) {
                    Toast.makeText(this@OfflinePlayerActivity, getString(R.string.off_tap_lock), Toast.LENGTH_SHORT).show()
                } else {
                    cleanUpAndExit()
                }
            }
        })
    }

    // ── HUD builder ───────────────────────────────────────────────────────
    private fun buildHUD(root: FrameLayout): FrameLayout {
        val dp = resources.displayMetrics.density
        val hud = FrameLayout(this)

        // Top bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#BB000000"))
            setPadding((12*dp).toInt(), (10*dp).toInt(), (12*dp).toInt(), (10*dp).toInt())
        }

        tvVideoNum = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        tvTimeLeft = TextView(this).apply {
            setTextColor(Color.parseColor("#00E676"))
            textSize = 14f
            textStyle(android.graphics.Typeface.BOLD)
        }

        // Lock button
        lockBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_lock_idle_lock)
            setColorFilter(Color.parseColor("#99FFFFFF"))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                (40*dp).toInt(), (40*dp).toInt()
            ).also { it.marginStart = (12*dp).toInt() }
            setOnClickListener { toggleLock() }
        }

        topBar.addView(tvVideoNum)
        topBar.addView(tvTimeLeft)
        topBar.addView(lockBtn)

        val topParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        )
        hud.addView(topBar, topParams)

        return hud
    }

    private fun TextView.textStyle(style: Int) {
        setTypeface(typeface, style)
    }

    private fun showHudBriefly() {
        hudRoot.animate().alpha(1f).setDuration(200).start()
        hudHandler.removeCallbacksAndMessages(null)
        hudHandler.postDelayed({
            hudRoot.animate().alpha(0f).setDuration(600).start()
        }, 3_000)
    }

    private fun toggleLock() {
        isTouchLocked = !isTouchLocked
        viewPager.isUserInputEnabled = !isTouchLocked
        lockBtn?.apply {
            setImageResource(
                if (isTouchLocked) android.R.drawable.ic_lock_lock
                else android.R.drawable.ic_lock_idle_lock
            )
            setColorFilter(
                if (isTouchLocked) Color.parseColor("#FF5252")
                else Color.parseColor("#99FFFFFF")
            )
        }
        Toast.makeText(
            this,
            if (isTouchLocked) getString(R.string.screen_locked) else getString(R.string.screen_unlocked),
            Toast.LENGTH_SHORT
        ).show()
    }

    /** Autoplay: go to the next video, wrapping to the first after the last. */
    private fun advanceToNextVideo(count: Int) {
        val next = viewPager.currentItem + 1
        if (next < count) viewPager.setCurrentItem(next, true)
        else              viewPager.setCurrentItem(0, false)
    }

    // ── Player control ────────────────────────────────────────────────────
    private fun findHolder(position: Int): VideoAdapter.VideoViewHolder? {
        val rv = viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView ?: return null
        return rv.findViewHolderForAdapterPosition(position) as? VideoAdapter.VideoViewHolder
    }

    private fun ensurePlayer(): ExoPlayer = player ?: ExoPlayer.Builder(this).build().also { exo ->
        exo.volume = 1f
        // Autoplay on  -> play once, then advance the pager (see playerListener).
        // Autoplay off -> loop the current video forever. A single video always loops.
        exo.repeatMode =
            if (!autoplayNext || files.size <= 1) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        exo.addListener(playerListener)
        player = exo
    }

    /** Plays [position] on the shared player, moving the player's surface to that page. */
    private fun playIndex(position: Int, retryIfNotReady: Boolean = true) {
        if (position !in files.indices) return
        val holder = findHolder(position)
        if (holder == null) {
            // The page may not be laid out yet; try once more after the current layout pass.
            if (retryIfNotReady) viewPager.post {
                if (viewPager.currentItem == position) playIndex(position, retryIfNotReady = false)
            }
            return
        }
        val exo = ensurePlayer()

        // Already showing and playing this video on this page (e.g. first-resume + page callback).
        if (loadedIndex == position && activeHolder === holder && exo.playbackState != Player.STATE_IDLE) return

        // Detach from the previous page BEFORE attaching to the new one.
        if (activeHolder !== holder) activeHolder?.detachPlayer()
        activeHolder = holder
        holder.attachPlayer(exo)

        val file = File(files[position])
        if (!file.exists()) {
            Log.e("OfflinePlayer", "File not found: ${file.path}")
            holder.showBuffering(false)
            return
        }
        userPaused = false
        holder.showPaused(false)
        holder.showBuffering(true)
        exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        exo.prepare()
        exo.playWhenReady = true
        loadedIndex = position
    }

    /** Tap on a page: pause / resume the active video. */
    override fun onVideoTapped(holder: VideoAdapter.VideoViewHolder) {
        if (holder !== activeHolder) return
        val exo = player ?: return
        if (exo.isPlaying) {
            exo.pause()
            userPaused = true
            holder.showPaused(true)
        } else if (userPaused) {
            exo.play()
            userPaused = false
            holder.showPaused(false)
        }
    }

    /** Frees the decoder and clears the keep-screen-on flag. Safe to call repeatedly. */
    private fun releasePlayer() {
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activeHolder?.detachPlayer()
        activeHolder = null
        player?.let {
            it.removeListener(playerListener)
            it.release()
        }
        player = null
        loadedIndex = -1
        userPaused = false
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────
    override fun onResume() {
        super.onResume()
        // Bedtime / a limit may have started while the app was in the background.
        val reason = Policy.blockReason(this, Policy.CAT_VIDEOS)
        if (reason != null) {
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        usage.start()
        timerHandler.postDelayed(usageTicker, checkIntervalMs)
        showHudBriefly()
        // (Re)start playback of the current page on a fresh player once layout is settled.
        viewPager.post { playIndex(viewPager.currentItem) }
    }

    override fun onPause() {
        super.onPause()
        usage.stop()
        timerHandler.removeCallbacks(usageTicker)
        hudHandler.removeCallbacksAndMessages(null)
        releasePlayer()        // free the hardware decoder while in the background
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)

        val isLandscape = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        // Re-apply fullscreen flags — some Samsung ROMs reset them on rotation
        Immersive.enter(this)

        // In landscape the video should fill the full width; ExoPlayer's PlayerView
        // handles aspect ratio internally via RESIZE_MODE_ZOOM set in VideoAdapter,
        // but the ViewPager itself needs to know the new dimensions.
        // Forcing a layout pass on the viewpager is sufficient.
        viewPager.requestLayout()

        // Reposition HUD: in landscape move it slightly to avoid notch on left side
        val dp = resources.displayMetrics.density
        hudRoot.setPadding(
            if (isLandscape) (24 * dp).toInt() else 0,
            0,
            if (isLandscape) (24 * dp).toInt() else 0,
            0
        )
    }

    private fun cleanUpAndExit() {
        timerHandler.removeCallbacks(usageTicker)
        hudHandler.removeCallbacksAndMessages(null)
        releasePlayer()
        finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Immersive.enter(this)
    }

}
