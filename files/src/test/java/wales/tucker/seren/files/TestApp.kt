package wales.tucker.seren.files

import wales.tucker.seren.files.fs.FileEntry
import wales.tucker.seren.files.fs.Search
import wales.tucker.seren.files.fs.Storage
import wales.tucker.seren.files.fs.Volume
import java.io.File
import java.nio.file.Files

/** Internal storage is a temporary folder, and access can be taken away to test asking for it. */
class TestStorage(val root: File) : Storage {
    var access = true

    /** Where internal storage is, when a test needs it somewhere other than [root]. */
    var volumeRoot: File? = null

    override fun hasAccess(): Boolean = access

    override fun volumes(): List<Volume> =
        listOf(Volume("Internal storage", volumeRoot ?: root, primary = true, totalBytes = 128L shl 30, freeBytes = 41_234_567_890L))

    override suspend fun recent(since: Long, limit: Int): List<FileEntry> = Search.recentlyChanged(listOf(root), since, limit)
}

class TestApp : SerenApp() {
    /**
     * FileProvider keeps each authority's roots for the life of the process, but every Robolectric
     * test has its own storage folder, so forget the last test's before this one starts.
     */
    override fun onCreate() {
        val cache = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.get(null)!!
        synchronized(cache) { (cache as MutableMap<*, *>).clear() }
        super.onCreate()
    }

    val root: File by lazy { Files.createTempDirectory("storage").toFile() }
    val storage by lazy { TestStorage(root) }
    override fun createContainer(): AppContainer = AppContainer(this, storage)
}
