package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuizAudioPlanTest {
    private fun card(
        sound: String? = null, builtIn: String? = null, voiceAr: String? = null, voiceEn: String? = null
    ) = QuizCard("animals_dog", "/p.png", "Dog", "كلب", "Doggy", true, false, builtIn, sound, voiceAr, voiceEn)

    @Test fun name_isSpokenByTtsInTheChosenLanguage() {
        assertEquals(AudioStep.Tts("كلب", QuizLang.AR), QuizAudioPlan.nameStep(card(), QuizLang.AR))
        assertEquals(AudioStep.Tts("Doggy", QuizLang.EN), QuizAudioPlan.nameStep(card(), QuizLang.EN))
    }

    @Test fun recordedVoice_replacesTtsOnlyForItsOwnLanguage() {
        val c = card(voiceAr = "ar.snd")
        assertEquals(AudioStep.RecordedFile("ar.snd"), QuizAudioPlan.nameStep(c, QuizLang.AR))
        assertEquals(AudioStep.Tts("Doggy", QuizLang.EN), QuizAudioPlan.nameStep(c, QuizLang.EN))
    }

    @Test fun sound_prefersParentsFileThenBuiltInThenNothing() {
        assertEquals(AudioStep.RecordedFile("mine.snd"), QuizAudioPlan.soundStep(card(sound = "mine.snd", builtIn = "dog")))
        assertEquals(AudioStep.BuiltIn("dog"), QuizAudioPlan.soundStep(card(builtIn = "dog")))
        assertNull(QuizAudioPlan.soundStep(card()))
    }

    @Test fun prompt_isNameThenSound() {
        assertEquals(
            listOf(AudioStep.Tts("كلب", QuizLang.AR), AudioStep.BuiltIn("dog")),
            QuizAudioPlan.prompt(card(builtIn = "dog"), QuizLang.AR)
        )
    }

    @Test fun prompt_isJustTheNameWhenThereIsNoSound() {
        assertEquals(listOf<AudioStep>(AudioStep.Tts("كلب", QuizLang.AR)), QuizAudioPlan.prompt(card(), QuizLang.AR))
    }

    @Test fun soundOnlyPrompt_playsJustTheSound() {
        assertEquals(
            listOf<AudioStep>(AudioStep.BuiltIn("dog")),
            QuizAudioPlan.prompt(card(builtIn = "dog"), QuizLang.AR, QuizMode.HEAR_SOUND)
        )
        assertEquals(
            listOf<AudioStep>(AudioStep.RecordedFile("mine.snd")),
            QuizAudioPlan.prompt(card(sound = "mine.snd", builtIn = "dog"), QuizLang.AR, QuizMode.HEAR_SOUND)
        )
    }

    @Test fun soundOnlyPrompt_fallsBackToTheNameWhenThereIsNoSound() {
        assertEquals(
            listOf<AudioStep>(AudioStep.Tts("كلب", QuizLang.AR)),
            QuizAudioPlan.prompt(card(), QuizLang.AR, QuizMode.HEAR_SOUND)
        )
    }

    @Test fun reveal_isNameThenSoundInEveryMode() {
        val c = card(builtIn = "dog")
        assertEquals(listOf(AudioStep.Tts("كلب", QuizLang.AR), AudioStep.BuiltIn("dog")), QuizAudioPlan.reveal(c, QuizLang.AR))
    }

    @Test fun hint_repeatsOnlyWhatWasAsked() {
        val c = card(builtIn = "dog")
        assertEquals(listOf<AudioStep>(AudioStep.Tts("كلب", QuizLang.AR)), QuizAudioPlan.hint(c, QuizLang.AR, QuizMode.HEAR_NAME))
        assertEquals(listOf<AudioStep>(AudioStep.BuiltIn("dog")), QuizAudioPlan.hint(c, QuizLang.AR, QuizMode.HEAR_SOUND))
    }

    @Test fun defaultPromptModeIsTheOldBehaviour() {
        val c = card(builtIn = "dog")
        assertEquals(QuizAudioPlan.prompt(c, QuizLang.AR, QuizMode.HEAR_NAME), QuizAudioPlan.prompt(c, QuizLang.AR))
    }

    @Test fun languageCodes() {
        assertEquals(QuizLang.AR, QuizLang.fromCode("ar"))
        assertEquals(QuizLang.EN, QuizLang.fromCode("en"))
        assertEquals(QuizLang.AR, QuizLang.fromCode(null))      // default: Arabic
        assertEquals(QuizLang.AR, QuizLang.fromCode("fr"))
    }
}
