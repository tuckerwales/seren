package wales.tucker.seren.files.fs

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class FileOpsTest {
    private val temp = TempDir()

    @After
    fun tearDown() = temp.delete()

    private fun transfer(sources: List<File>, dest: File, move: Boolean, policy: ConflictPolicy = ConflictPolicy.REPLACE) = runBlocking {
        FileOps.transfer(FileOps.plan(sources, dest, move), policy)
    }

    @Test
    fun namesAreChecked() {
        assertEquals("Enter a name", FileOps.validateName("  "))
        assertEquals("Names can't contain /", FileOps.validateName("a/b"))
        assertEquals("That name is reserved", FileOps.validateName(".."))
        assertEquals("That name is too long", FileOps.validateName("x".repeat(256)))
        assertNull(FileOps.validateName("Holiday photos 2026"))
        assertNull(FileOps.validateName(".config"))
    }

    @Test
    fun uniqueNamesCountUpAndKeepTheExtension() {
        temp.file("photo.jpg")
        assertEquals("photo (1).jpg", FileOps.uniqueName(temp.root, "photo.jpg"))
        temp.file("photo (1).jpg")
        assertEquals("photo (2).jpg", FileOps.uniqueName(temp.root, "photo.jpg"))
        assertEquals("photo (2).jpg", FileOps.uniqueName(temp.root, "photo (1).jpg"))
        temp.dir("v1.2")
        assertEquals("v1.2 (1)", FileOps.uniqueName(temp.root, "v1.2", isDirectory = true))
        temp.file(".env")
        assertEquals(".env (1)", FileOps.uniqueName(temp.root, ".env"))
        assertEquals("new.txt", FileOps.uniqueName(temp.root, "new.txt"))
    }

    @Test
    fun createAndRename() {
        val folder = FileOps.createFolder(temp.root, "Projects")
        assertTrue(folder.isDirectory)
        val file = FileOps.createFile(folder, "notes.txt")
        assertTrue(file.isFile)
        try {
            FileOps.createFile(folder, "notes.txt")
            fail()
        } catch (e: IOException) {
            assertEquals("notes.txt already exists", e.message)
        }
        val renamed = FileOps.rename(file, "Notes.txt")
        assertEquals(listOf("Projects/", "Projects/Notes.txt"), temp.tree())
        FileOps.createFile(folder, "todo.txt")
        try {
            FileOps.rename(renamed, "todo.txt")
            fail()
        } catch (e: IOException) {
            assertEquals("todo.txt already exists", e.message)
        }
    }

    @Test
    fun copiesFilesAndFoldersWithTheirDates() {
        val a = temp.file("src/a.txt", "alpha", modified = 1_600_000_000_000)
        val folder = temp.dir("src/photos")
        temp.file("src/photos/one.jpg", "1")
        temp.file("src/photos/deep/two.jpg", "22")
        val dest = temp.dir("dest")

        val result = transfer(listOf(a, folder), dest, move = false)

        assertEquals(2, result.done)
        assertTrue(result.failures.isEmpty())
        assertEquals(listOf("a.txt", "photos/", "photos/deep/", "photos/deep/two.jpg", "photos/one.jpg"), temp.tree(dest))
        assertEquals("alpha", File(dest, "a.txt").readText())
        assertEquals(1_600_000_000_000, File(dest, "a.txt").lastModified())
        assertTrue(a.exists())
    }

    @Test
    fun movesByRenamingAndLeavesNothingBehind() {
        val a = temp.file("src/a.txt", "alpha")
        val folder = temp.dir("src/photos")
        temp.file("src/photos/one.jpg")
        val dest = temp.dir("dest")

        val result = transfer(listOf(a, folder), dest, move = true)

        assertEquals(2, result.done)
        assertEquals(listOf(a to File(dest, "a.txt"), folder to File(dest, "photos")), result.targets)
        assertEquals(listOf("src/", "dest/", "dest/a.txt", "dest/photos/", "dest/photos/one.jpg").sorted(), temp.tree())
    }

    @Test
    fun conflictsAreFoundUpFront() = runBlocking {
        val a = temp.file("src/a.txt")
        val b = temp.file("src/b.txt")
        val dest = temp.dir("dest")
        temp.file("dest/a.txt")
        val plan = FileOps.plan(listOf(a, b), dest, move = false)
        assertEquals(listOf(a), plan.conflicts)
        assertEquals(a.length() + b.length(), plan.totalBytes)
    }

    @Test
    fun replaceOverwritesFilesAndMergesFolders() {
        val a = temp.file("src/a.txt", "new")
        val folder = temp.dir("src/docs")
        temp.file("src/docs/fresh.txt", "fresh")
        temp.file("src/docs/same.txt", "new same")
        val dest = temp.dir("dest")
        temp.file("dest/a.txt", "old")
        temp.file("dest/docs/kept.txt", "kept")
        temp.file("dest/docs/same.txt", "old same")

        val result = transfer(listOf(a, folder), dest, move = true, ConflictPolicy.REPLACE)

        assertEquals(2, result.done)
        assertEquals("new", File(dest, "a.txt").readText())
        assertEquals(listOf("fresh.txt", "kept.txt", "same.txt"), temp.tree(File(dest, "docs")))
        assertEquals("new same", File(dest, "docs/same.txt").readText())
        assertEquals(listOf("src/"), temp.tree().filter { it.startsWith("src") })
    }

    @Test
    fun keepBothAndSkip() {
        val a = temp.file("src/a.txt", "new")
        val dest = temp.dir("dest")
        temp.file("dest/a.txt", "old")

        val skipped = transfer(listOf(a), dest, move = false, ConflictPolicy.SKIP)
        assertEquals(0, skipped.done)
        assertEquals(1, skipped.skipped)
        assertEquals("old", File(dest, "a.txt").readText())

        val both = transfer(listOf(a), dest, move = false, ConflictPolicy.KEEP_BOTH)
        assertEquals(1, both.done)
        assertEquals("new", File(dest, "a (1).txt").readText())
        assertEquals("old", File(dest, "a.txt").readText())
    }

    @Test
    fun copyingIntoTheSameFolderMakesACopyAndMovingThereDoesNothing() {
        val a = temp.file("a.txt", "alpha")
        val copied = transfer(listOf(a), temp.root, move = false)
        assertEquals(listOf(a to File(temp.root, "a (1).txt")), copied.targets)
        val moved = transfer(listOf(a), temp.root, move = true)
        assertEquals(0, moved.done)
        assertEquals(1, moved.skipped)
        assertEquals(listOf("a (1).txt", "a.txt"), temp.tree())
    }

    @Test
    fun aFolderCantGoIntoItself() = runBlocking {
        val folder = temp.dir("photos")
        val inner = temp.dir("photos/2026")
        try {
            FileOps.plan(listOf(folder), inner, move = true)
            fail()
        } catch (e: IOException) {
            assertEquals("Can't move photos into itself", e.message)
        }
        try {
            FileOps.plan(listOf(folder), folder, move = false)
            fail()
        } catch (e: IOException) {
            assertEquals("Can't copy photos into itself", e.message)
        }
    }

    @Test
    fun cancellingACopyLeavesNoPartFiles() = runBlocking {
        val big = File(temp.dir("src"), "big.bin").apply { writeBytes(ByteArray(3 * 1024 * 1024)) }
        val dest = temp.dir("dest")
        val plan = FileOps.plan(listOf(big), dest, move = false)
        var job: Job? = null
        job = launch {
            FileOps.transfer(plan, ConflictPolicy.REPLACE) { bytes, _ -> if (bytes > 0) job?.cancel() }
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(emptyList<String>(), temp.tree(dest))
        assertTrue(big.exists())
    }

    @Test
    fun progressCountsEveryByte() = runBlocking {
        temp.file("src/a.txt", "12345")
        temp.file("src/b/c.txt", "123")
        val dest = temp.dir("dest")
        val plan = FileOps.plan(listOf(File(temp.root, "src")), dest, move = false)
        var last = 0L
        FileOps.transfer(plan, ConflictPolicy.REPLACE) { bytes, _ -> last = bytes }
        assertEquals(8L, plan.totalBytes)
        assertEquals(8L, last)
    }

    @Test
    fun deletingALinkLeavesItsTargetAlone() {
        val target = temp.dir("keep")
        temp.file("keep/precious.txt")
        val holder = temp.dir("holder")
        Files.createSymbolicLink(File(holder, "link").toPath(), target.toPath())

        assertTrue(FileOps.deleteRecursively(holder))
        assertFalse(holder.exists())
        assertTrue(File(target, "precious.txt").exists())
    }

    @Test
    fun measureAndChecksum() = runBlocking {
        temp.file("a/one.txt", "abc")
        temp.file("a/b/two.txt", "defg")
        val m = FileOps.measure(File(temp.root, "a"))
        assertEquals(Measure(bytes = 7, files = 2, folders = 2), m)
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            FileOps.sha256(File(temp.root, "a/one.txt")),
        )
    }

    @Test
    fun insideChecksWholeNames() {
        assertTrue(FileOps.isInside(File("/storage/emulated/0/DCIM/x"), File("/storage/emulated/0")))
        assertTrue(FileOps.isInside(File("/storage/emulated/0"), File("/storage/emulated/0/")))
        assertFalse(FileOps.isInside(File("/storage/emulated/01/x"), File("/storage/emulated/0")))
    }

    @Test
    fun copyingNeedsRoomForItAll() = runBlocking {
        val big = temp.file("Download/film.mp4", "x".repeat(3 * 1024 * 1024))
        val small = temp.file("Download/notes.txt", "x".repeat(1000))
        val card = temp.dir("SD card")
        try {
            FileOps.plan(listOf(big, small), card, move = false, freeBytes = 2 * 1024 * 1024)
            fail()
        } catch (e: IOException) {
            assertEquals("Not enough space in SD card. It needs 3.0 MB, and 2.0 MB is free.", e.message)
        }
        // Moving between volumes copies, so it needs the room too.
        try {
            FileOps.plan(listOf(big), card, move = true, freeBytes = 1024 * 1024)
            fail()
        } catch (e: IOException) {
            assertTrue(e.message!!.startsWith("Not enough space"))
        }
        // Moves on the same volume are renames, and only what leaves the volume is counted.
        assertEquals(3 * 1024 * 1024 + 1000L, FileOps.plan(listOf(big, small), card, move = true, freeBytes = 1, sameVolume = { true }).totalBytes)
        FileOps.plan(listOf(big, small), card, move = true, freeBytes = 2000, sameVolume = { it == big })
        // Free space that can't be told doesn't stop anything.
        FileOps.plan(listOf(big), card, move = false, freeBytes = 0)
        Unit
    }

    @Test
    fun namesFromOtherAppsAreMadeSafe() {
        assertEquals("Shared file", FileOps.safeName(null))
        assertEquals("Shared file", FileOps.safeName(" .. "))
        assertEquals("a_b.txt", FileOps.safeName("a/b.txt"))
        val long = FileOps.safeName("x".repeat(300) + ".jpeg")
        assertEquals(255, long.toByteArray().size)
        assertTrue(long.endsWith("x.jpeg"))
        // Cut at whole characters, never in the middle of one.
        val welsh = FileOps.safeName("ŵ".repeat(200) + ".txt")
        assertTrue(welsh.toByteArray().size <= 255)
        assertTrue(welsh.endsWith("ŵ.txt"))
    }

    @Test
    fun savingNeverReplacesAndLeavesNothingWhenItFails() = runBlocking {
        val dir = temp.dir("Download")
        temp.file("Download/notes.txt", "old")
        var progress = 0L
        val saved = FileOps.save("new".byteInputStream(), dir, "notes.txt") { progress = it }
        assertEquals("notes (1).txt", saved.name)
        assertEquals("new", saved.readText())
        assertEquals("old", File(dir, "notes.txt").readText())
        assertEquals(3L, progress)

        val failing = object : java.io.InputStream() {
            var sent = 0
            override fun read(): Int = if (sent++ < 10) 'x'.code else throw IOException("The other app stopped")
        }
        try {
            FileOps.save(failing, dir, "broken.bin")
            fail()
        } catch (e: IOException) {
            assertEquals("The other app stopped", e.message)
        }
        assertEquals(listOf("Download/", "Download/notes (1).txt", "Download/notes.txt"), temp.tree())
    }
}
