package com.example.familysafetube

/**
 * JSON (de)serialisation for the parent's card overrides and the child's per-card statistics.
 * Tolerant on purpose: a missing, corrupt or hand-edited value never crashes the quiz; it just reads as "default".
 */
object QuizCardStore {
    private const val VERSION = 1

    fun encodeOverrides(overrides: Map<String, CardOverride>): String {
        val cards = LinkedHashMap<String, Any?>()
        for ((id, o) in overrides.toSortedMap()) {
            if (o.isDefault) continue
            val m = LinkedHashMap<String, Any?>()
            if (!o.enabled) m["enabled"] = false
            o.nameAr?.let { m["name_ar"] = it }
            o.nameEn?.let { m["name_en"] = it }
            if (!o.useBuiltInSound) m["builtin_sound"] = false
            o.soundFile?.let { m["sound"] = it }
            o.voiceArFile?.let { m["voice_ar"] = it }
            o.voiceEnFile?.let { m["voice_en"] = it }
            o.focusSinceDay?.let { m["focus_since"] = it }
            cards[id] = m
        }
        return MiniJson.stringify(mapOf("version" to VERSION, "cards" to cards))
    }

    fun decodeOverrides(text: String?): Map<String, CardOverride> {
        if (text.isNullOrBlank()) return emptyMap()
        return try {
            val root = MiniJson.parse(text) as? Map<*, *> ?: return emptyMap()
            val cards = root["cards"] as? Map<*, *> ?: return emptyMap()
            val out = LinkedHashMap<String, CardOverride>()
            for ((k, v) in cards) {
                val id = k as? String ?: continue
                val m = v as? Map<*, *> ?: continue
                out[id] = CardOverride(
                    enabled = m["enabled"] as? Boolean ?: true,
                    nameAr = m["name_ar"] as? String,
                    nameEn = m["name_en"] as? String,
                    useBuiltInSound = m["builtin_sound"] as? Boolean ?: true,
                    soundFile = m["sound"] as? String,
                    voiceArFile = m["voice_ar"] as? String,
                    voiceEnFile = m["voice_en"] as? String,
                    focusSinceDay = (m["focus_since"] as? Double)?.toLong()
                )
            }
            out
        } catch (_: JsonException) { emptyMap() }
    }

    fun encodeStats(stats: Map<String, CardStats>): String =
        MiniJson.stringify(stats.toSortedMap().mapValues { (_, s) -> listOf(s.shown, s.correct, s.wrong) })

    fun decodeStats(text: String?): Map<String, CardStats> {
        if (text.isNullOrBlank()) return emptyMap()
        return try {
            val root = MiniJson.parse(text) as? Map<*, *> ?: return emptyMap()
            val out = LinkedHashMap<String, CardStats>()
            for ((k, v) in root) {
                val id = k as? String ?: continue
                val a = v as? List<*> ?: continue
                if (a.size != 3) continue
                val nums = a.map { (it as? Double)?.toInt() }
                if (nums.any { it == null }) continue
                val n = nums.map { it!!.coerceAtLeast(0) }
                out[id] = CardStats(n[0], n[1], n[2])
            }
            out
        } catch (_: JsonException) { emptyMap() }
    }
}
