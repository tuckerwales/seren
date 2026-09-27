package wales.tucker.seren.files.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import wales.tucker.seren.files.MainActivity
import wales.tucker.seren.files.TestApp
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Drives the real UI: browsing, creating, copying, moving, renaming, deleting and restoring files. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppBehaviourTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val root get() = app.root

    private fun file(path: String, text: String = path) = File(root, path).apply {
        parentFile!!.mkdirs()
        writeText(text)
    }

    private fun exists(text: String) =
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = compose.waitUntil(5_000) { exists(text) }

    private fun waitGone(text: String) = compose.waitUntil(5_000) { !exists(text) }

    private fun waitUntil(condition: () -> Boolean) = compose.waitUntil(5_000, condition)

    /** The three dot menu on the row for [name]. */
    private fun rowMenu(name: String): SemanticsNodeInteraction =
        compose.onNode(hasContentDescription("More") and hasAnyAncestor(hasText(name)))

    private fun dialogButton(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    private fun openStorage() {
        waitFor("Internal storage")
        compose.onNodeWithText("Internal storage").performClick()
        waitFor("New folder")
    }

    private fun back() {
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
    }

    @Test
    fun opensFoldersAndBackRetracesThem() {
        file("Download/report.pdf")
        file("Download/Trip/beach.jpg")
        file("Documents/cv.pdf")
        waitFor("Downloads")
        compose.onNodeWithText("Downloads").performClick()
        waitFor("report.pdf")
        compose.onNodeWithText("Trip").performClick()
        waitFor("beach.jpg")

        back()
        waitFor("report.pdf")
        // The breadcrumbs jump straight to any folder above.
        compose.onNodeWithText("Internal storage").performClick()
        waitFor("Documents")
        back()
        waitFor("report.pdf")
        back()
        waitFor("Downloads")
        compose.onNodeWithText("Storage").assertExists()
    }

    /*
     * Naming things (New folder, New file, Rename, Compress) happens in a dialog with a focused
     * text field, which Robolectric can't idle with, so OperationsTest covers those flows.
     */

    @Test
    fun copiesAndAsksAboutNamesAlreadyTaken() {
        file("Download/notes.txt", "hello")
        File(root, "Documents").mkdirs()
        openStorage()
        compose.onNodeWithText("Download").performClick()
        waitFor("notes.txt")
        rowMenu("notes.txt").performClick()
        compose.onNodeWithText("Copy").performClick()
        waitFor("Copying notes.txt")

        back()
        compose.onNodeWithText("Documents").performClick()
        waitFor("Empty folder")
        compose.onNodeWithText("Paste").performClick()
        waitFor("Copied 1 item to Documents")
        assertEquals("hello", File(root, "Documents/notes.txt").readText())
        assertTrue(File(root, "Download/notes.txt").exists())

        // The clipboard stays for a copy, so pasting again asks what to do.
        compose.onNodeWithText("Paste").performClick()
        waitFor("Replace notes.txt?")
        compose.onNodeWithText("Keep both").performClick()
        dialogButton("Copy").performClick()
        waitUntil { File(root, "Documents/notes (1).txt").exists() }
    }

    @Test
    fun picksSeveralAndMovesThem() {
        file("Download/a.txt")
        file("Download/b.txt")
        file("Download/c.txt")
        File(root, "Archive").mkdirs()
        openStorage()
        compose.onNodeWithText("Download").performClick()
        waitFor("a.txt")

        compose.onNodeWithText("a.txt").performTouchInput { longClick() }
        waitFor("1 selected")
        compose.onNodeWithText("c.txt").performClick()
        waitFor("2 selected")
        compose.onNodeWithText("Move").performClick()
        waitFor("Moving 2 items")

        back()
        compose.onNodeWithText("Archive").performClick()
        waitFor("Empty folder")
        compose.onNodeWithText("Paste").performClick()
        waitFor("Moved 2 items to Archive")
        assertEquals(listOf("a.txt", "c.txt"), File(root, "Archive").list()!!.sorted())
        assertEquals(listOf("b.txt"), File(root, "Download").list()!!.sorted())
        // A finished move clears the paste bar.
        waitGone("Moving 2 items")
    }

    @Test
    fun deletingMovesToTheTrashWithUndo() {
        file("draft.txt", "words")
        openStorage()
        waitFor("draft.txt")
        rowMenu("draft.txt").performClick()
        compose.onNodeWithText("Delete").performClick()
        waitFor("Move draft.txt to the trash?")
        dialogButton("Move to trash").performClick()
        waitFor("Moved draft.txt to the trash")
        assertFalse(File(root, "draft.txt").exists())

        compose.onNodeWithText("Undo").performClick()
        waitUntil { File(root, "draft.txt").exists() }
        assertEquals("words", File(root, "draft.txt").readText())
    }

    @Test
    fun theTrashTabRestoresAndDeletesForGood() {
        val keep = file("Documents/keep.txt")
        val lose = file("Documents/lose.txt")
        runBlocking { app.container.trash.moveToTrash(listOf(keep, lose)) }
        waitFor("Trash")
        compose.onNodeWithText("Trash").performClick()
        waitFor("keep.txt")
        assertTrue(exists("/Documents"))

        compose.onNodeWithText("keep.txt").performClick()
        waitFor("Restore keep.txt?")
        dialogButton("Restore").performClick()
        waitFor("Restored keep.txt")
        assertTrue(keep.exists())

        rowMenu("lose.txt").performClick()
        compose.onNodeWithText("Delete permanently").performClick()
        dialogButton("Delete").performClick()
        waitFor("Trash is empty")
        assertFalse(lose.exists())
        assertTrue(runBlocking { app.container.trash.items.first() }.isEmpty())
    }

    @Test
    fun withoutTheTrashDeletingIsPermanent() {
        runBlocking { app.container.settings.setUseTrash(false) }
        try {
            File(root, "old/inner").mkdirs()
            openStorage()
            rowMenu("old").performClick()
            compose.onNodeWithText("Delete").performClick()
            waitFor("It will be deleted for good, with everything inside.")
            dialogButton("Delete").performClick()
            waitFor("Deleted old")
            assertFalse(File(root, "old").exists())
        } finally {
            runBlocking { app.container.settings.setUseTrash(true) }
        }
    }

    @Test
    fun searchFindsFilesInFoldersBelow() {
        file("Documents/Taxes/2026/receipt-bike.pdf")
        file("Documents/receipt-coffee.png")
        file("Documents/notes.txt")
        waitFor("Internal storage")
        compose.onNodeWithContentDescription("Search").performClick()
        waitFor("Search by name")
        compose.onNode(hasSetTextAction()).performTextInput("receipt")
        waitFor("receipt-bike.pdf")
        compose.onNodeWithText("receipt-coffee.png").assertExists()
        compose.onNodeWithText("/Documents/Taxes/2026").assertExists()
        assertFalse(exists("notes.txt"))
    }

    @Test
    fun extractsZipFiles() {
        val zip = File(root, "photos.zip")
        ZipOutputStream(FileOutputStream(zip)).use { z ->
            z.putNextEntry(ZipEntry("one.jpg"))
            z.write(ByteArray(10))
            z.closeEntry()
        }
        openStorage()
        compose.onNodeWithText("photos.zip").performClick()
        waitFor("Extract photos.zip?")
        dialogButton("Extract").performClick()
        waitFor("Extracted to photos")
        assertTrue(File(root, "photos/one.jpg").exists())
    }

    @Test
    fun bookmarkedFoldersShowOnBrowse() {
        File(root, "Projects/seren").mkdirs()
        openStorage()
        rowMenu("Projects").performClick()
        compose.onNodeWithText("Add to bookmarks").performClick()
        waitFor("Added Projects to bookmarks")
        back()
        waitFor("Bookmarks")
        compose.onNodeWithText("/Projects").assertExists()
        compose.onNodeWithText("Projects").performClick()
        waitFor("seren")
    }

    @Test
    fun asksForAccessFirst() {
        app.storage.access = false
        compose.activityRule.scenario.recreate()
        waitFor("Allow access to your files")
        compose.onNodeWithText("Allow access").assertExists()
        app.storage.access = true
    }

    @Test
    fun theTrashRestoresOrDeletesSeveralAtOnce() {
        val a = file("Documents/a.txt")
        val b = file("Documents/b.txt")
        val c = file("Documents/c.txt")
        runBlocking { app.container.trash.moveToTrash(listOf(a, b, c)) }
        waitFor("Trash")
        compose.onNodeWithText("Trash").performClick()
        waitFor("a.txt")

        compose.onNodeWithText("a.txt").performTouchInput { longClick() }
        waitFor("1 selected")
        compose.onNodeWithText("b.txt").performClick()
        waitFor("2 selected")
        compose.onNodeWithContentDescription("Restore").performClick()
        waitFor("Restored 2 items")
        assertTrue(a.exists() && b.exists())
        assertFalse(c.exists())

        compose.onNodeWithText("c.txt").performTouchInput { longClick() }
        waitFor("1 selected")
        compose.onNodeWithContentDescription("Delete permanently").performClick()
        waitFor("Delete c.txt permanently?")
        dialogButton("Delete").performClick()
        waitFor("Trash is empty")
        assertTrue(runBlocking { app.container.trash.items.first() }.isEmpty())
    }

    @Test
    fun gridViewShowsTilesAndPicksWithALongPress() {
        file("Download/a.txt")
        file("Download/b.zip")
        File(root, "Download/Trip").mkdirs()
        waitFor("Downloads")
        compose.onNodeWithText("Downloads").performClick()
        waitFor("a.txt")
        try {
            compose.onNodeWithContentDescription("Grid view").performClick()
            waitUntil { runBlocking { app.container.settings.settings.first().gridView } }
            waitUntil { compose.onAllNodes(hasContentDescription("List view")).fetchSemanticsNodes().isNotEmpty() }
            // Tiles have no menu of their own: picking one offers everything instead.
            assertFalse(compose.onAllNodes(hasContentDescription("More") and hasAnyAncestor(hasText("a.txt"))).fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithText("Trip").performClick()
            waitFor("Empty folder")
            back()
            waitFor("b.zip")
            compose.onNodeWithText("b.zip").performTouchInput { longClick() }
            waitFor("1 selected")
            compose.onNodeWithText("More").performClick()
            waitFor("Extract")
            compose.onNodeWithText("Open with").assertExists()
        } finally {
            runBlocking { app.container.settings.setGridView(false) }
        }
    }

    @Test
    fun categoriesGatherFilesFromEveryFolder() {
        file("DCIM/Camera/beach.jpg")
        file("Download/meme.png")
        file("Documents/cv.pdf")
        java.io.RandomAccessFile(file("Movies/film.mkv"), "rw").use { it.setLength(wales.tucker.seren.files.fs.Categories.LARGE_BYTES * 2) }
        waitFor("Categories")
        waitFor("Images")
        compose.onNodeWithText("Images").performClick()
        waitFor("beach.jpg")
        compose.onNodeWithText("meme.png").assertExists()
        compose.onNodeWithText("/DCIM/Camera").assertExists()
        assertFalse(exists("cv.pdf"))

        // Deleting from a category goes to the trash like anywhere else.
        rowMenu("meme.png").performClick()
        compose.onNodeWithText("Delete").performClick()
        dialogButton("Move to trash").performClick()
        waitFor("Moved meme.png to the trash")
        waitUntil { compose.onAllNodes(hasContentDescription("More") and hasAnyAncestor(hasText("meme.png"))).fetchSemanticsNodes().isEmpty() }
        assertFalse(File(root, "Download/meme.png").exists())

        back()
        waitFor("Categories")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Large files"))
        compose.onNodeWithText("Large files").performClick()
        waitFor("film.mkv")
        compose.onNodeWithText("Biggest first", substring = true).assertExists()
    }

    @Test
    fun showInFolderPicksOutTheFile() {
        file("DCIM/Camera/beach.jpg")
        waitFor("Images")
        compose.onNodeWithText("Images").performClick()
        waitFor("beach.jpg")
        rowMenu("beach.jpg").performClick()
        compose.onNodeWithText("Show in folder").performClick()
        waitFor("New folder")
        // The folder's title and its breadcrumb.
        assertEquals(2, compose.onAllNodesWithText("Camera").fetchSemanticsNodes().size)
        compose.onNodeWithText("beach.jpg").assertExists()
    }

    @Test
    fun extracts7zFiles() {
        // The password dialog holds a text field, which Robolectric can't idle with, so
        // OperationsTest covers protected 7z files.
        javaClass.getResourceAsStream("/archives/project.7z")!!.use { input -> File(root, "project.7z").outputStream().use { input.copyTo(it) } }
        openStorage()
        compose.onNodeWithText("project.7z").performClick()
        waitFor("Extract project.7z?")
        dialogButton("Extract").performClick()
        waitFor("Extracted to project")
        assertEquals("deep\n", File(root, "project/project/src/deeply/nested/folder/with/a/name/long/enough/to/need/more/than/one/hundred/characters/file.txt").readText())
    }
}
