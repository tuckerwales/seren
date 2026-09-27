package wales.tucker.seren.files.ui

import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Environment
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import wales.tucker.seren.core.suite.SuiteApp
import wales.tucker.seren.core.suite.Suite
import wales.tucker.seren.files.MainActivity
import wales.tucker.seren.files.TestApp
import wales.tucker.seren.files.ui.common.Opener
import java.io.File

/** What Seren Files offers the other Seren apps, and how it shows files they ask about. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SuiteActionsTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val root get() = app.root

    private fun file(path: String, text: String = path) = File(root, path).apply {
        parentFile!!.mkdirs()
        writeText(text)
    }

    private fun exists(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = compose.waitUntil(5_000) { exists(text) }

    private fun install(vararg apps: SuiteApp) = apps.forEach {
        shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = it.packageName })
    }

    private fun openDownloads() {
        waitFor("Downloads")
        compose.onNodeWithText("Downloads").performClick()
    }

    private fun openMenu(name: String) {
        compose.onNode(hasContentDescription("More") and hasAnyAncestor(hasText(name))).performClick()
        waitFor("Open with")
    }

    /** Back goes to the menu's own window, which closes it and leaves the folder open. */
    private fun closeMenu() {
        Espresso.pressBack()
        compose.waitUntil(5_000) { !exists("Open with") }
    }

    @Test
    fun menusOnlyOfferSerenAppsThatAreInstalled() {
        file("Download/notes.txt")
        file("Download/id_ed25519", "-----BEGIN OPENSSH PRIVATE KEY-----")
        file("Download/photo.jpg")
        openDownloads()
        waitFor("notes.txt")

        openMenu("notes.txt")
        assertFalse(exists("Seren Edit"))
        assertFalse(exists("Seren SSH"))
        closeMenu()

        install(SuiteApp.EDIT, SuiteApp.SSH)
        openMenu("notes.txt")
        assertTrue(exists("Open in Seren Edit"))
        assertTrue(exists("Upload with Seren SSH"))
        assertFalse(exists("Import into Seren SSH"))
        closeMenu()

        openMenu("id_ed25519")
        assertTrue(exists("Import into Seren SSH"))
        closeMenu()

        // A photo is no job for a text editor, but can still be uploaded.
        openMenu("photo.jpg")
        assertFalse(exists("Open in Seren Edit"))
        assertTrue(exists("Upload with Seren SSH"))
    }

    @Test
    fun revealOpensTheFolderWithTheFileInView() {
        (0 until 60).forEach { file("Download/file-%02d.txt".format(it)) }
        waitFor("Downloads")
        val target = File(root, "Download/file-59.txt")
        compose.runOnUiThread { compose.activity.handleIntent(Suite.revealIntent(Uri.fromFile(target), target.name)) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("file-59.txt").fetchSemanticsNodes().isNotEmpty() }
        // Scrolled to, well down a long folder.
        compose.onNodeWithText("file-59.txt").assertIsDisplayed()
        assertTrue(exists("Download"))
    }

    @Test
    fun revealSaysSoWhenAFileIsGone() {
        File(root, "Download").mkdirs()
        waitFor("Downloads")
        val gone = File(root, "Download/deleted.txt")
        compose.runOnUiThread { compose.activity.handleIntent(Suite.revealIntent(Uri.fromFile(gone), gone.name)) }
        waitFor("deleted.txt isn't in Download any more")
    }

    @Test
    fun revealNeverShowsFilesOutsideStorage() {
        File(root, "Download").mkdirs()
        waitFor("Downloads")
        val private = Uri.fromFile(File(app.filesDir, "secret.txt"))
        compose.runOnUiThread { compose.activity.handleIntent(Suite.revealIntent(private, "secret.txt")) }
        waitFor("Seren Files couldn't find where secret.txt is")
        assertTrue(exists("Downloads"))
    }

    @Test
    fun otherIntentsAreIgnored() {
        File(root, "Download").mkdirs()
        waitFor("Downloads")
        compose.runOnUiThread { compose.activity.handleIntent(Intent(Intent.ACTION_VIEW, Uri.fromFile(File(root, "x")))) }
        compose.waitForIdle()
        assertTrue(exists("Downloads"))
    }

    @Test
    fun handoffsCarryAContentLinkAndOnlyTheAccessNeeded() {
        // Opener hands out FileProvider links, which only cover shared storage.
        val shared = Environment.getExternalStorageDirectory()
        val notes = File(shared, "Documents/notes.md").apply { parentFile!!.mkdirs(); writeText("# hi") }
        val key = File(shared, "Download/id_ed25519").apply { parentFile!!.mkdirs(); writeText("key") }
        val started = shadowOf(app)

        assertTrue(Opener.openInEdit(app, notes))
        val edit = started.nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, edit.action)
        assertEquals(SuiteApp.EDIT.packageName, edit.`package`)
        assertEquals("text/markdown", edit.type)
        assertEquals("content", edit.data!!.scheme)
        assertTrue(edit.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)

        assertTrue(Opener.uploadWithSsh(app, listOf(notes, key)))
        val upload = started.nextStartedActivity
        assertEquals(Intent.ACTION_SEND_MULTIPLE, upload.action)
        assertEquals(SuiteApp.SSH.packageName, upload.`package`)
        assertEquals(2, upload.clipData!!.itemCount)
        assertEquals(0, upload.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        assertTrue(Opener.importKeyToSsh(app, key))
        val import = started.nextStartedActivity
        assertEquals(Suite.ACTION_IMPORT_KEY, import.action)
        assertEquals("id_ed25519", import.getStringExtra(Suite.EXTRA_DISPLAY_NAME))
        assertEquals("content", import.data!!.scheme)
        assertEquals(0, import.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        // Files outside shared storage have no link, so nothing is started.
        assertFalse(Opener.openInEdit(app, File(app.filesDir, "private.txt").apply { writeText("x") }))
        assertNull(started.nextStartedActivity)
    }
}
