package wales.tucker.seren.files.ui

import android.content.pm.ShortcutManager
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import wales.tucker.seren.files.MainActivity
import wales.tucker.seren.files.TestApp
import wales.tucker.seren.files.ui.common.Shortcuts
import java.io.File

/** Folders pinned to the home screen, and opening them from there. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeShortcutTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val root get() = app.root

    private fun exists(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = compose.waitUntil(5_000) { exists(text) }

    private val shortcuts get() = shadowOf(app.getSystemService(ShortcutManager::class.java))

    @Test
    fun aFolderCanBePinnedToTheHomeScreen() {
        shortcuts.setIsRequestPinShortcutSupported(true)
        File(root, "Projects/seren").mkdirs()
        waitFor("Internal storage")
        compose.onNodeWithText("Internal storage").performClick()
        waitFor("Projects")
        compose.onNode(hasContentDescription("More") and hasAnyAncestor(hasText("Projects"))).performClick()
        compose.onNodeWithText("Add to home screen").performClick()
        compose.waitUntil(5_000) { app.getSystemService(ShortcutManager::class.java).pinnedShortcuts.isNotEmpty() }

        val pinned = app.getSystemService(ShortcutManager::class.java).pinnedShortcuts.single()
        assertEquals("Projects", pinned.shortLabel)
        assertEquals(Shortcuts.ACTION_OPEN_FOLDER, pinned.intent!!.action)
        assertEquals(File(root, "Projects").path, pinned.intent!!.getStringExtra(Shortcuts.EXTRA_PATH))
    }

    @Test
    fun launchersThatCantPinSaySo() {
        shortcuts.setIsRequestPinShortcutSupported(false)
        File(root, "Projects").mkdirs()
        waitFor("Internal storage")
        compose.onNodeWithText("Internal storage").performClick()
        waitFor("Projects")
        compose.onNode(hasContentDescription("More") and hasAnyAncestor(hasText("Projects"))).performClick()
        compose.onNodeWithText("Add to home screen").performClick()
        waitFor("Your home screen app can't add shortcuts")
    }

    @Test
    fun theShortcutOpensItsFolder() {
        val folder = File(root, "Projects/seren").apply { mkdirs() }
        File(folder, "README.md").writeText("hi")
        waitFor("Internal storage")
        compose.runOnUiThread { compose.activity.handleIntent(Shortcuts.openFolderIntent(app, folder)) }
        waitFor("README.md")
        assertTrue(exists("seren"))
    }

    @Test
    fun aShortcutToAFolderThatsGoneOrPrivateOpensNothing() {
        waitFor("Internal storage")
        compose.runOnUiThread { compose.activity.handleIntent(Shortcuts.openFolderIntent(app, File(root, "Deleted"))) }
        waitFor("Deleted was moved or deleted")
        compose.runOnUiThread { compose.activity.handleIntent(Shortcuts.openFolderIntent(app, app.filesDir)) }
        waitFor("files was moved or deleted")
        assertTrue(exists("Internal storage"))
    }
}
