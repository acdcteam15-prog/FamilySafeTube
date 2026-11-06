package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class CelebrationEffectsTest {
    private val sounding = CelebrationEffect.values().filter { it != CelebrationEffect.NONE }
    private fun peak(a: ShortArray, from: Int = 0, to: Int = a.size) = (from until to).maxOf { abs(a[it].toInt()) }
    private fun rms(a: ShortArray, from: Int, to: Int): Double = sqrt((from until to).sumOf { a[it].toDouble() * a[it] } / (to - from))
    private fun ms(samples: Int) = samples * 1000 / ToneSynth.SAMPLE_RATE

    @Test fun none_isSilentAndHasNoLength() {
        for (long in listOf(false, true)) {
            assertEquals(0, ToneSynth.renderEffect(CelebrationEffect.NONE, long).size)
            assertEquals(0, ToneSynth.effectDurationMs(CelebrationEffect.NONE, long))
        }
    }

    @Test fun theStatedDurationMatchesTheSamples() {
        for (e in sounding) for (long in listOf(false, true)) {
            val pcm = ToneSynth.renderEffect(e, long)
            assertTrue("$e long=$long", abs(ms(pcm.size) - ToneSynth.effectDurationMs(e, long)) <= 2)
        }
    }

    @Test fun theRoundVersionIsLongerThanTheAnswerVersion() {
        for (e in sounding) assertTrue("$e", ToneSynth.effectDurationMs(e, true) > ToneSynth.effectDurationMs(e, false))
    }

    @Test fun lengthsStayShortEnoughForABaby() {
        for (e in sounding) {
            assertTrue("$e answer", ToneSynth.effectDurationMs(e, false) in 300..1000)       // never delays the next question much
            assertTrue("$e round", ToneSynth.effectDurationMs(e, true) in 800..2600)
        }
    }

    @Test fun everyEffectIsAudible_butGentle() {
        for (e in sounding) for (long in listOf(false, true)) {
            val p = peak(ToneSynth.renderEffect(e, long))
            assertTrue("$e long=$long peak=$p too quiet", p > 3_000)
            assertTrue("$e long=$long peak=$p too loud for a baby", p <= (0.45 * Short.MAX_VALUE).toInt())
        }
    }

    /** The loudest 100 ms stretch (root-mean-square), which is what the ear follows. */
    private fun loudest(pcm: ShortArray): Double {
        val win = ToneSynth.SAMPLE_RATE / 10
        return (0..(pcm.size - win).coerceAtLeast(0) step win / 2).maxOf { rms(pcm, it, minOf(it + win, pcm.size)) }
    }

    @Test fun theEffectsAreAboutAsLoudAsTheCheer_soSwitchingDoesNotSurprise() {
        val cheer = loudest(ToneSynth.renderEffect(CelebrationEffect.CHEER, false))
        for (e in sounding) for (long in listOf(false, true)) {
            val ratio = loudest(ToneSynth.renderEffect(e, long)) / cheer
            assertTrue("$e long=$long is ${"%.2f".format(ratio)}x the cheer", ratio in 0.45..1.15)       // never louder than the cheer
        }
    }

    @Test fun everyEffectStartsAndEndsSoftly_soThereIsNoClick() {
        for (e in sounding) for (long in listOf(false, true)) {
            val pcm = ToneSynth.renderEffect(e, long)
            assertTrue("$e start", abs(pcm.first().toInt()) < 600); assertTrue("$e end", abs(pcm.last().toInt()) < 600)
        }
    }

    @Test fun theSoundIsTheSameEveryTime() {
        for (e in sounding) for (long in listOf(false, true)) assertTrue("$e", ToneSynth.renderEffect(e, long).contentEquals(ToneSynth.renderEffect(e, long)))
    }

    @Test fun theEffectsSoundDifferentFromEachOther() {
        val all = sounding.map { ToneSynth.renderEffect(it, false).toList() }
        for (i in all.indices) for (j in i + 1 until all.size) assertTrue(all[i] != all[j])
    }

    @Test fun cheer_isTheOriginalArpeggioForAnswers() {
        assertTrue(ToneSynth.renderEffect(CelebrationEffect.CHEER, false).contentEquals(ToneSynth.render(ToneSynth.CHEER)))
    }

    @Test fun applause_isNoise_buildsUpAndFadesAway() {
        val pcm = ToneSynth.renderEffect(CelebrationEffect.APPLAUSE, true)
        var crossings = 0; for (i in 1 until pcm.size) if ((pcm[i] >= 0) != (pcm[i - 1] >= 0)) crossings++
        assertTrue("noise crosses zero often ($crossings)", crossings > pcm.size / 20)
        val middle = rms(pcm, pcm.size * 3 / 10, pcm.size * 6 / 10); val end = rms(pcm, pcm.size * 9 / 10, pcm.size)
        val start = rms(pcm, 0, pcm.size / 40)
        assertTrue("middle louder than the tail ($middle vs $end)", middle > end * 2)
        assertTrue("middle louder than the very start ($middle vs $start)", middle > start * 2)
    }

    @Test fun sparkle_ringsAndFadesLikeABell() {
        val pcm = ToneSynth.renderEffect(CelebrationEffect.SPARKLE, true)
        val lastNoteStart = (7 * 90) * ToneSynth.SAMPLE_RATE / 1000                         // the long ending note
        val early = peak(pcm, lastNoteStart + 30 * ToneSynth.SAMPLE_RATE / 1000, lastNoteStart + 80 * ToneSynth.SAMPLE_RATE / 1000)
        val late = peak(pcm, lastNoteStart + 330 * ToneSynth.SAMPLE_RATE / 1000, lastNoteStart + 380 * ToneSynth.SAMPLE_RATE / 1000)
        assertTrue("early $early vs late $late", early > late * 3)
    }

    @Test fun fanfare_hasOvertones_soItIsNotAPureSine() {
        val fan = ToneSynth.renderEffect(CelebrationEffect.FANFARE, false)
        val pure = ToneSynth.render(listOf(Note(392.0, 90), Note(392.0, 90), Note(523.25, 330)))
        assertEquals(fan.size, pure.size)
        assertTrue(fan.indices.any { abs(fan[it] - pure[it]) > 1500 })
    }

    @Test fun theStepIsAPlainValue() {
        assertEquals(AudioStep.Effect(CelebrationEffect.SPARKLE, true), AudioStep.Effect(CelebrationEffect.SPARKLE, true))
        assertTrue(AudioStep.Effect(CelebrationEffect.SPARKLE, true) != AudioStep.Effect(CelebrationEffect.SPARKLE, false))
        assertEquals(AudioStep.Tts("x", QuizLang.EN), AudioStep.Tts("x", QuizLang.EN, 1f, 1f))   // the old two-argument form still means "normal voice"
    }
}
