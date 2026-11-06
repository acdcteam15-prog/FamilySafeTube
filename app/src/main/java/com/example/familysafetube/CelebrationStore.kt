package com.example.familysafetube

import androidx.datastore.preferences.core.MutablePreferences

/** Reads and writes the celebration settings (Settings > Celebration). The rules themselves are in CelebrationVoice.kt. */
object CelebrationStore {
    fun load(): CelebrationConfig {
        val st = AppGraph.settings
        return CelebrationConfig(
            onAnswer = st.getOr(Keys.CELEB_ON_ANSWER, true),
            onRound = st.getOr(Keys.CELEB_ON_ROUND, true),
            voice = VoiceSource.fromKey(st[Keys.CELEB_VOICE]),
            phrases = CelebrationSettings.parsePhrases(st[Keys.CELEB_PHRASES]),
            useName = st.getOr(Keys.CELEB_USE_NAME, false),
            name = CelebrationSettings.cleanName(st[Keys.CELEB_NAME]),
            style = VoiceStyle(st.getOr(Keys.CELEB_PITCH, 1f), st.getOr(Keys.CELEB_RATE, 1f)).clamped(),
            diacritics = st.getOr(Keys.CELEB_DIACRITICS, true),
            effect = CelebrationEffect.fromKey(st[Keys.CELEB_EFFECT]),
            answerClip = st[Keys.CELEB_CLIP_ANSWER].orEmpty(),
            roundClip = st[Keys.CELEB_CLIP_ROUND].orEmpty()
        )
    }

    /** Called from the Settings save: everything is cleaned on the way in, so a bad value can never be stored. */
    fun write(p: MutablePreferences, c: CelebrationConfig) {
        val style = c.style.clamped()
        p[Keys.CELEB_ON_ANSWER] = c.onAnswer
        p[Keys.CELEB_ON_ROUND] = c.onRound
        p[Keys.CELEB_VOICE] = c.voice.key
        p[Keys.CELEB_PHRASES] = CelebrationSettings.formatPhrases(c.phrases.ifEmpty { PhraseId.values().toSet() })
        p[Keys.CELEB_USE_NAME] = c.useName
        p[Keys.CELEB_NAME] = CelebrationSettings.cleanName(c.name)
        p[Keys.CELEB_PITCH] = style.pitch
        p[Keys.CELEB_RATE] = style.rate
        p[Keys.CELEB_DIACRITICS] = c.diacritics
        p[Keys.CELEB_EFFECT] = c.effect.key
        p[Keys.CELEB_CLIP_ANSWER] = c.answerClip
        p[Keys.CELEB_CLIP_ROUND] = c.roundClip
    }
}
