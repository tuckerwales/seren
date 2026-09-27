package wales.tucker.seren.auth.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import wales.tucker.seren.auth.data.Settings
import wales.tucker.seren.auth.ui.AddActions
import wales.tucker.seren.auth.ui.accounts.AccountsTab
import wales.tucker.seren.auth.ui.common.LocalMessenger
import wales.tucker.seren.auth.ui.settings.SettingsTab

enum class HomeTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    ACCOUNTS("Accounts", Icons.Outlined.Pin, Icons.Rounded.Pin),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

@Composable
fun HomeScreen(settings: Settings, actions: AddActions, onEdit: (Long) -> Unit, initialTab: HomeTab = HomeTab.ACCOUNTS) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(if (tab == t) t.selectedIcon else t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(LocalMessenger.current.host) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
                when (t) {
                    HomeTab.ACCOUNTS -> AccountsTab(settings = settings, actions = actions, onEdit = onEdit)
                    HomeTab.SETTINGS -> SettingsTab(settings = settings, onImport = actions.importFile)
                }
            }
        }
    }
}
