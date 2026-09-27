package wales.tucker.seren.files

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wales.tucker.seren.core.security.AppLock
import wales.tucker.seren.core.suite.Suite
import wales.tucker.seren.core.ui.theme.SerenTheme
import wales.tucker.seren.files.ops.Incoming
import wales.tucker.seren.files.ui.FilesAppUi

class MainActivity : FragmentActivity() {

    private val container get() = (application as SerenApp).container

    /** Files other Seren apps asked to show, waiting for the UI (and the app lock). */
    val reveals = Channel<RevealRequest>(Channel.CONFLATED)

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

        // With app lock on, keep folder listings out of the recents screen as well.
        lifecycleScope.launch {
            container.settings.settings.map { it.appLock }.distinctUntilChanged().collect { AppLock.hideFromRecents(this@MainActivity, it) }
        }

        // Things in the trash for more than 30 days are deleted for good.
        if (savedInstanceState == null && container.storage.hasAccess()) {
            lifecycleScope.launch { runCatching { container.trash.tidy() } }
        }

        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings = container.settings.settings.collectAsStateWithLifecycle(initialValue = null).value
            // Show only the window background until the saved settings and the lock state are
            // known, rather than flashing the default theme or the lock screen on every launch.
            if (settings == null || !lockChecked) return@setContent
            SerenTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                FilesAppUi(settings = settings, locked = locked, onUnlock = { authenticate() }, reveals = reveals)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    @VisibleForTesting
    internal fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Suite.ACTION_REVEAL -> {
                val uri = intent.data ?: return
                reveals.trySend(RevealRequest(uri, intent.getStringExtra(Suite.EXTRA_DISPLAY_NAME)))
            }
            // "Save to Seren Files" from the share sheet: people then open a folder and save there.
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> lifecycleScope.launch {
                val files = withContext(Dispatchers.IO) { Incoming.fromIntent(this@MainActivity, intent, container.storage.volumes()) }
                if (files.isEmpty()) {
                    container.operations.tell("There's nothing Seren Files can save in what was shared")
                } else {
                    container.operations.receive(files)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > AppLock.TIMEOUT_MS) {
            lifecycleScope.launch {
                if (container.settings.settings.first().appLock && canAuthenticate()) {
                    locked = true
                    authenticate()
                }
            }
        }
    }

    fun canAuthenticate(): Boolean = AppLock.canAuthenticate(this)

    fun authenticate(onResult: (Boolean) -> Unit = {}) {
        AppLock.authenticate(this, getString(R.string.app_name), "Unlock to see your files") { ok ->
            if (ok) locked = false
            onResult(ok)
        }
    }
}

/** A link to a file another app asked Seren Files to show, with its name if the link hides it. */
data class RevealRequest(val uri: Uri, val displayName: String?)
