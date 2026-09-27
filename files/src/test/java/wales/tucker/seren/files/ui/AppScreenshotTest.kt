package wales.tucker.seren.files.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
import wales.tucker.seren.files.MainActivity
import wales.tucker.seren.files.TestApp
import wales.tucker.seren.files.data.Bookmark
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Renders the main screens with Robolectric's native graphics and saves screenshots to
 * files/build/screenshots, for the README.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val root get() = app.root
    private val now = System.currentTimeMillis()

    private fun shot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/screenshots/$name.png")
    }

    private fun exists(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = compose.waitUntil(5_000) { exists(text) }

    private fun back() {
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
    }

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    /** A file of [size] bytes that takes no space (the listing only reads its length). */
    private fun sized(path: String, size: Long, age: Long): File = File(root, path).apply {
        parentFile!!.mkdirs()
        RandomAccessFile(this, "rw").use { it.setLength(size) }
        setLastModified(now - age)
    }

    private fun folder(path: String, age: Long): File = File(root, path).apply {
        mkdirs()
        setLastModified(now - age)
    }

    /** A plausible photo: a sky gradient with a sun or moon, padded out to a camera-sized file. */
    private fun photo(path: String, top: Int, bottom: Int, sun: Int, age: Long, size: Long = 3_400_000) {
        val bitmap = Bitmap.createBitmap(480, 360, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, 360f, top, bottom, Shader.TileMode.CLAMP) })
        canvas.drawCircle(330f, 150f, 60f, Paint().apply { shader = RadialGradient(330f, 150f, 60f, sun, sun and 0x00FFFFFF, Shader.TileMode.CLAMP) })
        canvas.drawRect(0f, 270f, 480f, 360f, Paint().apply { color = 0x66000000 })
        val file = File(root, path).apply { parentFile!!.mkdirs() }
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        RandomAccessFile(file, "rw").use { if (it.length() < size) it.setLength(size) }
        file.setLastModified(now - age)
    }

    private fun seed() {
        // Downloads
        sized("Download/boarding-pass-LHR.pdf", 188_416, 2 * hour)
        photo("Download/IMG_20260926_183012.jpg", 0xFF1D2B64.toInt(), 0xFFF8CDDA.toInt(), 0xFFFFE29F.toInt(), 5 * hour)
        sized("Download/invoice-2026-09.pdf", 94_208, 26 * hour)
        sized("Download/holiday-photos.zip", 48_523_264, 2 * day)
        sized("Download/podcast-episode-42.mp3", 39_845_888, 3 * day)
        sized("Download/recipes.md", 4_310, 5 * day)
        sized("Download/seren-files-0.1.0.apk", 9_647_104, 12 * day)
        sized("Download/Tickets/concert.pdf", 120_000, 9 * day)
        sized("Download/Tickets/train.pdf", 88_000, 9 * day)
        folder("Download/Tickets", 9 * day)
        File(root, "Download").setLastModified(now - 2 * hour)

        // Documents
        sized("Documents/Budget 2026.xlsx", 58_880, 4 * day)
        sized("Documents/CV.pdf", 212_992, 40 * day)
        sized("Documents/letter to the council.docx", 24_576, 21 * day)
        sized("Documents/Letters/landlord.docx", 20_480, 60 * day)
        folder("Documents/Letters", 60 * day)
        sized("Documents/Notes/ideas.md", 2_048, 1 * day)
        sized("Documents/Notes/garden.md", 1_024, 8 * day)
        sized("Documents/Notes/books.txt", 812, 30 * day)
        folder("Documents/Notes", 1 * day)
        sized("Documents/passport-scan.jpg", 1_250_000, 90 * day)

        // Camera
        val skies = listOf(
            Triple(0xFF0F2027, 0xFF2C5364, 0xFFE0EAFC),
            Triple(0xFFFF7E5F, 0xFFFEB47B, 0xFFFFF6B7),
            Triple(0xFF134E5E, 0xFF71B280, 0xFFFFFFFF),
            Triple(0xFF41295A, 0xFF2F0743, 0xFFFFD1FF),
            Triple(0xFF2193B0, 0xFF6DD5ED, 0xFFFFFFFF),
            Triple(0xFFEE0979, 0xFFFF6A00, 0xFFFFE259),
            Triple(0xFF00416A, 0xFFE4E5E6, 0xFFFFFFFF),
            Triple(0xFF3A1C71, 0xFFFFAF7B, 0xFFFFEFBA),
        )
        skies.forEachIndexed { i, (top, bottom, sun) ->
            // Newest first, each a few hours before the last, named the way cameras name them.
            val age = 3 * hour + i * 11 * hour
            val taken = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.ROOT).format(java.util.Date(now - age))
            photo("DCIM/Camera/IMG_$taken.jpg", top.toInt(), bottom.toInt(), sun.toInt(), age, 2_800_000L + i * 310_000L)
        }
        folder("DCIM/Camera", 3 * hour)

        sized("Pictures/Screenshots/Screenshot_20260925.png", 412_000, 2 * day)
        folder("Pictures/Screenshots", 2 * day)
        sized("Music/Field Recordings/rain on the roof.flac", 31_457_280, 50 * day)
        folder("Music/Field Recordings", 50 * day)
        sized("Movies/first-steps.mp4", 157_286_400, 70 * day)
        sized("Projects/seren/README.md", 6_000, 6 * hour)
        sized("Projects/garden-plan/beds.svg", 18_000, 10 * day)
        folder("Projects", 6 * hour)

        runBlocking {
            app.container.bookmarks.insert(Bookmark(File(root, "Projects").path, "Projects", now))
            app.container.bookmarks.insert(Bookmark(File(root, "Documents/Notes").path, "Notes", now))
            app.container.trash.moveToTrash(
                listOf(
                    sized("Documents/old-draft.docx", 31_744, 20 * day),
                    folder("Pictures/Screenshots 2025", 200 * day).also { sized("Pictures/Screenshots 2025/a.png", 300_000, 200 * day) },
                    sized("Download/IMG_0042.jpg", 2_100_000, 30 * day),
                ),
            )
        }
    }

    @Test
    fun mainScreens() {
        seed()
        waitFor("Bookmarks")
        shot("01_browse")

        compose.onNodeWithText("Downloads").performClick()
        waitFor("boarding-pass-LHR.pdf")
        Thread.sleep(1_000)
        shot("02_folder")

        compose.onNodeWithText("invoice-2026-09.pdf").performTouchInput { longClick() }
        compose.onNodeWithText("boarding-pass-LHR.pdf").performClick()
        waitFor("2 selected")
        shot("03_selection")
        back()
        back()

        compose.onNodeWithText("Camera").performClick()
        waitFor("DCIM")
        compose.onNodeWithText("Camera").performClick()
        waitFor("IMG_")
        // Thumbnails are made in the background.
        Thread.sleep(1_500)
        compose.waitForIdle()
        shot("04_photos")
        back()
        back()
        waitFor("Bookmarks")

        compose.onNodeWithText("Recent").performClick()
        waitFor("Yesterday")
        shot("05_recent")

        compose.onNodeWithText("Trash").performClick()
        waitFor("old-draft.docx")
        shot("06_trash")

        compose.onNodeWithText("Settings").performClick()
        waitFor("Use the trash")
        shot("07_settings")
    }

    @Test
    fun darkFolder() {
        runBlocking { app.container.settings.setThemeMode(ThemeMode.DARK) }
        try {
            seed()
            waitFor("Bookmarks")
            compose.onNodeWithText("Documents").performClick()
            waitFor("Budget 2026.xlsx")
            shot("08_folder_dark")
        } finally {
            // Settings outlive the test, so put them back for the others.
            runBlocking { app.container.settings.setThemeMode(ThemeMode.SYSTEM) }
        }
    }
}
