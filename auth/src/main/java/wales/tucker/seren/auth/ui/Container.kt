package wales.tucker.seren.auth.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import wales.tucker.seren.auth.AppContainer
import wales.tucker.seren.auth.MainActivity
import wales.tucker.seren.auth.SerenApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as SerenApp).container

/** Creates a ViewModel with access to the [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

/** Runs [action] after the user confirms it's them, if app lock is on (see MainActivity.confirmIdentity). */
@Composable
fun rememberIdentityCheck(): (reason: String, action: () -> Unit) -> Unit {
    val activity = LocalContext.current as? MainActivity
    return { reason, action -> activity?.confirmIdentity(reason, action) ?: action() }
}
