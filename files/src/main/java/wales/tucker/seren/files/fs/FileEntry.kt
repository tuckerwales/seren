package wales.tucker.seren.files.fs

import java.io.File
import java.io.IOException

/** A file or folder as shown in a list. */
data class FileEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    /** Bytes for a file; 0 for a folder. */
    val size: Long,
    val modified: Long,
    /** How many items a folder holds (hidden ones included), or -1 when it can't be read. */
    val childCount: Int = -1,
) {
    val file: File get() = File(path)
    val kind: FileKind get() = FileTypes.kind(name, isDirectory)
    val isHidden: Boolean get() = name.startsWith(".")

    companion object {
        fun of(file: File, countChildren: Boolean = true): FileEntry {
            val dir = file.isDirectory
            return FileEntry(
                path = file.path,
                name = file.name,
                isDirectory = dir,
                size = if (dir) 0 else file.length(),
                modified = file.lastModified(),
                childCount = if (dir && countChildren) file.list()?.size ?: -1 else -1,
            )
        }
    }
}

enum class SortBy(val label: String) {
    NAME("Name"),
    MODIFIED("Date changed"),
    SIZE("Size"),
    TYPE("Type"),
}

data class SortOrder(val by: SortBy = SortBy.NAME, val descending: Boolean = false)

object Listing {
    /** Where Seren Files keeps what's in the trash, at the root of each storage volume. */
    const val TRASH_FOLDER = ".SerenTrash"

    /**
     * The files and folders in [dir], folders first, sorted by [order]. Hidden items (names starting
     * with a dot) are left out unless [showHidden]; the trash folder is always left out.
     */
    fun list(dir: File, order: SortOrder, showHidden: Boolean): List<FileEntry> {
        val files = dir.listFiles() ?: throw IOException(
            if (!dir.exists()) "${dir.name} was moved or deleted" else "Seren Files can't read ${dir.name}",
        )
        return sort(
            files.asSequence()
                .filter { it.name != TRASH_FOLDER && (showHidden || !it.name.startsWith(".")) }
                .map { FileEntry.of(it) }
                .toList(),
            order,
        )
    }

    fun sort(entries: List<FileEntry>, order: SortOrder): List<FileEntry> {
        val byName = Comparator<FileEntry> { a, b -> NaturalOrder.compare(a.name, b.name) }
        val primary: Comparator<FileEntry> = when (order.by) {
            SortBy.NAME -> byName
            SortBy.MODIFIED -> compareBy<FileEntry> { it.modified }.then(byName)
            // Folders have no size of their own, so they sort by how much they hold.
            SortBy.SIZE -> compareBy<FileEntry> { if (it.isDirectory) it.childCount.toLong() else it.size }.then(byName)
            SortBy.TYPE -> compareBy<FileEntry> { FileTypes.extension(it.name) }.then(byName)
        }
        val ordered = if (order.descending) primary.reversed() else primary
        return entries.sortedWith(compareBy<FileEntry> { !it.isDirectory }.then(ordered))
    }
}

/**
 * Orders names the way people count: "photo 2" before "photo 10", ignoring case, with a stable
 * tie break so names differing only in case keep a fixed order.
 */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val startA = i
                val startB = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val numA = a.substring(startA, i).trimStart('0')
                val numB = b.substring(startB, j).trimStart('0')
                if (numA.length != numB.length) return numA.length - numB.length
                val cmp = numA.compareTo(numB)
                if (cmp != 0) return cmp
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        val rest = (a.length - i) - (b.length - j)
        return if (rest != 0) rest else a.compareTo(b)
    }
}
