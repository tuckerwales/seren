package wales.tucker.terminal.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import wales.tucker.terminal.SshLink
import wales.tucker.terminal.data.Host
import wales.tucker.terminal.data.Settings
import wales.tucker.terminal.ui.hosts.HostsTab
import wales.tucker.terminal.ui.keys.KeysTab
import wales.tucker.terminal.ui.settings.SettingsTab
import wales.tucker.terminal.ui.snippets.SnippetsTab

private enum class HomeTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOSTS("Hosts", Icons.Outlined.Dns, Icons.Rounded.Dns),
    KEYS("Keys", Icons.Outlined.Key, Icons.Rounded.Key),
    SNIPPETS("Snippets", Icons.Outlined.Terminal, Icons.Rounded.Terminal),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

@Composable
fun HomeScreen(
    settings: Settings,
    onConnect: (Host) -> Unit,
    onQuickConnect: (SshLink) -> Unit,
    quickConnectPrefill: String?,
    onPrefillConsumed: () -> Unit,
    onOpenSession: (Int) -> Unit,
    onEditHost: (Long?, Boolean) -> Unit,
    onImportKey: () -> Unit,
    onKnownHosts: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.HOSTS) }
    LaunchedEffect(quickConnectPrefill) { if (quickConnectPrefill != null) tab = HomeTab.HOSTS }
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
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
                when (t) {
                    HomeTab.HOSTS -> HostsTab(
                        onConnect = onConnect,
                        onQuickConnect = onQuickConnect,
                        quickConnectPrefill = quickConnectPrefill,
                        onPrefillConsumed = onPrefillConsumed,
                        onOpenSession = onOpenSession,
                        onEditHost = onEditHost,
                    )
                    HomeTab.KEYS -> KeysTab(onImportKey = onImportKey)
                    HomeTab.SNIPPETS -> SnippetsTab()
                    HomeTab.SETTINGS -> SettingsTab(settings = settings, onKnownHosts = onKnownHosts)
                }
            }
        }
    }
}
