package com.example.familysafetube

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import java.util.Locale
import kotlin.math.sqrt

class ImagePlayerActivity : AppCompatActivity(),
    TextToSpeech.OnInitListener, SensorEventListener {

    private lateinit var viewPager    : ViewPager2
    private lateinit var usage        : UsageTracker.Session
    private val windDown by lazy { WindDownMonitor(this, Policy.CAT_IMAGES) }
    private var lastShakeMs           = 0L
    private lateinit var tvTimeLeft   : TextView
    private lateinit var tvPageNum    : TextView
    private lateinit var tvCategory   : TextView
    private lateinit var hudRoot      : FrameLayout
    private var imageAdapter          : ImageAdapter? = null

    private val timerHandler          = Handler(Looper.getMainLooper())
    private val hudHandler            = Handler(Looper.getMainLooper())
    private val checkIntervalMs       = 10_000L
    private var tts                   : TextToSpeech? = null
    private var syncCategory          = "other"
    private var localFiles            = listOf<java.io.File>()

    // Shake detection
    private var sensorManager         : SensorManager? = null
    private var accel                 = 10f
    private var accelCurrent          = SensorManager.GRAVITY_EARTH
    private var accelLast             = SensorManager.GRAVITY_EARTH

    // Usage for all four image sub-categories is recorded under "images" so it matches the
    // "Images limit" setting (previously it was recorded under animals/letters/... and that limit
    // never triggered).
    private val usageTicker = object : Runnable {
        override fun run() {
            usage.flush()
            windDown.check()
            val left = TimeRules.minutesLeft(Policy.globalLimit(this@ImagePlayerActivity),
                                             Policy.globalElapsed(this@ImagePlayerActivity)).let { TimeRules.minutesLabel(it) }
            tvTimeLeft.text = "⏱ ${left}m"
            tvTimeLeft.setTextColor(when {
                left <= 5  -> Color.parseColor("#FF5252")
                left <= 10 -> Color.parseColor("#FFB300")
                else       -> Color.parseColor("#00E676")
            })
            val reason = Policy.blockReason(this@ImagePlayerActivity, Policy.CAT_IMAGES)
            if (reason != null) {
                // A full lock (time up, bedtime) needs no words: the lock screen takes over. Only a category limit explains itself.
                if (Policy.lockKind(this@ImagePlayerActivity) == LockKind.NONE) Toast.makeText(this@ImagePlayerActivity, reason, Toast.LENGTH_LONG).show()
                finish()
            } else {
                // look again exactly when the time runs out (not up to 10 s late)
                timerHandler.postDelayed(this, Policy.nextCheckDelayMs(this@ImagePlayerActivity, Policy.CAT_IMAGES))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Immersive.enter(this)

        usage        = UsageTracker.Session(this, Policy.CAT_IMAGES)
        val displayCategory = intent.getStringExtra("CATEGORY_NAME") ?: "Images"
        syncCategory = intent.getStringExtra("SYNC_CATEGORY") ?: "other"

        // ── Root ──────────────────────────────────────────────────────────
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#070B14")) }

        viewPager = ViewPager2(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = ViewPager2.ORIENTATION_VERTICAL
        }
        root.addView(viewPager)

        // Magic stars overlay
        val overlay = MagicTouchOverlay(this)
        root.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        viewPager.setOnTouchListener { _, event -> overlay.handleTouch(event); false }

        // HUD
        hudRoot = buildHUD(displayCategory)
        root.addView(hudRoot)

        setContentView(root)

        // ── TTS (Arabic preferred) ────────────────────────────────────────
        val isEducational = syncCategory.lowercase() in listOf("animals", "letters", "veg&fruits")
        if (isEducational && AppGraph.settings.getOr(Keys.VOICE_OVER, true)) {
            tts = TextToSpeech(this, this)
        }

        // ── Shake sensor ──────────────────────────────────────────────────
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // ── Load images ───────────────────────────────────────────────────
        localFiles = SyncEngine.getLocalFiles(this, syncCategory)
            .filter { it.extension.lowercase() in listOf("jpg","jpeg","png","webp","gif") }
            .sortedBy { it.name }

        if (localFiles.isEmpty()) {
            Toast.makeText(this, "No images in $displayCategory. Please SYNC.", Toast.LENGTH_LONG).show()
            finish(); return
        }

        imageAdapter = ImageAdapter(localFiles.map { it.absolutePath }, syncCategory)
        viewPager.adapter = imageAdapter

        // Infinite scroll: start in the middle
        val startPos = (Int.MAX_VALUE / 2) - (Int.MAX_VALUE / 2 % localFiles.size)
        viewPager.setCurrentItem(startPos, false)
        updatePageNum(startPos)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePageNum(position)
                showHudBriefly()

                // TTS: speak file name
                if (isEducational && AppGraph.settings.getOr(Keys.VOICE_OVER, true)) {
                    val actual   = position % localFiles.size
                    val fileName = localFiles[actual].nameWithoutExtension
                        .substringAfter("_").replace("_", " ")
                    speak(fileName)
                }
            }
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
    }

    // ── HUD ───────────────────────────────────────────────────────────────
    private fun buildHUD(categoryName: String): FrameLayout {
        val dp  = resources.displayMetrics.density
        val hud = FrameLayout(this)

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#BB000000"))
            setPadding((12*dp).toInt(), (10*dp).toInt(), (12*dp).toInt(), (10*dp).toInt())
        }

        tvCategory = TextView(this).apply {
            text      = categoryName
            setTextColor(Color.parseColor("#4F8EF7"))
            textSize  = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        tvPageNum = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
        }

        tvTimeLeft = TextView(this).apply {
            setTextColor(Color.parseColor("#00E676"))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginStart = (16*dp).toInt() }
        }

        topBar.addView(tvCategory)
        topBar.addView(tvPageNum)
        topBar.addView(tvTimeLeft)

        val topParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        )
        hud.addView(topBar, topParams)
        return hud
    }

    private fun updatePageNum(position: Int) {
        val actual = if (localFiles.isNotEmpty()) position % localFiles.size else 0
        tvPageNum.text = "${actual + 1} / ${localFiles.size}"
    }

    private fun showHudBriefly() {
        hudRoot.animate().alpha(1f).setDuration(200).start()
        hudHandler.removeCallbacksAndMessages(null)
        hudHandler.postDelayed({
            hudRoot.animate().alpha(0f).setDuration(700).start()
        }, 3_000)
    }

    // ── TTS ───────────────────────────────────────────────────────────────
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val arabic = Locale("ar")
            if (tts?.setLanguage(arabic) == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.US
            }
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    // ── Shake to shuffle ──────────────────────────────────────────────────
    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
        accelLast    = accelCurrent
        accelCurrent = sqrt((x*x + y*y + z*z).toDouble()).toFloat()
        val delta    = accelCurrent - accelLast
        accel        = accel * 0.9f + delta
        val nowMs = android.os.SystemClock.elapsedRealtime()
        if (accel > 12 && localFiles.isNotEmpty() && nowMs - lastShakeMs > 1_500) {
            lastShakeMs = nowMs
            val randomPos = (Int.MAX_VALUE / 2) -
                (Int.MAX_VALUE / 2 % localFiles.size) +
                (0 until localFiles.size).random()
            viewPager.setCurrentItem(randomPos, true)
            showShuffleFeedback()
        }
    }

    private fun showShuffleFeedback() {
        Toast.makeText(this, getString(R.string.img_shuffled), Toast.LENGTH_SHORT).show()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ── Lifecycle ─────────────────────────────────────────────────────────
    override fun onResume() {
        super.onResume()
        // FIX #3: Keep screen awake while viewing images
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sensorManager?.registerListener(
            this,
            sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL
        )
        // Bedtime / a limit may have started while the app was in the background.
        val reason = Policy.blockReason(this, Policy.CAT_IMAGES)
        if (reason != null) {
            if (Policy.lockKind(this) == LockKind.NONE) Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        usage.start()
        timerHandler.postDelayed(usageTicker, Policy.nextCheckDelayMs(this, Policy.CAT_IMAGES))
        showHudBriefly()
    }

    override fun onPause() {
        super.onPause()
        usage.stop()
        // FIX #3: Release wake lock when leaving
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sensorManager?.unregisterListener(this)
        timerHandler.removeCallbacks(usageTicker)
        hudHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        tts?.stop(); tts?.shutdown()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Immersive.enter(this)
    }

}
