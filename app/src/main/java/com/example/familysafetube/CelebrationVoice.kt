package com.example.familysafetube

import kotlin.math.abs
import kotlin.random.Random

/** The two moments the celebration can play. */
enum class CelebrationEvent(val praiseEvent: PraiseEvent) {
    /** After a right answer (in the matching game: a matched pair). */
    ANSWER(PraiseEvent.CORRECT),
    /** At the end of a round (in the matching game: a finished board). */
    ROUND(PraiseEvent.ROUND_DONE)
}

/** Whose voice says "well done". */
enum class VoiceSource(val key: String) {
    /** The parent's own recordings; when there is none for this moment, the phone voice is used instead. */
    RECORDINGS("recordings"),
    /** The phone's text-to-speech voice, with phrases and (optionally) the child's name. */
    PHONE("phone"),
    /** No spoken celebration. */
    OFF("off");

    companion object { fun fromKey(key: String?): VoiceSource = values().firstOrNull { it.key == key } ?: RECORDINGS }
}

/** The sound played just before the voice (made in code, no audio files). */
enum class CelebrationEffect(val key: String) {
    CHEER("cheer"), FANFARE("fanfare"), APPLAUSE("applause"), SPARKLE("sparkle"), NONE("none");

    companion object { fun fromKey(key: String?): CelebrationEffect = values().firstOrNull { it.key == key } ?: CHEER }
}

/** The phrases the phone voice can say (the words live in [CelebrationPhrases], in Arabic and English). */
enum class PhraseId { WELL_DONE, BRAVO, GREAT_JOB, EXCELLENT, AMAZING, YOU_DID_IT }

/** How the phone voice sounds: [pitch] and speaking [rate] (1.0 = the phone's normal voice). */
data class VoiceStyle(val pitch: Float, val rate: Float) {
    fun clamped() = VoiceStyle(pitch.coerceIn(PITCH_MIN, PITCH_MAX), rate.coerceIn(RATE_MIN, RATE_MAX))

    companion object {
        const val PITCH_MIN = 0.6f
        const val PITCH_MAX = 1.6f
        const val RATE_MIN = 0.6f
        const val RATE_MAX = 1.5f

        val NORMAL = VoiceStyle(1.0f, 1.0f)
        val PLAYFUL = VoiceStyle(1.25f, 1.1f)
        val GENTLE = VoiceStyle(0.9f, 0.85f)
        val EXCITED = VoiceStyle(1.4f, 1.2f)
        val PRESETS = listOf(NORMAL, PLAYFUL, GENTLE, EXCITED)

        /** The preset these values are (almost exactly), or null when they were tuned by hand. */
        fun presetOf(style: VoiceStyle): VoiceStyle? =
            PRESETS.firstOrNull { abs(it.pitch - style.pitch) < 0.01f && abs(it.rate - style.rate) < 0.01f }
    }
}

/** Everything the parent can set about the celebration (Settings > Celebration). */
data class CelebrationConfig(
    val onAnswer: Boolean = true,
    val onRound: Boolean = true,
    val voice: VoiceSource = VoiceSource.RECORDINGS,
    val phrases: Set<PhraseId> = PhraseId.values().toSet(),
    val useName: Boolean = false,
    val name: String = "",
    val style: VoiceStyle = VoiceStyle.NORMAL,
    val effect: CelebrationEffect = CelebrationEffect.CHEER,
    /** A recording file the parent picked for right answers; "" = a random one of the recordings. */
    val answerClip: String = "",
    /** The same for the end of a round. */
    val roundClip: String = ""
) {
    fun clipFor(event: CelebrationEvent): String = if (event == CelebrationEvent.ANSWER) answerClip else roundClip
    fun enabled(event: CelebrationEvent): Boolean = if (event == CelebrationEvent.ANSWER) onAnswer else onRound

    /** The same settings with [file] picked as the recording for [event] ("" = a random one). */
    fun withClip(event: CelebrationEvent, file: String): CelebrationConfig =
        if (event == CelebrationEvent.ANSWER) copy(answerClip = file) else copy(roundClip = file)
}

/** Cleaning and storing the settings (pure, unit-tested). */
object CelebrationSettings {
    const val NAME_MAX = 24

    /** "BRAVO,WELL_DONE" -> set; unknown words are ignored; nothing valid means all phrases. */
    fun parsePhrases(csv: String?): Set<PhraseId> {
        val parsed = csv.orEmpty().split(',').mapNotNull { t -> PhraseId.values().firstOrNull { it.name.equals(t.trim(), ignoreCase = true) } }.toSet()
        return parsed.ifEmpty { PhraseId.values().toSet() }
    }

    fun formatPhrases(set: Set<PhraseId>): String = PhraseId.values().filter { it in set }.joinToString(",") { it.name }

    /** Ticking or unticking one phrase; the last ticked phrase cannot be removed (the voice must have something to say). */
    fun withPhrase(current: Set<PhraseId>, id: PhraseId, on: Boolean): Set<PhraseId> {
        val next = if (on) current + id else current - id
        return if (next.isEmpty()) current else next
    }

    /** A name for the voice: no control characters or braces, single spaces, at most [NAME_MAX] letters. */
    fun cleanName(raw: String?): String =
        raw.orEmpty().filter { !it.isISOControl() && it != '{' && it != '}' }
            .trim().replace(Regex("\\s+"), " ").take(NAME_MAX).trim()
}

/** What the phone voice says. A name is added only when asked for and not blank. */
object CelebrationPhrases {
    private val EN = mapOf(
        PhraseId.WELL_DONE to "Well done", PhraseId.BRAVO to "Bravo", PhraseId.GREAT_JOB to "Great job",
        PhraseId.EXCELLENT to "Excellent", PhraseId.AMAZING to "Amazing", PhraseId.YOU_DID_IT to "You did it"
    )
    private val AR = mapOf(
        PhraseId.WELL_DONE to "أحسنت", PhraseId.BRAVO to "برافو", PhraseId.GREAT_JOB to "عمل رائع",
        PhraseId.EXCELLENT to "ممتاز", PhraseId.AMAZING to "رائع", PhraseId.YOU_DID_IT to "لقد نجحت"
    )

    /** "Well done!" / "Well done, Sara!" / "أحسنت!" / "أحسنت يا سارة!" */
    fun text(lang: QuizLang, id: PhraseId, name: String? = null): String {
        val base = (if (lang == QuizLang.AR) AR else EN).getValue(id)
        val who = name?.trim().orEmpty()
        return when {
            who.isEmpty() -> "$base!"
            lang == QuizLang.AR -> "$base يا $who!"
            else -> "$base, $who!"
        }
    }

    /** One of the ticked phrases at random (never the one used last time when there is a choice), with the name if wanted. */
    fun choose(config: CelebrationConfig, lang: QuizLang, last: PhraseId?, random: Random): Pair<PhraseId, String> {
        val pool = config.phrases.ifEmpty { setOf(PhraseId.WELL_DONE) }.sortedBy { it.ordinal }
        val choices = if (pool.size > 1) pool.filter { it != last } else pool
        val id = choices[random.nextInt(choices.size)]
        val name = if (config.useName) CelebrationSettings.cleanName(config.name) else ""
        return id to text(lang, id, name)
    }
}

/** What to play for one moment: the sound, then the voice. Pure, so every combination of settings is unit-tested. */
object CelebrationPlan {
    data class Result(val steps: List<AudioStep>, val phrase: PhraseId? = null, val clip: String? = null)

    /**
     * @param clips the parent's recordings for this moment (file names)
     * @param lastPhrase / [lastClip] what was used last time, so it is not repeated back to back
     */
    fun build(
        config: CelebrationConfig,
        event: CelebrationEvent,
        lang: QuizLang,
        clips: List<String>,
        lastPhrase: PhraseId?,
        lastClip: String?,
        random: Random = Random.Default
    ): Result {
        if (!config.enabled(event)) return Result(emptyList())
        val steps = ArrayList<AudioStep>()
        if (config.effect != CelebrationEffect.NONE) steps += AudioStep.Effect(config.effect, long = event == CelebrationEvent.ROUND)

        var phrase: PhraseId? = null
        var clip: String? = null
        if (config.voice == VoiceSource.RECORDINGS) {
            val wanted = config.clipFor(event)
            clip = if (wanted.isNotEmpty() && wanted in clips) wanted else PraisePicker.pick(clips, lastClip, random)
            if (clip != null) steps += AudioStep.RecordedFile(clip)
        }
        if (config.voice == VoiceSource.PHONE || (config.voice == VoiceSource.RECORDINGS && clip == null)) {
            val (id, text) = CelebrationPhrases.choose(config, lang, lastPhrase, random)
            val style = config.style.clamped()
            steps += AudioStep.Tts(text, lang, style.pitch, style.rate)
            phrase = id
        }
        return Result(steps, phrase, clip)
    }
}

/** Keeps "what was said last" for one screen, so the same phrase or clip is not repeated back to back. */
class CelebrationVoice(
    private val config: CelebrationConfig,
    private val praise: PraiseSet,
    private val lang: QuizLang,
    private val random: Random = Random.Default
) {
    private var lastPhrase: PhraseId? = null
    private val lastClip = HashMap<CelebrationEvent, String>()

    fun steps(event: CelebrationEvent): List<AudioStep> {
        val r = CelebrationPlan.build(config, event, lang, praise.clipsFor(event.praiseEvent), lastPhrase, lastClip[event], random)
        r.phrase?.let { lastPhrase = it }
        r.clip?.let { lastClip[event] = it }
        return r.steps
    }
}
