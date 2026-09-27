package wales.tucker.seren.core.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * An app's one snackbar, shared by every screen so a message survives navigating back (such as
 * "Added GitHub" after saving, or "Moved notes.txt to the trash" with Undo). Provide it with
 * [LocalMessenger] at the top of the app's UI.
 */
class Messenger(internal val scope: CoroutineScope) {
    val host = SnackbarHostState()

    fun show(message: String, action: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(
                message,
                actionLabel = action,
                duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }
}

/** Runs [block] in the app UI's scope, which outlives any one screen (for Undo actions). */
fun Messenger.launch(block: suspend () -> Unit) = scope.launch { block() }

val LocalMessenger = staticCompositionLocalOf<Messenger> { error("No Messenger provided") }
