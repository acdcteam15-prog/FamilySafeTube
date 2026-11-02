package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizPraiseTest {
    @Test fun add_remove_andCap() {
        var s = PraiseSet()
        s = s.add(PraiseEvent.CORRECT, "a.snd").add(PraiseEvent.CORRECT, "b.snd").add(PraiseEvent.CORRECT, "c.snd")
        assertTrue(s.isFull(PraiseEvent.CORRECT))
        assertEquals(listOf("a.snd", "b.snd", "c.snd"), s.add(PraiseEvent.CORRECT, "d.snd").clipsFor(PraiseEvent.CORRECT))   // 4th ignored
        assertEquals(listOf("a.snd", "c.snd"), s.remove(PraiseEvent.CORRECT, "b.snd").clipsFor(PraiseEvent.CORRECT))
        assertTrue(s.clipsFor(PraiseEvent.TRY_AGAIN).isEmpty())
    }

    @Test fun add_ignoresDuplicates() {
        val s = PraiseSet().add(PraiseEvent.ROUND_DONE, "x.snd").add(PraiseEvent.ROUND_DONE, "x.snd")
        assertEquals(1, s.clipsFor(PraiseEvent.ROUND_DONE).size)
    }

    @Test fun remove_lastClipLeavesNoEmptyEntry() {
        val s = PraiseSet().add(PraiseEvent.CORRECT, "a.snd").remove(PraiseEvent.CORRECT, "a.snd")
        assertEquals(PraiseSet(), s)
    }

    @Test fun store_roundTrip() {
        val s = PraiseSet().add(PraiseEvent.CORRECT, "a.snd").add(PraiseEvent.CORRECT, "b.snd").add(PraiseEvent.TRY_AGAIN, "t.snd")
        assertEquals(s, PraiseStore.decode(PraiseStore.encode(s)))
    }

    @Test fun store_toleratesGarbage() {
        assertEquals(PraiseSet(), PraiseStore.decode(null)); assertEquals(PraiseSet(), PraiseStore.decode(""))
        assertEquals(PraiseSet(), PraiseStore.decode("{bad")); assertEquals(PraiseSet(), PraiseStore.decode("[1]"))
        val s = PraiseStore.decode("""{"correct":["a","a","b","c","d"],"bogus":["x"],"try_again":"nope","round_done":[1,"r"]}""")
        assertEquals(listOf("a", "b", "c"), s.clipsFor(PraiseEvent.CORRECT))      // de-duplicated, capped at 3
        assertTrue(s.clipsFor(PraiseEvent.TRY_AGAIN).isEmpty())
        assertEquals(listOf("r"), s.clipsFor(PraiseEvent.ROUND_DONE))
    }

    @Test fun picker_neverRepeatsTheLastClipWhenThereIsChoice() {
        val clips = listOf("a", "b", "c")
        repeat(300) { seed -> assertFalse(PraisePicker.pick(clips, "b", Random(seed)) == "b") }
    }

    @Test fun picker_usesAllClips_andHandlesEdgeCases() {
        val picked = (0 until 200).map { PraisePicker.pick(listOf("a", "b", "c"), null, Random(it)) }.toSet()
        assertEquals(setOf("a", "b", "c"), picked)
        assertNull(PraisePicker.pick(emptyList(), null))
        assertEquals("only", PraisePicker.pick(listOf("only"), "only"))              // a single clip may repeat
    }
}
