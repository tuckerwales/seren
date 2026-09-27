package wales.tucker.seren.edit.ui

import android.net.Uri
import android.os.Environment
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import wales.tucker.seren.core.ui.theme.ThemeMode
import wales.tucker.seren.edit.MainActivity
import wales.tucker.seren.edit.SerenApp
import wales.tucker.seren.edit.data.Folder
import wales.tucker.seren.edit.data.RecentFile
import java.io.File

/**
 * Renders the main screens with Robolectric's native graphics and saves screenshots to
 * edit/build/screenshots, for the README.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val container get() = ApplicationProvider.getApplicationContext<SerenApp>().container

    private fun shot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/screenshots/$name.png")
    }

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun seed() = runBlocking {
        val now = System.currentTimeMillis()
        val tree = "content://com.android.externalstorage.documents/tree/primary%3A"
        container.database.folders().upsert(Folder("${tree}Documents", "Documents", "Internal storage", 0, now))
        container.database.folders().upsert(Folder("${tree}Projects%2Fsite", "site", "Internal storage/Projects", 1, now))
        val doc = "content://com.android.externalstorage.documents/document/primary%3A"
        val recent = container.database.recentFiles()
        recent.upsert(RecentFile("${doc}Documents%2Fnotes.md", "notes.md", "Internal storage/Documents", 0, now - 5 * 60_000))
        recent.upsert(RecentFile("${doc}Projects%2Fsite%2Findex.html", "index.html", "Internal storage/Projects/site", 5, now - 3 * 3_600_000))
        recent.upsert(RecentFile("${doc}Download%2Fnginx.conf", "nginx.conf", "Internal storage/Download", 3, now - 2 * 86_400_000))
        recent.upsert(RecentFile("${doc}Documents%2Ftodo.txt", "todo.txt", "Internal storage/Documents", 1, now - 4 * 86_400_000))
    }

    private fun sample(): File {
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStorageDirectory(), "Documents").apply { mkdirs() }
        return File(dir, "notes.md").apply {
            writeText(
                """
                # Garden plan

                Seren Edit is a quiet place to write notes, config files and code. Long lines like this one wrap to fit the screen.

                ## This weekend
                - Sow the beans along the south fence
                - Fix the gate latch
                - Order compost (two bags)

                ## Watering
                ```sh
                for bed in north south herbs; do
                    water --bed "${'$'}bed" --minutes 10
                done
                ```
                """.trimIndent() + "\n",
            )
        }
    }

    @Test
    fun mainScreens() {
        seed()
        waitFor("Documents")
        shot("01_files")

        compose.onNodeWithText("Recent").performClick()
        waitFor("nginx.conf")
        shot("02_recent")

        compose.onNodeWithText("Settings").performClick()
        waitFor("Color scheme")
        shot("04_settings")
    }

    @Test
    fun editor() {
        compose.activity.openLinks.trySend(Uri.fromFile(sample()))
        waitFor("Garden plan")
        shot("03_editor")
    }

    @Test
    fun editorInALightScheme() {
        runBlocking {
            container.settings.setThemeMode(ThemeMode.LIGHT)
            container.settings.setColorScheme("paper")
        }
        try {
            compose.activity.openLinks.trySend(Uri.fromFile(sample()))
            waitFor("Garden plan")
            shot("05_editor_paper")
        } finally {
            // Settings outlive the test, so put them back for the others.
            runBlocking {
                container.settings.setThemeMode(ThemeMode.SYSTEM)
                container.settings.setColorScheme("midnight")
            }
        }
    }
}
