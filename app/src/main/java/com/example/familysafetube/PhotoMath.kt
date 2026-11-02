package com.example.familysafetube

import kotlin.math.max

/** Pure helpers for importing the parent's photos (sampling, scaling, camera rotation). */
object PhotoMath {
    /** EXIF orientation values (android.media.ExifInterface constants) -> clockwise degrees to rotate. */
    fun rotationDegrees(exifOrientation: Int): Int = when (exifOrientation) {
        6 -> 90      // ORIENTATION_ROTATE_90
        3 -> 180     // ORIENTATION_ROTATE_180
        8 -> 270     // ORIENTATION_ROTATE_270
        else -> 0
    }

    /** Largest power-of-two sample size that keeps both sides at or above [targetPx] (>= 1). */
    fun sampleSize(width: Int, height: Int, targetPx: Int): Int {
        if (width <= 0 || height <= 0 || targetPx <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= targetPx && height / (sample * 2) >= targetPx) sample *= 2
        return sample
    }

    /** Size after scaling so the longest side is at most [maxPx]; never enlarges. */
    fun scaledSize(width: Int, height: Int, maxPx: Int): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= maxPx || longest <= 0) return width to height
        val f = maxPx.toDouble() / longest
        return max(1, Math.round(width * f).toInt()) to max(1, Math.round(height * f).toInt())
    }
}
