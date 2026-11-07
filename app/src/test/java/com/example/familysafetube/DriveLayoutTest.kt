package com.example.familysafetube

import com.example.familysafetube.DriveLayout.Folder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveLayoutTest {

    /** A fake Drive: parentId -> sub-folders. */
    private class FakeDrive(val tree: MutableMap<String, MutableList<Folder>> = mutableMapOf()) {
        var next = 0
        val created = mutableListOf<String>()
        fun list(parent: String): List<Folder> = tree[parent]?.toList() ?: emptyList()
        fun create(name: String, parent: String): Folder {
            val f = Folder("id${next++}", name, "2026-01-01T00:00:0${next}Z")
            tree.getOrPut(parent) { mutableListOf() }.add(f)
            created += "$parent/$name"
            return f
        }
    }

    private fun full(): FakeDrive {
        val d = FakeDrive()
        val off = Folder("off", "Offline"); val img = Folder("img", "Images")
        d.tree["root"] = mutableListOf(Folder("x", "Other stuff"), off)
        d.tree["off"] = mutableListOf(img)
        d.tree["img"] = mutableListOf(
            Folder("a", "Animals"), Folder("l", "Letters"), Folder("o", "Other"), Folder("v", "Veg&fruits")
        )
        return d
    }

    @Test fun findsTheWholeTree() {
        val r = DriveLayout.resolve(full()::list)
        assertTrue(r.missing().isEmpty())
        assertEquals(
            mapOf("videos" to "off", "animals" to "a", "letters" to "l", "veg&fruits" to "v", "other" to "o"),
            r.categoryIds()
        )
    }

    @Test fun namesIgnoreCaseAndSpaces() {
        val d = FakeDrive()
        d.tree["root"] = mutableListOf(Folder("off", " offline "))
        d.tree["off"] = mutableListOf(Folder("img", "IMAGES"))
        d.tree["img"] = mutableListOf(Folder("v", "veg&FRUITS"), Folder("a", "animals"))
        val r = DriveLayout.resolve(d::list)
        assertEquals("v", r.imageFolders["veg&fruits"]?.id)
        assertEquals("a", r.imageFolders["animals"]?.id)
        assertEquals(listOf("Offline/Images/Letters", "Offline/Images/Other"), r.missing())
    }

    @Test fun emptyDriveReportsEverythingMissingParentsFirst() {
        val r = DriveLayout.resolve(FakeDrive()::list)
        assertNull(r.offline)
        assertEquals(
            listOf("Offline", "Offline/Images", "Offline/Images/Animals", "Offline/Images/Letters",
                   "Offline/Images/Veg&fruits", "Offline/Images/Other").sorted(),
            r.missing().sorted()
        )
        assertEquals("Offline", r.missing().first())
        assertEquals(listOf("videos", "animals", "letters", "veg&fruits", "other").sorted(), r.missingCategories().sorted())
        assertTrue(r.categoryIds().isEmpty())
    }

    @Test fun imagesFolderMustBeInsideOffline() {
        val d = FakeDrive()
        d.tree["root"] = mutableListOf(Folder("off", "Offline"), Folder("strayImg", "Images"))
        val r = DriveLayout.resolve(d::list)
        assertNull(r.images)
        assertTrue("Offline/Images" in r.missing())
    }

    @Test fun duplicatesPickTheOldest() {
        val d = FakeDrive()
        d.tree["root"] = mutableListOf(
            Folder("new", "Offline", "2026-09-01T00:00:00Z"),
            Folder("old", "Offline", "2025-01-01T00:00:00Z")
        )
        assertEquals("old", DriveLayout.resolve(d::list).offline?.id)
    }

    @Test fun ensureCreatesOnlyWhatIsMissing() {
        val d = FakeDrive()
        d.tree["root"] = mutableListOf(Folder("off", "Offline"))
        val r = DriveLayout.ensure(d::list, d::create)
        assertTrue(r.missing().isEmpty())
        assertEquals(5, d.created.size)   // Images + the four picture folders
        assertTrue(d.created.none { it.endsWith("/Offline") })
        // a second run finds everything and creates nothing more
        d.created.clear()
        DriveLayout.ensure(d::list, d::create)
        assertTrue(d.created.isEmpty())
    }

    @Test fun ensureBuildsTheFullTreeInAnEmptyDrive() {
        val d = FakeDrive()
        val r = DriveLayout.ensure(d::list, d::create)
        assertEquals(6, d.created.size)
        assertEquals(5, r.categoryIds().size)
        assertTrue(DriveLayout.resolve(d::list).missing().isEmpty())
    }

    // ── new categories: any other folder inside Offline/Images ──
    @Test fun anotherFolderInImages_becomesACategory() {
        val d = full()
        d.tree["img"]!!.add(Folder("b", "Birds"))
        val r = DriveLayout.resolve(d::list)
        assertEquals(mapOf("birds" to "b"), r.customFolders.mapValues { it.value.id })
        assertEquals(mapOf("birds" to "Birds"), r.customNames())
        assertEquals("b", r.categoryIds()["birds"])
        assertTrue(r.missing().isEmpty())                       // a new folder never counts as "missing"
    }

    @Test fun theFourBuiltInFolders_areNeverNewCategories_evenWithDuplicatesOrOddCase() {
        val d = full()
        d.tree["img"]!!.add(Folder("a2", "animals ", "2030-01-01T00:00:00Z"))      // a duplicate of Animals
        d.tree["img"]!!.add(Folder("v2", "VEG&FRUITS"))
        assertTrue(DriveLayout.resolve(d::list).customFolders.isEmpty())
    }

    @Test fun twoFoldersWithTheSameId_theOldestWins_andUnusableNamesAreSkipped() {
        val kids = listOf(
            Folder("n", "Pets", "2026-05-01T00:00:00Z"), Folder("o", "pets ", "2026-01-01T00:00:00Z"),
            Folder("v", "Videos"), Folder("c", "config"), Folder("e", "  "), Folder("u", "///")
        )
        val out = DriveLayout.customFolders(kids)
        assertEquals(listOf("pets"), out.keys.toList())
        assertEquals("o", out["pets"]?.id)                      // "videos" and "config" are reserved ids; blank / symbol-only names are skipped
    }

    @Test fun ensure_keepsTheNewCategories() {
        val d = full()
        d.tree["img"]!!.add(Folder("b", "Birds"))
        val r = DriveLayout.ensure(d::list, d::create)
        assertEquals(listOf("birds"), r.customFolders.keys.toList())
        assertTrue(d.created.isEmpty())
    }
}
