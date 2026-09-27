package wales.tucker.seren.edit.ui.home

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import wales.tucker.seren.edit.data.Folder
import wales.tucker.seren.edit.data.Settings
import wales.tucker.seren.edit.ui.files.FilesTab
import wales.tucker.seren.edit.ui.recent.RecentTab
import wales.tucker.seren.edit.ui.settings.SettingsTab

enum class HomeTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    FILES("Files", Icons.Outlined.Folder, Icons.Rounded.Folder),
    RECENT("Recent", Icons.Outlined.History, Icons.Rounded.History),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

@Composable
fun HomeScreen(
    settings: Settings,
    onOpenFile: (Uri) -> Unit,
    onOpenFolder: (Folder) -> Unit,
    initialTab: HomeTab = HomeTab.FILES,
) {
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
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
                when (t) {
                    HomeTab.FILES -> FilesTab(onOpenFile = onOpenFile, onOpenFolder = onOpenFolder)
                    HomeTab.RECENT -> RecentTab(onOpenFile = onOpenFile)
                    HomeTab.SETTINGS -> SettingsTab(settings = settings)
                }
            }
        }
    }
}
