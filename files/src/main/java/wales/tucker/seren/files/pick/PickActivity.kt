package wales.tucker.seren.files.pick

import android.app.Activity
import android.os.Bundle
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import wales.tucker.seren.core.security.AppLock
import wales.tucker.seren.core.ui.theme.SerenTheme
import wales.tucker.seren.files.R
import wales.tucker.seren.files.SerenApp
import java.io.File

/**
 * Lets another app ask for a file ("Choose a file" in its attach or upload button): people browse
 * to one, or several when the app allows it, and the app gets a read only content link to each.
 * It runs in the asking app's task, so it only ever hands back what was chosen.
 */
class PickActivity : FragmentActivity() {

    private val container get() = (application as SerenApp).container

    @VisibleForTesting
    internal var locked by mutableStateOf(false)
    private var lockChecked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val request = PickRequest.from(intent)

        // With app lock on, choosing a file needs unlocking just as opening the app does.
        lifecycleScope.launch {
            locked = container.settings.settings.first().appLock
            lockChecked = true
            if (locked) authenticate()
        }

        setContent {
            val settings = container.settings.settings.collectAsStateWithLifecycle(initialValue = null).value
            if (settings == null || !lockChecked) return@setContent
            SerenTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                PickerUi(
                    request = request,
                    settings = settings,
                    locked = locked,
                    onUnlock = ::authenticate,
                    onPick = ::finishWith,
                    onCancel = ::finish,
                )
            }
        }
    }

    private fun authenticate() {
        AppLock.authenticate(this, getString(R.string.app_name), "Unlock to choose a file") { ok -> if (ok) locked = false }
    }

    @VisibleForTesting
    internal fun finishWith(files: List<File>) {
        val request = PickRequest.from(intent)
        val chosen = files.filter { it.isFile && request.accepts(it.name) }
        if (chosen.isEmpty()) return
        setResult(Activity.RESULT_OK, request.result(this, if (request.multiple) chosen else chosen.take(1)))
        finish()
    }
}
