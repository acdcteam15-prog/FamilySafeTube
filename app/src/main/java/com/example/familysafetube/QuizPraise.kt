package com.example.familysafetube

import kotlin.random.Random

/** The moments the parent's own voice can react to (instead of the synthesised sounds). */
enum class PraiseEvent(val key: String) {
    CORRECT("correct"),
    TRY_AGAIN("try_again"),
    ROUND_DONE("round_done");

    companion object {
        fun fromKey(key: String): PraiseEvent? = values().firstOrNull { it.key == key }
    }
}

/** The parent's recorded clips per moment (file names in the quiz-audio folder). Up to [MAX_CLIPS] each. */
data class PraiseSet(val clips: Map<PraiseEvent, List<String>> = emptyMap()) {
    fun clipsFor(event: PraiseEvent): List<String> = clips[event].orEmpty()
    fun isFull(event: PraiseEvent): Boolean = clipsFor(event).size >= MAX_CLIPS

    /** Adds a clip unless that moment already has [MAX_CLIPS] (then nothing changes). */
    fun add(event: PraiseEvent, file: String): PraiseSet =
        if (isFull(event) || file in clipsFor(event)) this else PraiseSet(clips + (event to clipsFor(event) + file))

    fun remove(event: PraiseEvent, file: String): PraiseSet {
        val left = clipsFor(event) - file
        return PraiseSet(if (left.isEmpty()) clips - event else clips + (event to left))
    }

    companion object { const val MAX_CLIPS = 3 }
}

object PraiseStore {
    fun encode(set: PraiseSet): String =
        MiniJson.stringify(PraiseEvent.values().filter { set.clipsFor(it).isNotEmpty() }.associate { it.key to set.clipsFor(it) })

    fun decode(text: String?): PraiseSet {
        if (text.isNullOrBlank()) return PraiseSet()
        return try {
            val root = MiniJson.parse(text) as? Map<*, *> ?: return PraiseSet()
            val map = LinkedHashMap<PraiseEvent, List<String>>()
            for ((k, v) in root) {
                val event = PraiseEvent.fromKey(k as? String ?: continue) ?: continue
                val files = (v as? List<*>)?.mapNotNull { it as? String }?.filter { it.isNotBlank() }?.distinct()
                    ?.take(PraiseSet.MAX_CLIPS) ?: continue
                if (files.isNotEmpty()) map[event] = files
            }
            PraiseSet(map)
        } catch (_: JsonException) { PraiseSet() }
    }
}

object PraisePicker {
    /** Picks a clip at random, never the one played last when there is a choice. Null if there are no clips. */
    fun pick(clips: List<String>, lastPlayed: String?, random: Random = Random.Default): String? {
        if (clips.isEmpty()) return null
        if (clips.size == 1) return clips[0]
        val candidates = clips.filter { it != lastPlayed }.ifEmpty { clips }
        return candidates[random.nextInt(candidates.size)]
    }
}
