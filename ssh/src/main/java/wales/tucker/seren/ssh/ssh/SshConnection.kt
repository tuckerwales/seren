package wales.tucker.seren.ssh.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Logger
import com.jcraft.jsch.Session
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import wales.tucker.seren.ssh.data.ForwardType
import wales.tucker.seren.ssh.data.KnownHost
import wales.tucker.seren.ssh.data.KnownHostDao
import wales.tucker.seren.ssh.data.PortForward
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64

/** Everything needed to open one SSH session. */
data class ConnectionTarget(
    val label: String,
    val hostname: String,
    val port: Int,
    val username: String,
    val password: String? = null,
    /** Unencrypted OpenSSH private key, if public key authentication should be attempted. */
    val privateKey: String? = null,
    val keyName: String? = null,
    val keepAliveSeconds: Int = 30,
    val compression: Boolean = false,
)

data class HostKeyRequest(
    val host: String,
    val keyType: String,
    val fingerprint: String,
    /** Fingerprint previously stored for this host and key type, when the key changed. */
    val previousFingerprint: String?,
    /**
     * Keys of other types already trusted for this host, when the server presents a key type it
     * has not used before. Not a first connection, but not a changed key either.
     */
    val otherKnownKeys: List<KnownHost> = emptyList(),
) {
    val changed: Boolean get() = previousFingerprint != null
    val newKeyType: Boolean get() = !changed && otherKnownKeys.isNotEmpty()
}

data class PasswordResponse(val password: String, val remember: Boolean)

data class KeyboardInteractivePrompt(val text: String, val echo: Boolean)

/**
 * Interactive callbacks during connection. All methods are called from a background thread and
 * may block until the user answers. Returning null means the user cancelled.
 */
interface ConnectionUi {
    fun verifyHostKey(request: HostKeyRequest): Boolean
    /** [error] explains why a password is being asked for again, when an earlier one failed. */
    fun promptPassword(target: String, message: String, error: String?): PasswordResponse?
    fun promptKeyboardInteractive(
        target: String,
        name: String,
        instruction: String,
        prompts: List<KeyboardInteractivePrompt>,
    ): List<String>?
    fun log(message: String)
    fun banner(message: String) {}
}

class AuthCancelledException : Exception("Authentication cancelled")

/** An open interactive shell channel. */
class ShellChannel internal constructor(private val channel: ChannelShell) {
    val input: InputStream = channel.inputStream
    val output: OutputStream = channel.outputStream

    val isOpen: Boolean get() = !channel.isClosed && channel.isConnected
    val exitStatus: Int get() = channel.exitStatus

    fun resize(cols: Int, rows: Int, widthPx: Int, heightPx: Int) {
        runCatching { channel.setPtySize(cols, rows, widthPx, heightPx) }
    }

    fun close() {
        runCatching { channel.disconnect() }
    }
}

/**
 * Wraps a JSch session (and an optional jump host session) with host key verification against the
 * app's known hosts database, interactive authentication and port forwarding.
 */
class SshConnection(
    private val knownHosts: KnownHostDao,
    private val ui: ConnectionUi,
) {
    private val jsch = JSch().apply {
        setInstanceLogger(object : Logger {
            override fun isEnabled(level: Int) = level >= Logger.INFO
            override fun log(level: Int, message: String) {
                message.substringAfter(DROPPED, "").lineSequence().first().takeIf { it.isNotBlank() }?.let { lostBecause = it }
                if (shouldShowLog(message)) ui.log(message)
            }
        })
    }

    /** Why the connection dropped, as JSch saw it, such as the server's disconnect message. */
    @Volatile
    var lostBecause: String? = null
        private set

    @Volatile
    private var session: Session? = null

    @Volatile
    private var jumpSession: Session? = null

    private val socksServers = mutableListOf<SocksProxyServer>()

    /** Password the user entered and asked to remember, to be persisted by the caller. */
    @Volatile
    var passwordToRemember: String? = null
        private set

    val isConnected: Boolean get() = session?.isConnected == true

    /** Remote server version string, available after connecting. */
    val serverVersion: String? get() = session?.serverVersion

    /**
     * Connects and authenticates. Blocks; call from a background thread.
     * @param jump optional jump host to tunnel through.
     */
    fun connect(target: ConnectionTarget, jump: ConnectionTarget? = null, timeoutMs: Int = 20_000) {
        if (jump != null) {
            ui.log("Connecting to jump host ${jump.hostname}:${jump.port}…")
            val js = openSession(jump, jump.hostname, jump.port, alias = null, remember = false)
            js.connect(timeoutMs)
            jumpSession = js
            val localPort = js.setPortForwardingL("127.0.0.1", 0, target.hostname, target.port)
            ui.log("Tunnel via ${jump.hostname} established")
            val s = openSession(target, "127.0.0.1", localPort, alias = hostKeyName(target.hostname, target.port), remember = true)
            ui.log("Connecting to ${target.hostname}:${target.port}…")
            s.connect(timeoutMs)
            session = s
        } else {
            ui.log("Connecting to ${target.hostname}:${target.port}…")
            val s = openSession(target, target.hostname, target.port, alias = null, remember = true)
            s.connect(timeoutMs)
            session = s
        }
        ui.log("Authenticated as ${target.username}")
    }

    private fun openSession(target: ConnectionTarget, host: String, port: Int, alias: String?, remember: Boolean): Session {
        val s = jsch.getSession(target.username, host, port)
        s.setHostKeyRepository(DatabaseHostKeyRepository(knownHosts, ui))
        s.setConfig("StrictHostKeyChecking", "yes")
        s.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password")
        s.setConfig("cipher.c2s", CIPHERS)
        s.setConfig("cipher.s2c", CIPHERS)
        if (target.compression) {
            s.setConfig("compression.s2c", "zlib@openssh.com,zlib,none")
            s.setConfig("compression.c2s", "zlib@openssh.com,zlib,none")
        }
        if (alias != null) s.setHostKeyAlias(alias)
        if (target.keepAliveSeconds > 0) {
            s.setServerAliveInterval(target.keepAliveSeconds * 1000)
            s.setServerAliveCountMax(4)
        }
        target.password?.let { s.setPassword(it.toByteArray(Charsets.UTF_8)) }
        if (target.privateKey != null) {
            s.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password")
            // Identities are per JSch instance; name them per target to avoid clashes with the jump host.
            jsch.addIdentity(
                "${target.keyName ?: "key"}@${target.hostname}",
                target.privateKey.toByteArray(Charsets.UTF_8),
                null,
                null,
            )
        }
        s.userInfo = InteractiveUserInfo(target, remember)
        return s
    }

    fun openShell(cols: Int, rows: Int, widthPx: Int, heightPx: Int): ShellChannel {
        val s = session ?: throw IllegalStateException("Not connected")
        val ch = s.openChannel("shell") as ChannelShell
        ch.setPtyType("xterm-256color", cols, rows, widthPx, heightPx)
        ch.setEnv("LANG", "en_US.UTF-8")
        ch.setEnv("COLORTERM", "truecolor")
        val shell = ShellChannel(ch)
        ch.connect(15_000)
        return shell
    }

    fun openSftp(): ChannelSftp {
        val s = session ?: throw IllegalStateException("Not connected")
        val ch = s.openChannel("sftp") as ChannelSftp
        ch.connect(15_000)
        return ch
    }

    /** Starts the given forwards; returns a human readable error for each forward that failed. */
    fun startForwards(forwards: List<PortForward>): List<String> {
        val s = session ?: return emptyList()
        val errors = mutableListOf<String>()
        for (f in forwards.filter { it.enabled }) {
            try {
                when (f.type) {
                    ForwardType.LOCAL -> s.setPortForwardingL(f.bindAddress.ifBlank { "127.0.0.1" }, f.sourcePort, f.destHost, f.destPort)
                    ForwardType.REMOTE -> s.setPortForwardingR(f.bindAddress.ifBlank { "localhost" }, f.sourcePort, f.destHost, f.destPort)
                    ForwardType.DYNAMIC -> {
                        val server = SocksProxyServer(s, f.bindAddress.ifBlank { "127.0.0.1" }, f.sourcePort)
                        server.start()
                        synchronized(socksServers) { socksServers += server }
                    }
                }
                ui.log("Forwarding ${f.summary}")
            } catch (e: Exception) {
                errors += "${f.summary}: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        return errors
    }

    fun disconnect() {
        synchronized(socksServers) {
            socksServers.forEach { it.stop() }
            socksServers.clear()
        }
        runCatching { session?.disconnect() }
        runCatching { jumpSession?.disconnect() }
        session = null
        jumpSession = null
    }

    private inner class InteractiveUserInfo(
        private val target: ConnectionTarget,
        private val remember: Boolean,
    ) : UserInfo, UIKeyboardInteractive {
        private var password: String? = null
        private var storedPasswordUsed = false
        private var passwordsTyped = 0

        /** Why the user is being asked (again), given the passwords tried so far. */
        private fun retryReason(): String? = when {
            passwordsTyped > 0 -> "Incorrect password, try again"
            storedPasswordUsed -> "The saved password was not accepted"
            else -> null
        }

        private fun askPassword(message: String): PasswordResponse {
            val response = ui.promptPassword(label, message, retryReason()) ?: throw cancel()
            passwordsTyped++
            if (remember && response.remember) passwordToRemember = response.password
            return response
        }
        private val label = "${target.username}@${target.hostname}"

        override fun getPassphrase(): String? = null
        override fun getPassword(): String? = password
        override fun promptPassphrase(message: String?): Boolean = false

        override fun promptPassword(message: String?): Boolean {
            if (!storedPasswordUsed && target.password != null) {
                storedPasswordUsed = true
                password = target.password
                return true
            }
            password = askPassword(message ?: "Password for $label").password
            return true
        }

        override fun promptYesNo(message: String?): Boolean = false

        override fun showMessage(message: String?) {
            if (!message.isNullOrBlank()) ui.banner(message)
        }

        override fun promptKeyboardInteractive(
            destination: String?,
            name: String?,
            instruction: String?,
            prompt: Array<out String>?,
            echo: BooleanArray?,
        ): Array<String>? {
            val prompts = prompt ?: return arrayOf()
            if (prompts.isEmpty()) return arrayOf()
            // Answer a lone password prompt with the stored password once.
            if (prompts.size == 1 && echo?.getOrNull(0) != true &&
                prompts[0].contains("password", ignoreCase = true) &&
                !storedPasswordUsed && target.password != null
            ) {
                storedPasswordUsed = true
                return arrayOf(target.password)
            }
            if (prompts.size == 1 && echo?.getOrNull(0) != true && prompts[0].contains("password", ignoreCase = true)) {
                return arrayOf(askPassword(prompts[0].trim()).password)
            }
            val list = prompts.mapIndexed { i, p -> KeyboardInteractivePrompt(p, echo?.getOrNull(i) ?: false) }
            val answers = ui.promptKeyboardInteractive(label, name.orEmpty(), instruction.orEmpty(), list) ?: throw cancel()
            return answers.toTypedArray()
        }

        private fun cancel(): RuntimeException = AuthCancelledRuntime()
    }

    companion object {
        /** Key used in known hosts, matching how JSch names hosts. */
        fun hostKeyName(host: String, port: Int): String = if (port == 22) host else "[$host]:$port"

        private const val DROPPED = "Caught an exception, leaving main loop due to "

        /**
         * ChaCha20-Poly1305 first, as OpenSSH prefers. JSch runs it on Bouncy Castle, the same on
         * every phone, where the AES ciphers go through Android's own crypto: with those, servers
         * rejected packets sent during uploads ("message authentication code incorrect") and
         * dropped the connection.
         */
        private const val CIPHERS =
            "chacha20-poly1305@openssh.com,aes128-gcm@openssh.com,aes256-gcm@openssh.com,aes128-ctr,aes192-ctr,aes256-ctr"

        private fun shouldShowLog(message: String): Boolean =
            message.startsWith("Connecting to") ||
                message.startsWith("Connection established") ||
                message.startsWith("Remote version string") ||
                message.startsWith("kex: algorithm:") ||
                message.startsWith("kex: host key algorithm") ||
                message.startsWith("kex: client->server cipher") ||
                message.startsWith("Authentication succeeded") ||
                message.startsWith("Authentications that can continue")

        /** Maps JSch exceptions to friendly messages. */
        fun describeError(e: Throwable): String {
            var t: Throwable? = e
            while (t != null) {
                if (t is AuthCancelledRuntime || t is AuthCancelledException) return "Authentication cancelled"
                t = t.cause
            }
            val msg = e.message.orEmpty()
            return when {
                e is com.jcraft.jsch.JSchUnknownHostKeyException -> "Host key was not accepted"
                e is com.jcraft.jsch.JSchChangedHostKeyException -> "Host key changed and was not accepted"
                msg.contains("Auth fail", true) || msg.contains("Auth cancel", true) -> "Authentication failed"
                msg.contains("UnknownHostException") || e.cause is java.net.UnknownHostException -> "Unknown host"
                msg.contains("timeout", true) || e.cause is java.net.SocketTimeoutException -> "Connection timed out"
                msg.contains("Connection refused", true) || e.cause is java.net.ConnectException -> "Connection refused"
                msg.contains("NoRouteToHost", true) || e.cause is java.net.NoRouteToHostException -> "No route to host"
                msg.contains("Algorithm negotiation fail", true) -> "No compatible algorithms with the server"
                e is JSchException && msg.isNotBlank() -> msg.removePrefix("java.net.").replaceFirstChar { it.uppercase() }
                msg.isNotBlank() -> msg
                else -> e.javaClass.simpleName
            }
        }
    }
}

internal class AuthCancelledRuntime : RuntimeException("Authentication cancelled")

/** JSch host key repository backed by the app's known hosts table. */
class DatabaseHostKeyRepository(
    private val dao: KnownHostDao,
    private val ui: ConnectionUi,
) : HostKeyRepository {

    override fun check(host: String, key: ByteArray): Int {
        val type = SshKeys.blobKeyType(key)
        val encoded = Base64.getEncoder().encodeToString(key)
        val entries = dao.findByHost(host)
        val sameType = entries.firstOrNull { it.keyType == type }
        if (sameType != null && sameType.key == encoded) return HostKeyRepository.OK
        if (sameType == null && entries.any { it.key == encoded }) return HostKeyRepository.OK

        val fingerprint = SshKeys.fingerprint(key)
        val others = if (sameType == null) entries else emptyList()
        val accepted = ui.verifyHostKey(HostKeyRequest(host, type, fingerprint, sameType?.fingerprint, others))
        if (!accepted) {
            return if (sameType != null) HostKeyRepository.CHANGED else HostKeyRepository.NOT_INCLUDED
        }
        dao.insert(KnownHost(host = host, keyType = type, key = encoded, fingerprint = fingerprint))
        return HostKeyRepository.OK
    }

    override fun add(hostkey: HostKey, ui: UserInfo?) {
        val blob = Base64.getDecoder().decode(hostkey.key)
        dao.insert(
            KnownHost(host = hostkey.host, keyType = hostkey.type, key = hostkey.key, fingerprint = SshKeys.fingerprint(blob)),
        )
    }

    override fun remove(host: String, type: String?) {
        if (type != null) dao.remove(host, type)
    }

    override fun remove(host: String, type: String?, key: ByteArray?) {
        remove(host, type)
    }

    override fun getKnownHostsRepositoryID(): String = "app"

    override fun getHostKey(): Array<HostKey> = emptyArray()

    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
}
