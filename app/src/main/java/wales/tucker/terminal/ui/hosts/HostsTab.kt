package wales.tucker.terminal.ui.hosts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import wales.tucker.terminal.AppContainer
import wales.tucker.terminal.SshLink
import wales.tucker.terminal.data.AuthType
import wales.tucker.terminal.data.Host
import wales.tucker.terminal.session.SessionState
import wales.tucker.terminal.session.TerminalSession
import wales.tucker.terminal.ui.common.Avatar
import wales.tucker.terminal.ui.common.EmptyState
import wales.tucker.terminal.ui.common.SectionHeader
import wales.tucker.terminal.ui.common.StatusDot
import wales.tucker.terminal.ui.common.containerViewModel
import wales.tucker.terminal.ui.common.relativeTime
import wales.tucker.terminal.ui.theme.MonoSmall
import wales.tucker.terminal.ui.theme.hostColor

class HostsViewModel(private val container: AppContainer) : ViewModel() {
    val hosts: StateFlow<List<Host>> = container.database.hostDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions = container.sessionManager.sessions

    fun delete(host: Host) = viewModelScope.launch {
        container.database.hostDao().clearJumpHost(host.id)
        container.database.hostDao().delete(host)
    }

    fun closeSession(session: TerminalSession) = container.sessionManager.close(session)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostsTab(
    onConnect: (Host) -> Unit,
    onQuickConnect: (SshLink) -> Unit,
    onOpenSession: (Int) -> Unit,
    onEditHost: (Long?, Boolean) -> Unit,
) {
    val vm = containerViewModel { HostsViewModel(it) }
    val hosts by vm.hosts.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Host?>(null) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    val filtered = remember(hosts, query) {
        if (query.isBlank()) hosts else hosts.filter {
            it.displayName.contains(query, true) || it.hostname.contains(query, true) ||
                it.username.contains(query, true) || it.group.contains(query, true)
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.group.trim() }.toSortedMap(compareBy { it.ifEmpty { "￿" } }) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search hosts") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            shape = RoundedCornerShape(24.dp),
                        )
                    } else {
                        Text("Terminal", fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) query = ""
                    }) {
                        Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, contentDescription = "Search")
                    }
                },
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets.statusBars,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEditHost(null, false) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("New host") },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            if (!searching) {
                item { QuickConnectCard(onQuickConnect) }
            }
            if (sessions.isNotEmpty() && !searching) {
                item { SectionHeader("Active sessions") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(sessions, key = { it.id }) { s ->
                            SessionCard(s, onClick = { onOpenSession(s.id) }, onClose = { vm.closeSession(s) })
                        }
                    }
                }
            }
            if (hosts.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Dns,
                        title = "No hosts yet",
                        message = "Save the servers you connect to for one-tap access, or use quick connect above.",
                        action = {
                            androidx.compose.material3.Button(onClick = { onEditHost(null, false) }) {
                                Icon(Icons.Rounded.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add your first host")
                            }
                        },
                    )
                }
            } else if (filtered.isEmpty()) {
                item {
                    EmptyState(icon = Icons.Rounded.Search, title = "No matches", message = "No hosts match \"$query\".")
                }
            }
            grouped.forEach { (group, list) ->
                item(key = "header-$group") { SectionHeader(group.ifEmpty { if (grouped.size > 1) "Other" else "Hosts" }) }
                items(list, key = { it.id }) { host ->
                    HostRow(
                        host = host,
                        activeCount = sessions.count { it.spec.hostId == host.id },
                        onClick = { onConnect(host) },
                        onEdit = { onEditHost(host.id, false) },
                        onDuplicate = { onEditHost(host.id, true) },
                        onDelete = { pendingDelete = host },
                    )
                }
            }
        }
    }

    pendingDelete?.let { host ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text("Delete ${host.displayName}?") },
            text = { Text("The host, its saved password and port forwards will be removed. Keys are kept.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(host); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun QuickConnectCard(onQuickConnect: (SshLink) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val submit = {
        val link = SshLink.parse(text)
        if (link == null) {
            error = true
        } else {
            error = false
            text = ""
            onQuickConnect(link)
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(8.dp))
                Text("Quick connect", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = false },
                    placeholder = { Text("user@host:port", style = MonoSmall) },
                    singleLine = true,
                    isError = error,
                    supportingText = if (error) ({ Text("Use the form user@host or user@host:port") }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.merge(MonoSmall.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize)),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = { submit() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Connect")
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: TerminalSession, onClick: () -> Unit, onClose: () -> Unit) {
    val state by session.state.collectAsState()
    val title by session.title.collectAsState()
    Card(
        onClick = onClick,
        modifier = Modifier.width(220.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(stateColor(state))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stateLabel(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close session") }
        }
    }
}

@Composable
fun stateColor(state: SessionState): Color = when (state) {
    SessionState.Connected -> Color(0xFF2FBF71)
    SessionState.Connecting -> Color(0xFFF5A524)
    is SessionState.Disconnected -> MaterialTheme.colorScheme.outline
    is SessionState.Failed -> MaterialTheme.colorScheme.error
}

fun stateLabel(state: SessionState): String = when (state) {
    SessionState.Connected -> "Connected"
    SessionState.Connecting -> "Connecting…"
    is SessionState.Disconnected -> state.reason
    is SessionState.Failed -> state.error
}

@Composable
private fun HostRow(
    host: Host,
    activeCount: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(host.displayName, hostColor(host.color))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    host.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (activeCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    StatusDot(Color(0xFF2FBF71))
                }
                if (host.authType == AuthType.KEY) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.VpnKey, contentDescription = "Key authentication", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                host.address,
                style = MonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AnimatedVisibility(host.lastConnectedAt > 0) {
                Text(
                    "Last connected ${relativeTime(host.lastConnectedAt).lowercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More")
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}
