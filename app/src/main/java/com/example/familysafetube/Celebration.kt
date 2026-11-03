package com.example.familysafetube

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * The maths of the end-of-round celebration: falling confetti and stars, fireworks bursts and the dancing logo.
 * Everything is a pure function of the time in seconds, so the screen only has to draw what these return
 * (and the behaviour is unit-tested without a phone).
 */
object Celebration {
    const val PALETTE_SIZE = 8
    const val BURST_INTERVAL_SEC = 0.75f
    const val SPARKS_PER_BURST = 28
    const val SPARK_LIFE_SEC = 1.5f
    private const val FALL_CYCLE = 1.25f

    /** Deterministic pseudo-random number in 0..1 (no random state, so the animation is reproducible). */
    private fun hash01(n: Int, salt: Int): Float {
        var h = n * 374761393 + salt * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0x7fffffff) / 2147483647f
    }

    // ── confetti and falling stars ───────────────────────────────────────────
    data class Piece(
        val xFrac: Float, val phase: Float, val speed: Float, val swayAmp: Float, val swayFreq: Float,
        val size: Float, val colorIndex: Int, val spin: Float, val star: Boolean
    )

    fun pieces(count: Int): List<Piece> = List(count.coerceAtLeast(0)) { i ->
        Piece(
            xFrac = 0.03f + 0.94f * hash01(i, 1),
            phase = hash01(i, 2),
            speed = 0.16f + 0.22f * hash01(i, 3),
            swayAmp = 0.01f + 0.03f * hash01(i, 4),
            swayFreq = 1.2f + 2.4f * hash01(i, 5),
            size = 0.6f + 0.8f * hash01(i, 6),
            colorIndex = (hash01(i, 7) * PALETTE_SIZE).toInt().coerceIn(0, PALETTE_SIZE - 1),
            spin = (hash01(i, 8) - 0.5f) * 720f,
            star = i % 4 == 0
        )
    }

    /** Vertical position as a fraction of the height, -0.1 (above the screen) to 1.15 (below), looping forever. */
    fun fallFraction(p: Piece, tSec: Float): Float = ((p.phase * FALL_CYCLE + p.speed * tSec) % FALL_CYCLE) - 0.1f

    /** Horizontal position as a fraction of the width: it drifts from side to side while falling. */
    fun swayFraction(p: Piece, tSec: Float): Float =
        (p.xFrac + p.swayAmp * sin(p.swayFreq * tSec + p.phase * 6.2832f)).coerceIn(0f, 1f)

    fun rotationDeg(p: Piece, tSec: Float): Float = (p.spin * tSec) % 360f

    /** The 10 corner points (outer, inner, outer, ...) of a five-pointed star, starting at the top. */
    fun starPoints(cx: Float, cy: Float, outer: Float): List<Pair<Float, Float>> {
        val inner = outer * 0.45f
        return List(10) { i ->
            val r = if (i % 2 == 0) outer else inner
            val a = (-PI / 2 + i * PI / 5).toFloat()
            (cx + r * cos(a)) to (cy + r * sin(a))
        }
    }

    // ── fireworks ────────────────────────────────────────────────────────────
    data class Spark(val dx: Float, val dy: Float, val alpha: Float, val sizeFactor: Float)

    /** The bursts that are still visible at [tSec]: (burst number, seconds since it went off). A new one every 0.75 s. */
    fun activeBursts(tSec: Float): List<Pair<Int, Float>> {
        if (tSec < 0f) return emptyList()
        val last = floor(tSec / BURST_INTERVAL_SEC).toInt()
        val first = max(0, floor((tSec - SPARK_LIFE_SEC) / BURST_INTERVAL_SEC).toInt())
        return (first..last).map { n -> n to (tSec - n * BURST_INTERVAL_SEC) }.filter { (_, age) -> age >= 0f && age < SPARK_LIFE_SEC }
    }

    /** Where burst number [n] goes off, as fractions of the screen (upper half, away from the edges). */
    fun burstOrigin(n: Int): Pair<Float, Float> = (0.15f + 0.70f * hash01(n, 11)) to (0.10f + 0.35f * hash01(n, 12))

    fun burstColor(n: Int): Int = (hash01(n, 13) * PALETTE_SIZE).toInt().coerceIn(0, PALETTE_SIZE - 1)

    /**
     * One spark of a burst [ageSec] seconds after it went off, in units of the burst radius (dx right, dy down),
     * with its opacity (fading out) and relative size; null once it has burnt out.
     */
    fun sparkAt(burst: Int, index: Int, ageSec: Float): Spark? {
        if (ageSec < 0f || ageSec >= SPARK_LIFE_SEC) return null
        val p = ageSec / SPARK_LIFE_SEC
        val angle = (2.0 * PI * index / SPARKS_PER_BURST).toFloat() + (hash01(index, 21 + burst) - 0.5f) * 0.25f
        val speed = 0.6f + 0.4f * hash01(index, 22)
        val out = 1f - (1f - p) * (1f - p)                 // flies out fast, then slows down
        return Spark(
            dx = cos(angle) * speed * out,
            dy = sin(angle) * speed * out + 0.35f * p * p,  // gravity pulls the sparks down
            alpha = (1f - p).pow(1.5f),
            sizeFactor = 1f - 0.5f * p
        )
    }

    // ── the dancing logo ─────────────────────────────────────────────────────
    /** [offsetY] is a fraction of the logo's height (negative = up); the logo squashes when it lands. */
    data class Dance(val offsetY: Float, val rotationDeg: Float, val scaleX: Float, val scaleY: Float)

    fun dance(tSec: Float): Dance {
        val phase = (tSec * 0.9f) % 1f                       // about one hop a second
        val hop = abs(sin(PI.toFloat() * phase))             // 0 on the ground, 1 at the top of the hop
        val grounded = 1f - hop
        val squash = 0.10f * grounded * grounded
        return Dance(
            offsetY = -0.30f * hop,
            rotationDeg = 9f * sin(tSec * 2f * PI.toFloat() * 1.8f),   // wiggles left and right
            scaleX = 1f + squash,
            scaleY = 1f - squash
        )
    }
}
