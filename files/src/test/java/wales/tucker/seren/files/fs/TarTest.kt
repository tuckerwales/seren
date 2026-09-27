package wales.tucker.seren.files.fs

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPOutputStream

/** Extracting tar, tar.gz and gz files, from GNU tar's own output and from hand made archives. */
class TarTest {
    private val temp = TempDir()

    @After
    fun tearDown() = temp.delete()

    private fun fixture(name: String): File {
        val out = File(temp.root, name)
        javaClass.getResourceAsStream("/archives/$name")!!.use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    private val deep = "project/src/deeply/nested/folder/with/a/name/long/enough/to/need/more/than/one/hundred/characters/file.txt"

    @Test
    fun extractsGnuTarGz() = runBlocking {
        val archive = fixture("gnu.tar.gz")
        assertEquals(ArchiveFormat.TAR_GZ, Archives.format(archive.name))
        val out = File(temp.root, Archives.extractedName(archive))
        assertEquals("gnu", out.name)

        var progress = 0L
        Archives.extract(archive, out) { progress = it }
        assertEquals(archive.length(), progress)
        assertEquals(Archives.progressTotal(archive), progress)

        assertEquals("hello from gnu tar\n", File(out, "project/readme.txt").readText())
        assertEquals("ünïcödé\n", File(out, "project/café.txt").readText())
        // A name over 100 characters comes from GNU's long name entry.
        assertEquals("deep\n", File(out, deep).readText())
        assertTrue(File(out, "project/empty").isDirectory)
        // Links are left out, so nothing can point outside the folder.
        assertFalse(File(out, "project/link").exists())
        assertFalse(FileOps.isLink(File(out, "project/link")))
        // 2024-01-02 03:04:05 UTC
        assertEquals(1_704_164_645_000, File(out, "project/readme.txt").lastModified())
    }

    @Test
    fun extractsPaxTar() = runBlocking {
        val archive = fixture("pax.tar")
        val out = File(temp.root, "pax")
        Archives.extract(archive, out)
        assertEquals("deep\n", File(out, deep).readText())
        assertEquals("ünïcödé\n", File(out, "project/café.txt").readText())
        assertFalse(File(out, "project/link").exists())
    }

    @Test
    fun gunzipsASingleFile() = runBlocking {
        val archive = fixture("readme.txt.gz")
        assertTrue(Archives.extractsToFile(archive))
        assertEquals("readme.txt", Archives.extractedName(archive))
        val out = File(temp.root, "readme.txt")
        Archives.extract(archive, out)
        assertEquals("hello from gnu tar\n", out.readText())
    }

    @Test
    fun tarSlipIsRefusedAndNothingIsLeft() = runBlocking {
        val archive = File(temp.root, "evil.tar").apply {
            writeBytes(tar(entry("fine.txt", "ok"), entry("../escaped.txt", "bad")))
        }
        val out = File(temp.root, "evil")
        try {
            Archives.extract(archive, out)
            fail()
        } catch (e: IOException) {
            assertEquals("evil.tar tries to put files outside the folder, so it wasn't extracted", e.message)
        }
        assertFalse(File(temp.root, "escaped.txt").exists())
        assertFalse(out.exists())
    }

    @Test
    fun absolutePathsStayInsideTheFolder() = runBlocking {
        val archive = File(temp.root, "abs.tar").apply { writeBytes(tar(entry("/etc/hosts", "local"))) }
        val out = File(temp.root, "abs")
        Archives.extract(archive, out)
        assertEquals("local", File(out, "etc/hosts").readText())
    }

    @Test
    fun damagedArchivesAreExplained() = runBlocking {
        val notTar = temp.file("notes.tar", "just some text, not a tar file at all")
        try {
            Archives.extract(notTar, File(temp.root, "notes"))
            fail()
        } catch (e: IOException) {
            assertEquals("notes.tar is damaged or isn't a tar file", e.message)
        }
        assertFalse(File(temp.root, "notes").exists())

        // Cut short in the middle of a file's data.
        val whole = tar(entry("big.bin", "x".repeat(5000)))
        val cut = File(temp.root, "cut.tgz").apply { writeBytes(gzip(whole.copyOf(1500))) }
        try {
            Archives.extract(cut, File(temp.root, "cut"))
            fail()
        } catch (e: IOException) {
            assertEquals("cut.tgz is damaged or isn't a tar.gz file", e.message)
        }
        assertFalse(File(temp.root, "cut").exists())

        val notGz = temp.file("x.gz", "plain")
        try {
            Archives.extract(notGz, File(temp.root, "x"))
            fail()
        } catch (e: IOException) {
            assertEquals("x.gz is damaged or isn't a gz file", e.message)
        }
        assertFalse(File(temp.root, "x").exists())
    }

    @Test
    fun formatsAndNames() {
        assertEquals(ArchiveFormat.ZIP, Archives.format("photos.zip"))
        assertEquals(ArchiveFormat.ZIP, Archives.format("app.apks"))
        assertEquals(ArchiveFormat.TAR, Archives.format("backup.TAR"))
        assertEquals(ArchiveFormat.TAR_GZ, Archives.format("backup.tar.gz"))
        assertEquals(ArchiveFormat.TAR_GZ, Archives.format("backup.tgz"))
        assertEquals(ArchiveFormat.GZIP, Archives.format("access.log.gz"))
        assertEquals(null, Archives.format("photos.rar"))
        assertEquals(null, Archives.format(".gz"))
        assertEquals("backup", Archives.extractedName(File("/x/backup.tar.gz")))
        assertEquals("backup", Archives.extractedName(File("/x/backup.tgz")))
        assertEquals("access.log", Archives.extractedName(File("/x/access.log.gz")))
        assertEquals(mapOf("path" to "a/b c.txt", "mtime" to "1"), TarReader.paxHeaders("18 path=a/b c.txt\n11 mtime=1\n"))
    }

    private fun entry(name: String, text: String) = name to text.toByteArray()

    /** A minimal ustar archive of plain files, the way tar writes them. */
    private fun tar(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((name, data) in entries) {
            val header = ByteArray(512)
            fun put(offset: Int, text: String) = text.toByteArray().copyInto(header, offset)
            put(0, name)
            put(100, "0000644\u0000")
            put(108, "0000000\u0000")
            put(116, "0000000\u0000")
            put(124, "%011o\u0000".format(data.size))
            put(136, "%011o\u0000".format(1_700_000_000))
            put(148, "        ")
            header[156] = '0'.code.toByte()
            put(257, "ustar\u000000")
            val sum = header.sumOf { it.toInt() and 0xFF }
            put(148, "%06o\u0000 ".format(sum))
            out.write(header)
            out.write(data)
            out.write(ByteArray((512 - data.size % 512) % 512))
        }
        out.write(ByteArray(1024))
        return out.toByteArray()
    }

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { GZIPOutputStream(it).use { z -> z.write(bytes) } }.toByteArray()
}
