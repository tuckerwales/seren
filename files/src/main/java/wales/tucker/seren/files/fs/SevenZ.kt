package wales.tucker.seren.files.fs

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.MemoryLimitException
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Extracting 7z files, which Commons Compress reads with XZ for Java doing the LZMA. */
internal object SevenZ {
    private const val BUFFER = 64 * 1024

    /** Unix's "this is a link" bits, which 7z keeps above the Windows attributes. */
    private const val UNIX_TYPE_MASK = 0xF000
    private const val UNIX_LINK = 0xA000
    private const val WINDOWS_REPARSE_POINT = 0x400

    fun open(archive: File, password: String?): SevenZFile = SevenZFile.builder()
        .setFile(archive)
        .setMaxMemoryLimitKiB(Archives.memoryLimitKb())
        .setUseDefaultNameForUnnamedEntries(true)
        .apply { if (password != null) setPassword(password.toCharArray()) }
        .get()

    suspend fun extract(archive: File, target: File, password: String?, onProgress: (Long) -> Unit) {
        if (target.exists()) throw IOException("${target.name} already exists")
        if (!target.mkdirs()) throw IOException("Couldn't create ${target.name}")
        val root = target.canonicalPath
        var bytes = 0L
        try {
            open(archive, password).use { z ->
                val buffer = ByteArray(BUFFER)
                var entries = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = z.nextEntry ?: break
                    entries++
                    // An "anti item" marks something deleted in an update; links could point anywhere.
                    if (entry.isAntiItem || isLink(entry)) continue
                    val out = File(target, entry.name.replace('\\', '/').trimStart('/'))
                    Archives.checkInside(out, root, archive)
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { output ->
                            if (entry.hasStream()) {
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val read = z.read(buffer)
                                    if (read < 0) break
                                    output.write(buffer, 0, read)
                                    bytes += read
                                    onProgress(bytes)
                                }
                            }
                        }
                    }
                    if (entry.hasLastModifiedDate) out.setLastModified(entry.lastModifiedDate.time)
                }
                if (entries == 0) throw IOException("${archive.name} is empty")
            }
        } catch (e: Throwable) {
            FileOps.deleteRecursively(target)
            throw explain(e, archive, password)
        }
    }

    fun isLink(entry: SevenZArchiveEntry): Boolean {
        if (!entry.hasWindowsAttributes) return false
        val attributes = entry.windowsAttributes
        return (attributes ushr 16) and UNIX_TYPE_MASK == UNIX_LINK || attributes and WINDOWS_REPARSE_POINT != 0
    }

    /** Turns what went wrong into something to tell people, leaving cancelling alone. */
    private fun explain(e: Throwable, archive: File, password: String?): Throwable = when {
        e is kotlinx.coroutines.CancellationException -> e
        // Seren Files' own reasons, already worded for people.
        e is IOException && e.message?.let { it.endsWith("already exists") || it.contains("outside the folder") || it.endsWith("is empty") } == true -> e
        e is PasswordRequiredException -> PasswordNeededException(archive, wrong = false)
        e is MemoryLimitException || e is org.tukaani.xz.MemoryLimitException || e is OutOfMemoryError -> Archives.tooBig(archive)
        e is IOException && e.message?.contains("Unsupported compression method") == true ->
            IOException("${archive.name} uses a kind of compression Seren Files can't extract")
        // Encrypted data that won't decode almost always means the password is wrong.
        password != null && (e is IOException || e is IllegalArgumentException || e is IllegalStateException) ->
            PasswordNeededException(archive, wrong = true)
        e is IOException || e is IllegalArgumentException || e is IllegalStateException ->
            IOException("${archive.name} is damaged or isn't a 7z file")
        else -> e
    }
}
