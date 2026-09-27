package wales.tucker.seren.ssh.ui.hosts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wales.tucker.seren.ssh.AppContainer
import wales.tucker.seren.ssh.data.AuthType
import wales.tucker.seren.ssh.data.ForwardType
import wales.tucker.seren.ssh.data.Host
import wales.tucker.seren.ssh.data.PortForward
import wales.tucker.seren.ssh.data.SshKey
import wales.tucker.seren.ssh.emulator.ColorSchemes
import wales.tucker.seren.ssh.ui.common.SectionHeader
import wales.tucker.seren.ssh.ui.common.containerViewModel
import wales.tucker.seren.ssh.ui.theme.HostColors
import wales.tucker.seren.ssh.ui.theme.MonoSmall

data class HostForm(
    val id: Long = 0,
    val nickname: String = "",
    val hostname: String = "",
    val port: String = "22",
    val username: String = "",
    val authType: AuthType = AuthType.PASSWORD,
    val password: String = "",
    val hasSavedPassword: Boolean = false,
    val clearSavedPassword: Boolean = false,
    val keyId: Long? = null,
    val color: Int = 0,
    val group: String = "",
    val startupCommand: String = "",
    val jumpHostId: Long? = null,
    val keepAlive: String = "30",
    val compression: Boolean = false,
    val colorSchemeId: String? = null,
    val forwards: List<PortForward> = emptyList(),
    val lastConnectedAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val hostnameError: String? get() = if (hostname.isBlank()) "Required" else if (hostname.any { it.isWhitespace() }) "No spaces allowed" else null
    val usernameError: String? get() = if (username.isBlank()) "Required" else null
    val portError: String? get() = if (port.toIntOrNull() !in 1..65535) "1 to 65535" else null
    val keyError: String? get() = if (authType == AuthType.KEY && keyId == null) "Choose a key" else null
    val isValid: Boolean get() = hostnameError == null && usernameError == null && portError == null && keyError == null
}

class HostEditorViewModel(
    private val container: AppContainer,
    private val hostId: Long?,
    private val duplicate: Boolean,
    fromSessionId: Int? = null,
) : ViewModel() {
    /** The quick connect session this new host is being saved from, if any. */
    private val fromSession = fromSessionId?.let { container.sessionManager.get(it) }?.takeIf { it.hostId.value <= 0 }

    private val _form = MutableStateFlow(HostForm(color = (0 until HostColors.size).random()))
    val form: StateFlow<HostForm> = _form.asStateFlow()

    /** The form as loaded, to tell whether leaving would lose changes. */
    private var initial: HostForm = _form.value

    val hasChanges: Boolean get() = _form.value != initial
    private var encryptedPassword: String? = null

    val keys: StateFlow<List<SshKey>> = container.database.keyDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val hosts: StateFlow<List<Host>> = container.database.hostDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    val isNew: Boolean get() = hostId == null || duplicate

    init {
        fromSession?.let { s ->
            val target = s.spec.target
            val password = s.typedPassword
            _form.value = _form.value.copy(
                hostname = target.hostname,
                port = target.port.toString(),
                username = target.username,
                authType = if (password != null) AuthType.PASSWORD else AuthType.NONE,
                password = password.orEmpty(),
                lastConnectedAt = System.currentTimeMillis(),
            )
            initial = _form.value
        }
        if (hostId != null) {
            viewModelScope.launch {
                val h = container.database.hostDao().get(hostId) ?: return@launch
                val forwards = container.database.portForwardDao().forHost(hostId)
                encryptedPassword = h.encryptedPassword
                _form.value = HostForm(
                    id = if (duplicate) 0 else h.id,
                    nickname = if (duplicate) "${h.nickname} copy".trim() else h.nickname,
                    hostname = h.hostname,
                    port = h.port.toString(),
                    username = h.username,
                    authType = h.authType,
                    hasSavedPassword = h.encryptedPassword != null,
                    keyId = h.keyId,
                    color = h.color,
                    group = h.group,
                    startupCommand = h.startupCommand,
                    jumpHostId = h.jumpHostId,
                    keepAlive = h.keepAliveSeconds.toString(),
                    compression = h.compression,
                    colorSchemeId = h.colorSchemeId,
                    forwards = forwards,
                    lastConnectedAt = if (duplicate) 0 else h.lastConnectedAt,
                    createdAt = if (duplicate) System.currentTimeMillis() else h.createdAt,
                )
                initial = _form.value
            }
        }
    }

    fun update(transform: (HostForm) -> HostForm) = _form.update(transform)

    fun save() {
        val f = _form.value
        if (!f.isValid) return
        viewModelScope.launch {
            val pw = withContext(Dispatchers.Default) {
                when {
                    f.authType == AuthType.NONE -> null
                    f.password.isNotEmpty() -> container.secretBox.encryptString(f.password)
                    f.clearSavedPassword -> null
                    else -> encryptedPassword
                }
            }
            val host = Host(
                id = f.id,
                nickname = f.nickname.trim(),
                hostname = f.hostname.trim(),
                port = f.port.toInt(),
                username = f.username.trim(),
                authType = f.authType,
                encryptedPassword = pw,
                keyId = if (f.authType == AuthType.KEY) f.keyId else null,
                color = f.color,
                group = f.group.trim(),
                startupCommand = f.startupCommand,
                jumpHostId = f.jumpHostId?.takeIf { it != f.id },
                keepAliveSeconds = f.keepAlive.toIntOrNull()?.coerceIn(0, 3600) ?: 30,
                compression = f.compression,
                colorSchemeId = f.colorSchemeId,
                lastConnectedAt = f.lastConnectedAt,
                createdAt = f.createdAt,
            )
            val dao = container.database.hostDao()
            val id = if (host.id == 0L) dao.insert(host) else host.id.also { dao.update(host) }
            container.database.portForwardDao().replaceForHost(id, f.forwards)
            fromSession?.linkToHost(id)
            _saved.value = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HostEditorScreen(hostId: Long?, duplicate: Boolean, onDone: () -> Unit, fromSessionId: Int? = null) {
    val vm = containerViewModel(key = "host-$hostId-$duplicate-$fromSessionId") { HostEditorViewModel(it, hostId, duplicate, fromSessionId) }
    val form by vm.form.collectAsStateWithLifecycle()
    val keys by vm.keys.collectAsStateWithLifecycle()
    val hosts by vm.hosts.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    var showErrors by remember { mutableStateOf(false) }
    var editingForward by remember { mutableStateOf<Pair<Int, PortForward>?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hostnameFocus = remember { FocusRequester() }
    val portFocus = remember { FocusRequester() }
    val usernameFocus = remember { FocusRequester() }
    val keyPickerView = remember { BringIntoViewRequester() }

    LaunchedEffect(saved) { if (saved) onDone() }

    val leave = { if (vm.hasChanges && !saved) confirmDiscard = true else onDone() }
    BackHandler(enabled = !saved) { leave() }

    /** Saves, or shows the errors and moves to the first field that needs fixing. */
    val save = {
        showErrors = true
        when {
            form.hostnameError != null -> hostnameFocus.requestFocus()
            form.portError != null -> portFocus.requestFocus()
            form.usernameError != null -> usernameFocus.requestFocus()
            form.keyError != null -> scope.launch { keyPickerView.bringIntoView() }
            else -> vm.save()
        }
        Unit
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "New host" else "Edit host") },
                navigationIcon = {
                    IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    TextButton(onClick = save) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            SectionHeader("Connection")
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.nickname,
                    onValueChange = { v -> vm.update { it.copy(nickname = v) } },
                    label = { Text("Name") },
                    placeholder = { Text("My server") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = form.hostname,
                        onValueChange = { v -> vm.update { it.copy(hostname = v) } },
                        label = { Text("Host") },
                        placeholder = { Text("example.com") },
                        singleLine = true,
                        isError = showErrors && form.hostnameError != null,
                        supportingText = if (showErrors && form.hostnameError != null) ({ Text(form.hostnameError!!) }) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                        modifier = Modifier.weight(1f).focusRequester(hostnameFocus),
                    )
                    OutlinedTextField(
                        value = form.port,
                        onValueChange = { v -> vm.update { it.copy(port = v.filter(Char::isDigit).take(5)) } },
                        label = { Text("Port") },
                        singleLine = true,
                        isError = showErrors && form.portError != null,
                        supportingText = if (showErrors && form.portError != null) ({ Text(form.portError!!) }) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(112.dp).focusRequester(portFocus),
                    )
                }
                OutlinedTextField(
                    value = form.username,
                    onValueChange = { v -> vm.update { it.copy(username = v) } },
                    label = { Text("Username") },
                    singleLine = true,
                    isError = showErrors && form.usernameError != null,
                    supportingText = if (showErrors && form.usernameError != null) ({ Text(form.usernameError!!) }) else null,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().focusRequester(usernameFocus),
                )
            }

            SectionHeader("Authentication")
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val options = listOf(AuthType.PASSWORD to "Password", AuthType.KEY to "Key", AuthType.NONE to "Ask")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { i, (type, label) ->
                        SegmentedButton(
                            selected = form.authType == type,
                            onClick = { vm.update { it.copy(authType = type) } },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        ) { Text(label) }
                    }
                }
                when (form.authType) {
                    AuthType.PASSWORD -> PasswordField(form, vm)
                    AuthType.KEY -> {
                        Box(Modifier.bringIntoViewRequester(keyPickerView)) {
                            KeyPicker(keys, form.keyId, showErrors && form.keyError != null) { id -> vm.update { it.copy(keyId = id) } }
                        }
                        if (keys.isEmpty()) {
                            Text(
                                "You have no keys yet. Generate or import one from the Keys tab.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        PasswordField(form, vm, optional = true)
                    }
                    AuthType.NONE -> Text(
                        "You'll be asked for a password or other credentials each time you connect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionHeader("Appearance")
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HostColors.forEachIndexed { i, c ->
                        ColorSwatch(c, selected = form.color == i) { vm.update { it.copy(color = i) } }
                    }
                }
                OutlinedTextField(
                    value = form.group,
                    onValueChange = { v -> vm.update { it.copy(group = v) } },
                    label = { Text("Group") },
                    placeholder = { Text("e.g. Production") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SchemePicker(form.colorSchemeId) { id -> vm.update { it.copy(colorSchemeId = id) } }
            }

            SectionHeader("Port forwarding")
            Card(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                form.forwards.forEachIndexed { i, f ->
                    ListItem(
                        headlineContent = { Text(f.summary, style = MonoSmall) },
                        supportingContent = {
                            Text(
                                when (f.type) {
                                    ForwardType.LOCAL -> "Local ${f.bindAddress}:${f.sourcePort} to remote ${f.destHost}:${f.destPort}"
                                    ForwardType.REMOTE -> "Remote port ${f.sourcePort} to local ${f.destHost}:${f.destPort}"
                                    ForwardType.DYNAMIC -> "SOCKS proxy on ${f.bindAddress}:${f.sourcePort}"
                                },
                            )
                        },
                        leadingContent = { Icon(Icons.Rounded.SwapHoriz, null) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = f.enabled, onCheckedChange = { en ->
                                    vm.update { it.copy(forwards = it.forwards.toMutableList().also { l -> l[i] = f.copy(enabled = en) }) }
                                })
                                IconButton(onClick = {
                                    vm.update { it.copy(forwards = it.forwards.toMutableList().also { l -> l.removeAt(i) }) }
                                }) { Icon(Icons.Rounded.Close, contentDescription = "Remove") }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { editingForward = i to f },
                    )
                    HorizontalDivider()
                }
                TextButton(
                    onClick = { editingForward = -1 to PortForward(hostId = form.id, type = ForwardType.LOCAL, sourcePort = 8080, destPort = 80) },
                    modifier = Modifier.padding(8.dp),
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add forward")
                }
            }

            SectionHeader("Advanced")
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                JumpHostPicker(hosts.filter { it.id != form.id }, form.jumpHostId) { id -> vm.update { it.copy(jumpHostId = id) } }
                OutlinedTextField(
                    value = form.startupCommand,
                    onValueChange = { v -> vm.update { it.copy(startupCommand = v) } },
                    label = { Text("Startup command") },
                    placeholder = { Text("tmux new -A -s main", style = MonoSmall) },
                    textStyle = MonoSmall.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.keepAlive,
                    onValueChange = { v -> vm.update { it.copy(keepAlive = v.filter(Char::isDigit).take(4)) } },
                    label = { Text("Keep-alive interval (seconds, 0 to disable)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { vm.update { it.copy(compression = !it.compression) } }) {
                    Column(Modifier.weight(1f)) {
                        Text("Compression", style = MaterialTheme.typography.bodyLarge)
                        Text("Helps on slow links", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = form.compression, onCheckedChange = { c -> vm.update { it.copy(compression = c) } })
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = save,
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(52.dp).navigationBarsPadding(),
            ) {
                Icon(Icons.Rounded.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("Save host")
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text(if (vm.isNew) "This host hasn't been saved." else "Your changes to this host haven't been saved.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDone() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }

    editingForward?.let { (index, forward) ->
        PortForwardDialog(
            initial = forward,
            onDismiss = { editingForward = null },
            onSave = { f ->
                vm.update {
                    val list = it.forwards.toMutableList()
                    if (index >= 0) list[index] = f else list.add(f)
                    it.copy(forwards = list)
                }
                editingForward = null
            },
        )
    }
}

@Composable
private fun PasswordField(form: HostForm, vm: HostEditorViewModel, optional: Boolean = false) {
    var visible by remember { mutableStateOf(false) }
    val saved = form.hasSavedPassword && !form.clearSavedPassword
    OutlinedTextField(
        value = form.password,
        onValueChange = { v -> vm.update { it.copy(password = v) } },
        label = { Text(if (optional) "Fallback password (optional)" else "Password") },
        placeholder = { Text(if (saved) "Saved (leave empty to keep)" else "Ask when connecting") },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "Toggle visibility")
            }
        },
        supportingText = { Text("Stored encrypted with a hardware-backed key on this device") },
        modifier = Modifier.fillMaxWidth(),
    )
    if (saved) {
        TextButton(onClick = { vm.update { it.copy(clearSavedPassword = true, password = "") } }) { Text("Forget saved password") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyPicker(keys: List<SshKey>, selected: Long?, error: Boolean, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = keys.firstOrNull { it.id == selected }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current?.let { "${it.name} (${it.type.label})" } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Private key") },
            placeholder = { Text("Choose a key") },
            isError = error,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            keys.forEach { k ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(k.name)
                            Text(k.fingerprint, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { onSelect(k.id); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JumpHostPicker(hosts: List<Host>, selected: Long?, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = hosts.firstOrNull { it.id == selected }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current?.displayName ?: "None",
            onValueChange = {},
            readOnly = true,
            label = { Text("Jump host (ProxyJump)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("None") }, onClick = { onSelect(null); expanded = false })
            hosts.forEach { h ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(h.displayName)
                            Text(h.address, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { onSelect(h.id); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchemePicker(selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = selected?.let { ColorSchemes.byId(it) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current?.name ?: "App default",
            onValueChange = {},
            readOnly = true,
            label = { Text("Terminal theme") },
            leadingIcon = current?.let { s -> { SchemePreviewDot(s.background, s.foreground) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("App default") }, onClick = { onSelect(null); expanded = false })
            ColorSchemes.ALL.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.name) },
                    leadingIcon = { SchemePreviewDot(s.background, s.foreground) },
                    onClick = { onSelect(s.id); expanded = false },
                )
            }
        }
    }
}

@Composable
fun SchemePreviewDot(bg: Int, fg: Int) {
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(Color(bg)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(fg)))
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.White)
    }
}

@Composable
private fun PortForwardDialog(initial: PortForward, onDismiss: () -> Unit, onSave: (PortForward) -> Unit) {
    var type by remember { mutableStateOf(initial.type) }
    var bind by remember { mutableStateOf(initial.bindAddress) }
    var source by remember { mutableStateOf(initial.sourcePort.toString()) }
    var destHost by remember { mutableStateOf(initial.destHost) }
    var destPort by remember { mutableStateOf(initial.destPort.toString()) }
    val sourceOk = source.toIntOrNull() in 1..65535
    val destOk = type == ForwardType.DYNAMIC || (destHost.isNotBlank() && destPort.toIntOrNull() in 1..65535)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Port forward") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val types = listOf(ForwardType.LOCAL to "Local", ForwardType.REMOTE to "Remote", ForwardType.DYNAMIC to "Dynamic")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    types.forEachIndexed { i, (t, label) ->
                        SegmentedButton(
                            selected = type == t,
                            onClick = {
                                type = t
                                if (t == ForwardType.REMOTE && bind == "127.0.0.1") bind = "localhost"
                                if (t != ForwardType.REMOTE && bind == "localhost") bind = "127.0.0.1"
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, types.size),
                        ) { Text(label) }
                    }
                }
                Text(
                    when (type) {
                        ForwardType.LOCAL -> "Connections to a port on this device are tunnelled to a host reachable from the server."
                        ForwardType.REMOTE -> "Connections to a port on the server are tunnelled back to a host reachable from this device."
                        ForwardType.DYNAMIC -> "Runs a SOCKS proxy on this device that routes traffic through the server."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = bind, onValueChange = { bind = it.trim() },
                        label = { Text(if (type == ForwardType.REMOTE) "Remote bind" else "Bind address") },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = source, onValueChange = { source = it.filter(Char::isDigit).take(5) },
                        label = { Text("Port") }, singleLine = true, isError = !sourceOk,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(96.dp),
                    )
                }
                if (type != ForwardType.DYNAMIC) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = destHost, onValueChange = { destHost = it.trim() },
                            label = { Text("Destination host") }, singleLine = true, modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = destPort, onValueChange = { destPort = it.filter(Char::isDigit).take(5) },
                            label = { Text("Port") }, singleLine = true,
                            isError = destPort.toIntOrNull() !in 1..65535,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(96.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = sourceOk && destOk,
                onClick = {
                    onSave(
                        initial.copy(
                            type = type,
                            bindAddress = bind.ifBlank { if (type == ForwardType.REMOTE) "localhost" else "127.0.0.1" },
                            sourcePort = source.toInt(),
                            destHost = if (type == ForwardType.DYNAMIC) "" else destHost,
                            destPort = if (type == ForwardType.DYNAMIC) 0 else destPort.toInt(),
                        ),
                    )
                },
            ) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
