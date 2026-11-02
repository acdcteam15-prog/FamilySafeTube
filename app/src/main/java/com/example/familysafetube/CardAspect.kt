package com.example.familysafetube

import android.graphics.BitmapFactory

/** Reads a card's real shape (width / height) so the quiz can size its frames to fit the picture exactly. */
object CardAspect {
    /** Width / height of the card's picture; letter and number tiles are square; 0 if unknown. */
    fun of(card: QuizCard): Float = when (val art = card.art) {
        is CardArt.Picture -> fileAspect(art.path)
        else -> 1f
    }

    /** Reads only the image header (no pixels), so it is fast and uses almost no memory. */
    private fun fileAspect(path: String): Float {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        return if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth.toFloat() / bounds.outHeight else 0f
    }
}
