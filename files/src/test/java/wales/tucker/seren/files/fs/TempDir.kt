package wales.tucker.seren.files.fs

import java.io.File
import java.nio.file.Files

/** A fresh folder for a test, with helpers to fill it. */
class TempDir {
    val root: File = Files.createTempDirectory("seren-files").toFile()

    fun file(path: String, text: String = path, modified: Long? = null): File =
        File(root, path).apply {
            parentFile!!.mkdirs()
            writeText(text)
            if (modified != null) setLastModified(modified)
        }

    fun dir(path: String): File = File(root, path).apply { mkdirs() }

    /** Every path under [dir], relative and sorted, folders ending in "/". */
    fun tree(dir: File = root): List<String> =
        dir.walkTopDown().drop(1).map { it.relativeTo(dir).path + if (it.isDirectory) "/" else "" }.sorted().toList()

    fun delete() {
        root.deleteRecursively()
    }
}
