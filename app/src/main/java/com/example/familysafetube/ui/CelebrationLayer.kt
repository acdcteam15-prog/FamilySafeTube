package com.example.familysafetube.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import com.example.familysafetube.Celebration

private val PALETTE = listOf(
    Color(0xFFFF5252), Color(0xFFFFB300), Color(0xFFFFEB3B), Color(0xFF00E676),
    Color(0xFF29B6F6), Color(0xFF7C4DFF), Color(0xFFFF4081), Color(0xFFFFFFFF)
)

/** Seconds since the celebration started, updated on every frame. Read it only inside draw/graphicsLayer blocks. */
@Composable
fun rememberCelebrationClock(): State<Float> {
    val time = remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> time.value = (now - start) / 1_000_000_000f }
        }
    }
    return time
}

/**
 * Fireworks bursts in the upper half, with confetti and five-pointed stars falling over the whole screen.
 * All positions come from [Celebration] (pure, tested); this only draws them. It takes no touches.
 */
@Composable
fun CelebrationLayer(time: State<Float>, modifier: Modifier = Modifier) {
    val pieces = remember { Celebration.pieces(70) }
    Canvas(modifier) {
        val t = time.value
        val w = size.width
        val h = size.height
        val unit = size.minDimension / 100f                    // about 1 % of the short side

        // fireworks first, so the confetti falls in front of them
        for ((burst, age) in Celebration.activeBursts(t)) {
            val (ox, oy) = Celebration.burstOrigin(burst)
            val radius = size.minDimension * 0.30f
            val color = PALETTE[Celebration.burstColor(burst)]
            for (i in 0 until Celebration.SPARKS_PER_BURST) {
                val spark = Celebration.sparkAt(burst, i, age) ?: continue
                drawCircle(
                    color = color.copy(alpha = spark.alpha),
                    radius = 3.2f * unit * spark.sizeFactor,
                    center = Offset(ox * w + spark.dx * radius, oy * h + spark.dy * radius)
                )
            }
        }

        for (p in pieces) {
            val cx = Celebration.swayFraction(p, t) * w
            val cy = Celebration.fallFraction(p, t) * h
            val color = PALETTE[p.colorIndex]
            val s = 3.2f * unit * p.size
            rotate(degrees = Celebration.rotationDeg(p, t), pivot = Offset(cx, cy)) {
                if (p.star) {
                    val path = Path()
                    Celebration.starPoints(cx, cy, s * 1.6f).forEachIndexed { i, (x, y) ->
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(path, color)
                } else {
                    drawRect(color = color, topLeft = Offset(cx - s / 2f, cy - s * 0.3f), size = Size(s, s * 0.6f))
                }
            }
        }
    }
}

/** The logo hopping and wiggling; it squashes a little each time it lands. */
@Composable
fun DancingLogo(time: State<Float>, modifier: Modifier = Modifier) {
    AppLogo(
        modifier.graphicsLayer {
            val d = Celebration.dance(time.value)
            transformOrigin = TransformOrigin(0.5f, 1f)         // squash down onto the "ground"
            translationY = d.offsetY * size.height
            rotationZ = d.rotationDeg
            scaleX = d.scaleX
            scaleY = d.scaleY
        }
    )
}
