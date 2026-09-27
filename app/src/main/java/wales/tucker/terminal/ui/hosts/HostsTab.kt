package wales.tucker.terminal.ui.hosts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.rounded.History
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
import wales.tucker.terminal.ui.common.groupedShape
import wales.tucker.terminal.ui.common.relativeTime
import wales.tucker.terminal.ui.theme.MonoSmall
import wales.tucker.terminal.ui.theme.hostColor

class HostsViewModel(private val container: AppContainer) : ViewModel() {
    val hosts: StateFlow<List<Host>> = container.database.hostDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions = container.sessionManager.sessions

    /** Saved hosts with at least one connected session. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val connectedHostIds: StateFlow<Set<Long>> = sessions
        .flatMapLatest { list ->
            if (list.isEmpty()) flowOf(emptySet())
            else combine(list.map { s -> s.state.map { st -> s.spec.hostId.takeIf { st == SessionState.Connected } } }) { ids ->
                ids.filterNotNull().toSet()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

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
    val connectedHostIds by vm.connectedHostIds.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Host?>(null) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

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
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search hosts") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(end = 4.dp).focusRequester(focus),
                            shape = CircleShape,
                            colors = pillFieldColors(MaterialTheme.colorScheme.surfaceContainerHigh),
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
                        Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, contentDescription = if (searching) "Close search" else "Search")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surface),
                windowInsets = WindowInsets.statusBars,
            )
        },
        floatingActionButton = {
            // The empty state has its own add button.
            if (hosts.isNotEmpty()) ExtendedFloatingActionButton(
                onClick = { onEditHost(null, false) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("New host") },
                expanded = fabExpanded,
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            state = listState,
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
                item(key = "header-$group") {
                    SectionHeader(group.ifEmpty { if (grouped.size > 1) "Other" else "Hosts" }, trailing = list.size.toString())
                }
                itemsIndexed(list, key = { _, host -> host.id }) { index, host ->
                    HostRow(
                        host = host,
                        shape = groupedShape(index, list.size),
                        connected = host.id in connectedHostIds,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Quick connect", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Connect once without saving a host",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            TextField(
                value = text,
                onValueChange = { text = it; error = false },
                placeholder = { Text("user@host:port", style = monoField, color = MaterialTheme.colorScheme.outline) },
                singleLine = true,
                isError = error,
                supportingText = if (error) ({ Text("Use the form user@host or user@host:port") }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                trailingIcon = {
                    FilledIconButton(
                        onClick = { submit() },
                        enabled = text.isNotBlank(),
                        modifier = Modifier.padding(end = 6.dp).size(44.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Connect")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                textStyle = monoField,
                colors = pillFieldColors(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
    }
}

private val monoField @Composable get() = MonoSmall.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize)

/** Borderless, filled text field colors for pill shaped inputs. */
@Composable
private fun pillFieldColors(container: Color) = TextFieldDefaults.colors(
    focusedContainerColor = container,
    unfocusedContainerColor = container,
    errorContainerColor = container,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

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
    shape: Shape,
    connected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(host.displayName, hostColor(host.color))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    host.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (connected) {
                    Spacer(Modifier.width(8.dp))
                    StatusDot(stateColor(SessionState.Connected), Modifier.semantics { contentDescription = "Connected" })
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
            if (host.lastConnectedAt > 0) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        relativeTime(host.lastConnectedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}
