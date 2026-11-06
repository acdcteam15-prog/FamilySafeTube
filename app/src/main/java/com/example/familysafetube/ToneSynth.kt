package com.example.familysafetube

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/** A note: [freqHz] = 0 means silence. */
data class Note(val freqHz: Double, val ms: Int)

/**
 * Generates the cheer / "oops" sounds in code, so the app needs no audio assets for them.
 * Each note has a short fade in and out so there are no clicks. Gentle volume: this is for a baby.
 */
object ToneSynth {
    const val SAMPLE_RATE = 22_050
    private const val DEFAULT_VOLUME = 0.30
    // Spreading energy over overtones / noise makes a sound quieter than a pure note of the same peak, so these are raised
    // until each effect is about as loud as the cheer (still gentle: never louder than 0.5 of full scale).
    private const val FANFARE_VOLUME = 0.50
    private const val SPARKLE_VOLUME = 0.42
    private const val APPLAUSE_VOLUME = 0.40
    private const val SQUEEZE = 3.0

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

    // ── celebration effects (also made in code: no audio files) ──────────────

    /** Length of a celebration effect in milliseconds (0 for NONE). [long] is the end-of-round version. */
    fun effectDurationMs(effect: CelebrationEffect, long: Boolean): Int = when (effect) {
        CelebrationEffect.NONE -> 0
        CelebrationEffect.APPLAUSE -> if (long) 2_400 else 900
        else -> durationMs(effectNotes(effect, long))
    }

    private fun effectNotes(effect: CelebrationEffect, long: Boolean): List<Note> = when (effect) {
        CelebrationEffect.CHEER ->
            if (long) listOf(Note(523.25, 110), Note(659.25, 110), Note(783.99, 110), Note(1046.5, 110), Note(783.99, 100), Note(1046.5, 400))
            else CHEER
        CelebrationEffect.FANFARE ->
            if (long) listOf(Note(392.0, 110), Note(392.0, 110), Note(392.0, 110), Note(523.25, 300), Note(440.0, 110), Note(523.25, 640))
            else listOf(Note(392.0, 90), Note(392.0, 90), Note(523.25, 330))
        CelebrationEffect.SPARKLE ->
            if (long) listOf(Note(1046.5, 90), Note(1318.5, 90), Note(1568.0, 90), Note(2093.0, 90), Note(1760.0, 90), Note(2349.0, 90), Note(2637.0, 90), Note(3136.0, 400))
            else listOf(Note(1318.5, 80), Note(1568.0, 80), Note(1760.0, 80), Note(2093.0, 80), Note(2637.0, 240))
        CelebrationEffect.APPLAUSE, CelebrationEffect.NONE -> emptyList()
    }

    /** The samples of a celebration effect (gentle volume, soft fades, the same every time). */
    fun renderEffect(effect: CelebrationEffect, long: Boolean): ShortArray = when (effect) {
        CelebrationEffect.NONE -> ShortArray(0)
        CelebrationEffect.CHEER -> render(effectNotes(effect, long))
        CelebrationEffect.FANFARE -> renderShaped(effectNotes(effect, long), doubleArrayOf(1.0, 0.5, 0.33, 0.2), decay = 0.0, volume = FANFARE_VOLUME)   // brassy: strong overtones
        CelebrationEffect.SPARKLE -> renderShaped(effectNotes(effect, long), doubleArrayOf(1.0, 0.15), decay = 9.0, volume = SPARKLE_VOLUME)   // bell-like: rings, then fades
        CelebrationEffect.APPLAUSE -> renderApplause(effectDurationMs(effect, long), APPLAUSE_VOLUME)
    }

    /** Notes made of several overtones, with an optional exponential decay (bells ring and fade). */
    private fun renderShaped(notes: List<Note>, harmonics: DoubleArray, decay: Double, volume: Double = DEFAULT_VOLUME): ShortArray {
        val v = volume.coerceIn(0.0, 0.8)
        val norm = harmonics.sum()
        val out = ShortArray(notes.sumOf { SAMPLE_RATE * it.ms / 1000 })
        var pos = 0
        for (n in notes) {
            val count = SAMPLE_RATE * n.ms / 1000
            val fade = min(count / 4, SAMPLE_RATE * 25 / 1000).coerceAtLeast(1)
            for (i in 0 until count) {
                val fadeEnv = when {
                    i < fade -> i.toDouble() / fade
                    i > count - fade -> (count - i).toDouble() / fade
                    else -> 1.0
                }
                val env = fadeEnv * exp(-decay * i / SAMPLE_RATE)
                var s = 0.0
                for (h in harmonics.indices) s += harmonics[h] * sin(2.0 * PI * n.freqHz * (h + 1) * i / SAMPLE_RATE)
                out[pos++] = (s / norm * env * v * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }

    /** Hands clapping: many short decaying bursts of noise, building up quickly and fading away. */
    private fun renderApplause(durationMs: Int, volume: Double = DEFAULT_VOLUME): ShortArray {
        val n = SAMPLE_RATE * durationMs / 1000
        if (n <= 0) return ShortArray(0)
        val clapLen = SAMPLE_RATE * 14 / 1000
        val rnd = java.util.Random(20_260_504L)                 // fixed seed: always the same applause
        val mix = DoubleArray(n)
        repeat(durationMs / 6) {
            val pos = rnd.nextInt(n)
            val x = pos.toDouble() / n
            val envelope = min(1.0, x / 0.12) * min(1.0, (1.0 - x) / 0.40)
            val amp = envelope * (0.35 + 0.65 * rnd.nextDouble())
            for (k in 0 until clapLen) {
                val idx = pos + k
                if (idx >= n) break
                mix[idx] += amp * (rnd.nextDouble() * 2.0 - 1.0) * exp(-k / (clapLen / 4.0))
            }
        }
        var prev = 0.0                                          // soften the hiss a little
        for (i in 0 until n) { prev = 0.55 * mix[i] + 0.45 * prev; mix[i] = prev }
        val peak = mix.maxOf { abs(it) }
        if (peak <= 0.0) return ShortArray(n)
        // Noise has a high peak but a low average loudness. A soft curve squeezes the peaks, so it sounds as full as the other
        // effects without being any louder at its loudest instant (tanh keeps every sample within -1..1).
        val squeeze = tanh(SQUEEZE)
        val gain = volume.coerceIn(0.0, 0.8) * Short.MAX_VALUE
        val fade = SAMPLE_RATE * 25 / 1000
        return ShortArray(n) { i ->
            val edge = min(1.0, min(i.toDouble() / fade, (n - 1 - i).toDouble() / fade))
            (tanh(SQUEEZE * mix[i] / peak) / squeeze * gain * edge).toInt().toShort()
        }
    }
}
