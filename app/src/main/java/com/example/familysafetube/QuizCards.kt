package com.example.familysafetube

import java.time.LocalDate

/** What a card looks like. Pictures come from files; letters and numbers are drawn by the app. */
sealed interface CardArt {
    data class Picture(val path: String) : CardArt
    /** A big letter or digit on a coloured tile. */
    data class Glyph(val text: String, val colorIndex: Int) : CardArt
    /** [count] stars on a coloured tile (for counting). */
    data class Dots(val count: Int, val colorIndex: Int) : CardArt
}

/** The parent's changes to one card (stored as JSON, see [QuizCardStore]). Only differences from the defaults are saved. */
data class CardOverride(
    val enabled: Boolean = true,
    val nameAr: String? = null,
    val nameEn: String? = null,
    /** false = do not use the bundled animal sound for this card. */
    val useBuiltInSound: Boolean = true,
    /** File names inside the quiz-audio folder. */
    val soundFile: String? = null,
    val voiceArFile: String? = null,
    val voiceEnFile: String? = null,
    /** Epoch day on which the parent starred this as a "focus word"; the focus lasts [QuizCards.FOCUS_DAYS] days. */
    val focusSinceDay: Long? = null
) {
    val isDefault: Boolean get() = this == CardOverride()
}

/** Everything the quiz and the editor need to know about one card. */
data class QuizCard(
    val id: String,
    val picturePath: String,
    val labelEn: String,
    val nameAr: String,
    val nameEn: String,
    val enabled: Boolean,
    /** True when no Arabic name is known (not in the catalog, not set by the parent). */
    val needsArabicName: Boolean,
    val builtInSoundKey: String?,
    val soundFile: String?,
    val voiceArFile: String?,
    val voiceEnFile: String?,
    /** A "word of the week": asked more often. */
    val focus: Boolean = false,
    val art: CardArt = CardArt.Picture(picturePath),
    /** A card the parent made from their own photo. */
    val isFamily: Boolean = false
) {
    val hasSound: Boolean get() = soundFile != null || builtInSoundKey != null
    val category: String get() = id.substringBefore('_')
}

/** The three things a parent can attach to a card by recording or importing audio. */
enum class AudioSlot {
    SOUND, VOICE_AR, VOICE_EN;

    /** Stable text used in file names. */
    val key: String get() = name.lowercase()

    fun fileOf(o: CardOverride): String? = when (this) {
        SOUND -> o.soundFile
        VOICE_AR -> o.voiceArFile
        VOICE_EN -> o.voiceEnFile
    }

    fun with(o: CardOverride, file: String?): CardOverride = when (this) {
        SOUND -> o.copy(soundFile = file)
        VOICE_AR -> o.copy(voiceArFile = file)
        VOICE_EN -> o.copy(voiceEnFile = file)
    }
}

/** Applies the edit form's text fields. A name equal to the default (or blank) is stored as "no override". */
fun CardOverride.withNames(nameAr: String, nameEn: String, defaultEnglish: String, defaultArabic: String?): CardOverride {
    val ar = nameAr.trim().takeIf { it.isNotEmpty() && it != defaultArabic }
    val en = nameEn.trim().takeIf { it.isNotEmpty() && it != defaultEnglish }
    return copy(nameAr = ar, nameEn = en)
}

object QuizCards {
    /** How long a starred "focus word" stays boosted. */
    const val FOCUS_DAYS = 7

    fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

    /** True while a word starred on [sinceDay] is still within its week. */
    fun isFocused(sinceDay: Long?, today: Long): Boolean = sinceDay != null && today - sinceDay in 0..FOCUS_DAYS

    /** Stable id from the picture's file name: "animals_lion.png" -> "animals_lion". */
    fun idFromFileName(fileName: String): String = fileName.substringBeforeLast('.').lowercase()

    /** Combines the synced pictures, the built-in catalog and the parent's overrides into cards. */
    fun build(
        pictures: List<Pair<String, String>>,
        overrides: Map<String, CardOverride>,
        today: Long = todayEpochDay()
    ): List<QuizCard> =
        pictures.map { (fileName, path) ->
            val id = idFromFileName(fileName)
            val label = QuizRules.labelFromFileName(fileName)
            val entry = QuizCatalog.lookup(label)
            val o = overrides[id] ?: CardOverride()
            val arOverride = o.nameAr?.trim()?.takeIf { it.isNotEmpty() }
            val enOverride = o.nameEn?.trim()?.takeIf { it.isNotEmpty() }
            QuizCard(
                id = id,
                picturePath = path,
                labelEn = label,
                nameAr = arOverride ?: entry?.ar ?: label,
                nameEn = enOverride ?: label,
                enabled = o.enabled,
                // a file already named in Arabic (e.g. "ميم - موزة") has its Arabic name; nothing to ask for
                needsArabicName = arOverride == null && entry == null && !QuizText.containsArabic(label),
                builtInSoundKey = if (o.useBuiltInSound && o.soundFile == null) entry?.soundKey else null,
                soundFile = o.soundFile,
                voiceArFile = o.voiceArFile,
                voiceEnFile = o.voiceEnFile,
                focus = isFocused(o.focusSinceDay, today)
            )
        }.sortedBy { it.id }

    /** Applies the parent's override (names, voices, on/off, focus) on top of a ready-made card (decks and family cards). */
    fun applyOverride(base: QuizCard, o: CardOverride?, today: Long = todayEpochDay()): QuizCard {
        val ov = o ?: CardOverride()
        return base.copy(
            nameAr = ov.nameAr?.trim()?.takeIf { it.isNotEmpty() } ?: base.nameAr,
            nameEn = ov.nameEn?.trim()?.takeIf { it.isNotEmpty() } ?: base.nameEn,
            enabled = ov.enabled,
            soundFile = ov.soundFile ?: base.soundFile,
            voiceArFile = ov.voiceArFile,
            voiceEnFile = ov.voiceEnFile,
            focus = isFocused(ov.focusSinceDay, today)
        )
    }

    /** Cards made from the parent's own photos (family, pets, the car...). */
    fun buildFamily(
        entries: List<FamilyCard>,
        photoDir: String,
        overrides: Map<String, CardOverride>,
        today: Long = todayEpochDay()
    ): List<QuizCard> = entries.map { e ->
        val ar = e.nameAr.ifBlank { e.nameEn }
        val en = e.nameEn.ifBlank { e.nameAr }
        applyOverride(
            QuizCard(
                id = e.id,
                picturePath = "$photoDir/${e.photoFile}",
                labelEn = en,
                nameAr = ar,
                nameEn = en,
                enabled = true,
                needsArabicName = false,
                builtInSoundKey = null,
                soundFile = null,
                voiceArFile = null,
                voiceEnFile = null,
                isFamily = true
            ),
            overrides[e.id], today
        )
    }.sortedBy { it.id }
}
