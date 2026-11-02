package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ToneSynthTest {
    @Test fun length_matchesTheNoteDurations() {
        val pcm = ToneSynth.render(ToneSynth.CHEER)
        val exact = ToneSynth.SAMPLE_RATE * ToneSynth.durationMs(ToneSynth.CHEER) / 1000
        // each note's sample count is rounded down on its own, so allow at most one sample per note
        assertTrue("size ${pcm.size} vs $exact", exact - pcm.size in 0..ToneSynth.CHEER.size)
    }

    @Test fun volume_isGentle() {
        val peak = ToneSynth.render(ToneSynth.CHEER).maxOf { abs(it.toInt()) }
        assertTrue("peak $peak", peak <= (0.31 * Short.MAX_VALUE).toInt())
        assertTrue("not silent: $peak", peak > 1000)
    }

    @Test fun volume_isCappedEvenIfAskedLouder() {
        val peak = ToneSynth.render(ToneSynth.OOPS, volume = 5.0).maxOf { abs(it.toInt()) }
        assertTrue(peak <= (0.81 * Short.MAX_VALUE).toInt())
    }

    @Test fun noClicks_startsAndEndsNearSilence() {
        for (notes in listOf(ToneSynth.CHEER, ToneSynth.OOPS)) {
            val pcm = ToneSynth.render(notes)
            assertTrue(abs(pcm.first().toInt()) < 300)
            assertTrue(abs(pcm.last().toInt()) < 300)
        }
    }

    @Test fun rests_areSilent() {
        val pcm = ToneSynth.render(listOf(Note(0.0, 100)))
        assertTrue(pcm.all { it.toInt() == 0 })
        assertEquals(ToneSynth.SAMPLE_RATE / 10, pcm.size)
    }

    @Test fun oopsIsLowerAndSofterThanCheer() {
        val cheerMax = ToneSynth.CHEER.maxOf { it.freqHz }
        val oopsMax = ToneSynth.OOPS.maxOf { it.freqHz }
        assertTrue(oopsMax < ToneSynth.CHEER.minOf { it.freqHz })
        assertTrue(cheerMax > oopsMax)
    }
}
