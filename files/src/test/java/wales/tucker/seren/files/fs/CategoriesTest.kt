package wales.tucker.seren.files.fs

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.RandomAccessFile

class CategoriesTest {
    private val temp = TempDir()

    @After
    fun tearDown() = temp.delete()

    private fun entry(name: String, size: Long = 100, modified: Long = 1) = FileEntry("/s/$name", name, false, size, modified)

    @Test
    fun summariesCountEachKindAndSkipEmptyOnes() {
        val files = listOf(
            entry("a.jpg", 10),
            entry("b.png", 20),
            entry("film.mp4", Categories.LARGE_BYTES + 1),
            entry("cv.pdf", 5),
            entry("notes.md", 5),
            entry("backup.zip", 7),
            entry("mystery.bin", 3),
        )
        assertEquals(
            listOf(
                CategorySummary(Category.IMAGES, 2, 30),
                CategorySummary(Category.VIDEOS, 1, Categories.LARGE_BYTES + 1),
                CategorySummary(Category.DOCUMENTS, 2, 10),
                CategorySummary(Category.ARCHIVES, 1, 7),
                CategorySummary(Category.LARGE, 1, Categories.LARGE_BYTES + 1),
            ),
            Categories.summarize(files),
        )
    }

    @Test
    fun largeFilesComeBiggestFirstAndOthersNewestFirst() {
        val files = listOf(
            entry("old.jpg", modified = 1),
            entry("new.jpg", modified = 3),
            entry("mid.jpg", modified = 2),
            entry("big.iso", Categories.LARGE_BYTES * 4),
            entry("bigger.mkv", Categories.LARGE_BYTES * 8),
            entry("small.mkv", Categories.LARGE_BYTES - 1),
        )
        assertEquals(listOf("new.jpg", "mid.jpg", "old.jpg"), Categories.list(files, Category.IMAGES).map { it.name })
        assertEquals(listOf("bigger.mkv", "big.iso"), Categories.list(files, Category.LARGE).map { it.name })
        assertEquals(listOf("new.jpg"), Categories.list(files, Category.IMAGES, limit = 1).map { it.name })
    }

    @Test
    fun walkingFindsFilesButNotHiddenOnesOrFolders() = runBlocking {
        temp.file("DCIM/Camera/one.jpg")
        temp.file("Download/two.pdf")
        temp.file(".hidden/three.jpg")
        temp.file("Download/.secret.jpg")
        temp.file("${Listing.TRASH_FOLDER}/1-gone.jpg")
        temp.dir("Empty")
        RandomAccessFile(temp.file("Movies/film.mkv"), "rw").use { it.setLength(Categories.LARGE_BYTES) }
        val all = Search.allFiles(listOf(temp.root))
        assertEquals(listOf("one.jpg", "two.pdf", "film.mkv").sorted(), all.map { it.name }.sorted())
        assertEquals(listOf("film.mkv"), Categories.list(all, Category.LARGE).map { it.name })
    }
}
