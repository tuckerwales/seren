package wales.tucker.seren.files.fs

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** The archives Seren Files can extract. */
enum class ArchiveFormat {
    ZIP,
    TAR,
    TAR_GZ,

    /** One compressed file, such as "notes.txt.gz", which becomes "notes.txt" rather than a folder. */
    GZIP,
}

/**
 * Compressing to zip, the one archive format every device can open, and extracting zip, tar,
 * tar.gz and gz files.
 */
object Archives {
    private const val BUFFER = 64 * 1024

    /** Which kind of archive [name] is, going by its name, or null when it isn't one. */
    fun format(name: String): ArchiveFormat? {
        val lower = name.lowercase(java.util.Locale.ROOT)
        return when {
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> ArchiveFormat.TAR_GZ
            lower.endsWith(".tar") -> ArchiveFormat.TAR
            lower.endsWith(".gz") && lower.length > 3 -> ArchiveFormat.GZIP
            FileTypes.extension(name) in setOf("zip", "jar", "apks", "xapk") -> ArchiveFormat.ZIP
            else -> null
        }
    }

    fun canExtract(name: String): Boolean = format(name) != null

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

    /**
     * How many bytes extracting [archive] will report as it goes, for progress: a zip's
     * uncompressed size, or the archive's own size for the others (their progress counts the bytes
     * read from it). 0 when it can't be told.
     */
    fun progressTotal(archive: File): Long = when (format(archive.name)) {
        ArchiveFormat.ZIP -> runCatching {
            ZipFile(archive).use { z -> z.entries().asSequence().sumOf { it.size.coerceAtLeast(0) } }
        }.getOrDefault(0L)
        null -> 0L
        else -> archive.length()
    }

    /**
     * Extracts [archive] to [target]: a new folder for zip and tar files, or a new file for a
     * single gzipped file. Entries that would land outside the folder (a "zip slip") are refused,
     * and links inside tar files are skipped. A cancelled or failed extraction removes what it made.
     */
    suspend fun extract(archive: File, target: File, onProgress: (Long) -> Unit = {}) {
        when (format(archive.name)) {
            ArchiveFormat.TAR -> extractTar(archive, target, gzipped = false, onProgress)
            ArchiveFormat.TAR_GZ -> extractTar(archive, target, gzipped = true, onProgress)
            ArchiveFormat.GZIP -> gunzip(archive, target, onProgress)
            // Zip files don't always have the right name (an .apk is one), so try anything else as a zip.
            else -> extractZip(archive, target, onProgress)
        }
    }

    private suspend fun extractZip(zip: File, target: File, onProgress: (Long) -> Unit) {
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

    private suspend fun extractTar(archive: File, target: File, gzipped: Boolean, onProgress: (Long) -> Unit) {
        if (target.exists()) throw IOException("${target.name} already exists")
        if (!target.mkdirs()) throw IOException("Couldn't create ${target.name}")
        val root = target.canonicalPath
        try {
            val counted = CountingInputStream(BufferedInputStream(FileInputStream(archive), BUFFER), onProgress)
            val input: InputStream = if (gzipped) GZIPInputStream(counted, BUFFER) else counted
            input.use {
                val tar = TarReader(it)
                var entries = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = tar.next() ?: break
                    entries++
                    val out = File(target, entry.name)
                    val path = out.canonicalPath
                    if (path != root && !path.startsWith("$root/")) {
                        throw IOException("${archive.name} tries to put files outside the folder, so it wasn't extracted")
                    }
                    when (entry.type) {
                        TarReader.Type.FOLDER -> out.mkdirs()
                        TarReader.Type.FILE -> {
                            out.parentFile?.mkdirs()
                            FileOutputStream(out).use { output ->
                                val buffer = ByteArray(BUFFER)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val read = tar.read(buffer)
                                    if (read < 0) break
                                    output.write(buffer, 0, read)
                                }
                            }
                        }
                        // Links could point anywhere on the device, so they're left out.
                        TarReader.Type.OTHER -> continue
                    }
                    if (entry.modified > 0) out.setLastModified(entry.modified)
                }
                if (entries == 0) throw IOException("${archive.name} is empty or isn't a tar file")
            }
        } catch (e: Throwable) {
            FileOps.deleteRecursively(target)
            if (e is java.util.zip.ZipException || e is TarReader.BadTar || e is java.io.EOFException) {
                throw IOException("${archive.name} is damaged or isn't a ${if (gzipped) "tar.gz" else "tar"} file")
            }
            throw e
        }
    }

    private suspend fun gunzip(archive: File, target: File, onProgress: (Long) -> Unit) {
        if (target.exists()) throw IOException("${target.name} already exists")
        try {
            GZIPInputStream(CountingInputStream(BufferedInputStream(FileInputStream(archive), BUFFER), onProgress), BUFFER).use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: Throwable) {
            target.delete()
            if (e is java.util.zip.ZipException || e is java.io.EOFException) throw IOException("${archive.name} is damaged or isn't a gz file")
            throw e
        }
    }

    /**
     * What extracting [archive] makes: "photos.zip" and "photos.tar.gz" become a folder called
     * "photos", and "notes.txt.gz" becomes the file "notes.txt".
     */
    fun extractedName(archive: File): String {
        val name = archive.name
        val lower = name.lowercase(java.util.Locale.ROOT)
        val suffix = listOf(".tar.gz", ".tgz", ".tar", ".gz").firstOrNull { lower.endsWith(it) && name.length > it.length }
        if (suffix != null) return name.dropLast(suffix.length)
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else "$name contents"
    }

    /** Whether extracting [archive] makes a single file rather than a folder. */
    fun extractsToFile(archive: File): Boolean = format(archive.name) == ArchiveFormat.GZIP

    /** Passes on how many bytes have been read so far, for progress through compressed archives. */
    private class CountingInputStream(input: InputStream, private val onProgress: (Long) -> Unit) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int = super.read().also { if (it >= 0) onProgress(++count) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also {
            if (it > 0) {
                count += it
                onProgress(count)
            }
        }

        override fun skip(n: Long): Long = super.skip(n).also {
            count += it
            onProgress(count)
        }
    }
}

/**
 * Reads tar archives: the ustar format, with GNU long names and pax headers, which covers the tar
 * files people come across. Only plain files and folders are extracted.
 */
internal class TarReader(private val input: InputStream) {
    enum class Type { FILE, FOLDER, OTHER }

    data class Entry(val name: String, val type: Type, val size: Long, val modified: Long)

    class BadTar(message: String) : IOException(message)

    private val header = ByteArray(BLOCK)

    /** Bytes of the current entry not yet read, and the padding after it. */
    private var remaining = 0L
    private var padding = 0L

    /** The next entry, or null at the end of the archive. Skips whatever's left of the last one. */
    fun next(): Entry? {
        skipFully(remaining + padding)
        remaining = 0
        padding = 0
        var longName: String? = null
        var paxPath: String? = null
        while (true) {
            if (!readBlock()) return null
            if (header.all { it == 0.toByte() }) return null
            if (!checksumMatches()) throw BadTar("Bad header checksum")
            val size = number(124, 12)
            if (size < 0) throw BadTar("Bad size")
            val flag = header[156].toInt().toChar()
            when (flag) {
                // A GNU long name, or pax extended headers, for the entry that follows.
                'L' -> longName = String(readData(size), Charsets.UTF_8).trimEnd('\u0000')
                'x' -> paxPath = paxHeaders(String(readData(size), Charsets.UTF_8))["path"] ?: paxPath
                'g' -> readData(size)
                else -> {
                    val name = longName ?: paxPath ?: headerName()
                    val type = when {
                        flag == '5' -> Type.FOLDER
                        (flag == '0' || flag == '\u0000' || flag == '7') && name.endsWith("/") -> Type.FOLDER
                        flag == '0' || flag == '\u0000' || flag == '7' -> Type.FILE
                        else -> Type.OTHER
                    }
                    // Folders and links have no data, whatever their size field says.
                    val data = if (flag == '1' || flag == '2' || flag == '5') 0 else size
                    remaining = data
                    padding = (BLOCK - data % BLOCK) % BLOCK
                    val cleaned = name.trimStart('/').trimEnd('/').removePrefix("./")
                    if (cleaned.isEmpty() || cleaned == ".") {
                        skipFully(remaining + padding)
                        remaining = 0
                        padding = 0
                        longName = null
                        paxPath = null
                        continue
                    }
                    return Entry(cleaned, type, data, number(136, 12) * 1000)
                }
            }
        }
    }

    /** Reads from the current entry's data; -1 at its end. */
    fun read(buffer: ByteArray): Int {
        if (remaining <= 0) return -1
        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (read < 0) throw java.io.EOFException("The archive ends too soon")
        remaining -= read
        return read
    }

    private fun headerName(): String {
        val name = string(0, 100)
        val magic = string(257, 6)
        val prefix = if (magic.startsWith("ustar")) string(345, 155) else ""
        return if (prefix.isEmpty()) name else "$prefix/$name"
    }

    private fun string(offset: Int, length: Int): String {
        var end = offset
        while (end < offset + length && header[end] != 0.toByte()) end++
        return String(header, offset, end - offset, Charsets.UTF_8)
    }

    /** An octal number, or a big-endian binary one when the first byte has its high bit set (GNU). */
    private fun number(offset: Int, length: Int): Long {
        if (header[offset].toInt() and 0x80 != 0) {
            var value = 0L
            for (i in offset + 1 until offset + length) value = (value shl 8) or (header[i].toLong() and 0xFF)
            return value
        }
        val text = string(offset, length).trim()
        if (text.isEmpty()) return 0
        return text.toLongOrNull(8) ?: throw BadTar("Bad number")
    }

    private fun checksumMatches(): Boolean {
        val stored = number(148, 8)
        var sum = 0L
        for (i in 0 until BLOCK) sum += if (i in 148 until 156) 32 else header[i].toLong() and 0xFF
        return sum == stored
    }

    private fun readBlock(): Boolean {
        var read = 0
        while (read < BLOCK) {
            val n = input.read(header, read, BLOCK - read)
            if (n < 0) {
                if (read == 0) return false
                throw java.io.EOFException("The archive ends too soon")
            }
            read += n
        }
        return true
    }

    private fun readData(size: Long): ByteArray {
        if (size > MAX_HEADER_DATA) throw BadTar("Header too large")
        val data = ByteArray(size.toInt())
        var read = 0
        while (read < data.size) {
            val n = input.read(data, read, data.size - read)
            if (n < 0) throw java.io.EOFException("The archive ends too soon")
            read += n
        }
        skipFully((BLOCK - size % BLOCK) % BLOCK)
        return data
    }

    private fun skipFully(count: Long) {
        var left = count
        val scratch = ByteArray(BLOCK)
        while (left > 0) {
            val n = input.read(scratch, 0, minOf(left, BLOCK.toLong()).toInt())
            if (n < 0) throw java.io.EOFException("The archive ends too soon")
            left -= n
        }
    }

    companion object {
        const val BLOCK = 512
        private const val MAX_HEADER_DATA = 1L shl 20

        /** Pax records look like "30 path=some/long/name.txt\n", each starting with its own length. */
        fun paxHeaders(text: String): Map<String, String> {
            val out = mutableMapOf<String, String>()
            var at = 0
            val bytes = text.toByteArray(Charsets.UTF_8)
            while (at < bytes.size) {
                val space = (at until bytes.size).firstOrNull { bytes[it] == ' '.code.toByte() } ?: break
                val length = String(bytes, at, space - at, Charsets.US_ASCII).toIntOrNull() ?: break
                if (length <= 0 || at + length > bytes.size) break
                val record = String(bytes, space + 1, at + length - space - 2, Charsets.UTF_8)
                val eq = record.indexOf('=')
                if (eq > 0) out[record.substring(0, eq)] = record.substring(eq + 1)
                at += length
            }
            return out
        }
    }
}
