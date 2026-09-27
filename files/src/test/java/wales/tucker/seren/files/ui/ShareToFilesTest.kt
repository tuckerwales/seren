package wales.tucker.seren.files.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import wales.tucker.seren.files.MainActivity
import wales.tucker.seren.files.TestApp
import java.io.File

/** "Save to Seren Files" from another app's share sheet. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareToFilesTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val root get() = app.root

    private fun exists(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = compose.waitUntil(5_000) { exists(text) }

    private fun share(intent: Intent) = compose.runOnUiThread { compose.activity.handleIntent(intent) }

    /** A file another app shares; here, somewhere on shared storage it can read. */
    private fun shared(name: String, text: String): Uri = Uri.fromFile(File(root, "Other app/$name").apply { parentFile!!.mkdirs(); writeText(text) })

    @Test
    fun savesSharedFilesIntoTheFolderPeopleOpen() {
        File(root, "Download/receipt.pdf").apply { parentFile!!.mkdirs(); writeText("already here") }
        waitFor("Downloads")
        share(
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                arrayListOf(shared("receipt.pdf", "new receipt"), shared("photo.jpg", "jpeg")),
            ),
        )
        waitFor("Saving 2 items")
        waitFor("Open a folder to save in")

        compose.onNodeWithText("Downloads").performClick()
        waitFor("Save here")
        compose.onNodeWithText("Save here").performClick()
        waitFor("Saved 2 items to Download")
        // Nothing already there is replaced.
        assertEquals("already here", File(root, "Download/receipt.pdf").readText())
        assertEquals("new receipt", File(root, "Download/receipt (1).pdf").readText())
        assertEquals("jpeg", File(root, "Download/photo.jpg").readText())
        compose.waitUntil(5_000) { !exists("Save here") }
        assertNull(app.container.operations.incoming.value)
    }

    @Test
    fun sharedTextBecomesATextFile() {
        File(root, "Documents").mkdirs()
        waitFor("Documents")
        share(
            Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "https://seren.example/recipe")
                .putExtra(Intent.EXTRA_SUBJECT, "Soup/recipe"),
        )
        waitFor("Saving Soup_recipe.txt")
        compose.onNodeWithText("Documents").performClick()
        waitFor("Save here")
        compose.onNodeWithText("Save here").performClick()
        waitFor("Saved Soup_recipe.txt to Documents")
        assertEquals("https://seren.example/recipe", File(root, "Documents/Soup_recipe.txt").readText())
    }

    @Test
    fun savingCanBeCancelled() {
        waitFor("Internal storage")
        share(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, shared("a.txt", "a")))
        waitFor("Saving a.txt")
        compose.onNodeWithContentDescription("Don't save").performClick()
        compose.waitUntil(5_000) { !exists("Saving a.txt") }
        assertNull(app.container.operations.incoming.value)
    }

    @Test
    fun privateFilesCantBeSharedIn() {
        waitFor("Internal storage")
        // Another app pointing at Seren Files' own private files must get nothing.
        val secret = File(app.filesDir, "secret.txt").apply { writeText("secret") }
        share(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, Uri.fromFile(secret)))
        waitFor("There's nothing Seren Files can save in what was shared")
        assertNull(app.container.operations.incoming.value)
        assertFalse(exists("Saving"))
        assertTrue(exists("Internal storage"))
    }
}
