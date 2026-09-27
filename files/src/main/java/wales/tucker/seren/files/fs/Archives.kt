package wales.tucker.seren.files.fs

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Compressing to and extracting from zip files, the one archive format every device can open. */
object Archives {
    private const val BUFFER = 64 * 1024

    fun canExtract(name: String): Boolean = FileTypes.extension(name) in setOf("zip", "jar", "apks", "xapk")

    /**
     * Compresses [sources] into a new zip file [target], with each source at the top level of the
     * archive. [onProgress] gets the bytes read so far. A cancelled or failed zip is deleted.
     */
    suspend fun compress(sources: List<File>, target: File, onProgress: (Long) -> Unit = {}) {
        if (target.exists()) throw IOException("${target.name} already exists")
        var bytes = 0L
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { zip ->
                val buffer = ByteArray(BUFFER)
                suspend fun add(file: File, path: String) {
                    currentCoroutineContext().ensureActive()
                    if (file.isDirectory && !FileOps.isLink(file)) {
                        zip.putNextEntry(ZipEntry("$path/").apply { time = file.lastModified() })
                        zip.closeEntry()
                        for (child in file.listFiles().orEmpty().sortedBy { it.name }) add(child, "$path/${child.name}")
                    } else if (file.isFile) {
                        zip.putNextEntry(ZipEntry(path).apply { time = file.lastModified() })
                        FileInputStream(file).use { input ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                zip.write(buffer, 0, read)
                                bytes += read
                                onProgress(bytes)
                            }
                        }
                        zip.closeEntry()
                    }
                }
                for (source in sources) add(source, source.name)
            }
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
    }

    /** The total uncompressed size of [zip], for progress, or 0 when the archive doesn't say. */
    fun uncompressedSize(zip: File): Long = runCatching {
        ZipFile(zip).use { z -> z.entries().asSequence().sumOf { it.size.coerceAtLeast(0) } }
    }.getOrDefault(0L)

    /**
     * Extracts [zip] into a new folder [target]. Entries that would land outside it (a "zip slip")
     * are refused. A cancelled or failed extraction removes the folder again.
     */
    suspend fun extract(zip: File, target: File, onProgress: (Long) -> Unit = {}) {
        if (target.exists()) throw IOException("${target.name} already exists")
        if (!target.mkdirs()) throw IOException("Couldn't create ${target.name}")
        val root = target.canonicalPath
        var bytes = 0L
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(zip))).use { input ->
                val buffer = ByteArray(BUFFER)
                var entry = input.nextEntry
                if (entry == null) throw IOException("${zip.name} is empty or isn't a zip file")
                while (entry != null) {
                    currentCoroutineContext().ensureActive()
                    val out = File(target, entry.name)
                    val path = out.canonicalPath
                    if (path != root && !path.startsWith("$root/")) {
                        throw IOException("${zip.name} tries to put files outside the folder, so it wasn't extracted")
                    }
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { output ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                bytes += read
                                onProgress(bytes)
                            }
                        }
                    }
                    if (entry.time > 0) out.setLastModified(entry.time)
                    input.closeEntry()
                    entry = input.nextEntry
                }
            }
        } catch (e: Throwable) {
            FileOps.deleteRecursively(target)
            if (e is java.util.zip.ZipException) throw IOException("${zip.name} is damaged or isn't a zip file")
            throw e
        }
    }

    /** "photos.zip" becomes "photos"; a folder name for extracting into. */
    fun folderNameFor(zip: File): String {
        val name = zip.name
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else "$name contents"
    }
}
