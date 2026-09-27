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
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.tukaani.xz.XZInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** The archives Seren Files can extract, with the name people know each by. */
enum class ArchiveFormat(val label: String, internal val suffixes: List<String>) {
    ZIP("zip", emptyList()),
    SEVEN_Z("7z", listOf(".7z")),
    TAR("tar", listOf(".tar")),
    TAR_GZ("tar.gz", listOf(".tar.gz", ".tgz")),
    TAR_XZ("tar.xz", listOf(".tar.xz", ".txz")),
    TAR_BZ2("tar.bz2", listOf(".tar.bz2", ".tbz2", ".tbz")),

    /** One compressed file, such as "notes.txt.gz", which becomes "notes.txt" rather than a folder. */
    GZIP("gz", listOf(".gz")),
    XZ("xz", listOf(".xz")),
    BZIP2("bz2", listOf(".bz2")),
    ;

    /** Whether this is one compressed file rather than an archive of several. */
    val single: Boolean get() = this == GZIP || this == XZ || this == BZIP2
}

/** Extracting an archive that needs a password, when none or the wrong one was given. */
class PasswordNeededException(val archive: File, val wrong: Boolean) :
    IOException(if (wrong) "That password doesn't open ${archive.name}" else "${archive.name} needs a password")

/**
 * Compressing to zip, the one archive format every device can open, and extracting zip, 7z, tar
 * (plain, gz, xz and bz2) and single gz, xz and bz2 files.
 */
object Archives {
    private const val BUFFER = 64 * 1024

    /** Which kind of archive [name] is, going by its name, or null when it isn't one. */
    fun format(name: String): ArchiveFormat? {
        val lower = name.lowercase(java.util.Locale.ROOT)
        // Longest suffix first, so "backup.tar.gz" is a tar.gz rather than one gzipped file.
        val bySuffix = ArchiveFormat.entries
            .flatMap { f -> f.suffixes.map { it to f } }
            .sortedByDescending { it.first.length }
            .firstOrNull { (suffix, _) -> lower.endsWith(suffix) && lower.length > suffix.length }
            ?.second
        return bySuffix ?: ArchiveFormat.ZIP.takeIf { FileTypes.extension(name) in setOf("zip", "jar", "apks", "xapk") }
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
        // 7z lists its files' sizes up front, unless the list itself is encrypted.
        ArchiveFormat.SEVEN_Z -> runCatching {
            SevenZ.open(archive, null).use { z -> z.entries.sumOf { if (it.isDirectory || it.isAntiItem || SevenZ.isLink(it)) 0L else it.size.coerceAtLeast(0) } }
        }.getOrDefault(0L)
        null -> 0L
        else -> archive.length()
    }

    /**
     * Extracts [archive] to [target]: a new folder for zip and tar files, or a new file for a
     * single compressed file. Entries that would land outside the folder (a "zip slip") are
     * refused, and links inside archives are skipped. A cancelled or failed extraction removes
     * what it made. A 7z file protected with a [password] throws [PasswordNeededException] when
     * it's missing or wrong.
     */
    suspend fun extract(archive: File, target: File, password: String? = null, onProgress: (Long) -> Unit = {}) {
        when (val format = format(archive.name)) {
            ArchiveFormat.SEVEN_Z -> SevenZ.extract(archive, target, password, onProgress)
            ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_XZ, ArchiveFormat.TAR_BZ2 -> extractTar(archive, target, format, onProgress)
            ArchiveFormat.GZIP, ArchiveFormat.XZ, ArchiveFormat.BZIP2 -> decompress(archive, target, format, onProgress)
            // Zip files don't always have the right name (an .apk is one), so try anything else as a zip.
            ArchiveFormat.ZIP, null -> extractZip(archive, target, onProgress)
        }
    }

    /** Reads what's compressed inside [input], for the tar formats and single compressed files. */
    private fun decompressing(input: InputStream, format: ArchiveFormat): InputStream = when (format) {
        ArchiveFormat.TAR_GZ, ArchiveFormat.GZIP -> GZIPInputStream(input, BUFFER)
        ArchiveFormat.TAR_XZ, ArchiveFormat.XZ -> XZInputStream(input, memoryLimitKb())
        ArchiveFormat.TAR_BZ2, ArchiveFormat.BZIP2 -> BZip2CompressorInputStream(input, true)
        else -> input
    }

    /** Whether [e] means the data didn't decompress: a damaged or mislabelled file. */
    private fun isBadData(e: Throwable) = e is java.util.zip.ZipException || e is java.io.EOFException || e is TarReader.BadTar ||
        e is org.tukaani.xz.XZIOException || e is org.tukaani.xz.CorruptedInputException ||
        (e is IOException && e.message?.let { it.contains("Stream is not in the BZip2 format") || it.contains("crc error", ignoreCase = true) } == true)

    /** Half of what the app may use, the most a decoder may ask for before it's refused rather than crashing. */
    internal fun memoryLimitKb(): Int = (Runtime.getRuntime().maxMemory() / 2 / 1024).coerceIn(16L * 1024, Int.MAX_VALUE.toLong()).toInt()

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

    private suspend fun extractTar(archive: File, target: File, format: ArchiveFormat, onProgress: (Long) -> Unit) {
        if (target.exists()) throw IOException("${target.name} already exists")
        if (!target.mkdirs()) throw IOException("Couldn't create ${target.name}")
        val root = target.canonicalPath
        try {
            val counted = CountingInputStream(BufferedInputStream(FileInputStream(archive), BUFFER), onProgress)
            decompressing(counted, format).use {
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
            if (e is org.tukaani.xz.MemoryLimitException) throw tooBig(archive)
            if (isBadData(e)) throw IOException("${archive.name} is damaged or isn't a ${format.label} file")
            throw e
        }
    }

    private suspend fun decompress(archive: File, target: File, format: ArchiveFormat, onProgress: (Long) -> Unit) {
        if (target.exists()) throw IOException("${target.name} already exists")
        try {
            decompressing(CountingInputStream(BufferedInputStream(FileInputStream(archive), BUFFER), onProgress), format).use { input ->
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
            if (e is org.tukaani.xz.MemoryLimitException) throw tooBig(archive)
            if (isBadData(e)) throw IOException("${archive.name} is damaged or isn't a ${format.label} file")
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
        val suffix = ArchiveFormat.entries.flatMap { it.suffixes }.sortedByDescending { it.length }
            .firstOrNull { lower.endsWith(it) && name.length > it.length }
        if (suffix != null) return name.dropLast(suffix.length)
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else "$name contents"
    }

    /** Whether extracting [archive] makes a single file rather than a folder. */
    fun extractsToFile(archive: File): Boolean = format(archive.name)?.single == true

    internal fun tooBig(archive: File) = IOException("${archive.name} needs more memory to extract than this device can give it")

    /** Refuses [out] if it's outside [root], the folder being extracted into. */
    internal fun checkInside(out: File, root: String, archive: File) {
        val path = out.canonicalPath
        if (path != root && !path.startsWith("$root/")) {
            throw IOException("${archive.name} tries to put files outside the folder, so it wasn't extracted")
        }
    }

    /** Passes on how many bytes have been read so far, for progress through compressed archives. */
    internal class CountingInputStream(input: InputStream, private val onProgress: (Long) -> Unit) : FilterInputStream(input) {
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
