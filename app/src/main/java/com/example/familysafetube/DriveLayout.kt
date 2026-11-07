package com.example.familysafetube

/**
 * The Drive folder tree the app expects in the signed-in user's My Drive:
 *
 *   My Drive/Offline/                 offline videos, Youtube.txt, optional settings.json
 *   My Drive/Offline/Images/Animals
 *   My Drive/Offline/Images/Letters
 *   My Drive/Offline/Images/Other
 *   My Drive/Offline/Images/Veg&fruits
 *   My Drive/Offline/Images/<any other folder>   -> a new picture category, named like the folder (see [ImageCategories])
 *
 * Names are matched ignoring upper/lower case and surrounding spaces. Pure logic (no Android, no
 * network) so it is unit-tested; [DriveApi] supplies the real folder listing and folder creation.
 */
object DriveLayout {
    const val ROOT_NAME = "Offline"
    const val IMAGES_NAME = "Images"

    /** Sync category -> folder name inside Offline/Images (also the names used when creating them). */
    val IMAGE_FOLDERS: Map<String, String> = linkedMapOf(
        "animals" to "Animals",
        "letters" to "Letters",
        "veg&fruits" to "Veg&fruits",
        "other" to "Other"
    )

    data class Folder(val id: String, val name: String, val createdTime: String = "")

    /** What was found in Drive. A null folder means it does not exist (yet). */
    class Resolved(
        val offline: Folder?,
        val images: Folder?,
        /** category -> folder, only for the image folders that exist */
        val imageFolders: Map<String, Folder>,
        /** Any other folder inside Offline/Images: category id ([ImageCategories.slug]) -> folder. These are the new categories. */
        val customFolders: Map<String, Folder> = emptyMap()
    ) {
        /** Category id -> the folder's name as written in Drive, for the new categories. */
        fun customNames(): Map<String, String> = customFolders.mapValues { it.value.name.trim() }

        /** Drive paths that are still missing, parents first. Empty when everything is there. */
        fun missing(): List<String> {
            val out = mutableListOf<String>()
            if (offline == null) out += ROOT_NAME
            if (images == null) out += "$ROOT_NAME/$IMAGES_NAME"
            for ((cat, name) in IMAGE_FOLDERS) if (cat !in imageFolders) out += "$ROOT_NAME/$IMAGES_NAME/$name"
            return out
        }

        /** Sync category -> Drive folder id for every folder that exists. "videos" is the Offline folder. */
        fun categoryIds(): Map<String, String> {
            val m = linkedMapOf<String, String>()
            offline?.let { m["videos"] = it.id }
            for ((cat, f) in imageFolders) m[cat] = f.id
            for ((cat, f) in customFolders) m[cat] = f.id
            return m
        }

        /** Sync categories whose Drive folder does not exist. */
        fun missingCategories(): List<String> {
            val out = mutableListOf<String>()
            if (offline == null) out += "videos"
            for (cat in IMAGE_FOLDERS.keys) if (cat !in imageFolders) out += cat
            return out
        }
    }

    private fun norm(s: String) = s.trim().lowercase()

    /** The folder called [wanted] among [children]; if there are duplicates the oldest one wins (stable choice). */
    fun pick(children: List<Folder>, wanted: String): Folder? =
        children.filter { norm(it.name) == norm(wanted) }
            .minWithOrNull(compareBy<Folder>({ it.createdTime.ifEmpty { "~" } }, { it.id }))

    /** Looks the tree up. [listChildren] returns the sub-folders of a folder id ("root" = My Drive). */
    fun resolve(listChildren: (parentId: String) -> List<Folder>): Resolved {
        val offline = pick(listChildren("root"), ROOT_NAME)
        val images = offline?.let { pick(listChildren(it.id), IMAGES_NAME) }
        val kids = images?.let { listChildren(it.id) } ?: emptyList()
        val found = linkedMapOf<String, Folder>()
        for ((cat, name) in IMAGE_FOLDERS) pick(kids, name)?.let { found[cat] = it }
        return Resolved(offline, images, found, customFolders(kids))
    }

    /**
     * Every folder in Offline/Images that is not one of the four built-in ones is a category of its own. Folders whose names
     * make the same id ("Birds" and "birds ") count once (the oldest wins); a name that cannot make a usable id is skipped.
     */
    fun customFolders(kids: List<Folder>): Map<String, Folder> {
        val builtIn = IMAGE_FOLDERS.values.map { norm(it) }.toSet()
        val bySlug = LinkedHashMap<String, MutableList<Folder>>()
        for (k in kids.sortedBy { it.name.lowercase() }) {
            if (norm(k.name) in builtIn) continue
            val slug = ImageCategories.slug(k.name)
            if (slug.isEmpty() || slug in ImageCategories.RESERVED) continue
            bySlug.getOrPut(slug) { mutableListOf() }.add(k)
        }
        val out = linkedMapOf<String, Folder>()
        for ((slug, list) in bySlug) out[slug] = list.minWith(compareBy<Folder>({ it.createdTime.ifEmpty { "~" } }, { it.id }))
        return out
    }

    /** Like [resolve], but creates whatever is missing with [create] (name, parentId) and returns the full tree. */
    fun ensure(listChildren: (String) -> List<Folder>, create: (name: String, parentId: String) -> Folder): Resolved {
        val r = resolve(listChildren)
        val offline = r.offline ?: create(ROOT_NAME, "root")
        val images = r.images ?: create(IMAGES_NAME, offline.id)
        val found = LinkedHashMap(r.imageFolders)
        for ((cat, name) in IMAGE_FOLDERS) if (cat !in found) found[cat] = create(name, images.id)
        return Resolved(offline, images, found, r.customFolders)
    }
}
