package wales.tucker.seren.files.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

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
