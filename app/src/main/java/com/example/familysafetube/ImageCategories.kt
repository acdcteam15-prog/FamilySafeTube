package com.example.familysafetube

/**
 * The picture categories. Four are built in (Animals, Letters, Veg&fruits, Other); every other folder the parent adds inside
 * Drive's Offline/Images becomes a new category named like the folder. Pure logic (no Android), unit-tested.
 *
 * A category's **id** is also the prefix of its local file names ("<id>_<file>"), so it never contains "_".
 */
object ImageCategories {
    /** The built-in ids, in [DriveLayout.IMAGE_FOLDERS] order. */
    val BUILT_IN: List<String> = DriveLayout.IMAGE_FOLDERS.keys.toList()

    /** "Other" is shown as pictures only: it is not used in the quiz or the matching game (as before). */
    const val OTHER = "other"

    /** Ids a Drive folder may not take: the built-in ones and the two non-picture sync groups. */
    val RESERVED: Set<String> = BUILT_IN.toSet() + setOf("videos", "config")

    private const val MAX_LENGTH = 40
    private const val NOT_ALLOWED = "_/\\:*?\"<>|"

    /** "Birds" -> "birds"; "Sea_life / fish" -> "sea life fish". Letters of any language are kept. Empty = unusable. */
    fun slug(folderName: String): String =
        folderName.lowercase()
            .map { if (it in NOT_ALLOWED || it.isISOControl()) ' ' else it }
            .joinToString("")
            .trim().replace(Regex("\\s+"), " ")
            .take(MAX_LENGTH).trim()

    /** Everything except Other is educational: names are spoken, and the pictures become quiz and matching cards. */
    fun isQuizCategory(id: String): Boolean = id != OTHER

    /** Alphabetical by the name shown on screen (ignoring capitals), with Other always last. */
    fun order(ids: Collection<String>, nameOf: (String) -> String): List<String> =
        ids.distinct().sortedWith(compareBy<String>({ it == OTHER }, { nameOf(it).lowercase() }, { it }))
}

/**
 * What the app remembers about the categories (stored as one JSON text in the settings):
 *  - [known]: the new categories seen in Drive, id -> the folder's name as written there;
 *  - [pending]: categories whose Drive folder is gone but whose pictures are still on the phone: the parent decides in Settings;
 *  - [kept]: categories the parent chose to keep on the phone after the folder was removed (not asked again).
 */
data class CategoryBook(
    val known: Map<String, String> = emptyMap(),
    val pending: Set<String> = emptySet(),
    val kept: Set<String> = emptySet()
) {
    /** Ids of the new (non built-in) categories the phone has. */
    fun customIds(): List<String> = known.keys.filter { it !in ImageCategories.RESERVED }

    /** Every picture category: the four built-in ones plus the new ones. */
    fun allIds(): List<String> = ImageCategories.BUILT_IN + customIds()

    /**
     * Brings the book up to date after a successful look at Offline/Images.
     * @param customInDrive the new folders found now: id -> name
     * @param builtInInDrive which built-in folders exist now
     * @param idsWithFiles the categories that have pictures on the phone
     */
    fun afterScan(customInDrive: Map<String, String>, builtInInDrive: Set<String>, idsWithFiles: Set<String>): CategoryBook {
        val inDrive = customInDrive.keys + builtInInDrive
        val kept2 = kept - inDrive                                  // a kept folder that came back is synced again
        val known2 = (known + customInDrive).filterKeys { it in inDrive || it in idsWithFiles }
        val pending2 = (ImageCategories.BUILT_IN + known2.keys)
            .filter { it in idsWithFiles && it !in inDrive && it !in kept2 }.toSet()
        return CategoryBook(known2, pending2, kept2)
    }

    /** The parent's answer for a removed folder: [removeFromPhone] true = its pictures are deleted (the caller does that), false = keep them. */
    fun resolved(id: String, removeFromPhone: Boolean): CategoryBook =
        if (removeFromPhone) CategoryBook(known - id, pending - id, kept - id)
        else CategoryBook(known, pending - id, kept + id)

    fun encode(): String = MiniJson.stringify(
        mapOf("known" to known, "pending" to pending.sorted(), "kept" to kept.sorted())
    )

    companion object {
        fun decode(text: String?): CategoryBook {
            if (text.isNullOrBlank()) return CategoryBook()
            return try {
                val root = MiniJson.parse(text) as? Map<*, *> ?: return CategoryBook()
                val known = LinkedHashMap<String, String>()
                (root["known"] as? Map<*, *>)?.forEach { (k, v) ->
                    val id = k as? String ?: return@forEach
                    val name = v as? String ?: return@forEach
                    if (id.isNotBlank() && id !in ImageCategories.RESERVED && '_' !in id) known[id] = name
                }
                fun ids(key: String): Set<String> =
                    (root[key] as? List<*>)?.mapNotNull { it as? String }?.filter { it.isNotBlank() }?.toSet().orEmpty()
                CategoryBook(known, ids("pending"), ids("kept"))
            } catch (_: JsonException) {
                CategoryBook()
            }
        }
    }
}
