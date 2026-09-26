package wales.tucker.terminal.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import wales.tucker.terminal.data.KnownHost
import wales.tucker.terminal.ui.common.EmptyState
import wales.tucker.terminal.ui.common.appContainer
import wales.tucker.terminal.ui.common.relativeTime
import wales.tucker.terminal.ui.theme.MonoSmall

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnownHostsScreen(onBack: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val hosts by container.database.knownHostDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var deleting by remember { mutableStateOf<KnownHost?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Known hosts") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        if (hosts.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = Icons.Rounded.Shield,
                    title = "No known hosts",
                    message = "Host keys you trust when connecting are saved here and verified on every connection.",
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(hosts, key = { it.id }) { h ->
                    ListItem(
                        headlineContent = { Text(h.host) },
                        supportingContent = {
                            Column {
                                Text(h.keyType, style = MaterialTheme.typography.labelMedium)
                                Text(h.fingerprint, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Added ${relativeTime(h.addedAt).lowercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        },
                        leadingContent = { Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            IconButton(onClick = { deleting = h }) { Icon(Icons.Rounded.Delete, contentDescription = "Remove") }
                        },
                    )
                }
            }
        }
    }

    deleting?.let { h ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Forget ${h.host}?") },
            text = { Text("You'll be asked to verify this host's key again the next time you connect.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { container.database.knownHostDao().delete(h) }
                    deleting = null
                }) { Text("Forget") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
