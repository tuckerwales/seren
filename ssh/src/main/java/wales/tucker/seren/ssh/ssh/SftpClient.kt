package wales.tucker.seren.ssh.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpProgressMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

data class RemoteFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isLink: Boolean,
    val size: Long,
    val modifiedAt: Long,
    val permissions: String,
)

/** Coroutine friendly wrapper over a JSch SFTP channel. Operations are serialized. */
class SftpClient(private val channel: ChannelSftp) {
    private val mutex = Mutex()

    val isConnected: Boolean get() = channel.isConnected && !channel.isClosed

    private suspend fun <T> io(block: ChannelSftp.() -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { channel.block() }
    }

    suspend fun home(): String = io { home }

    suspend fun realPath(path: String): String = io { realpath(path) }

    suspend fun list(path: String): List<RemoteFile> = io {
        val entries = ls(path)
        entries.mapNotNull { e ->
            val entry = e as ChannelSftp.LsEntry
            if (entry.filename == "." || entry.filename == "..") return@mapNotNull null
            val full = join(path, entry.filename)
            var attrs = entry.attrs
            var isDir = attrs.isDir
            if (attrs.isLink) {
                // Resolve symlinks so linked directories can be browsed.
                runCatching { stat(full) }.getOrNull()?.let {
                    isDir = it.isDir
                    attrs = if (it.isDir) attrs else it
                }
            }
            entry.toRemote(full, attrs, isDir)
        }.sortedWith(compareBy<RemoteFile> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    suspend fun stat(path: String): RemoteFile = io {
        val a = stat(path)
        RemoteFile(path.substringAfterLast('/'), path, a.isDir, a.isLink, a.size, a.mTime * 1000L, a.permissionsString)
    }

    suspend fun mkdir(path: String) = io { mkdir(path) }

    suspend fun rename(from: String, to: String) = io { rename(from, to) }

    suspend fun delete(file: RemoteFile) = io {
        if (file.isDirectory && !file.isLink) deleteRecursive(this, file.path) else rm(file.path)
    }

    private fun deleteRecursive(ch: ChannelSftp, path: String) {
        for (e in ch.ls(path)) {
            val entry = e as ChannelSftp.LsEntry
            if (entry.filename == "." || entry.filename == "..") continue
            val child = join(path, entry.filename)
            if (entry.attrs.isDir && !entry.attrs.isLink) deleteRecursive(ch, child) else ch.rm(child)
        }
        ch.rmdir(path)
    }

    suspend fun chmod(path: String, mode: Int) = io { chmod(mode, path) }

    /** Downloads [path] into [out]. Cancelling the calling coroutine stops the transfer. */
    suspend fun download(path: String, out: OutputStream, onProgress: (Long, Long) -> Unit) {
        val job = currentCoroutineContext().job
        io {
            val size = runCatching { stat(path).size }.getOrDefault(-1L)
            get(path, out, monitor(size, job, onProgress))
        }
        job.ensureActive()
    }

    /**
     * Uploads [input] to [path]. Cancelling the calling coroutine stops the transfer and leaves a
     * partial file behind, for the caller to remove.
     */
    suspend fun upload(input: InputStream, path: String, size: Long, onProgress: (Long, Long) -> Unit) {
        val job = currentCoroutineContext().job
        io { put(input, path, monitor(size, job, onProgress), ChannelSftp.OVERWRITE) }
        job.ensureActive()
    }

    suspend fun readText(path: String, maxBytes: Int = 1_000_000): String = io {
        get(path).use { stream ->
            val bytes = stream.readNBytesCompat(maxBytes)
            String(bytes, Charsets.UTF_8)
        }
    }

    suspend fun writeText(path: String, text: String) = io {
        put(text.byteInputStream(Charsets.UTF_8), path, ChannelSftp.OVERWRITE)
    }

    fun close() {
        runCatching { channel.disconnect() }
    }

    /** Reports progress, and stops the transfer once [job] is cancelled. */
    private fun monitor(total: Long, job: Job, onProgress: (Long, Long) -> Unit) = object : SftpProgressMonitor {
        private var transferred = 0L
        override fun init(op: Int, src: String?, dest: String?, max: Long) {
            onProgress(0, if (total > 0) total else max)
        }

        override fun count(count: Long): Boolean {
            transferred += count
            onProgress(transferred, total)
            return job.isActive
        }

        override fun end() {}
    }

    private fun ChannelSftp.LsEntry.toRemote(full: String, attrs: SftpATTRS, isDir: Boolean) = RemoteFile(
        name = filename,
        path = full,
        isDirectory = isDir,
        isLink = this.attrs.isLink,
        size = attrs.size,
        modifiedAt = attrs.mTime * 1000L,
        permissions = attrs.permissionsString,
    )

    companion object {
        fun join(dir: String, name: String): String = if (dir.endsWith("/")) dir + name else "$dir/$name"

        fun parent(path: String): String {
            if (path == "/" || path.isEmpty()) return "/"
            val trimmed = path.trimEnd('/')
            val idx = trimmed.lastIndexOf('/')
            return if (idx <= 0) "/" else trimmed.substring(0, idx)
        }

        private fun InputStream.readNBytesCompat(max: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (total < max) {
                val n = read(buf, 0, minOf(buf.size, max - total))
                if (n < 0) break
                out.write(buf, 0, n)
                total += n
            }
            return out.toByteArray()
        }
    }
}
