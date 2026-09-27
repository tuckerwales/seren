package wales.tucker.seren.edit

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
import wales.tucker.seren.core.ui.theme.SerenTheme
import wales.tucker.seren.edit.ui.EditAppUi

class MainActivity : FragmentActivity() {

    private val container get() = (application as SerenApp).container

    /** Files other apps asked Seren Edit to open, waiting for the UI. */
    val openLinks = Channel<Uri>(Channel.BUFFERED)

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

        // With app lock on, keep file contents out of the recents screen as well.
        lifecycleScope.launch {
            container.settings.settings.map { it.appLock }.distinctUntilChanged().collect { AppLock.hideFromRecents(this@MainActivity, it) }
        }

        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings = container.settings.settings.collectAsStateWithLifecycle(initialValue = null).value
            // Show only the window background until the saved settings and the lock state are
            // known, rather than flashing the default theme or the lock screen on every launch.
            if (settings == null || !lockChecked) return@setContent
            SerenTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                EditAppUi(settings = settings, locked = locked, onUnlock = { authenticate() }, openLinks = openLinks)
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
        if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > AppLock.TIMEOUT_MS) {
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
        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_EDIT) openLinks.trySend(uri)
    }

    fun canAuthenticate(): Boolean = AppLock.canAuthenticate(this)

    fun authenticate(onResult: (Boolean) -> Unit = {}) {
        AppLock.authenticate(this, getString(R.string.app_name), "Unlock to see your files") { ok ->
            if (ok) locked = false
            onResult(ok)
        }
    }
}
