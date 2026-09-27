package wales.tucker.terminal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import wales.tucker.terminal.data.Settings
import wales.tucker.terminal.ui.TerminalAppUi
import wales.tucker.terminal.ui.theme.TerminalTheme

class MainActivity : FragmentActivity() {

    private val container get() = (application as TerminalApp).container

    /** ssh:// links waiting to be opened by the UI. */
    val deepLinks = Channel<SshLink>(Channel.BUFFERED)

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
            locked = settings.appLock && canAuthenticate()
            lockChecked = true
            if (locked) authenticate()
        }

        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
            TerminalTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                TerminalAppUi(
                    settings = settings,
                    locked = locked || !lockChecked,
                    onUnlock = { authenticate() },
                    deepLinks = deepLinks,
                )
            }
        }
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
        if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > LOCK_TIMEOUT_MS) {
            lifecycleScope.launch {
                if (container.settings.settings.first().appLock && canAuthenticate()) {
                    locked = true
                    authenticate()
                }
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW && uri.scheme == "ssh") {
            SshLink.parse(uri)?.let { deepLinks.trySend(it) }
        }
    }

    fun canAuthenticate(): Boolean =
        BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(onResult: (Boolean) -> Unit = {}) {
        if (!canAuthenticate()) {
            locked = false
            onResult(true)
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    locked = false
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(false)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_name))
            .setSubtitle("Unlock to access your servers")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }

    companion object {
        private const val LOCK_TIMEOUT_MS = 30_000L
        const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}

/** A parsed ssh://[user@]host[:port] link. */
data class SshLink(val username: String, val hostname: String, val port: Int) {
    companion object {
        fun parse(uri: Uri): SshLink? {
            val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
            val user = uri.userInfo?.substringBefore(';')?.substringBefore(':')?.takeIf { it.isNotBlank() } ?: return null
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
