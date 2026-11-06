package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CelebrationVoiceTest {
    private val clips = listOf("c1.snd", "c2.snd", "c3.snd")
    private fun plan(
        cfg: CelebrationConfig, event: CelebrationEvent = CelebrationEvent.ANSWER, lang: QuizLang = QuizLang.EN,
        clips: List<String> = emptyList(), lastPhrase: PhraseId? = null, lastClip: String? = null, seed: Int = 1
    ) = CelebrationPlan.build(cfg, event, lang, clips, lastPhrase, lastClip, Random(seed))

    // ── phrases ──
    @Test fun phrases_englishAndArabic_withAndWithoutAName() {
        assertEquals("Well done!", CelebrationPhrases.text(QuizLang.EN, PhraseId.WELL_DONE))
        assertEquals("Well done, Sara!", CelebrationPhrases.text(QuizLang.EN, PhraseId.WELL_DONE, "Sara"))
        assertEquals("أحسنت!", CelebrationPhrases.text(QuizLang.AR, PhraseId.WELL_DONE, diacritics = false))
        assertEquals("أحسنت يا سارة!", CelebrationPhrases.text(QuizLang.AR, PhraseId.WELL_DONE, "سارة", diacritics = false))
        assertEquals("Bravo!", CelebrationPhrases.text(QuizLang.EN, PhraseId.BRAVO, "   "))      // a blank name is no name
        assertEquals("Bravo!", CelebrationPhrases.text(QuizLang.EN, PhraseId.BRAVO, null))
    }

    // ── Arabic vowel marks (tashkeel) ──
    @Test fun arabic_isSpokenWithVowelMarksByDefault() {
        assertEquals("أَحْسَنْتَ!", CelebrationPhrases.text(QuizLang.AR, PhraseId.WELL_DONE))
        assertEquals("أَحْسَنْتَ يَا سَارَة!", CelebrationPhrases.text(QuizLang.AR, PhraseId.WELL_DONE, "سَارَة"))
        for (id in PhraseId.values()) assertTrue("$id", ArabicMarks.hasMarks(CelebrationPhrases.text(QuizLang.AR, id)))
    }

    @Test fun arabic_markedAndPlainAreTheSameWords() {
        for (id in PhraseId.values()) for (name in listOf(null, "سارة", "سَارَة")) {
            val plain = CelebrationPhrases.text(QuizLang.AR, id, name, diacritics = false)
            val marked = CelebrationPhrases.text(QuizLang.AR, id, name, diacritics = true)
            assertFalse("$id plain has marks", ArabicMarks.hasMarks(plain))
            assertEquals("$id", plain, ArabicMarks.strip(marked))
        }
    }

    @Test fun arabic_switchingMarksOffTakesThemOffTheNameToo() {
        assertEquals("أحسنت يا سارة!", CelebrationPhrases.text(QuizLang.AR, PhraseId.WELL_DONE, "سَارَة", diacritics = false))
    }

    @Test fun english_isNotAffectedByTheMarksSwitch() {
        for (id in PhraseId.values()) assertEquals(CelebrationPhrases.text(QuizLang.EN, id, "Sara", true), CelebrationPhrases.text(QuizLang.EN, id, "Sara", false))
    }

    @Test fun arabicMarks_helpers() {
        assertTrue(ArabicMarks.isMark('\u064E')); assertTrue(ArabicMarks.isMark('\u0652')); assertTrue(ArabicMarks.isMark('\u0670'))
        assertFalse(ArabicMarks.isMark('أ')); assertFalse(ArabicMarks.isMark('a'))
        assertEquals("سارة", ArabicMarks.strip("سَارَة"))
    }

    @Test fun thePlan_usesTheMarksSetting() {
        val on = CelebrationConfig(voice = VoiceSource.PHONE, effect = CelebrationEffect.NONE, useName = true, name = "سَارَة")
        repeat(30) { seed ->
            val t = (plan(on, lang = QuizLang.AR, seed = seed).steps.single() as AudioStep.Tts).text
            assertTrue(t, ArabicMarks.hasMarks(t))
            val off = (plan(on.copy(diacritics = false), lang = QuizLang.AR, seed = seed).steps.single() as AudioStep.Tts).text
            assertFalse(off, ArabicMarks.hasMarks(off))
        }
        assertTrue(CelebrationConfig().diacritics)
    }

    @Test fun cleanName_keepsTheMarks() {
        assertEquals("سَارَة", CelebrationSettings.cleanName("  سَارَة "))
    }

    @Test fun everyPhraseExistsInBothLanguages_andIsClean() {
        for (lang in QuizLang.values()) for (id in PhraseId.values()) for (name in listOf(null, "Yusuf")) {
            val t = CelebrationPhrases.text(lang, id, name)
            assertTrue("$lang $id: '$t'", t.length > 3 && t.endsWith("!"))
            assertFalse(t.contains("{") || t.contains("}") || t.contains("  ") || t.contains("null"))
            if (name != null) assertTrue(t.contains(name))
        }
        for (id in PhraseId.values()) assertNotEquals(CelebrationPhrases.text(QuizLang.EN, id), CelebrationPhrases.text(QuizLang.AR, id))
        assertEquals(PhraseId.values().size, PhraseId.values().map { CelebrationPhrases.text(QuizLang.EN, it) }.toSet().size)   // all different
    }

    // ── settings clean-up ──
    @Test fun cleanName() {
        assertEquals("Sara", CelebrationSettings.cleanName("  Sara  "))
        assertEquals("Sara Ali", CelebrationSettings.cleanName("Sara    Ali"))
        assertEquals("Sara", CelebrationSettings.cleanName("{Sara}"))
        assertEquals("Sara", CelebrationSettings.cleanName("Sa\nra".replace("\n", "") + "\u0007"))
        assertEquals("", CelebrationSettings.cleanName(null)); assertEquals("", CelebrationSettings.cleanName("   "))
        assertEquals(CelebrationSettings.NAME_MAX, CelebrationSettings.cleanName("x".repeat(100)).length)
        assertEquals("سارة", CelebrationSettings.cleanName("سارة"))
    }

    @Test fun parsePhrases() {
        assertEquals(PhraseId.values().toSet(), CelebrationSettings.parsePhrases(null))
        assertEquals(PhraseId.values().toSet(), CelebrationSettings.parsePhrases(""))
        assertEquals(PhraseId.values().toSet(), CelebrationSettings.parsePhrases("nonsense,??"))         // nothing valid: all
        assertEquals(setOf(PhraseId.BRAVO, PhraseId.WELL_DONE), CelebrationSettings.parsePhrases("bravo, WELL_DONE ,junk"))
        val some = setOf(PhraseId.AMAZING, PhraseId.GREAT_JOB)
        assertEquals(some, CelebrationSettings.parsePhrases(CelebrationSettings.formatPhrases(some)))
        assertEquals("WELL_DONE,BRAVO", CelebrationSettings.formatPhrases(setOf(PhraseId.BRAVO, PhraseId.WELL_DONE)))   // stable order
    }

    @Test fun theLastTickedPhraseCannotBeRemoved() {
        val one = setOf(PhraseId.BRAVO)
        assertEquals(one, CelebrationSettings.withPhrase(one, PhraseId.BRAVO, on = false))
        assertEquals(setOf(PhraseId.BRAVO, PhraseId.AMAZING), CelebrationSettings.withPhrase(one, PhraseId.AMAZING, on = true))
        assertEquals(one, CelebrationSettings.withPhrase(setOf(PhraseId.BRAVO, PhraseId.AMAZING), PhraseId.AMAZING, on = false))
        assertEquals(one, CelebrationSettings.withPhrase(one, PhraseId.BRAVO, on = true))
    }

    @Test fun voiceStyle_presetsAreInRange_andClamping() {
        for (p in VoiceStyle.PRESETS) { assertEquals(p, p.clamped()); assertTrue(p.pitch in VoiceStyle.PITCH_MIN..VoiceStyle.PITCH_MAX && p.rate in VoiceStyle.RATE_MIN..VoiceStyle.RATE_MAX) }
        assertEquals(4, VoiceStyle.PRESETS.toSet().size)
        assertEquals(VoiceStyle(VoiceStyle.PITCH_MAX, VoiceStyle.RATE_MIN), VoiceStyle(9f, 0.01f).clamped())
        assertEquals(VoiceStyle(VoiceStyle.PITCH_MIN, VoiceStyle.RATE_MAX), VoiceStyle(-1f, 9f).clamped())
        assertEquals(VoiceStyle.PLAYFUL, VoiceStyle.presetOf(VoiceStyle(1.252f, 1.1f)))
        assertNull(VoiceStyle.presetOf(VoiceStyle(1.1f, 1.0f)))
        assertTrue(VoiceStyle.PLAYFUL.pitch > VoiceStyle.NORMAL.pitch && VoiceStyle.GENTLE.rate < VoiceStyle.NORMAL.rate)
    }

    @Test fun storedKeysFallBackToSafeDefaults() {
        assertEquals(VoiceSource.RECORDINGS, VoiceSource.fromKey(null)); assertEquals(VoiceSource.RECORDINGS, VoiceSource.fromKey("zzz"))
        assertEquals(VoiceSource.PHONE, VoiceSource.fromKey("phone")); assertEquals(VoiceSource.OFF, VoiceSource.fromKey("off"))
        assertEquals(CelebrationEffect.CHEER, CelebrationEffect.fromKey(null)); assertEquals(CelebrationEffect.CHEER, CelebrationEffect.fromKey("zzz"))
        for (e in CelebrationEffect.values()) assertEquals(e, CelebrationEffect.fromKey(e.key))
        for (v in VoiceSource.values()) assertEquals(v, VoiceSource.fromKey(v.key))
    }

    // ── the plan ──
    @Test fun eachMomentCanBeSwitchedOffOnItsOwn() {
        val onlyRound = CelebrationConfig(onAnswer = false, onRound = true)
        assertTrue(plan(onlyRound, CelebrationEvent.ANSWER).steps.isEmpty())
        assertTrue(plan(onlyRound, CelebrationEvent.ROUND).steps.isNotEmpty())
        val onlyAnswer = CelebrationConfig(onAnswer = true, onRound = false)
        assertTrue(plan(onlyAnswer, CelebrationEvent.ROUND).steps.isEmpty())
        assertTrue(plan(onlyAnswer, CelebrationEvent.ANSWER).steps.isNotEmpty())
        assertTrue(plan(CelebrationConfig(onAnswer = false, onRound = false), CelebrationEvent.ROUND).steps.isEmpty())
    }

    @Test fun theSoundComesFirst_thenTheVoice_andTheRoundVersionIsLonger() {
        val cfg = CelebrationConfig(voice = VoiceSource.PHONE, effect = CelebrationEffect.FANFARE)
        val answer = plan(cfg, CelebrationEvent.ANSWER).steps
        assertEquals(AudioStep.Effect(CelebrationEffect.FANFARE, long = false), answer[0])
        assertTrue(answer[1] is AudioStep.Tts); assertEquals(2, answer.size)
        assertEquals(AudioStep.Effect(CelebrationEffect.FANFARE, long = true), plan(cfg, CelebrationEvent.ROUND).steps[0])
    }

    @Test fun noSoundEffectMeansNoEffectStep() {
        val steps = plan(CelebrationConfig(voice = VoiceSource.PHONE, effect = CelebrationEffect.NONE)).steps
        assertEquals(1, steps.size); assertTrue(steps[0] is AudioStep.Tts)
    }

    @Test fun voiceOff_playsOnlyTheSound_orNothing() {
        assertEquals(listOf<AudioStep>(AudioStep.Effect(CelebrationEffect.APPLAUSE, false)),
            plan(CelebrationConfig(voice = VoiceSource.OFF, effect = CelebrationEffect.APPLAUSE), clips = clips).steps)
        assertTrue(plan(CelebrationConfig(voice = VoiceSource.OFF, effect = CelebrationEffect.NONE), clips = clips).steps.isEmpty())
    }

    @Test fun phoneVoice_ignoresRecordings_andAppliesTheStyleAndLanguage() {
        val cfg = CelebrationConfig(voice = VoiceSource.PHONE, effect = CelebrationEffect.NONE, style = VoiceStyle.EXCITED)
        val r = plan(cfg, lang = QuizLang.AR, clips = clips)
        val tts = r.steps.single() as AudioStep.Tts
        assertEquals(QuizLang.AR, tts.lang); assertEquals(VoiceStyle.EXCITED.pitch, tts.pitch, 0f); assertEquals(VoiceStyle.EXCITED.rate, tts.rate, 0f)
        assertNull(r.clip); assertTrue(r.steps.none { it is AudioStep.RecordedFile })
    }

    @Test fun styleOutOfRangeIsClampedBeforeItReachesTheVoice() {
        val tts = plan(CelebrationConfig(voice = VoiceSource.PHONE, effect = CelebrationEffect.NONE, style = VoiceStyle(9f, 0f))).steps.single() as AudioStep.Tts
        assertEquals(VoiceStyle.PITCH_MAX, tts.pitch, 0f); assertEquals(VoiceStyle.RATE_MIN, tts.rate, 0f)
    }

    @Test fun recordings_playARecording_notTheVoice() {
        val r = plan(CelebrationConfig(voice = VoiceSource.RECORDINGS, effect = CelebrationEffect.NONE), clips = clips)
        val step = r.steps.single(); assertTrue(step is AudioStep.RecordedFile); assertTrue((step as AudioStep.RecordedFile).fileName in clips)
        assertNull(r.phrase)
    }

    @Test fun recordings_theParentCanPickOneClip_perMoment() {
        val cfg = CelebrationConfig(voice = VoiceSource.RECORDINGS, effect = CelebrationEffect.NONE, answerClip = "c2.snd", roundClip = "c3.snd")
        repeat(30) { seed ->
            assertEquals(AudioStep.RecordedFile("c2.snd"), plan(cfg, CelebrationEvent.ANSWER, clips = clips, seed = seed).steps.single())
            assertEquals(AudioStep.RecordedFile("c3.snd"), plan(cfg, CelebrationEvent.ROUND, clips = clips, seed = seed).steps.single())
        }
    }

    @Test fun recordings_aPickedClipThatWasDeleted_fallsBackToARandomOne() {
        val cfg = CelebrationConfig(voice = VoiceSource.RECORDINGS, effect = CelebrationEffect.NONE, answerClip = "gone.snd")
        val step = plan(cfg, clips = clips).steps.single() as AudioStep.RecordedFile
        assertTrue(step.fileName in clips)
    }

    @Test fun recordings_withNoneMade_usesThePhoneVoiceInstead() {
        val r = plan(CelebrationConfig(voice = VoiceSource.RECORDINGS, effect = CelebrationEffect.NONE), clips = emptyList())
        assertTrue(r.steps.single() is AudioStep.Tts); assertNotEquals(null, r.phrase); assertNull(r.clip)
    }

    @Test fun recordings_neverTheSameClipTwiceInARow() {
        val cfg = CelebrationConfig(voice = VoiceSource.RECORDINGS, effect = CelebrationEffect.NONE)
        repeat(200) { seed -> val r = plan(cfg, clips = clips, lastClip = "c1.snd", seed = seed); assertTrue(r.clip != "c1.snd") }
    }

    // ── the child's name ──
    @Test fun theName_isSpokenOnlyWhenAskedFor() {
        val on = CelebrationConfig(voice = VoiceSource.PHONE, effect = CelebrationEffect.NONE, useName = true, name = "Sara")
        repeat(20) { seed -> assertTrue((plan(on, seed = seed).steps.single() as AudioStep.Tts).text.contains("Sara")) }
        val off = on.copy(useName = false)
        repeat(20) { seed -> assertFalse((plan(off, seed = seed).steps.single() as AudioStep.Tts).text.contains("Sara")) }
        val blank = on.copy(name = "   ")
        assertFalse((plan(blank).steps.single() as AudioStep.Tts).text.contains(","))                  // no dangling ", !"
    }

    @Test fun theName_isCleanedBeforeItIsSpoken() {
        val cfg = CelebrationConfig(voice = VoiceSource.PHONE, effect = CelebrationEffect.NONE, useName = true, name = " {Sara}  \u0007 ")
        val text = (plan(cfg).steps.single() as AudioStep.Tts).text
        assertTrue(text.contains("Sara") && !text.contains("{") && !text.contains("  "))
    }

    @Test fun theName_isNeverUsedWithRecordings() {
        val cfg = CelebrationConfig(voice = VoiceSource.RECORDINGS, effect = CelebrationEffect.NONE, useName = true, name = "Sara")
        assertTrue(plan(cfg, clips = clips).steps.single() is AudioStep.RecordedFile)
    }

    // ── choosing a phrase ──
    @Test fun onlyTickedPhrasesAreSpoken() {
        val cfg = CelebrationConfig(voice = VoiceSource.PHONE, effect = CelebrationEffect.NONE, phrases = setOf(PhraseId.BRAVO, PhraseId.AMAZING))
        val allowed = setOf("Bravo!", "Amazing!")
        repeat(100) { seed -> assertTrue((plan(cfg, seed = seed).steps.single() as AudioStep.Tts).text in allowed) }
    }

    @Test fun noPhraseTicked_stillSaysWellDone() {
        val cfg = CelebrationConfig(voice = VoiceSource.PHONE, effect = CelebrationEffect.NONE, phrases = emptySet())
        assertEquals("Well done!", (plan(cfg).steps.single() as AudioStep.Tts).text)
    }

    @Test fun thePhraseIsNeverRepeatedBackToBack_whenThereIsAChoice() {
        val cfg = CelebrationConfig(voice = VoiceSource.PHONE, effect = CelebrationEffect.NONE)
        repeat(300) { seed -> assertTrue(plan(cfg, lastPhrase = PhraseId.BRAVO, seed = seed).phrase != PhraseId.BRAVO) }
        val single = cfg.copy(phrases = setOf(PhraseId.BRAVO))
        assertEquals(PhraseId.BRAVO, plan(single, lastPhrase = PhraseId.BRAVO).phrase)                  // one phrase: it may repeat
    }

    @Test fun allTickedPhrasesGetUsed() {
        val cfg = CelebrationConfig(voice = VoiceSource.PHONE, effect = CelebrationEffect.NONE)
        assertEquals(PhraseId.values().toSet(), (0 until 400).mapNotNull { plan(cfg, seed = it).phrase }.toSet())
    }

    // ── remembering what was said last ──
    @Test fun celebrationVoice_variesFromOneRightAnswerToTheNext() {
        val voice = CelebrationVoice(CelebrationConfig(voice = VoiceSource.PHONE, effect = CelebrationEffect.NONE), PraiseSet(), QuizLang.EN, Random(5))
        var last = ""
        repeat(200) { val t = (voice.steps(CelebrationEvent.ANSWER).single() as AudioStep.Tts).text; assertNotEquals(last, t); last = t }
    }

    @Test fun celebrationVoice_remembersClipsPerMoment_andUsesTheRightRecordings() {
        val praise = PraiseSet().add(PraiseEvent.CORRECT, "a1.snd").add(PraiseEvent.CORRECT, "a2.snd").add(PraiseEvent.ROUND_DONE, "r1.snd")
        val voice = CelebrationVoice(CelebrationConfig(voice = VoiceSource.RECORDINGS, effect = CelebrationEffect.NONE), praise, QuizLang.AR, Random(9))
        var last = ""
        repeat(100) {
            val f = (voice.steps(CelebrationEvent.ANSWER).single() as AudioStep.RecordedFile).fileName
            assertTrue(f in setOf("a1.snd", "a2.snd")); assertNotEquals(last, f); last = f
        }
        assertEquals(AudioStep.RecordedFile("r1.snd"), voice.steps(CelebrationEvent.ROUND).single())    // its own recordings, not the answer ones
    }

    @Test fun celebrationVoice_isSilentWhenTheMomentIsOff() {
        val voice = CelebrationVoice(CelebrationConfig(onAnswer = false), PraiseSet(), QuizLang.EN)
        assertTrue(voice.steps(CelebrationEvent.ANSWER).isEmpty())
    }

    @Test fun pickingARecording_changesOnlyThatMoment() {
        val base = CelebrationConfig()
        val a = base.withClip(CelebrationEvent.ANSWER, "x.snd"); val r = base.withClip(CelebrationEvent.ROUND, "y.snd")
        assertEquals("x.snd", a.clipFor(CelebrationEvent.ANSWER)); assertEquals("", a.clipFor(CelebrationEvent.ROUND))
        assertEquals("y.snd", r.clipFor(CelebrationEvent.ROUND)); assertEquals("", r.clipFor(CelebrationEvent.ANSWER))
        assertEquals(base, a.withClip(CelebrationEvent.ANSWER, ""))                                      // back to "random"
    }

    @Test fun defaults_keepWhatTheAppDidBefore_recordingsThenTheCheer() {
        val d = CelebrationConfig()
        assertEquals(VoiceSource.RECORDINGS, d.voice); assertEquals(CelebrationEffect.CHEER, d.effect)
        assertTrue(d.onAnswer && d.onRound); assertFalse(d.useName)
        assertEquals(PhraseId.values().toSet(), d.phrases); assertEquals(VoiceStyle.NORMAL, d.style)
    }
}
