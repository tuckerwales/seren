package wales.tucker.seren.files.fs

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class ListingTest {
    private val temp = TempDir()

    @After
    fun tearDown() = temp.delete()

    @Test
    fun namesSortTheWayPeopleCount() {
        val names = listOf("photo 10.jpg", "Photo 2.jpg", "photo 1.jpg", "notes.txt", "Notes.txt", "a", "photo 02.jpg")
        assertEquals(
            listOf("a", "Notes.txt", "notes.txt", "photo 1.jpg", "Photo 2.jpg", "photo 02.jpg", "photo 10.jpg"),
            names.sortedWith(NaturalOrder),
        )
    }

    @Test
    fun foldersComeFirstThenTheChosenOrder() {
        temp.file("b.txt", "12345", modified = 3_000_000)
        temp.file("a.jpg", "1", modified = 1_000_000)
        temp.file("c.md", "123", modified = 2_000_000)
        temp.dir("Alpha").also { File(it, "x").writeText("x") }.setLastModified(4_000_000)
        temp.dir("zeta").setLastModified(5_000_000)

        fun names(order: SortOrder) = Listing.list(temp.root, order, showHidden = false).map { it.name }

        assertEquals(listOf("Alpha", "zeta", "a.jpg", "b.txt", "c.md"), names(SortOrder(SortBy.NAME)))
        assertEquals(listOf("zeta", "Alpha", "c.md", "b.txt", "a.jpg"), names(SortOrder(SortBy.NAME, descending = true)))
        assertEquals(listOf("Alpha", "zeta", "a.jpg", "c.md", "b.txt"), names(SortOrder(SortBy.MODIFIED)))
        assertEquals(listOf("zeta", "Alpha", "b.txt", "c.md", "a.jpg"), names(SortOrder(SortBy.MODIFIED, descending = true)))
        assertEquals(listOf("b.txt", "c.md", "a.jpg"), names(SortOrder(SortBy.SIZE, descending = true)).drop(2))
        assertEquals(listOf("a.jpg", "c.md", "b.txt"), names(SortOrder(SortBy.TYPE)).drop(2))
    }

    @Test
    fun hiddenItemsAndTheTrashAreLeftOut() {
        temp.file(".hidden")
        temp.file("seen.txt")
        temp.dir(Listing.TRASH_FOLDER)
        assertEquals(listOf("seen.txt"), Listing.list(temp.root, SortOrder(), showHidden = false).map { it.name })
        assertEquals(listOf(".hidden", "seen.txt"), Listing.list(temp.root, SortOrder(), showHidden = true).map { it.name })
    }

    @Test
    fun foldersKnowHowManyItemsTheyHold() {
        temp.file("docs/a.txt")
        temp.file("docs/b.txt")
        temp.file("docs/.c")
        val docs = Listing.list(temp.root, SortOrder(), showHidden = false).single()
        assertTrue(docs.isDirectory)
        assertEquals(3, docs.childCount)
        assertEquals(FileKind.FOLDER, docs.kind)
    }

    @Test(expected = IOException::class)
    fun aMissingFolderSaysSo() {
        Listing.list(File(temp.root, "gone"), SortOrder(), showHidden = false)
    }
}
