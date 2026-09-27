package wales.tucker.seren.files.fs

/** Kinds of file gathered from everywhere on the device, so people can find them without knowing where they are. */
enum class Category(val label: String, private val kinds: Set<FileKind>) {
    IMAGES("Images", setOf(FileKind.IMAGE)),
    VIDEOS("Videos", setOf(FileKind.VIDEO)),
    AUDIO("Audio", setOf(FileKind.AUDIO)),
    DOCUMENTS("Documents", setOf(FileKind.PDF, FileKind.DOCUMENT, FileKind.TEXT)),
    ARCHIVES("Archives", setOf(FileKind.ARCHIVE)),
    APPS("Apps", setOf(FileKind.APP)),

    /** Any file of [Categories.LARGE_BYTES] or more, biggest first, for freeing up space. */
    LARGE("Large files", emptySet()),
    ;

    fun matches(entry: FileEntry): Boolean =
        !entry.isDirectory && if (this == LARGE) entry.size >= Categories.LARGE_BYTES else entry.kind in kinds
}

/** How many files a category has and how much space they take. */
data class CategorySummary(val category: Category, val count: Int, val bytes: Long)

object Categories {
    /** The smallest file counted as large: 25 MB. */
    const val LARGE_BYTES = 25L * 1024 * 1024

    /** Most files a category shows; past this, people should look in folders. */
    const val LIMIT = 500

    /** A summary for each category that has any files, in [Category] order. */
    fun summarize(files: List<FileEntry>): List<CategorySummary> = Category.entries.mapNotNull { category ->
        val matching = files.filter(category::matches)
        if (matching.isEmpty()) null else CategorySummary(category, matching.size, matching.sumOf { it.size })
    }

    /** The files in [category]: biggest first for large files, otherwise newest first. */
    fun list(files: List<FileEntry>, category: Category, limit: Int = LIMIT): List<FileEntry> {
        val order = if (category == Category.LARGE) {
            compareByDescending<FileEntry> { it.size }.thenByDescending { it.modified }
        } else {
            compareByDescending<FileEntry> { it.modified }.thenBy(NaturalOrder) { it.name }
        }
        return files.filter(category::matches).sortedWith(order).take(limit)
    }
}
