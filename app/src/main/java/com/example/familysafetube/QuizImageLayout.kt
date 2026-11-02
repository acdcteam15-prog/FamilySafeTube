package com.example.familysafetube

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

enum class ImageArrangement { ROW, COLUMN, GRID }

/** How to lay the answer pictures out: the arrangement and the size of EACH picture, in dp. */
data class ImagePlan(val arrangement: ImageArrangement, val widthDp: Float, val heightDp: Float) {
    val area: Float get() = widthDp * heightDp
}

/**
 * Chooses the layout that makes the answer pictures as BIG as possible for the space available.
 *
 *  - ROW    all pictures side by side   (best in landscape / on tablets)
 *  - COLUMN pictures stacked            (best on a phone held upright: each picture is nearly full width)
 *  - GRID   two per row                 (best on a portrait tablet with 3+ pictures)
 *
 * Pictures may be a little wider than tall or a little taller than wide ([MIN_ASPECT]..[MAX_ASPECT],
 * width / height) so that space is used without distorting photos much (they are cropped, not stretched).
 * Pure, so every case is unit-tested.
 */
object QuizImageLayout {
    const val MIN_SIDE = 96f
    const val MAX_SIDE = 520f
    const val MAX_ASPECT = 1.5f
    const val MIN_ASPECT = 0.8f
    const val GAP_DP = 12f

    /** A picture is cropped (to fill its frame) only if its shape is within this fraction of the frame's shape. */
    const val CROP_TOLERANCE = 0.25f
    private const val MIN_CONTENT_ASPECT = 0.4f
    private const val MAX_CONTENT_ASPECT = 2.5f

    /**
     * True if filling the frame by cropping loses little (a wide animal photo in a wide frame);
     * false if the picture should be shown whole (a tall letter card must never lose its top or bottom).
     */
    fun shouldCrop(imageAspect: Float, tileAspect: Float): Boolean =
        imageAspect > 0f && tileAspect > 0f && abs(imageAspect / tileAspect - 1f) <= CROP_TOLERANCE

    /** The typical shape (width / height) of the pictures in a question, or 0 if none is known. */
    fun meanAspect(aspects: List<Float>): Float {
        val known = aspects.filter { it > 0f }
        return if (known.isEmpty()) 0f else known.average().toFloat().coerceIn(MIN_CONTENT_ASPECT, MAX_CONTENT_ASPECT)
    }

    /**
     * The biggest rectangle that fits in a [cellW] x [cellH] cell. With a known content [aspect] the rectangle has
     * exactly that shape (so the picture is shown whole); without one it may be a little wider or taller than square.
     */
    private fun fit(cellW: Float, cellH: Float, aspect: Float = 0f): Pair<Float, Float> {
        if (aspect > 0f) {
            val a = aspect.coerceIn(MIN_CONTENT_ASPECT, MAX_CONTENT_ASPECT)
            var w = min(cellW, cellH * a)
            var h = w / a
            if (w > MAX_SIDE) { w = MAX_SIDE; h = w / a }
            if (h > MAX_SIDE) { h = MAX_SIDE; w = h * a }
            val shortest = min(w, h)
            if (shortest < MIN_SIDE) { val f = MIN_SIDE / shortest; w *= f; h *= f }
            return w to h
        }
        val w0 = min(cellW, cellH * MAX_ASPECT)          // not wider than 1.5 x the height
        val h0 = min(cellH, w0 / MIN_ASPECT)              // not taller than 1.25 x the width
        val w = min(w0, MAX_SIDE).coerceAtLeast(MIN_SIDE)
        val h = min(h0, MAX_SIDE).coerceAtLeast(MIN_SIDE)
        return w to h
    }

    fun plan(
        count: Int,
        availableWidthDp: Float,
        availableHeightDp: Float,
        gapDp: Float = GAP_DP,
        /** Shape (width / height) of the pictures, from [meanAspect]; 0 = unknown. */
        aspect: Float = 0f
    ): ImagePlan {
        val n = count.coerceAtLeast(1)
        val w = availableWidthDp.coerceAtLeast(MIN_SIDE)
        val h = availableHeightDp.coerceAtLeast(MIN_SIDE)

        val candidates = ArrayList<ImagePlan>(3)

        val (rw, rh) = fit((w - gapDp * (n - 1)) / n, h, aspect)
        candidates += ImagePlan(ImageArrangement.ROW, rw, rh)

        if (n >= 3) {
            val rows = ceil(n / 2.0).toInt()
            val (gw, gh) = fit((w - gapDp) / 2, (h - gapDp * (rows - 1)) / rows, aspect)
            candidates += ImagePlan(ImageArrangement.GRID, gw, gh)
        }

        val (cw, ch) = fit(w, (h - gapDp * (n - 1)) / n, aspect)
        candidates += ImagePlan(ImageArrangement.COLUMN, cw, ch)

        // First maximum wins, so ties prefer ROW, then GRID, then COLUMN.
        return candidates.maxByOrNull { it.area }!!
    }
}
