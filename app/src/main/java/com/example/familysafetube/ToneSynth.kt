package com.example.familysafetube

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** A note: [freqHz] = 0 means silence. */
data class Note(val freqHz: Double, val ms: Int)

/**
 * Generates the cheer / "oops" sounds in code, so the app needs no audio assets for them.
 * Each note has a short fade in and out so there are no clicks. Gentle volume: this is for a baby.
 */
object ToneSynth {
    const val SAMPLE_RATE = 22_050
    private const val DEFAULT_VOLUME = 0.30

    /** Happy rising arpeggio (C5 E5 G5). */
    val CHEER = listOf(Note(523.25, 110), Note(659.25, 110), Note(783.99, 220))
    /** Soft, low, falling "oh-oh" (deliberately not harsh). */
    val OOPS = listOf(Note(330.0, 150), Note(262.0, 260))

    fun durationMs(notes: List<Note>) = notes.sumOf { it.ms }

    fun render(notes: List<Note>, volume: Double = DEFAULT_VOLUME): ShortArray {
        val v = volume.coerceIn(0.0, 0.8)
        val out = ArrayList<Short>()
        for (n in notes) {
            val count = SAMPLE_RATE * n.ms / 1000
            val fade = min(count / 4, SAMPLE_RATE * 25 / 1000).coerceAtLeast(1)
            for (i in 0 until count) {
                val env = when {
                    i < fade -> i.toDouble() / fade
                    i > count - fade -> (count - i).toDouble() / fade
                    else -> 1.0
                }
                val s = if (n.freqHz <= 0.0) 0.0 else sin(2.0 * PI * n.freqHz * i / SAMPLE_RATE)
                out.add((s * env * v * Short.MAX_VALUE).toInt().toShort())
            }
        }
        return out.toShortArray()
    }
}
