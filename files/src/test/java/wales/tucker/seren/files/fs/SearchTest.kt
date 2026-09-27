package wales.tucker.seren.files.fs

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTest {
    private val temp = TempDir()

    @After
    fun tearDown() = temp.delete()

    @Test
    fun findsByNameNearestFirstAndSkipsHiddenAndTrash() = runBlocking {
        temp.file("deep/down/Report final.pdf")
        temp.file("report.txt")
        temp.file(".secret/report.txt")
        temp.file("${Listing.TRASH_FOLDER}/report.txt")
        temp.dir("Reports")

        val found = Search.find(temp.root, "REPORT", showHidden = false).toList().map { it.path.removePrefix(temp.root.path + "/") }
        assertEquals(listOf("report.txt", "Reports", "deep/down/Report final.pdf"), found)

        val withHidden = Search.find(temp.root, "report", showHidden = true).toList().map { it.path.removePrefix(temp.root.path + "/") }
        assertEquals(listOf(".secret/report.txt"), withHidden - found.toSet())
    }

    @Test
    fun stopsAtTheLimit() = runBlocking {
        repeat(10) { temp.file("file $it.txt") }
        assertEquals(3, Search.find(temp.root, "file", showHidden = false, limit = 3).toList().size)
        assertEquals(0, Search.find(temp.root, "  ", showHidden = false).toList().size)
    }

    @Test
    fun recentlyChangedFilesNewestFirst() = runBlocking {
        temp.file("old.txt", modified = 1_000)
        temp.file("a/new.txt", modified = 9_000)
        temp.file("b/newer.txt", modified = 10_000)
        temp.file(".hidden/newest.txt", modified = 11_000)
        temp.file("Android/data/some.app/cache.bin", modified = 12_000)

        val found = Search.recentlyChanged(listOf(temp.root), since = 5_000, limit = 10).map { it.name }
        assertEquals(listOf("newer.txt", "new.txt"), found)
    }
}
