package com.example.familysafetube

import android.content.Context

/** One line of the picture-category list: what to show and how many pictures are on the phone. */
data class CategoryRow(val id: String, val name: String, val count: Int)

/**
 * Android side of the picture categories: reads and saves the [CategoryBook], counts pictures, and carries out the parent's
 * decision about a folder that was removed from Drive. The rules are in ImageCategories.kt.
 */
object ImageCategoryStore {
    private val PICTURE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")

    fun load(): CategoryBook = CategoryBook.decode(AppGraph.settings[Keys.IMAGE_BOOK])

    /** The name shown on screen: the built-in ones are translated, a new category is named like its Drive folder. */
    fun displayName(ctx: Context, id: String, book: CategoryBook = load()): String = when (id) {
        "animals" -> ctx.getString(R.string.cat_animals)
        "letters" -> ctx.getString(R.string.cat_letters)
        "veg&fruits" -> ctx.getString(R.string.cat_veg)
        ImageCategories.OTHER -> ctx.getString(R.string.cat_other)
        else -> book.known[id]?.takeIf { it.isNotBlank() } ?: id
    }

    /** Number of pictures of one category on the phone. */
    fun count(ctx: Context, id: String): Int =
        SyncEngine.getLocalFiles(ctx, id).count { it.extension.lowercase() in PICTURE_EXTENSIONS && !it.name.endsWith(".tmp") }

    /** Every category in screen order (alphabetical, Other last) with its picture count. */
    fun rows(ctx: Context): List<CategoryRow> {
        val book = load()
        return ImageCategories.order(book.allIds()) { displayName(ctx, it, book) }
            .map { CategoryRow(it, displayName(ctx, it, book), count(ctx, it)) }
    }

    /** The categories whose pictures feed the quiz and the matching game (every one except Other). */
    fun quizIds(): List<String> = load().allIds().filter { ImageCategories.isQuizCategory(it) }

    /** Which of [candidates] have at least one file on the phone (file names are "<id>_<file>"). */
    fun idsWithFiles(ctx: Context, candidates: Collection<String>): Set<String> {
        val names = ctx.filesDir.listFiles()?.map { it.name.lowercase() }.orEmpty()
        return candidates.filter { id -> names.any { it.startsWith("${id}_") } }.toSet()
    }

    /** The parent's decision for a category whose Drive folder was removed. */
    fun resolveRemoved(ctx: Context, id: String, removeFromPhone: Boolean) {
        if (removeFromPhone) {
            for (f in SyncEngine.getLocalFiles(ctx, id)) f.delete()
        }
        AppGraph.settings.edit { p ->
            p[Keys.IMAGE_BOOK] = CategoryBook.decode(p[Keys.IMAGE_BOOK]).resolved(id, removeFromPhone).encode()
        }
    }

    /** Called by the sync after a successful look at Offline/Images. Returns the names of the categories seen for the first time. */
    fun updateAfterScan(ctx: Context, customInDrive: Map<String, String>, builtInInDrive: Set<String>): List<String> {
        val before = load().known.keys
        val withFiles = idsWithFiles(ctx, ImageCategories.BUILT_IN + before + customInDrive.keys)
        AppGraph.settings.edit { p ->
            p[Keys.IMAGE_BOOK] = CategoryBook.decode(p[Keys.IMAGE_BOOK]).afterScan(customInDrive, builtInInDrive, withFiles).encode()
        }
        return customInDrive.filterKeys { it !in before }.values.toList()
    }
}
