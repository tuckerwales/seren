package wales.tucker.seren.files.ops

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import wales.tucker.seren.files.TestApp
import wales.tucker.seren.files.data.Bookmark
import wales.tucker.seren.files.fs.ConflictPolicy
import java.io.File
import java.util.zip.ZipFile

/** The flows behind New folder, Rename, Compress, Extract, Paste and Delete, and what they say. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class OperationsTest {
    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val root get() = app.root
    private val ops get() = app.container.operations
    private val said = mutableListOf<String>()

    private fun file(path: String, text: String = path) = File(root, path).apply {
        parentFile!!.mkdirs()
        writeText(text)
    }

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun listen() {
        scope.launch { ops.messages.collect { said += it.text } }
    }

    @After
    fun stop() = scope.cancel()

    /** Runs [block], then lets the main looper and IO work run until [done]. */
    private fun run(done: () -> Boolean = { !ops.busy }, block: () -> Unit) {
        block()
        val deadline = System.currentTimeMillis() + 5_000
        do {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        } while (!done() && System.currentTimeMillis() < deadline)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun newFoldersAndFilesSayWhatWentWrong() {
        run { assertEquals(File(root, "Projects"), ops.createFolder(root, "Projects")) }
        run { assertNull(ops.createFolder(root, "Projects")) }
        run { assertNull(ops.createFolder(root, "a/b")) }
        run { assertEquals(File(root, "notes.txt"), ops.createFile(root, "notes.txt")) }
        assertEquals(listOf("Projects already exists", "Names can't contain /"), said)
        assertTrue(File(root, "notes.txt").isFile)
    }

    @Test
    fun renamingAFolderKeepsItsBookmarks() = runBlocking {
        val folder = File(root, "Projects/seren").apply { mkdirs() }
        app.container.bookmarks.insert(Bookmark(folder.parent!!, "Projects", 1))
        app.container.bookmarks.insert(Bookmark(folder.path, "seren", 2))

        run(done = { runBlocking { app.container.bookmarks.observe().first().any { it.name == "Work" } } }) {
            ops.rename(File(root, "Projects"), "Work")
        }

        val marks = app.container.bookmarks.observe().first().map { it.name to it.path.removePrefix(root.path) }
        assertEquals(listOf("seren" to "/Work/seren", "Work" to "/Work"), marks)
    }

    @Test
    fun pastingReportsWhatHappened() {
        val a = file("Download/a.txt")
        val b = file("Download/b.txt")
        val docs = File(root, "Documents").apply { mkdirs() }

        ops.setClipboard(listOf(a, b), move = true)
        val plan = runBlocking { ops.plan(listOf(a, b), docs, move = true)!! }
        run { ops.transfer(plan, ConflictPolicy.REPLACE) }
        assertEquals(listOf("Moved 2 items to Documents"), said)
        assertNull(ops.clipboard.value)

        val c = file("Download/a.txt", "again")
        val again = runBlocking { ops.plan(listOf(c), docs, move = false)!! }
        assertEquals(listOf(c), again.conflicts)
        run { ops.transfer(again, ConflictPolicy.SKIP) }
        assertEquals("Skipped 1 item already in Documents", said.last())

        assertNull(runBlocking { ops.plan(listOf(docs), File(docs, "."), move = true) })
        assertEquals("Can't move Documents into itself", said.last())
    }

    @Test
    fun compressAndExtract() {
        file("Pictures/one.jpg", "1")
        file("Pictures/two.jpg", "22")
        run { ops.compress(listOf(File(root, "Pictures")), root, "Pictures") }
        assertEquals("Compressed to Pictures.zip", said.last())
        ZipFile(File(root, "Pictures.zip")).use { z ->
            assertEquals(listOf("Pictures/", "Pictures/one.jpg", "Pictures/two.jpg"), z.entries().toList().map { it.name })
        }

        run { ops.extract(File(root, "Pictures.zip")) }
        assertEquals("Extracted to Pictures (1)", said.last())
        assertEquals("22", File(root, "Pictures (1)/Pictures/two.jpg").readText())

        run { ops.compress(listOf(File(root, "Pictures")), root, "Pictures.zip") }
        assertEquals("Pictures.zip already exists", said.last())
    }

    @Test
    fun deletingForGoodForgetsBookmarksInside() {
        val folder = File(root, "Old/inner").apply { mkdirs() }
        runBlocking { app.container.bookmarks.insert(Bookmark(folder.path, "inner", 1)) }
        run { ops.delete(listOf(File(root, "Old")), permanently = true) }
        assertEquals("Deleted Old", said.last())
        assertFalse(File(root, "Old").exists())
        assertTrue(runBlocking { app.container.bookmarks.observe().first() }.isEmpty())
    }

    @Test
    fun aProtected7zAsksForItsPassword() {
        val archive = File(root, "locked.7z")
        javaClass.getResourceAsStream("/archives/locked.7z")!!.use { input -> archive.outputStream().use { input.copyTo(it) } }

        run { ops.extract(archive) }
        val asked = ops.passwordNeeded.value!!
        assertEquals(archive, asked.archive)
        assertFalse(asked.wrong)
        assertFalse(File(root, "locked").exists())
        // Asked on screen, not said in the snackbar.
        assertTrue(said.isEmpty())

        run { ops.extract(archive, "wrong") }
        assertTrue(ops.passwordNeeded.value!!.wrong)

        run { ops.extract(archive, "hunter2") }
        assertNull(ops.passwordNeeded.value)
        assertEquals("Extracted to locked", said.last())
        assertEquals("hello from gnu tar\n", File(root, "locked/readme.txt").readText())
    }
}
