package com.example.familysafetube

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.animation.*
import android.widget.FrameLayout
import android.widget.TextView

class MagicTouchOverlay(context: Context) : FrameLayout(context) {

    // Emoji burst pool — kids love these
    private val particles = listOf(
        "⭐", "🌟", "✨", "💫", "🎉", "🎊", "❤️", "💛",
        "💚", "💙", "💜", "🌈", "🦋", "🌸", "🍭", "🎈"
    )

    // Multiple bursts per touch for a more spectacular effect
    private val burstCount = 5

    fun handleTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> burst(event.x, event.y)
            MotionEvent.ACTION_MOVE -> {
                // Sparse trail on drag
                if ((0..3).random() == 0) burst(event.x, event.y, count = 2)
            }
        }
    }

    private fun burst(cx: Float, cy: Float, count: Int = burstCount) {
        repeat(count) {
            val offsetX = (-60..60).random().toFloat()
            val offsetY = (-60..60).random().toFloat()
            spawnParticle(cx + offsetX, cy + offsetY)
        }
    }

    private fun spawnParticle(x: Float, y: Float) {
        val size = (80..160).random()
        val emoji = particles.random()

        val tv = TextView(context).apply {
            text     = emoji
            textSize = (size / 4f)
            val params = LayoutParams(size, size)
            params.leftMargin = (x - size / 2).toInt()
            params.topMargin  = (y - size / 2).toInt()
            layoutParams = params
        }

        addView(tv)

        // Random arc trajectory
        val endX   = (-200..200).random().toFloat()
        val endY   = (-300..-80).random().toFloat()   // always flies upward
        val rotate = (-360..360).random().toFloat()

        val anim = AnimationSet(false).apply {
            addAnimation(TranslateAnimation(0f, endX, 0f, endY).apply {
                interpolator = DecelerateInterpolator(1.5f)
            })
            addAnimation(RotateAnimation(
                0f, rotate,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
            ))
            addAnimation(ScaleAnimation(
                0.3f, 1.4f, 0.3f, 1.4f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
            ).apply { interpolator = OvershootInterpolator(2f) })
            addAnimation(AlphaAnimation(1f, 0f).apply {
                startOffset = 300
                interpolator = AccelerateInterpolator()
            })
            duration  = (500..900).random().toLong()
            fillAfter = false
        }

        anim.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(a: Animation?) {}
            override fun onAnimationRepeat(a: Animation?) {}
            override fun onAnimationEnd(a: Animation?) { post { removeView(tv) } }
        })

        tv.startAnimation(anim)
    }
}
