package com.example.familysafetube

/** A card the parent made from their own photo. The photo lives in the app's private "quiz_family" folder. */
data class FamilyCard(val id: String, val photoFile: String, val nameAr: String, val nameEn: String)

object QuizFamilyStore {
    private const val VERSION = 1

    /** Ids start with "family_" so every family card is its own category-mate in the quiz. */
    fun newId(nowMs: Long): String = "family_$nowMs"

    fun encode(cards: List<FamilyCard>): String = MiniJson.stringify(
        mapOf(
            "version" to VERSION,
            "cards" to cards.map { mapOf("id" to it.id, "photo" to it.photoFile, "name_ar" to it.nameAr, "name_en" to it.nameEn) }
        )
    )

    /** Tolerant: corrupt input reads as "no family cards"; entries without an id or photo are skipped. */
    fun decode(text: String?): List<FamilyCard> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            val root = MiniJson.parse(text) as? Map<*, *> ?: return emptyList()
            val list = root["cards"] as? List<*> ?: return emptyList()
            list.mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                val id = (m["id"] as? String)?.takeIf { it.startsWith("family_") } ?: return@mapNotNull null
                val photo = (m["photo"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                FamilyCard(id, photo, (m["name_ar"] as? String).orEmpty(), (m["name_en"] as? String).orEmpty())
            }.distinctBy { it.id }
        } catch (_: JsonException) { emptyList() }
    }
}
