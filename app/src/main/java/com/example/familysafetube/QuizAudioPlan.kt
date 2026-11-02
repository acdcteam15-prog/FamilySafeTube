package com.example.familysafetube

enum class QuizLang(val code: String, val localeTag: String) {
    AR("ar", "ar"), EN("en", "en");

    companion object {
        fun fromCode(code: String?): QuizLang = values().firstOrNull { it.code == code } ?: AR
    }
}

/** One thing to play, in order. Pure data so the sequencing rules can be unit-tested. */
sealed interface AudioStep {
    /** Spoken by the phone's text-to-speech engine. */
    data class Tts(val text: String, val lang: QuizLang) : AudioStep
    /** A recorded/imported file inside the quiz-audio folder. */
    data class RecordedFile(val fileName: String) : AudioStep
    /** One of the animal sounds shipped with the app. */
    data class BuiltIn(val key: String) : AudioStep
}

/** What the child is asked to do. */
enum class QuizMode {
    /** Hear the name (then the animal's sound) and pick the picture. */
    HEAR_NAME,
    /** Hear ONLY the animal's sound and pick the picture. The name is revealed after a correct answer. */
    HEAR_SOUND,
    /** Hear a letter's name and pick the picture of something that starts with it. */
    LETTER_TO_WORD,
    /** See and hear an object and pick the letter it starts with. */
    WORD_TO_LETTER
}

object QuizAudioPlan {
    /** The spoken name: the parent's recording for this language if there is one, else text-to-speech. */
    fun nameStep(card: QuizCard, lang: QuizLang): AudioStep {
        val recorded = if (lang == QuizLang.AR) card.voiceArFile else card.voiceEnFile
        if (recorded != null) return AudioStep.RecordedFile(recorded)
        val text = if (lang == QuizLang.AR) card.nameAr else card.nameEn
        return AudioStep.Tts(text, lang)
    }

    /** The animal's sound: the parent's own file wins over the built-in one; null if the card has none. */
    fun soundStep(card: QuizCard): AudioStep? =
        card.soundFile?.let { AudioStep.RecordedFile(it) } ?: card.builtInSoundKey?.let { AudioStep.BuiltIn(it) }

    /** The question itself. Sound-only questions play just the sound (or the name if the card has none). */
    fun prompt(card: QuizCard, lang: QuizLang, mode: QuizMode = QuizMode.HEAR_NAME): List<AudioStep> =
        when (mode) {
            QuizMode.HEAR_SOUND -> listOfNotNull(soundStep(card)).ifEmpty { listOf(nameStep(card, lang)) }
            else -> listOfNotNull(nameStep(card, lang), soundStep(card))
        }

    /** After a correct answer: always the full "name, then sound", revealing the answer. */
    fun reveal(card: QuizCard, lang: QuizLang): List<AudioStep> = listOfNotNull(nameStep(card, lang), soundStep(card))

    /** After a wrong tap: a gentle repeat of what was asked (just the name, or just the sound). */
    fun hint(card: QuizCard, lang: QuizLang, mode: QuizMode): List<AudioStep> =
        when (mode) {
            QuizMode.HEAR_SOUND -> prompt(card, lang, mode)
            else -> listOf(nameStep(card, lang))
        }

    // ── whole-question versions (they also handle the letter <-> word questions) ──

    /** What is said/played to ask the question. */
    fun promptFor(q: QuizQuestion, lang: QuizLang): List<AudioStep> = when (q.mode) {
        QuizMode.LETTER_TO_WORD -> listOf(nameStep(q.promptCard ?: q.target, lang))          // "ميم"
        QuizMode.WORD_TO_LETTER -> listOf(nameStep(q.stimulus ?: q.target, lang))            // "موزة"
        else -> prompt(q.target, lang, q.mode)
    }

    /** After a correct answer: the answer is revealed ("ميم، موزة" / "موزة، ميم" / name + sound). */
    fun revealFor(q: QuizQuestion, lang: QuizLang): List<AudioStep> = when (q.mode) {
        QuizMode.LETTER_TO_WORD ->
            listOfNotNull(nameStep(q.promptCard ?: q.target, lang), nameStep(q.target, lang), soundStep(q.target))
        QuizMode.WORD_TO_LETTER ->
            listOfNotNull(nameStep(q.stimulus ?: q.target, lang), nameStep(q.target, lang))
        else -> reveal(q.target, lang)
    }

    /** After a wrong tap: a gentle repeat of what was asked. */
    fun hintFor(q: QuizQuestion, lang: QuizLang): List<AudioStep> = when (q.mode) {
        QuizMode.LETTER_TO_WORD, QuizMode.WORD_TO_LETTER -> promptFor(q, lang)
        else -> hint(q.target, lang, q.mode)
    }
}
