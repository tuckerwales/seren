package wales.tucker.seren.files.fs

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchivesTest {
    private val temp = TempDir()

    @After
    fun tearDown() = temp.delete()

    @Test
    fun compressThenExtractGivesTheSameFiles() = runBlocking {
        val notes = temp.file("notes.txt", "remember the milk", modified = 1_600_000_000_000)
        val photos = temp.dir("photos")
        temp.file("photos/one.jpg", "1".repeat(1000))
        temp.file("photos/trip/two.jpg", "2")
        temp.dir("photos/empty")
        val zip = File(temp.root, "Archive.zip")

        var progress = 0L
        Archives.compress(listOf(notes, photos), zip) { progress = it }
        assertEquals(1018L, progress)
        assertEquals(1018L, Archives.progressTotal(zip))

        val out = File(temp.root, Archives.extractedName(zip))
        Archives.extract(zip, out)
        assertEquals(
            listOf("notes.txt", "photos/", "photos/empty/", "photos/one.jpg", "photos/trip/", "photos/trip/two.jpg"),
            temp.tree(out),
        )
        assertEquals("remember the milk", File(out, "notes.txt").readText())
        // Zip keeps times to two seconds.
        assertTrue(kotlin.math.abs(File(out, "notes.txt").lastModified() - 1_600_000_000_000) <= 2000)
    }

    @Test
    fun zipSlipIsRefused() = runBlocking {
        val zip = File(temp.root, "evil.zip")
        ZipOutputStream(FileOutputStream(zip)).use { z ->
            z.putNextEntry(ZipEntry("fine.txt"))
            z.write("ok".toByteArray())
            z.closeEntry()
            z.putNextEntry(ZipEntry("../escaped.txt"))
            z.write("bad".toByteArray())
            z.closeEntry()
        }
        val out = File(temp.root, "evil")
        try {
            Archives.extract(zip, out)
            fail()
        } catch (e: IOException) {
            assertEquals("evil.zip tries to put files outside the folder, so it wasn't extracted", e.message)
        }
        assertFalse(File(temp.root, "escaped.txt").exists())
        assertFalse(out.exists())
    }

    @Test
    fun aFileThatIsntAZipIsExplained() = runBlocking {
        val bogus = temp.file("bogus.zip", "not a zip at all")
        try {
            Archives.extract(bogus, File(temp.root, "bogus"))
            fail()
        } catch (e: IOException) {
            assertEquals("bogus.zip is empty or isn't a zip file", e.message)
        }
        assertFalse(File(temp.root, "bogus").exists())
    }

    @Test
    fun namesForTheExtractedFolder() {
        assertEquals("photos", Archives.extractedName(File("/x/photos.zip")))
        assertEquals("archive.tar", Archives.extractedName(File("/x/archive.tar.zip")))
        assertTrue(Archives.canExtract("Photos.ZIP"))
        assertFalse(Archives.canExtract("photos.rar"))
    }
}
