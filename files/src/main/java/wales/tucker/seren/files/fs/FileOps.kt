package wales.tucker.seren.files.fs

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import wales.tucker.seren.core.ui.formatSize

/** What to do when something with the same name is already where files are going. */
enum class ConflictPolicy {
    /** Replace files with the same name; folders with the same name are merged. */
    REPLACE,

    /** Keep both, giving the new one a name like "photo (1).jpg". */
    KEEP_BOTH,

    /** Leave the existing one and don't copy or move that item. */
    SKIP,
}

/** A copy or move worked out before it starts, so conflicts can be asked about up front. */
data class TransferPlan(
    val sources: List<File>,
    val destination: File,
    val move: Boolean,
    /** Sources whose name is already taken in [destination]. */
    val conflicts: List<File>,
    val totalBytes: Long,
)

data class TransferResult(
    val done: Int,
    val skipped: Int,
    /** Name and reason for each item that failed. */
    val failures: List<Pair<String, String>>,
    /** Each item that was copied or moved, and where it ended up. */
    val targets: List<Pair<File, File>>,
)

/** Totals for a file or everything in a folder. */
data class Measure(val bytes: Long, val files: Int, val folders: Int)

/** Everything Seren Files does to files, on plain java.io so it can be tested on the JVM. */
object FileOps {
    private const val BUFFER = 256 * 1024

    /** Why [name] can't be used for a file or folder, or null when it's fine. */
    fun validateName(name: String): String? = when {
        name.isBlank() -> "Enter a name"
        name.contains('/') -> "Names can't contain /"
        name.contains('\u0000') -> "Names can't contain that character"
        name == "." || name == ".." -> "That name is reserved"
        name.toByteArray().size > 255 -> "That name is too long"
        else -> null
    }

    /**
     * [name] if nothing in [dir] has it yet, otherwise the first free "name (1).ext", "name (2).ext"
     * and so on. Folders and names without an extension get the number at the end.
     */
    fun uniqueName(dir: File, name: String, isDirectory: Boolean = false): String {
        if (!File(dir, name).exists()) return name
        val dot = name.lastIndexOf('.')
        val (base, ext) = if (isDirectory || dot <= 0) name to "" else name.substring(0, dot) to name.substring(dot)
        val stem = Regex("""^(.*) \((\d+)\)$""").matchEntire(base)?.groupValues?.get(1) ?: base
        var n = 1
        while (true) {
            val candidate = "$stem ($n)$ext"
            if (!File(dir, candidate).exists()) return candidate
            n++
        }
    }

    fun createFolder(parent: File, name: String): File {
        validateName(name)?.let { throw IOException(it) }
        val folder = File(parent, name)
        if (folder.exists()) throw IOException("$name already exists")
        if (!folder.mkdir()) throw IOException("Couldn't create $name")
        return folder
    }

    fun createFile(parent: File, name: String): File {
        validateName(name)?.let { throw IOException(it) }
        val file = File(parent, name)
        if (file.exists()) throw IOException("$name already exists")
        if (!file.createNewFile()) throw IOException("Couldn't create $name")
        return file
    }

    /** Renames [file] to [newName] in the same folder; changing only the case of a name works too. */
    fun rename(file: File, newName: String): File {
        validateName(newName)?.let { throw IOException(it) }
        if (newName == file.name) return file
        val target = File(file.parentFile, newName)
        val caseOnly = newName.equals(file.name, ignoreCase = true)
        if (target.exists() && !caseOnly) throw IOException("$newName already exists")
        if (caseOnly) {
            // Some file systems ignore case, so go through a temporary name.
            val temp = File(file.parentFile, uniqueName(file.parentFile!!, ".${file.name}.renaming"))
            if (!file.renameTo(temp) || !temp.renameTo(target)) throw IOException("Couldn't rename ${file.name}")
        } else if (!file.renameTo(target)) {
            throw IOException("Couldn't rename ${file.name}")
        }
        return target
    }

    /**
     * Works out copying or moving [sources] into [destination]: what will clash and how many bytes
     * there are. Throws with a reason when it can't be done at all, such as a folder into itself,
     * or when [destination] hasn't the [freeBytes] for what has to be written there. Moves that
     * stay on [sameVolume] are renames, which need no space. A [freeBytes] of 0 means unknown.
     */
    suspend fun plan(
        sources: List<File>,
        destination: File,
        move: Boolean,
        freeBytes: Long = destination.usableSpace,
        sameVolume: (File) -> Boolean = { false },
    ): TransferPlan {
        if (!destination.isDirectory) throw IOException("${destination.name} was moved or deleted")
        val destCanonical = destination.canonicalPath
        for (source in sources) {
            if (source.isDirectory && !isLink(source)) {
                val sourceCanonical = source.canonicalPath
                if (destCanonical == sourceCanonical || destCanonical.startsWith("$sourceCanonical/")) {
                    throw IOException("Can't ${if (move) "move" else "copy"} ${source.name} into itself")
                }
            }
        }
        val conflicts = sources.filter { it.parentFile?.canonicalPath != destCanonical && File(destination, it.name).exists() }
        val sizes = sources.associateWith { measure(it).bytes }
        val total = sizes.values.sum()
        val needed = sizes.filterKeys { !(move && sameVolume(it)) }.values.sum()
        if (freeBytes in 1 until needed) {
            val where = destination.name.ifEmpty { destination.path }
            throw IOException("Not enough space in $where. It needs ${formatSize(needed)}, and ${formatSize(freeBytes)} is free.")
        }
        return TransferPlan(sources, destination, move, conflicts, total)
    }

    /**
     * Copies or moves everything in [plan], settling clashes with [policy]. Copying into the folder
     * an item is already in makes a copy beside it; moving there does nothing. [onProgress] gets the
     * bytes done so far and the name being worked on. Cancelling leaves no half-written files.
     */
    suspend fun transfer(
        plan: TransferPlan,
        policy: ConflictPolicy,
        onProgress: (bytes: Long, name: String) -> Unit = { _, _ -> },
    ): TransferResult {
        var done = 0
        var skipped = 0
        val failures = mutableListOf<Pair<String, String>>()
        val targets = mutableListOf<Pair<File, File>>()
        val progress = ProgressCounter(onProgress)
        val destCanonical = plan.destination.canonicalPath

        for (source in plan.sources) {
            currentCoroutineContext().ensureActive()
            if (!source.exists() && !isLink(source)) {
                failures += source.name to "It was moved or deleted"
                continue
            }
            val sameFolder = source.parentFile?.canonicalPath == destCanonical
            if (sameFolder && plan.move) {
                skipped++
                continue
            }
            var target = File(plan.destination, source.name)
            var merge = false
            if (sameFolder) {
                target = File(plan.destination, uniqueName(plan.destination, source.name, source.isDirectory))
            } else if (target.exists() || isLink(target)) {
                when (policy) {
                    ConflictPolicy.SKIP -> {
                        skipped++
                        progress.add(measure(source).bytes, source.name)
                        continue
                    }
                    ConflictPolicy.KEEP_BOTH -> target = File(plan.destination, uniqueName(plan.destination, source.name, source.isDirectory))
                    ConflictPolicy.REPLACE -> merge = source.isDirectory && target.isDirectory && !isLink(source) && !isLink(target)
                }
            }
            try {
                if (plan.move && !merge && renameOver(source, target)) {
                    progress.add(measure(target).bytes, source.name)
                } else {
                    copy(source, target, progress)
                    if (plan.move && !deleteRecursively(source)) throw IOException("Copied, but couldn't remove the original")
                }
                done++
                targets += source to target
            } catch (e: IOException) {
                failures += source.name to (e.message ?: "Couldn't ${if (plan.move) "move" else "copy"} it")
            }
        }
        return TransferResult(done, skipped, failures, targets)
    }

    /**
     * Moves [source] to [target] by renaming, which is instant on the same volume. A file already at
     * [target] is only deleted once [source] is safely beside it. False when renaming can't work,
     * such as between volumes, so the caller copies instead.
     */
    private fun renameOver(source: File, target: File): Boolean {
        if (!target.exists() && !isLink(target)) return source.renameTo(target)
        if (target.isDirectory && !isLink(target)) return false
        val parent = target.parentFile ?: return false
        val temp = File(parent, uniqueName(parent, ".${target.name}.part"))
        if (!source.renameTo(temp)) return false
        if (!target.delete() || !temp.renameTo(target)) {
            temp.renameTo(source)
            return false
        }
        return true
    }

    /** Copies [source] to [target], replacing a file there and merging into a folder there. */
    private suspend fun copy(source: File, target: File, progress: ProgressCounter) {
        currentCoroutineContext().ensureActive()
        if (source.isDirectory && !isLink(source)) {
            if (target.exists() && !target.isDirectory) {
                if (!deleteRecursively(target)) throw IOException("Couldn't replace ${target.name}")
            }
            if (!target.exists() && !target.mkdirs()) throw IOException("Couldn't create ${target.name}")
            val children = source.listFiles() ?: throw IOException("Seren Files can't read ${source.name}")
            for (child in children) copy(child, File(target, child.name), progress)
            target.setLastModified(source.lastModified())
        } else {
            if (target.isDirectory && !isLink(target)) {
                if (!deleteRecursively(target)) throw IOException("Couldn't replace ${target.name}")
            }
            copyFile(source, target, progress)
        }
    }

    /** Writes to a hidden temporary file first, so a failed copy never costs the file it replaces. */
    private suspend fun copyFile(source: File, target: File, progress: ProgressCounter) {
        val parent = target.parentFile ?: throw IOException("Couldn't copy ${source.name}")
        val temp = File(parent, uniqueName(parent, ".${target.name}.part"))
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        progress.add(read.toLong(), source.name)
                    }
                    output.fd.sync()
                }
            }
            temp.setLastModified(source.lastModified())
            if (target.exists() && !target.delete()) throw IOException("Couldn't replace ${target.name}")
            if (!temp.renameTo(target)) throw IOException("Couldn't copy ${source.name}")
        } catch (e: Throwable) {
            temp.delete()
            if (e is IOException && e.message?.contains("ENOSPC") == true) throw IOException("Not enough space")
            throw e
        }
    }

    /**
     * A usable file name from one another app gave, which may be missing, have slashes in it or be
     * too long: slashes become underscores, and a long name is cut short but keeps its extension.
     */
    fun safeName(raw: String?, fallback: String = "Shared file"): String {
        var name = raw.orEmpty().replace('/', '_').replace("\u0000", "").trim()
        if (name.isEmpty() || name == "." || name == "..") name = fallback
        if (name.toByteArray().size <= 255) return name
        val ext = FileTypes.extension(name).let { if (it.isEmpty() || it.length > 16) "" else ".$it" }
        var stem = name.dropLast(ext.length)
        while ((stem + ext).toByteArray().size > 255) stem = stem.dropLast(1)
        return stem + ext
    }

    /**
     * Saves what [input] holds as a new file in [dir] called [name], or "name (1)" and so on when
     * that's taken. It's written to a hidden file first, so a failed or cancelled save leaves
     * nothing behind. [onProgress] gets the bytes written so far.
     */
    suspend fun save(input: java.io.InputStream, dir: File, name: String, onProgress: (Long) -> Unit = {}): File {
        validateName(name)?.let { throw IOException(it) }
        if (!dir.isDirectory) throw IOException("${dir.name} was moved or deleted")
        val temp = File(dir, uniqueName(dir, ".$name.part"))
        try {
            var bytes = 0L
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    bytes += read
                    onProgress(bytes)
                }
                output.fd.sync()
            }
            // Chosen only now, so two saves of the same name can't both take it.
            val target = File(dir, uniqueName(dir, name))
            if (!temp.renameTo(target)) throw IOException("Couldn't save $name")
            return target
        } catch (e: Throwable) {
            temp.delete()
            if (e is IOException && e.message?.contains("ENOSPC") == true) throw IOException("Not enough space")
            throw e
        }
    }

    /**
     * Deletes [file], and everything in it if it's a folder. Links are removed without touching
     * what they point to. Returns false if anything couldn't be deleted.
     */
    fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory && !isLink(file)) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        return file.delete() || !file.exists() && !isLink(file)
    }

    /** The bytes, files and folders in [file] (a folder counts itself). */
    suspend fun measure(file: File): Measure {
        if (!file.isDirectory || isLink(file)) return Measure(if (file.isFile) file.length() else 0, 1, 0)
        var bytes = 0L
        var files = 0
        var folders = 1
        val stack = ArrayDeque<File>().apply { add(file) }
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val dir = stack.removeLast()
            for (child in dir.listFiles().orEmpty()) {
                if (child.isDirectory && !isLink(child)) {
                    folders++
                    stack.add(child)
                } else {
                    files++
                    bytes += child.length()
                }
            }
        }
        return Measure(bytes, files, folders)
    }

    /** The SHA-256 of [file] as lower case hex. */
    suspend fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun isLink(file: File): Boolean = runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)

    /** Whether [file] is [folder] or somewhere inside it. */
    fun isInside(file: File, folder: File): Boolean {
        val f = file.absolutePath.trimEnd('/')
        val d = folder.absolutePath.trimEnd('/')
        return f == d || f.startsWith("$d/")
    }

    private class ProgressCounter(private val onProgress: (Long, String) -> Unit) {
        private var bytes = 0L
        fun add(count: Long, name: String) {
            bytes += count
            onProgress(bytes, name)
        }
    }
}
