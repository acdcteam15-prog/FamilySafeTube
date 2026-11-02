package com.example.familysafetube

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

enum class ImportResult { OK, TOO_BIG, FAILED }

/**
 * Android-side access to the quiz data: the synced pictures, the parent's per-card changes, the
 * recordings folder and the child's statistics. All rules live in the pure classes (QuizCards,
 * QuizEngine, QuizCardStore); this class only reads and writes.
 */
object QuizRepository {
    private const val AUDIO_DIR = "quiz_audio"
    private const val MAX_IMPORT_BYTES = 5L * 1024 * 1024
    private val PICTURE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    private val PICTURE_CATEGORIES = listOf("animals", "letters", "veg&fruits")

    // ── files ────────────────────────────────────────────────────────────────
    /** Recordings live in their own sub-folder, so sync's deletion pass (top-level files only) never touches them. */
    fun audioDir(ctx: Context): File = File(ctx.filesDir, AUDIO_DIR).also { it.mkdirs() }

    fun audioFile(ctx: Context, name: String): File = File(audioDir(ctx), File(name).name)   // .name blocks "../"

    fun newAudioFileName(cardId: String, slot: AudioSlot): String =
        "${cardId.lowercase().replace(Regex("[^a-z0-9_-]"), "_")}_${slot.key}_${System.currentTimeMillis()}.snd"

    fun deleteAudio(ctx: Context, name: String?) {
        if (name != null) audioFile(ctx, name).delete()
    }

    fun pictures(ctx: Context): List<File> =
        PICTURE_CATEGORIES.flatMap { SyncEngine.getLocalFiles(ctx, it) }.filter {
            it.extension.lowercase() in PICTURE_EXTENSIONS && !it.name.endsWith(".tmp") && it.length() > 1024L
        }

    /** Every card the quiz can use: synced pictures, the built-in decks switched on in Settings, and the parent's family cards. */
    fun loadCards(ctx: Context): List<QuizCard> {
        val overrides = overrides()
        val pictures = QuizCards.build(this.pictures(ctx).map { it.name to it.absolutePath }, overrides)
        val decks = QuizDecks.cards(enabledDecks(), overrides)
        val family = QuizCards.buildFamily(familyEntries(), FamilyPhotos.dir(ctx).absolutePath, overrides)
        return (pictures + decks + family).sortedBy { it.id }
    }

    // ── letters, numbers (always available to the games, whether or not the decks are switched on) ──
    fun letterCards(): List<QuizCard> = QuizDecks.cards(setOf(DeckId.AR_LETTERS, DeckId.EN_LETTERS), overrides())
    fun numberTiles(): List<QuizCard> = QuizDecks.cards(setOf(DeckId.NUMBERS), overrides())
    fun countTiles(): List<QuizCard> = QuizDecks.cards(setOf(DeckId.COUNTING), overrides())

    // ── sticker album ───────────────────────────────────────────────────────
    fun stickers(): List<String> = StickerAlbum.decode(AppGraph.settings[Keys.QUIZ_STICKERS_JSON])

    fun addSticker(cardId: String) {
        AppGraph.settings.edit { p ->
            val now = StickerAlbum.decode(p[Keys.QUIZ_STICKERS_JSON])
            if (cardId !in now) p[Keys.QUIZ_STICKERS_JSON] = StickerAlbum.encode(now + cardId)
        }
    }

    // ── levels and decks ────────────────────────────────────────────────────
    fun level(): QuizLevel = QuizLevel.fromCode(AppGraph.settings[Keys.QUIZ_LEVEL])
    fun enabledDecks(): Set<DeckId> = QuizDecks.parse(AppGraph.settings[Keys.QUIZ_DECKS])

    // ── focus words ("words this week") ─────────────────────────────────────
    fun setFocus(cardId: String, on: Boolean) =
        updateOverride(cardId) { it.copy(focusSinceDay = if (on) QuizCards.todayEpochDay() else null) }

    fun clearAllFocus() {
        AppGraph.settings.edit { p ->
            val cleared = QuizCardStore.decodeOverrides(p[Keys.QUIZ_CARDS_JSON]).mapValues { it.value.copy(focusSinceDay = null) }
            p[Keys.QUIZ_CARDS_JSON] = QuizCardStore.encodeOverrides(cleared)
        }
    }

    // ── praise voices ───────────────────────────────────────────────────────
    fun praise(): PraiseSet = PraiseStore.decode(AppGraph.settings[Keys.QUIZ_PRAISE_JSON])

    fun updatePraise(change: (PraiseSet) -> PraiseSet) {
        AppGraph.settings.edit { p -> p[Keys.QUIZ_PRAISE_JSON] = PraiseStore.encode(change(PraiseStore.decode(p[Keys.QUIZ_PRAISE_JSON]))) }
    }

    fun newPraiseFileName(event: PraiseEvent): String = "praise_${event.key}_${System.currentTimeMillis()}.snd"

    // ── family cards ────────────────────────────────────────────────────────
    fun familyEntries(): List<FamilyCard> = QuizFamilyStore.decode(AppGraph.settings[Keys.QUIZ_FAMILY_JSON])

    fun addFamilyCard(card: FamilyCard) {
        AppGraph.settings.edit { p -> p[Keys.QUIZ_FAMILY_JSON] = QuizFamilyStore.encode(QuizFamilyStore.decode(p[Keys.QUIZ_FAMILY_JSON]) + card) }
    }

    /** Removes a family card with its photo and every recording attached to it. */
    fun deleteFamilyCard(ctx: Context, cardId: String) {
        val entry = familyEntries().firstOrNull { it.id == cardId }
        val o = overrides()[cardId]
        listOfNotNull(o?.soundFile, o?.voiceArFile, o?.voiceEnFile).forEach { deleteAudio(ctx, it) }
        entry?.let { FamilyPhotos.file(ctx, it.photoFile).delete() }
        AppGraph.settings.edit { p ->
            p[Keys.QUIZ_FAMILY_JSON] = QuizFamilyStore.encode(QuizFamilyStore.decode(p[Keys.QUIZ_FAMILY_JSON]).filter { it.id != cardId })
            p[Keys.QUIZ_CARDS_JSON] = QuizCardStore.encodeOverrides(QuizCardStore.decodeOverrides(p[Keys.QUIZ_CARDS_JSON]) - cardId)
        }
    }

    /** Copies a picked audio file into the recordings folder (size-capped). */
    fun importAudio(ctx: Context, uri: Uri, target: File): ImportResult {
        return try {
            val input = ctx.contentResolver.openInputStream(uri) ?: return ImportResult.FAILED
            var total = 0L
            input.use { src ->
                target.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = src.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_IMPORT_BYTES) { target.delete(); return ImportResult.TOO_BIG }
                        out.write(buf, 0, n)
                    }
                }
            }
            if (total == 0L) { target.delete(); ImportResult.FAILED } else ImportResult.OK
        } catch (e: IOException) {
            target.delete(); ImportResult.FAILED
        } catch (e: SecurityException) {
            target.delete(); ImportResult.FAILED
        }
    }

    // ── the parent's per-card changes ───────────────────────────────────────
    fun overrides(): Map<String, CardOverride> = QuizCardStore.decodeOverrides(AppGraph.settings[Keys.QUIZ_CARDS_JSON])

    /** Read-modify-write inside one store edit, so two quick changes can never overwrite each other. */
    fun updateOverride(cardId: String, change: (CardOverride) -> CardOverride) {
        AppGraph.settings.edit { p ->
            val map = QuizCardStore.decodeOverrides(p[Keys.QUIZ_CARDS_JSON]).toMutableMap()
            map[cardId] = change(map[cardId] ?: CardOverride())
            p[Keys.QUIZ_CARDS_JSON] = QuizCardStore.encodeOverrides(map)
        }
    }

    // ── the child's progress ────────────────────────────────────────────────
    fun stats(): Map<String, CardStats> = QuizCardStore.decodeStats(AppGraph.settings[Keys.QUIZ_STATS_JSON])

    fun recordAnswer(cardId: String, correct: Boolean) {
        AppGraph.settings.edit { p ->
            val map = QuizCardStore.decodeStats(p[Keys.QUIZ_STATS_JSON]).toMutableMap()
            map[cardId] = (map[cardId] ?: CardStats()).record(correct)
            p[Keys.QUIZ_STATS_JSON] = QuizCardStore.encodeStats(map)
        }
    }

    // ── language ────────────────────────────────────────────────────────────
    fun language(): QuizLang = QuizLang.fromCode(AppGraph.settings[Keys.QUIZ_LANG])
}
