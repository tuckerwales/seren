package wales.tucker.seren.files.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
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
import wales.tucker.seren.core.ui.LocalMessenger
import wales.tucker.seren.files.data.Settings
import wales.tucker.seren.files.ui.Navigator
import wales.tucker.seren.files.ui.browse.BrowseTab
import wales.tucker.seren.files.ui.recent.RecentTab
import wales.tucker.seren.files.ui.settings.SettingsTab
import wales.tucker.seren.files.ui.trash.TrashTab

enum class HomeTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    BROWSE("Browse", Icons.Outlined.Folder, Icons.Rounded.Folder),
    RECENT("Recent", Icons.Outlined.History, Icons.Rounded.History),
    TRASH("Trash", Icons.Outlined.Delete, Icons.Rounded.Delete),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

@Composable
fun HomeScreen(settings: Settings, navigator: Navigator, initialTab: HomeTab = HomeTab.BROWSE) {
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
                    HomeTab.BROWSE -> BrowseTab(settings = settings, navigator = navigator)
                    HomeTab.RECENT -> RecentTab(settings = settings, navigator = navigator)
                    HomeTab.TRASH -> TrashTab()
                    HomeTab.SETTINGS -> SettingsTab(settings = settings)
                }
            }
        }
    }
}
