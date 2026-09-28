package wales.tucker.seren.files.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import wales.tucker.seren.files.TestApp
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class BookmarksBackupTest {
    @Test
    fun roundTripsBookmarks() {
        val original = listOf(
            Bookmark("/storage/emulated/0/Projects", "Projects", 100),
            Bookmark("/storage/emulated/0/Documents/Notes", "Notes", 200),
        )
        val bytes = ByteArrayOutputStream().also { BookmarksBackup.export(original, it) }.toByteArray()
        val imported = BookmarksBackup.import(ByteArrayInputStream(bytes))
        assertEquals(original, imported)
        assertTrue(bytes.toString(Charsets.UTF_8).contains("seren-files-bookmarks"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherFormats() {
        BookmarksBackup.import(ByteArrayInputStream("""{"format":"other","bookmarks":[]}""".toByteArray()))
    }
}
