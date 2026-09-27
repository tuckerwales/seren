package wales.tucker.seren.files.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import wales.tucker.seren.files.TestApp
import wales.tucker.seren.files.fs.Listing
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class TrashBinTest {
    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val root get() = app.root
    private val trash get() = app.container.trash

    private fun file(path: String, text: String = path) = File(root, path).apply {
        parentFile!!.mkdirs()
        writeText(text)
    }

    @Test
    fun trashedItemsLeaveTheirFolderAndComeBack() = runBlocking {
        val notes = file("Documents/notes.txt", "hello")
        val photos = File(root, "DCIM/Trip").apply { mkdirs() }
        file("DCIM/Trip/one.jpg")

        val result = trash.moveToTrash(listOf(notes, photos))

        assertEquals(2, result.done.size)
        assertFalse(notes.exists())
        assertFalse(photos.exists())
        val stored = File(root, Listing.TRASH_FOLDER)
        assertTrue(File(stored, ".nomedia").exists())
        assertEquals(listOf("Trip", "notes.txt"), trash.items.first().map { it.name }.sorted())
        assertEquals(5L, result.done.first { it.name == "notes.txt" }.size)

        val restored = trash.restore(trash.items.first())
        assertEquals(2, restored.done.size)
        assertEquals("hello", notes.readText())
        assertTrue(File(photos, "one.jpg").exists())
        assertTrue(trash.items.first().isEmpty())
    }

    @Test
    fun restoringRecreatesTheFolderAndAvoidsNamesTakenSince() = runBlocking {
        val a = file("Projects/old/a.txt", "old a")
        val item = trash.moveToTrash(listOf(a)).done.single()
        File(root, "Projects/old").deleteRecursively()

        assertEquals(File(root, "Projects/old/a.txt"), trash.restore(listOf(item)).done.single())

        val again = trash.moveToTrash(listOf(a)).done.single()
        file("Projects/old/a.txt", "new a")
        val back = trash.restore(listOf(again)).done.single()
        assertEquals("a (1).txt", back.name)
        assertEquals("old a", back.readText())
        assertEquals("new a", a.readText())
    }

    @Test
    fun deleteForeverEmptyAndTidy() = runBlocking {
        val items = trash.moveToTrash(listOf(file("a.txt"), file("b.txt"), file("c.txt"))).done
        trash.deleteForever(listOf(items[0]))
        assertFalse(File(items[0].storedPath).exists())
        assertEquals(2, trash.items.first().size)

        // Something removed from the trash folder by hand is forgotten.
        File(items[1].storedPath).delete()
        trash.tidy()
        assertEquals(listOf("c.txt"), trash.items.first().map { it.name })

        trash.empty()
        assertTrue(trash.items.first().isEmpty())
        assertEquals(listOf(".nomedia"), File(root, Listing.TRASH_FOLDER).list()!!.toList())
    }

    @Test
    fun itemsOlderThanThirtyDaysAreDeletedForGood() = runBlocking {
        val dao = app.container.database.trash()
        val old = File(root, "${Listing.TRASH_FOLDER}/1-old.txt").apply { parentFile!!.mkdirs(); writeText("old") }
        dao.insert(TrashItem(name = "old.txt", originalPath = "$root/old.txt", storedPath = old.path, isDirectory = false, size = 3, deletedAt = System.currentTimeMillis() - TrashBin.KEEP_MILLIS - 1000))
        trash.moveToTrash(listOf(file("new.txt")))

        trash.tidy()

        assertFalse(old.exists())
        assertEquals(listOf("new.txt"), trash.items.first().map { it.name })
    }
}
