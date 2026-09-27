package wales.tucker.seren.files.fs

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * Extracting 7z files made by py7zr, and tar.xz, tar.bz2, xz and bz2 files made by GNU tar, xz and
 * bzip2: other programs' output, so the reading is checked against more than itself.
 */
class SevenZTest {
    private val temp = TempDir()

    @After
    fun tearDown() = temp.delete()

    private fun fixture(name: String): File {
        val out = File(temp.root, name)
        javaClass.getResourceAsStream("/archives/$name")!!.use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    private val deep = "project/src/deeply/nested/folder/with/a/name/long/enough/to/need/more/than/one/hundred/characters/file.txt"

    private fun extract(name: String, password: String? = null): File {
        val archive = fixture(name)
        val out = File(temp.root, Archives.extractedName(archive))
        runBlocking { Archives.extract(archive, out, password) }
        return out
    }

    private fun failure(name: String, password: String? = null): IOException {
        val archive = fixture(name)
        val out = File(temp.root, "out-$name")
        try {
            runBlocking { Archives.extract(archive, out, password) }
            fail("$name extracted")
        } catch (e: IOException) {
            // Nothing half extracted is left behind.
            assertFalse(out.exists())
            return e
        }
        throw AssertionError()
    }

    @Test
    fun extracts7z() {
        val archive = fixture("project.7z")
        assertEquals(ArchiveFormat.SEVEN_Z, Archives.format(archive.name))
        assertEquals(19L + 12 + 5, Archives.progressTotal(archive))
        val out = File(temp.root, "project")
        var progress = 0L
        runBlocking { Archives.extract(archive, out) { progress = it } }
        assertEquals(36L, progress)

        assertEquals("hello from gnu tar\n", File(out, "project/readme.txt").readText())
        assertEquals("ünïcödé\n", File(out, "project/café.txt").readText())
        assertEquals("deep\n", File(out, deep).readText())
        assertTrue(File(out, "project/empty").isDirectory)
        // Links are left out, so nothing can point outside the folder.
        assertFalse(File(out, "project/link").exists())
        assertFalse(FileOps.isLink(File(out, "project/link")))
        // 2024-01-02 03:04:05 UTC
        assertEquals(1_704_164_645_000, File(out, "project/readme.txt").lastModified())
    }

    @Test
    fun passwordProtected7zAsksForThePassword() {
        val missing = failure("locked.7z")
        assertTrue(missing is PasswordNeededException)
        assertFalse((missing as PasswordNeededException).wrong)
        assertEquals("locked.7z needs a password", missing.message)

        val wrong = failure("locked.7z", password = "letmein")
        assertTrue(wrong is PasswordNeededException && wrong.wrong)
        assertEquals("That password doesn't open locked.7z", wrong.message)

        val out = extract("locked.7z", password = "hunter2")
        assertEquals("hello from gnu tar\n", File(out, "readme.txt").readText())
    }

    @Test
    fun sevenZSlipIsRefused() {
        assertEquals("evil.7z tries to put files outside the folder, so it wasn't extracted", failure("evil.7z").message)
        assertFalse(File(temp.root, "escaped.txt").exists())
        // A password doesn't turn that into a wrong password.
        assertEquals("evil.7z tries to put files outside the folder, so it wasn't extracted", failure("evil.7z", "x").message)
    }

    @Test
    fun unsupportedAndDamaged7zAreExplained() {
        assertEquals("ppmd.7z uses a kind of compression Seren Files can't extract", failure("ppmd.7z").message)
        temp.file("fake.7z", "not a 7z file at all")
        val e = try {
            runBlocking { Archives.extract(File(temp.root, "fake.7z"), File(temp.root, "fake")) }
            null
        } catch (e: IOException) {
            e
        }
        assertEquals("fake.7z is damaged or isn't a 7z file", e?.message)
        assertFalse(File(temp.root, "fake").exists())
    }

    @Test
    fun extractsTarXzAndTarBz2() {
        for (name in listOf("project.tar.xz", "project.tar.bz2")) {
            val out = extract(name)
            assertEquals(name, "project", out.name)
            assertEquals(name, "deep\n", File(out, deep).readText())
            assertEquals(name, "ünïcödé\n", File(out, "project/café.txt").readText())
            assertFalse(name, File(out, "project/link").exists())
            out.deleteRecursively()
        }
        assertEquals(ArchiveFormat.TAR_XZ, Archives.format("a.txz"))
        assertEquals(ArchiveFormat.TAR_BZ2, Archives.format("a.tbz2"))
    }

    @Test
    fun decompressesSingleXzAndBz2Files() {
        for (name in listOf("readme.txt.xz", "readme.txt.bz2")) {
            val archive = fixture(name)
            assertTrue(name, Archives.extractsToFile(archive))
            val out = File(temp.root, Archives.extractedName(archive))
            assertEquals("readme.txt", out.name)
            runBlocking { Archives.extract(archive, out) }
            assertEquals(name, "hello from gnu tar\n", out.readText())
            out.delete()
        }
    }

    @Test
    fun damagedXzAndBz2AreExplained() {
        temp.file("notes.xz", "not xz")
        temp.file("notes.tar.bz2", "not bzip2 either")
        for ((name, label) in listOf("notes.xz" to "xz", "notes.tar.bz2" to "tar.bz2")) {
            val out = File(temp.root, "out")
            try {
                runBlocking { Archives.extract(File(temp.root, name), out) }
                fail(name)
            } catch (e: IOException) {
                assertEquals("$name is damaged or isn't a $label file", e.message)
            }
            assertFalse(out.exists())
        }
    }
}
