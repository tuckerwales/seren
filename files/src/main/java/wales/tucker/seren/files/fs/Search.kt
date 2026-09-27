package wales.tucker.seren.files.fs

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/** Walking folders to find files by name or by when they changed. */
object Search {
    /** Most results a search shows; past this, people should narrow it down. */
    const val LIMIT = 500

    /**
     * Files and folders under [root] whose name contains [query], ignoring case, nearest first.
     * Hidden items and everything inside hidden folders are skipped unless [showHidden].
     */
    fun find(root: File, query: String, showHidden: Boolean, limit: Int = LIMIT): Flow<FileEntry> = flow {
        val q = query.trim()
        if (q.isEmpty()) return@flow
        var found = 0
        walk(root, showHidden) { file ->
            if (file.name.contains(q, ignoreCase = true)) {
                emit(FileEntry.of(file, countChildren = false))
                found++
            }
            found < limit
        }
    }

    /**
     * Files under [roots] changed at or after [since], newest first, at most [limit]. Used when the
     * media store can't answer. Skips hidden folders and apps' private Android folder.
     */
    suspend fun recentlyChanged(roots: List<File>, since: Long, limit: Int): List<FileEntry> {
        val found = mutableListOf<FileEntry>()
        for (root in roots) {
            walk(root, showHidden = false) { file ->
                if (!file.isDirectory && file.lastModified() >= since) found += FileEntry.of(file)
                true
            }
        }
        return found.sortedByDescending { it.modified }.take(limit)
    }

    /**
     * Visits everything under [root] breadth first, calling [visit] for each item until it returns
     * false. Links to folders are not followed, so a loop can't trap the walk.
     */
    private suspend fun walk(root: File, showHidden: Boolean, visit: suspend (File) -> Boolean) {
        val queue = ArrayDeque<File>().apply { add(root) }
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val dir = queue.removeFirst()
            val children = dir.listFiles()?.sortedWith(compareBy(NaturalOrder) { it.name }) ?: continue
            for (child in children) {
                if (child.name == Listing.TRASH_FOLDER) continue
                if (!showHidden && child.name.startsWith(".")) continue
                if (!visit(child)) return
                if (child.isDirectory && !FileOps.isLink(child) && !isPrivateAppData(root, child)) queue.add(child)
            }
        }
    }

    /** Android/data and Android/obb belong to other apps and can't be read on newer Android anyway. */
    private fun isPrivateAppData(root: File, dir: File): Boolean {
        val relative = dir.path.removePrefix(root.path).trimStart('/')
        return relative == "Android/data" || relative == "Android/obb"
    }
}
