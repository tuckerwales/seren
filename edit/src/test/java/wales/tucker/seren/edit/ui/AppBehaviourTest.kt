package wales.tucker.seren.edit.ui

import android.net.Uri
import android.os.Environment
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import wales.tucker.seren.edit.MainActivity
import wales.tucker.seren.edit.SerenApp
import java.io.File
import wales.tucker.seren.core.suite.Suite
import wales.tucker.seren.core.suite.SuiteApp

/** Drives the real UI: opening, editing and saving files, and the guards around them. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppBehaviourTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<SerenApp>()

    private fun exists(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun file(name: String, bytes: ByteArray): File {
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStorageDirectory(), "Documents").apply { mkdirs() }
        return File(dir, name).apply { writeBytes(bytes) }
    }

    private fun open(file: File) {
        compose.activity.openLinks.trySend(Uri.fromFile(file))
        compose.waitUntil(5_000) { exists(file.name) }
    }

    @Test
    fun editsAndSavesAFileKeepingItsLineEndings() {
        val f = file("todo.txt", "milk\r\neggs\r\n".toByteArray())
        open(f)
        compose.waitUntil(5_000) { exists("milk") }
        compose.onNodeWithText("Internal storage/Documents").assertExists()

        compose.onNode(hasSetTextAction()).performTextInput("bread\n")
        compose.waitUntil(5_000) { exists("Unsaved changes") }
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { exists("Saved todo.txt") }

        assertEquals("bread\r\nmilk\r\neggs\r\n", f.readText())
        compose.onNodeWithText("Internal storage/Documents").assertExists()
    }

    @Test
    fun opensAndSavesFilesHandedOverBySerenFiles() {
        val f = file("deploy.sh", "echo hi\n".toByteArray())
        // Exactly what "Open in Seren Edit" in Seren Files sends (with a file link in place of its
        // FileProvider link, which Robolectric can't serve).
        val intent = Suite.viewIntent(SuiteApp.EDIT, Uri.fromFile(f), "text/plain", writable = true)
        compose.runOnUiThread { compose.activity.handleIntent(intent) }
        compose.waitUntil(5_000) { exists("echo hi") }

        compose.onNode(hasSetTextAction()).performTextInput("set -e\n")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { exists("Saved deploy.sh") }
        assertEquals("set -e\necho hi\n", f.readText())
    }

    @Test
    fun leavingWithUnsavedChangesAsksFirst() {
        val f = file("notes.md", "# Notes\n".toByteArray())
        open(f)
        compose.waitUntil(5_000) { exists("# Notes") }
        compose.onNode(hasSetTextAction()).performTextInput("Draft ")

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(5_000) { exists("Discard changes to notes.md?") }
        compose.onNodeWithText("Keep editing").performClick()
        compose.waitUntil(5_000) { !exists("Discard changes") }
        compose.onNodeWithText("Draft # Notes", substring = true).assertExists()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard").performClick()
        compose.waitUntil(5_000) { exists("New file") }
        assertEquals("# Notes\n", f.readText())
    }

    @Test
    fun openedFilesShowUpInRecent() {
        open(file("nginx.conf", "server {}\n".toByteArray()))
        compose.waitUntil(5_000) { exists("server {}") }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Recent").performClick()
        compose.waitUntil(5_000) { exists("Opened just now") }
        compose.onNodeWithText("nginx.conf").assertExists()
    }

    @Test
    fun binaryFilesAreRefusedWithAReason() {
        open(file("photo.jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0, 0, 0x10)))
        compose.waitUntil(5_000) { exists("Couldn't open photo.jpg") }
        compose.onNodeWithText("It doesn't look like a text file.").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.waitUntil(5_000) { exists("New file") }
    }

    @Test
    fun lockScreenNamesTheApp() {
        compose.activity.locked = true
        compose.waitUntil(5_000) { exists("Seren Edit is locked") }
        compose.activity.locked = false
        compose.waitUntil(5_000) { exists("New file") }
    }
}
