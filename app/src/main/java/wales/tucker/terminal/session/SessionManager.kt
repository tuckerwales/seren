package wales.tucker.terminal.session

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wales.tucker.terminal.data.AppDatabase
import wales.tucker.terminal.data.AuthType
import wales.tucker.terminal.data.Host
import wales.tucker.terminal.data.SettingsRepository
import wales.tucker.terminal.security.SecretBox
import wales.tucker.terminal.ssh.ConnectionTarget
import wales.tucker.terminal.ssh.SshConnection
import java.util.concurrent.atomic.AtomicInteger

/** Owns all live terminal sessions for the lifetime of the process. */
class SessionManager(
    private val context: Context,
    private val db: AppDatabase,
    private val secretBox: SecretBox,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val nextId = AtomicInteger(1)
    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    fun get(id: Int): TerminalSession? = _sessions.value.firstOrNull { it.id == id }

    /** Opens a new session to a saved host. */
    suspend fun open(host: Host): TerminalSession {
        val spec = withContext(Dispatchers.IO) { buildSpec(host) }
        return start(spec)
    }

    /** Opens a session to an unsaved host (quick connect). */
    suspend fun openQuick(username: String, hostname: String, port: Int): TerminalSession {
        val host = Host(nickname = "", hostname = hostname, port = port, username = username, authType = AuthType.NONE)
        return open(host)
    }

    private suspend fun start(spec: SessionSpec): TerminalSession {
        val scrollback = settings.settings.first().scrollback
        val session = TerminalSession(
            id = nextId.getAndIncrement(),
            spec = spec,
            connectionFactory = { ui -> SshConnection(db.knownHostDao(), ui) },
            scope = scope,
            scrollback = scrollback,
            onPasswordRemembered = { pw -> rememberPassword(spec.hostId, pw) },
            onConnected = { markConnected(spec.hostId) },
        )
        _sessions.update { it + session }
        ensureService()
        session.connect()
        return session
    }

    fun close(session: TerminalSession) {
        session.close()
        _sessions.update { it - session }
    }

    fun closeAll() {
        _sessions.value.forEach { it.close() }
        _sessions.value = emptyList()
    }

    private fun ensureService() {
        val intent = Intent(context, SessionService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    private fun rememberPassword(hostId: Long, password: String) {
        if (hostId <= 0) return
        scope.launch(Dispatchers.IO) {
            val host = db.hostDao().get(hostId) ?: return@launch
            db.hostDao().update(
                host.copy(
                    encryptedPassword = secretBox.encryptString(password),
                    authType = if (host.authType == AuthType.NONE) AuthType.PASSWORD else host.authType,
                ),
            )
        }
    }

    private fun markConnected(hostId: Long) {
        if (hostId <= 0) return
        scope.launch(Dispatchers.IO) { db.hostDao().markConnected(hostId, System.currentTimeMillis()) }
    }

    private suspend fun buildSpec(host: Host): SessionSpec {
        val target = buildTarget(host)
        val jump = host.jumpHostId?.takeIf { it != host.id }?.let { db.hostDao().get(it) }?.let { buildTarget(it) }
        val forwards = if (host.id > 0) db.portForwardDao().forHost(host.id) else emptyList()
        return SessionSpec(
            hostId = host.id,
            title = host.displayName,
            subtitle = host.address,
            color = host.color,
            target = target,
            jump = jump,
            forwards = forwards,
            startupCommand = host.startupCommand,
            colorSchemeId = host.colorSchemeId,
        )
    }

    private suspend fun buildTarget(host: Host): ConnectionTarget {
        val password = host.encryptedPassword?.let { runCatching { secretBox.decryptString(it) }.getOrNull() }
        var privateKey: String? = null
        var keyName: String? = null
        if (host.authType == AuthType.KEY && host.keyId != null) {
            db.keyDao().get(host.keyId)?.let { key ->
                privateKey = runCatching { secretBox.decryptString(key.encryptedPrivateKey) }.getOrNull()
                keyName = key.name
            }
        }
        return ConnectionTarget(
            label = host.displayName,
            hostname = host.hostname,
            port = host.port,
            username = host.username,
            password = password,
            privateKey = privateKey,
            keyName = keyName,
            keepAliveSeconds = host.keepAliveSeconds,
            compression = host.compression,
        )
    }
}
