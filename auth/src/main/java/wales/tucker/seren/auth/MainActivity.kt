package wales.tucker.seren.auth

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock as AndroidClock
import android.view.WindowManager
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
import wales.tucker.seren.auth.ui.AuthAppUi
import wales.tucker.seren.core.security.AppLock
import wales.tucker.seren.core.ui.theme.SerenTheme

class MainActivity : FragmentActivity() {

    private val container get() = (application as SerenApp).container

    /** otpauth links other apps (or a browser) asked Seren Auth to open, waiting for the UI. */
    val openLinks = Channel<String>(Channel.BUFFERED)

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

        lifecycleScope.launch {
            container.settings.settings.map { it.appLock to it.blockScreenshots }.distinctUntilChanged().collect { (lock, block) ->
                applyWindowSecurity(lock, block)
            }
        }

        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings = container.settings.settings.collectAsStateWithLifecycle(initialValue = null).value
            // Show only the window background until the saved settings and the lock state are
            // known, rather than flashing the default theme or the lock screen on every launch.
            if (settings == null || !lockChecked) return@setContent
            SerenTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                AuthAppUi(settings = settings, locked = locked, onUnlock = { authenticate() }, openLinks = openLinks)
            }
        }
    }

    /**
     * Blocking screenshots also keeps codes out of recent apps. Without it, app lock still hides
     * the recents thumbnail the way every Seren app does.
     */
    private fun applyWindowSecurity(appLock: Boolean, blockScreenshots: Boolean) {
        if (blockScreenshots) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            AppLock.hideFromRecents(this, appLock)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = AndroidClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0 && AndroidClock.elapsedRealtime() - stoppedAt > AppLock.TIMEOUT_MS) {
            lifecycleScope.launch {
                if (container.settings.settings.first().appLock && canAuthenticate()) {
                    locked = true
                    authenticate()
                }
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.dataString ?: return
        if (intent.action == Intent.ACTION_VIEW) openLinks.trySend(data)
    }

    fun canAuthenticate(): Boolean = AppLock.canAuthenticate(this)

    fun authenticate(onResult: (Boolean) -> Unit = {}) {
        AppLock.authenticate(this, getString(R.string.app_name), "Unlock to see your codes") { ok ->
            if (ok) locked = false
            onResult(ok)
        }
    }

    /**
     * Runs [action] once the user proves it's them, when app lock is on. Used before anything
     * shows or exports setup keys, since those let someone make codes forever.
     */
    fun confirmIdentity(reason: String, action: () -> Unit) {
        lifecycleScope.launch {
            if (!container.settings.settings.first().appLock || !canAuthenticate()) {
                action()
                return@launch
            }
            AppLock.authenticate(this@MainActivity, getString(R.string.app_name), reason) { ok -> if (ok) action() }
        }
    }
}
