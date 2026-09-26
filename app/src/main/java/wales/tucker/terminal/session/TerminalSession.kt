package wales.tucker.terminal.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import wales.tucker.terminal.data.PortForward
import wales.tucker.terminal.emulator.KeyEncoder
import wales.tucker.terminal.emulator.TerminalClient
import wales.tucker.terminal.emulator.TerminalEmulator
import wales.tucker.terminal.emulator.TerminalKey
import wales.tucker.terminal.ssh.ConnectionTarget
import wales.tucker.terminal.ssh.ConnectionUi
import wales.tucker.terminal.ssh.HostKeyRequest
import wales.tucker.terminal.ssh.KeyboardInteractivePrompt
import wales.tucker.terminal.ssh.PasswordResponse
import wales.tucker.terminal.ssh.SftpClient
import wales.tucker.terminal.ssh.ShellChannel
import wales.tucker.terminal.ssh.SshConnection
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

sealed interface SessionState {
    data object Connecting : SessionState
    data object Connected : SessionState
    data class Disconnected(val reason: String) : SessionState
    data class Failed(val error: String) : SessionState
}

/** A question the connection is waiting on the user to answer. */
sealed class SessionPrompt {
    abstract fun cancel()

    class HostKey(val request: HostKeyRequest, private val answer: CompletableDeferred<Boolean>) : SessionPrompt() {
        fun respond(accept: Boolean) = answer.complete(accept)
        override fun cancel() {
            answer.complete(false)
        }
    }

    class Password(
        val target: String,
        val message: String,
        val canRemember: Boolean,
        private val answer: CompletableDeferred<PasswordResponse?>,
    ) : SessionPrompt() {
        fun respond(response: PasswordResponse?) = answer.complete(response)
        override fun cancel() {
            answer.complete(null)
        }
    }

    class KeyboardInteractive(
        val target: String,
        val name: String,
        val instruction: String,
        val prompts: List<KeyboardInteractivePrompt>,
        private val answer: CompletableDeferred<List<String>?>,
    ) : SessionPrompt() {
        fun respond(response: List<String>?) = answer.complete(response)
        override fun cancel() {
            answer.complete(null)
        }
    }
}

/** Everything a session needs to (re)connect. */
data class SessionSpec(
    val hostId: Long,
    val title: String,
    val subtitle: String,
    val color: Int,
    val target: ConnectionTarget,
    val jump: ConnectionTarget?,
    val forwards: List<PortForward>,
    val startupCommand: String,
    val colorSchemeId: String?,
)

/**
 * One SSH terminal session: owns the connection, the shell channel and the terminal emulator.
 * Survives activity recreation; lives in [SessionManager].
 */
class TerminalSession(
    val id: Int,
    val spec: SessionSpec,
    private val connectionFactory: (ConnectionUi) -> SshConnection,
    private val scope: CoroutineScope,
    scrollback: Int,
    private val onPasswordRemembered: (String) -> Unit,
    private val onConnected: () -> Unit,
) : TerminalClient {

    val emulator = TerminalEmulator(80, 24, scrollback, this)

    private val _state = MutableStateFlow<SessionState>(SessionState.Connecting)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _title = MutableStateFlow(spec.title)
    val title: StateFlow<String> = _title.asStateFlow()

    private val _prompt = MutableStateFlow<SessionPrompt?>(null)
    val prompt: StateFlow<SessionPrompt?> = _prompt.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    /** Color scheme currently applied to [emulator], so re-entering the screen keeps OSC changes. */
    @Volatile
    var appliedSchemeId: String? = null

    /** Called (from any thread) whenever the screen content changed. */
    @Volatile
    var screenListener: (() -> Unit)? = null

    private var connection: SshConnection? = null
    private var shell: ShellChannel? = null
    private var writer: ExecutorService = newWriter()
    private var connectJob: Job? = null
    private val generation = AtomicInteger(0)

    @Volatile
    private var cols = 80

    @Volatile
    private var rows = 24

    @Volatile
    private var widthPx = 0

    @Volatile
    private var heightPx = 0

    private val sftpLock = Any()
    private var sftp: SftpClient? = null

    val isConnected: Boolean get() = _state.value == SessionState.Connected

    private fun newWriter(): ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, "ssh-writer-$id").apply { isDaemon = true } }

    fun connect() {
        if (connectJob?.isActive == true) return
        val gen = generation.incrementAndGet()
        _state.value = SessionState.Connecting
        _log.value = emptyList()
        connectJob = scope.launch(Dispatchers.IO) {
            val conn = connectionFactory(ui)
            connection = conn
            try {
                conn.connect(spec.target, spec.jump)
                if (gen != generation.get()) {
                    conn.disconnect(); return@launch
                }
                conn.passwordToRemember?.let(onPasswordRemembered)
                val errors = conn.startForwards(spec.forwards)
                errors.forEach { _events.tryEmit(SessionEvent.Message("Forward failed: $it")) }
                val sh = conn.openShell(cols, rows, widthPx, heightPx)
                shell = sh
                _state.value = SessionState.Connected
                onConnected()
                if (spec.startupCommand.isNotBlank()) {
                    write((spec.startupCommand.trimEnd('\n') + "\r").toByteArray())
                }
                readLoop(sh, gen)
            } catch (e: Exception) {
                if (gen == generation.get()) {
                    val msg = SshConnection.describeError(e)
                    _state.value = SessionState.Failed(msg)
                    appendLog("Error: $msg")
                }
                conn.disconnect()
            } finally {
                _prompt.value = null
            }
        }
    }

    private fun readLoop(sh: ShellChannel, gen: Int) {
        val buf = ByteArray(16 * 1024)
        var reason = "Connection closed"
        try {
            while (true) {
                val n = sh.input.read(buf)
                if (n < 0) break
                if (n == 0) continue
                synchronized(emulator) { emulator.append(buf, 0, n) }
                screenListener?.invoke()
            }
            val status = sh.exitStatus
            if (status >= 0) reason = "Session ended (exit status $status)"
        } catch (e: IOException) {
            reason = e.message?.let { "Connection lost: $it" } ?: "Connection lost"
        }
        if (gen != generation.get()) return
        synchronized(emulator) {
            emulator.append("\r\n\u001b[0;2m[$reason]\u001b[0m\r\n")
        }
        screenListener?.invoke()
        closeSftp()
        connection?.disconnect()
        _state.value = SessionState.Disconnected(reason)
    }

    fun reconnect() {
        disconnectInternal()
        synchronized(emulator) {
            emulator.append("\u001b[0;2m[Reconnecting…]\u001b[0m\r\n")
        }
        screenListener?.invoke()
        connect()
    }

    private fun disconnectInternal() {
        generation.incrementAndGet()
        _prompt.value?.cancel()
        _prompt.value = null
        connectJob?.cancel()
        connectJob = null
        val sh = shell
        val conn = connection
        shell = null
        connection = null
        closeSftp()
        scope.launch(Dispatchers.IO) {
            sh?.close()
            conn?.disconnect()
        }
    }

    /** Disconnects for good. */
    fun close() {
        disconnectInternal()
        _state.value = SessionState.Disconnected("Disconnected")
        writer.shutdownNow()
        screenListener = null
    }

    // region Input

    fun write(data: ByteArray) {
        val sh = shell ?: return
        if (writer.isShutdown) return
        writer.execute {
            try {
                sh.output.write(data)
                sh.output.flush()
            } catch (_: IOException) {
            }
        }
    }

    fun writeText(text: String) = write(text.toByteArray(Charsets.UTF_8))

    fun sendKey(key: TerminalKey, modifiers: Int = 0) {
        val bytes = synchronized(emulator) {
            KeyEncoder.encode(key, modifiers, emulator.applicationCursorKeys, emulator.newLineMode)
        }
        write(bytes)
    }

    fun paste(text: String) {
        val bytes = synchronized(emulator) { emulator.encodePaste(text) }
        write(bytes)
    }

    fun resize(newCols: Int, newRows: Int, newWidthPx: Int, newHeightPx: Int) {
        if (newCols <= 0 || newRows <= 0) return
        val changed = newCols != cols || newRows != rows
        cols = newCols
        rows = newRows
        widthPx = newWidthPx
        heightPx = newHeightPx
        if (changed) {
            synchronized(emulator) { emulator.resize(newCols, newRows) }
            screenListener?.invoke()
        }
        val sh = shell ?: return
        if (!writer.isShutdown) writer.execute { sh.resize(newCols, newRows, newWidthPx, newHeightPx) }
    }

    // endregion

    // region SFTP

    /** Opens (or reuses) an SFTP channel on this session's connection. */
    suspend fun sftp(): SftpClient = withContext(Dispatchers.IO) {
        synchronized(sftpLock) {
            sftp?.takeIf { it.isConnected }?.let { return@withContext it }
            val conn = connection?.takeIf { it.isConnected } ?: throw IOException("Not connected")
            SftpClient(conn.openSftp()).also { sftp = it }
        }
    }

    private fun closeSftp() {
        synchronized(sftpLock) {
            sftp?.close()
            sftp = null
        }
    }

    // endregion

    // region TerminalClient

    override fun onWrite(data: ByteArray) = write(data)

    override fun onTitleChanged(title: String) {
        _title.value = title.ifBlank { spec.title }
    }

    override fun onBell() {
        _events.tryEmit(SessionEvent.Bell)
    }

    override fun onClipboardSet(text: String) {
        _events.tryEmit(SessionEvent.Clipboard(text))
    }

    // endregion

    private fun appendLog(line: String) {
        _log.update { (it + line).takeLast(200) }
    }

    private fun <T> ask(prompt: SessionPrompt, deferred: CompletableDeferred<T>): T {
        _prompt.value = prompt
        return try {
            runBlocking { deferred.await() }
        } finally {
            _prompt.compareAndSet(prompt, null)
        }
    }

    private val ui = object : ConnectionUi {
        override fun verifyHostKey(request: HostKeyRequest): Boolean {
            val d = CompletableDeferred<Boolean>()
            return ask(SessionPrompt.HostKey(request, d), d)
        }

        override fun promptPassword(target: String, message: String): PasswordResponse? {
            val d = CompletableDeferred<PasswordResponse?>()
            return ask(SessionPrompt.Password(target, message, spec.hostId > 0, d), d)
        }

        override fun promptKeyboardInteractive(
            target: String,
            name: String,
            instruction: String,
            prompts: List<KeyboardInteractivePrompt>,
        ): List<String>? {
            val d = CompletableDeferred<List<String>?>()
            return ask(SessionPrompt.KeyboardInteractive(target, name, instruction, prompts, d), d)
        }

        override fun log(message: String) = appendLog(message)

        override fun banner(message: String) {
            synchronized(emulator) {
                emulator.append(message.replace("\r\n", "\n").replace("\n", "\r\n"))
                if (!message.endsWith("\n")) emulator.append("\r\n")
            }
            screenListener?.invoke()
        }
    }
}

sealed interface SessionEvent {
    data object Bell : SessionEvent
    data class Clipboard(val text: String) : SessionEvent
    data class Message(val text: String) : SessionEvent
}
