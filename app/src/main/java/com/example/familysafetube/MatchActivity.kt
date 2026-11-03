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
import com.example.familysafetube.ui.MatchScreen

/**
 * The matching game ("tap two that go together"). Like the quiz it is just screen time: counted as Images
 * time, and it obeys the daily limit, the Images limit, bedtime and the wind-down warnings.
 */
class MatchActivity : AppCompatActivity() {

    private lateinit var usage: UsageTracker.Session
    private val windDown by lazy { WindDownMonitor(this, Policy.CAT_IMAGES) }
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            usage.flush()
            windDown.check()
            val reason = Policy.blockReason(this@MatchActivity, Policy.CAT_IMAGES)
            if (reason != null) {
                if (Policy.lockKind(this@MatchActivity) == LockKind.NONE) Toast.makeText(this@MatchActivity, reason, Toast.LENGTH_LONG).show()
                finish()
            } else {
                handler.postDelayed(this, Policy.nextCheckDelayMs(this@MatchActivity, Policy.CAT_IMAGES))
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
        setContent {
            FamilySafeTubeTheme { MatchScreen(cards = cards, onExit = { finish() }) }
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
