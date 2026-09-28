package wales.tucker.seren.ssh

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import wales.tucker.seren.core.security.AppLock
import wales.tucker.seren.core.suite.Suite
import wales.tucker.seren.core.ui.theme.SerenTheme
import wales.tucker.seren.ssh.ui.TerminalAppUi

class MainActivity : FragmentActivity() {

    private val container get() = (application as SerenApp).container

    /** ssh:// links waiting to be opened by the UI. */
    val deepLinks = Channel<SshLink>(Channel.BUFFERED)

    /** Sessions to show, from taps on the sessions or transfer notification. */
    val sessionLinks = Channel<SessionLink>(Channel.CONFLATED)

    /** Files shared with Seren SSH, waiting for people to pick where to upload them. */
    val sharedFiles = Channel<List<Uri>>(Channel.CONFLATED)

    /** Private keys handed over by Seren Files, waiting to be imported. */
    val keyFiles = Channel<KeyFile>(Channel.CONFLATED)

    @VisibleForTesting
    internal var locked by mutableStateOf(false)
    private var lockChecked by mutableStateOf(false)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val settings = container.settings.settings.first()
            locked = settings.appLock
            lockChecked = true
            if (locked) authenticate()
        }

        // With app lock on, keep server output out of the recents screen as well.
        lifecycleScope.launch {
            container.settings.settings.map { it.appLock }.distinctUntilChanged().collect(::hideFromRecents)
        }

        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings = container.settings.settings.collectAsStateWithLifecycle(initialValue = null).value
            // Show only the window background until the saved settings and the lock state are
            // known, rather than flashing the default theme or the lock screen on every launch.
            if (settings == null || !lockChecked) return@setContent
            SerenTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                TerminalAppUi(
                    settings = settings,
                    locked = locked,
                    onUnlock = { authenticate() },
                    deepLinks = deepLinks,
                    sessionLinks = sessionLinks,
                    sharedFiles = sharedFiles,
                    keyFiles = keyFiles,
                )
            }
        }
    }

    @VisibleForTesting
    internal var hiddenFromRecents = false
        private set

    private fun hideFromRecents(hide: Boolean) {
        hiddenFromRecents = hide
        AppLock.hideFromRecents(this, hide)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > AppLock.TIMEOUT_MS) {
            lifecycleScope.launch {
                if (container.settings.settings.first().appLock) {
                    locked = true
                    authenticate()
                }
            }
        }
    }

    @VisibleForTesting
    internal fun handleIntent(intent: Intent?) {
        if (intent?.hasExtra(EXTRA_SESSION_ID) == true) {
            sessionLinks.trySend(
                SessionLink(
                    sessionId = intent.getIntExtra(EXTRA_SESSION_ID, 0),
                    openSftp = intent.getBooleanExtra(EXTRA_OPEN_SFTP, false),
                ),
            )
            return
        }
        when (intent?.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                sharedUris(intent).takeIf { it.isNotEmpty() }?.let { sharedFiles.trySend(it) }
                return
            }
            Suite.ACTION_IMPORT_KEY -> {
                intent.data?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }?.let {
                    keyFiles.trySend(KeyFile(it, intent.getStringExtra(Suite.EXTRA_DISPLAY_NAME)))
                }
                return
            }
        }
        val uri = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW && uri.scheme == "ssh") {
            SshLink.parse(uri)?.let { deepLinks.trySend(it) }
        }
    }

    fun canAuthenticate(): Boolean = AppLock.canAuthenticate(this)

    fun authenticate(onResult: (Boolean) -> Unit = {}) {
        AppLock.authenticate(this, getString(R.string.app_name), "Unlock to access your servers") { ok ->
            if (ok) locked = false
            onResult(ok)
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "wales.tucker.seren.ssh.SESSION_ID"
        const val EXTRA_OPEN_SFTP = "wales.tucker.seren.ssh.OPEN_SFTP"

        /**
         * The files in a share: the stream extras, or the clip when an app only sets that. Only
         * content links count, so another app can't point Seren SSH at its own private files.
         */
        @Suppress("DEPRECATION")
        internal fun sharedUris(intent: Intent): List<Uri> {
            val streams = when (intent.action) {
                Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
                Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                else -> emptyList()
            }
            val clip = intent.clipData?.let { c -> (0 until c.itemCount).mapNotNull { c.getItemAt(it).uri } }.orEmpty()
            return streams.ifEmpty { clip }.filter { it.scheme == ContentResolver.SCHEME_CONTENT }.distinct()
        }
    }
}

/** Open [sessionId], and the SFTP browser when [openSftp] is set (transfer notification). */
data class SessionLink(val sessionId: Int, val openSftp: Boolean = false)

/** A private key file to import, with its name when the app that sent it knows it. */
data class KeyFile(val uri: Uri, val displayName: String?)

/** A parsed ssh://[user@]host[:port] link. [username] is empty when a link names no user. */
data class SshLink(val username: String, val hostname: String, val port: Int) {
    val hasUser: Boolean get() = username.isNotBlank()

    /** "host", "host:port" or "[v6:host]:port", as typed into quick connect after "user@". */
    val address: String
        get() {
            val host = if (':' in hostname) "[$hostname]" else hostname
            return if (port == 22) host else "$host:$port"
        }

    companion object {
        fun parse(uri: Uri): SshLink? {
            val host = uri.host?.takeIf { it.isNotBlank() }?.removeSurrounding("[", "]") ?: return null
            val user = uri.userInfo?.substringBefore(';')?.substringBefore(':')?.trim().orEmpty()
            val port = if (uri.port in 1..65535) uri.port else 22
            return SshLink(user, host, port)
        }

        /** Parses "user@host[:port]" as typed into quick connect. */
        fun parse(text: String): SshLink? {
            val t = text.trim().removePrefix("ssh://").removePrefix("ssh ").trim()
            val at = t.lastIndexOf('@')
            if (at <= 0 || at == t.length - 1) return null
            val user = t.substring(0, at)
            var hostPart = t.substring(at + 1)
            var port = 22
            if (hostPart.startsWith("[")) {
                val end = hostPart.indexOf(']')
                if (end < 0) return null
                val rest = hostPart.substring(end + 1)
                hostPart = hostPart.substring(1, end)
                if (rest.startsWith(":")) port = rest.substring(1).toIntOrNull() ?: return null
            } else if (hostPart.count { it == ':' } == 1) {
                port = hostPart.substringAfter(':').toIntOrNull() ?: return null
                hostPart = hostPart.substringBefore(':')
            }
            if (hostPart.isBlank() || port !in 1..65535 || user.any { it.isWhitespace() } || hostPart.any { it.isWhitespace() }) return null
            return SshLink(user, hostPart, port)
        }
    }
}
