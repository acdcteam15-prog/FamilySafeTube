package com.example.familysafetube

/**
 * The sticker album: a visual reward. After each finished round the child may unlock the sticker of
 * something they just got right. Stickers never give screen time.
 */
object StickerAlbum {
    /** Collected card ids, in the order they were earned. */
    fun decode(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            (MiniJson.parse(text) as? List<*>)?.mapNotNull { (it as? String)?.takeIf { s -> s.isNotBlank() } }?.distinct() ?: emptyList()
        } catch (_: JsonException) { emptyList() }
    }

    fun encode(collected: List<String>): String = MiniJson.stringify(collected.distinct())

    /**
     * The sticker to award after a round: the first thing answered correctly (in the order answered) that is a real
     * card and not already collected. Null if there is nothing new (then the round is celebrated without a sticker).
     */
    fun award(roundCorrectIds: List<String>, collected: List<String>, eligible: Set<String>): String? =
        roundCorrectIds.distinct().firstOrNull { it in eligible && it !in collected }

    data class Progress(val collected: Int, val total: Int)

    fun progress(collected: List<String>, eligible: Set<String>) = Progress(collected.count { it in eligible }, eligible.size)

    /** Album order: collected stickers newest first, then the ones still to find. */
    fun albumOrder(cards: List<QuizCard>, collected: List<String>): List<Pair<QuizCard, Boolean>> {
        val byId = cards.associateBy { it.id }
        val have = collected.reversed().mapNotNull { byId[it] }.map { it to true }
        val haveIds = have.map { it.first.id }.toSet()
        val missing = cards.filter { it.id !in haveIds }.sortedBy { it.id }.map { it to false }
        return have + missing
    }
}
