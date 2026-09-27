package wales.tucker.seren.files.pick

import android.app.Activity
import android.content.Intent
import android.os.Environment
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import wales.tucker.seren.files.TestApp
import java.io.File

/** Choosing files for another app: what's offered, and what the app gets back. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PickTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    private var scenario: ActivityScenario<PickActivity>? = null

    /** Results carry FileProvider links, which only cover shared storage, so the files live there. */
    private val shared get() = Environment.getExternalStorageDirectory()

    private fun file(path: String, text: String = path) = File(shared, path).apply {
        parentFile!!.mkdirs()
        writeText(text)
    }

    private fun exists(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = compose.waitUntil(5_000) { exists(text) }

    private fun launch(intent: Intent): ActivityScenario<PickActivity> {
        // The picker browses the device's volumes; here, shared storage is the only one.
        app.storage.let { it.volumeRoot = shared }
        return ActivityScenario.launchActivityForResult<PickActivity>(intent.setClass(app, PickActivity::class.java)).also { scenario = it }
    }

    /**
     * FileProvider keeps each authority's roots for the life of the process, but every Robolectric
     * test has its own storage folder, so forget the last test's.
     */
    @Before
    fun forgetFileProviderRoots() {
        val cache = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        synchronized(cache.get(null)!!) { (cache.get(null) as MutableMap<*, *>).clear() }
    }

    @After
    fun tearDown() {
        scenario?.close()
        app.storage.volumeRoot = null
        File(shared, "Download").deleteRecursively()
        File(shared, "Pictures").deleteRecursively()
    }

    @Test
    fun choosingAFileHandsBackAReadOnlyLink() {
        file("Download/report.pdf", "pdf")
        val s = launch(Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE))
        waitFor("Choose a file")
        waitFor("Internal storage")
        compose.onNodeWithText("Internal storage").performClick()
        // Exactly "Download", the folder, not the "Downloads" shortcut.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Download").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Download").performClick()
        waitFor("report.pdf")
        compose.onNodeWithText("report.pdf").performClick()

        val result = s.result
        assertEquals(Activity.RESULT_OK, result.resultCode)
        val uri = result.resultData.data!!
        assertEquals("content", uri.scheme)
        assertTrue(result.resultData.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, result.resultData.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals("pdf", app.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() })
    }

    @Test
    fun onlyFilesOfTheAskedTypeAreOffered() {
        file("Pictures/cat.jpg")
        file("Pictures/notes.txt")
        launch(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"))
        waitFor("Choose a photo")
        waitFor("Pictures")
        compose.onNodeWithText("Pictures").performClick()
        waitFor("cat.jpg")
        assertFalse(exists("notes.txt"))
    }

    @Test
    fun severalCanBeChosenWhenTheAppAllowsIt() {
        file("Download/a.txt", "a")
        file("Download/b.txt", "b")
        file("Download/c.txt", "c")
        val s = launch(Intent(Intent.ACTION_GET_CONTENT).setType("text/plain").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true))
        waitFor("Choose text files")
        waitFor("Downloads")
        compose.onNodeWithText("Downloads").performClick()
        waitFor("a.txt")
        compose.onNodeWithText("a.txt").performClick()
        compose.onNodeWithText("c.txt").performClick()
        waitFor("2 chosen")
        compose.onNodeWithText("Choose").performClick()

        val result = s.result
        assertEquals(Activity.RESULT_OK, result.resultCode)
        val clip = result.resultData.clipData!!
        val texts = (0 until clip.itemCount).map { i -> app.contentResolver.openInputStream(clip.getItemAt(i).uri)!!.use { it.readBytes().decodeToString() } }
        assertEquals(listOf("a", "c"), texts.sorted())
    }

    @Test
    fun backingOutCancels() {
        val s = launch(Intent(Intent.ACTION_GET_CONTENT).setType("*/*"))
        waitFor("Choose a file")
        compose.onNodeWithContentDescription("Cancel").performClick()
        assertEquals(Activity.RESULT_CANCELED, s.result.resultCode)
    }

    @Test
    fun requestsReadTheirTypes() {
        val one = PickRequest.from(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"))
        assertTrue(one.accepts("photo.JPG"))
        assertFalse(one.accepts("notes.txt"))
        assertFalse(one.multiple)

        val several = PickRequest.from(
            Intent(Intent.ACTION_GET_CONTENT).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "text/*"))
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true),
        )
        assertTrue(several.accepts("cv.pdf"))
        assertTrue(several.accepts("notes.md"))
        assertFalse(several.accepts("song.mp3"))
        assertEquals("Choose files", several.title)

        assertTrue(PickRequest.from(null).accepts("anything.bin"))
        assertEquals("Choose a video", PickRequest.from(Intent().setType("video/*")).title)
        assertEquals("Choose a photo", PickRequest.from(Intent().setType("image/png")).title)
        assertEquals("Choose a file", PickRequest.from(Intent().setType("application/pdf")).title)
    }
}
