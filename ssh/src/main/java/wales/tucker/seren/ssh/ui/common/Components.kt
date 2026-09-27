package wales.tucker.seren.ssh.ui.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.platform.LocalContext
import wales.tucker.seren.ssh.AppContainer
import wales.tucker.seren.ssh.SerenApp

// The shared components (SectionHeader, Avatar, EmptyState and so on) live in Seren Core, in
// wales.tucker.seren.core.ui.

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
