package wales.tucker.seren.files.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import wales.tucker.seren.files.fs.FileOps
import wales.tucker.seren.files.fs.Listing
import wales.tucker.seren.files.fs.Storage
import java.io.File

/** What happened to a batch of items: the ones that worked, and a reason for each one that didn't. */
data class BatchResult<T>(val done: List<T>, val failures: List<Pair<String, String>>)

/**
 * The trash. Items are moved (renamed, so it's instant) into a hidden folder at the root of the
 * volume they're on, and remembered in the database so they can go back where they came from.
 * Anything older than [KEEP_MILLIS] is deleted for good.
 */
class TrashBin(private val dao: TrashDao, private val storage: Storage, private val now: () -> Long) {

    val items: Flow<List<TrashItem>> = dao.observe()

    suspend fun moveToTrash(files: List<File>): BatchResult<TrashItem> = withContext(Dispatchers.IO) {
        val done = mutableListOf<TrashItem>()
        val failures = mutableListOf<Pair<String, String>>()
        val time = now()
        for (file in files) {
            if (!file.exists()) {
                failures += file.name to "It was moved or deleted"
                continue
            }
            val folder = trashFolderFor(file)
            if (folder == null) {
                failures += file.name to "Couldn't create the trash folder"
                continue
            }
            val size = FileOps.measure(file).bytes
            val stored = File(folder, FileOps.uniqueName(folder, "$time-${file.name}", file.isDirectory))
            if (!file.renameTo(stored)) {
                failures += file.name to "Couldn't move it to the trash"
                continue
            }
            val item = TrashItem(
                name = file.name,
                originalPath = file.path,
                storedPath = stored.path,
                isDirectory = stored.isDirectory,
                size = size,
                deletedAt = time,
            )
            done += item.copy(id = dao.insert(item))
        }
        BatchResult(done, failures)
    }

    /**
     * Puts [items] back where they were, recreating their folder if it's gone. If something new has
     * taken the name, the restored item gets a name like "notes (1).txt".
     */
    suspend fun restore(items: List<TrashItem>): BatchResult<File> = withContext(Dispatchers.IO) {
        val done = mutableListOf<File>()
        val failures = mutableListOf<Pair<String, String>>()
        for (item in items) {
            val stored = File(item.storedPath)
            if (!stored.exists()) {
                dao.delete(item.id)
                failures += item.name to "It's no longer in the trash"
                continue
            }
            val parent = File(item.originalPath).parentFile
            if (parent == null || !parent.isDirectory && !parent.mkdirs()) {
                failures += item.name to "Couldn't recreate ${parent?.name ?: "its folder"}"
                continue
            }
            val target = File(parent, FileOps.uniqueName(parent, item.name, item.isDirectory))
            if (!stored.renameTo(target)) {
                failures += item.name to "Couldn't move it back"
                continue
            }
            dao.delete(item.id)
            done += target
        }
        BatchResult(done, failures)
    }

    suspend fun deleteForever(items: List<TrashItem>): BatchResult<TrashItem> = withContext(Dispatchers.IO) {
        val done = mutableListOf<TrashItem>()
        val failures = mutableListOf<Pair<String, String>>()
        for (item in items) {
            if (FileOps.deleteRecursively(File(item.storedPath))) {
                dao.delete(item.id)
                done += item
            } else {
                failures += item.name to "Couldn't delete it"
            }
        }
        BatchResult(done, failures)
    }

    suspend fun empty(): BatchResult<TrashItem> = deleteForever(dao.all())

    /** Deletes what's been in the trash longer than [KEEP_MILLIS], and forgets items that vanished. */
    suspend fun tidy() = withContext(Dispatchers.IO) {
        deleteForever(dao.olderThan(now() - KEEP_MILLIS))
        for (item in dao.all()) {
            if (!File(item.storedPath).exists()) dao.delete(item.id)
        }
    }

    private fun trashFolderFor(file: File): File? {
        val volumes = storage.volumes()
        val root = volumes.filter { FileOps.isInside(file, it.root) }.maxByOrNull { it.root.path.length }?.root
            ?: volumes.firstOrNull()?.root
            ?: return null
        val folder = File(root, Listing.TRASH_FOLDER)
        if (!folder.isDirectory && !folder.mkdirs()) return null
        // Keep trashed photos and music out of gallery and music apps.
        runCatching { File(folder, ".nomedia").createNewFile() }
        return folder
    }

    companion object {
        const val KEEP_DAYS = 30
        const val KEEP_MILLIS = KEEP_DAYS * 24L * 60 * 60 * 1000
    }
}
