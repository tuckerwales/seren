package wales.tucker.seren.ssh.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException
import com.jcraft.jsch.SftpProgressMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Progress monitor that swallows callback failures and stops when the job is cancelled. */
internal fun safeProgressMonitor(
    total: Long,
    job: Job,
    onProgress: (Long, Long) -> Unit,
): SftpProgressMonitor = object : SftpProgressMonitor {
    private var transferred = 0L
    override fun init(op: Int, src: String?, dest: String?, max: Long) {
        runCatching { onProgress(0, if (total > 0) total else max) }
    }

    override fun count(count: Long): Boolean {
        transferred += count
        runCatching { onProgress(transferred, total) }
        return job.isActive
    }

    override fun end() {}
}

data class RemoteFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isLink: Boolean,
    val size: Long,
    val modifiedAt: Long,
    val permissions: String,
)

/**
 * Coroutine-friendly wrapper over a JSch SFTP channel.
 *
 * JSch delivers channel data through a [java.io.PipedInputStream] that tracks a single reader
 * thread. Running ops on [Dispatchers.IO]'s pool lets that reader die between calls, so the
 * session thread's next write hits "Read end dead", disconnects the channel, and under load can
 * stall the whole session until the TCP connection drops ("End of IO Stream Read"). Every call
 * therefore runs on one dedicated thread for this client.
 */
class SftpClient(private val channel: ChannelSftp) {
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "sftp-${System.identityHashCode(this)}").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val closed = AtomicBoolean(false)

    val isConnected: Boolean get() = !closed.get() && channel.isConnected && !channel.isClosed

    private suspend fun <T> io(block: ChannelSftp.() -> T): T {
        if (closed.get()) throw java.io.IOException("SFTP channel is closed")
        return withContext(dispatcher) { channel.block() }
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
        try {
            io {
                val size = runCatching { stat(path).size }.getOrDefault(-1L)
                get(path, out, monitor(size, job, onProgress))
            }
        } catch (e: SftpException) {
            // Returning false from the progress monitor ends the transfer with this id.
            if (e.id == ChannelSftp.SSH_FX_FAILURE && !job.isActive) throw kotlinx.coroutines.CancellationException("Transfer cancelled", e)
            throw e
        }
        job.ensureActive()
    }

    /**
     * Uploads [input] to [path]. Cancelling the calling coroutine stops the transfer and leaves a
     * partial file behind, for the caller to remove.
     */
    suspend fun upload(input: InputStream, path: String, size: Long, onProgress: (Long, Long) -> Unit) {
        val job = currentCoroutineContext().job
        try {
            io { put(input, path, monitor(size, job, onProgress), ChannelSftp.OVERWRITE) }
        } catch (e: SftpException) {
            if (e.id == ChannelSftp.SSH_FX_FAILURE && !job.isActive) throw kotlinx.coroutines.CancellationException("Transfer cancelled", e)
            throw e
        }
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
        if (!closed.compareAndSet(false, true)) return
        // Disconnect first so an in-flight put/get fails out of the dedicated thread, then stop
        // that thread. Doing this on the caller avoids deadlocking if close() is itself invoked
        // from an SFTP op.
        runCatching { channel.disconnect() }
        dispatcher.close()
        executor.shutdownNow()
    }

    /**
     * Reports progress and stops the transfer once [job] is cancelled. Callbacks must never throw:
     * an exception here aborts mid-protocol without closing the remote handle, which can desync
     * the channel and take down the SSH session.
     */
    private fun monitor(total: Long, job: Job, onProgress: (Long, Long) -> Unit) =
        safeProgressMonitor(total, job, onProgress)

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
