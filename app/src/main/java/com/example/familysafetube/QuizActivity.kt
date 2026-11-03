package com.example.familysafetube

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.familysafetube.ui.FamilySafeTubeTheme
import com.example.familysafetube.ui.QuizScreen

/**
 * "Tap the right picture" game built from the synced animal / fruit-and-vegetable pictures.
 *
 * Screen-time rule: the quiz is just another kind of screen time. It is counted as Images time and
 * obeys the daily limit, the Images limit, bedtime and the wind-down warnings, exactly like Images mode.
 * (Earning extra minutes by playing was removed.)
 */
class QuizActivity : AppCompatActivity() {

    private lateinit var usage: UsageTracker.Session
    private val windDown by lazy { WindDownMonitor(this, Policy.CAT_IMAGES) }
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            usage.flush()
            windDown.check()
            val reason = Policy.blockReason(this@QuizActivity, Policy.CAT_IMAGES)
            if (reason != null) {
                if (Policy.lockKind(this@QuizActivity) == LockKind.NONE) Toast.makeText(this@QuizActivity, reason, Toast.LENGTH_LONG).show()
                finish()
            } else {
                handler.postDelayed(this, Policy.nextCheckDelayMs(this@QuizActivity, Policy.CAT_IMAGES))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        usage = UsageTracker.Session(this, Policy.CAT_IMAGES)

        val cards = QuizRepository.loadCards(this)
        if (!QuizEngine.isPlayable(cards)) {
            Toast.makeText(this, getString(R.string.quiz_not_enough), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContent {
            FamilySafeTubeTheme { QuizScreen(cards = cards, onExit = { finish() }) }
        }
    }

    override fun onResume() {
        super.onResume()
        val reason = Policy.blockReason(this, Policy.CAT_IMAGES)
        if (reason != null) {
            if (Policy.lockKind(this) == LockKind.NONE) Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        usage.start()
        handler.postDelayed(ticker, Policy.nextCheckDelayMs(this, Policy.CAT_IMAGES))
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        usage.stop()
    }
}
