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

    override fun hasAccess(): Boolean = access

    override fun volumes(): List<Volume> =
        listOf(Volume("Internal storage", root, primary = true, totalBytes = 128L shl 30, freeBytes = 41_234_567_890L))

    override suspend fun recent(since: Long, limit: Int): List<FileEntry> = Search.recentlyChanged(listOf(root), since, limit)
}

class TestApp : SerenApp() {
    val root: File by lazy { Files.createTempDirectory("storage").toFile() }
    val storage by lazy { TestStorage(root) }
    override fun createContainer(): AppContainer = AppContainer(this, storage)
}
