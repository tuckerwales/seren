package wales.tucker.terminal.ui.keys

import android.content.Intent
import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wales.tucker.terminal.AppContainer
import wales.tucker.terminal.data.KeyType
import wales.tucker.terminal.data.SshKey
import wales.tucker.terminal.ssh.SshKeys
import wales.tucker.terminal.ui.common.Avatar
import wales.tucker.terminal.ui.common.Chip
import wales.tucker.terminal.ui.common.EmptyState
import wales.tucker.terminal.ui.common.containerViewModel
import wales.tucker.terminal.ui.common.copyToClipboard
import wales.tucker.terminal.ui.theme.MonoSmall

class KeysViewModel(private val container: AppContainer) : ViewModel() {
    val keys: StateFlow<List<SshKey>> = container.database.keyDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    fun generate(name: String, type: KeyType, bits: Int, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            _generating.value = true
            val error = try {
                withContext(Dispatchers.Default) {
                    val material = SshKeys.generate(type, bits, name.replace(' ', '-').ifBlank { "terminal" })
                    container.database.keyDao().insert(
                        SshKey(
                            name = name.ifBlank { "${type.label} key" },
                            type = material.type,
                            bits = material.bits,
                            encryptedPrivateKey = container.secretBox.encryptString(material.privateKey),
                            publicKey = material.publicKey,
                            fingerprint = material.fingerprint,
                        ),
                    )
                }
                null
            } catch (e: Exception) {
                e.message ?: "Key generation failed"
            }
            _generating.value = false
            onDone(error)
        }
    }

    fun rename(key: SshKey, name: String) = viewModelScope.launch {
        container.database.keyDao().update(key.copy(name = name))
    }

    fun delete(key: SshKey) = viewModelScope.launch {
        container.database.hostDao().clearKey(key.id)
        container.database.keyDao().delete(key)
    }

    suspend fun hostsUsing(key: SshKey): Int = container.database.hostDao().countUsingKey(key.id)

    /** Decrypts the private key for export. */
    suspend fun privateKey(key: SshKey): String = withContext(Dispatchers.Default) {
        container.secretBox.decryptString(key.encryptedPrivateKey)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysTab(onImportKey: () -> Unit) {
    val vm = containerViewModel { KeysViewModel(it) }
    val keys by vm.keys.collectAsStateWithLifecycle()
    val generating by vm.generating.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showGenerate by remember { mutableStateOf(false) }
    var fabMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SshKey?>(null) }
    var deleting by remember { mutableStateOf<SshKey?>(null) }
    var exporting by remember { mutableStateOf<SshKey?>(null) }

    fun copy(text: String, label: String, sensitive: Boolean = false) {
        copyToClipboard(context, label, text, sensitive)
        // Android 13 and later confirm copies themselves.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Keys", fontWeight = FontWeight.SemiBold) }, windowInsets = WindowInsets.statusBars) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                ExtendedFloatingActionButton(
                    onClick = { fabMenu = true },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Add key") },
                )
                DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Generate new key") },
                        leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) },
                        onClick = { fabMenu = false; showGenerate = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Import existing key") },
                        leadingIcon = { Icon(Icons.Rounded.Download, null) },
                        onClick = { fabMenu = false; onImportKey() },
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (keys.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = Icons.Rounded.Key,
                    title = "No SSH keys",
                    message = "Keys let you log in without typing passwords. Generate a new Ed25519 key or import one you already use.",
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onImportKey) { Text("Import") }
                            Button(onClick = { showGenerate = true }) { Text("Generate") }
                        }
                    },
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(keys, key = { it.id }) { key ->
                    KeyCard(
                        key = key,
                        onCopy = { copy(key.publicKey, "Public key") },
                        onShare = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, key.publicKey)
                            context.startActivity(Intent.createChooser(send, "Share public key"))
                        },
                        onRename = { renaming = key },
                        onExport = { exporting = key },
                        onDelete = { deleting = key },
                    )
                }
            }
        }
    }

    if (showGenerate) {
        GenerateKeyDialog(
            generating = generating,
            onDismiss = { if (!generating) showGenerate = false },
            onGenerate = { name, type, bits ->
                vm.generate(name, type, bits) { error ->
                    showGenerate = false
                    Toast.makeText(context, error ?: "Key generated", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    renaming?.let { key ->
        var name by remember(key) { mutableStateOf(key.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename key") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { vm.rename(key, name.trim()); renaming = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    deleting?.let { key ->
        val users by produceState<Int?>(null, key) { value = vm.hostsUsing(key) }
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text("Delete ${key.name}?") },
            text = {
                Text(
                    when (users) {
                        null -> "This cannot be undone."
                        0 -> "No saved hosts use this key. This cannot be undone."
                        1 -> "1 host uses this key and will fall back to password authentication. This cannot be undone."
                        else -> "$users hosts use this key and will fall back to password authentication. This cannot be undone."
                    },
                )
            },
            confirmButton = { TextButton(onClick = { vm.delete(key); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }

    exporting?.let { key ->
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { exporting = null },
            title = { Text("Copy private key?") },
            text = {
                Text(
                    "Anyone with your private key can log in to your servers. Only copy it to move it somewhere you trust. " +
                        "It is cleared from the clipboard after a minute.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        copy(vm.privateKey(key), "Private key", sensitive = true)
                        exporting = null
                    }
                }) { Text("Copy private key") }
            },
            dismissButton = { TextButton(onClick = { exporting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun KeyCard(
    key: SshKey,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(key.name, MaterialTheme.colorScheme.tertiary, icon = Icons.Rounded.Key)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(key.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Chip(if (key.type == KeyType.ED25519) key.type.label else "${key.type.label} ${key.bits}")
                }
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Copy private key") }, leadingIcon = { Icon(Icons.Rounded.Key, null) }, onClick = { menu = false; onExport() })
                        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(key.fingerprint, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                key.publicKey,
                style = MonoSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCopy, contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy public key")
                }
                OutlinedButton(onClick = onShare, contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
            }
        }
    }
}

@Composable
private fun GenerateKeyDialog(generating: Boolean, onDismiss: () -> Unit, onGenerate: (String, KeyType, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(KeyType.ED25519) }
    var bits by remember { mutableIntStateOf(256) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AutoAwesome, null) },
        title = { Text("Generate key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("My phone") },
                    singleLine = true,
                    enabled = !generating,
                    modifier = Modifier.fillMaxWidth(),
                )
                val types = listOf(KeyType.ED25519, KeyType.ECDSA, KeyType.RSA)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    types.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = type == t,
                            onClick = {
                                type = t
                                bits = when (t) {
                                    KeyType.RSA -> 3072
                                    KeyType.ECDSA -> 256
                                    else -> 256
                                }
                            },
                            enabled = !generating,
                            shape = SegmentedButtonDefaults.itemShape(i, types.size),
                        ) { Text(t.label) }
                    }
                }
                val sizes = when (type) {
                    KeyType.RSA -> listOf(2048, 3072, 4096)
                    KeyType.ECDSA -> listOf(256, 384, 521)
                    else -> emptyList()
                }
                if (sizes.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sizes.forEach { s ->
                            FilterChip(selected = bits == s, onClick = { bits = s }, label = { Text("$s bits") }, enabled = !generating)
                        }
                    }
                }
                Text(
                    when (type) {
                        KeyType.ED25519 -> "Recommended. Small, fast and secure; supported by OpenSSH 6.5 and later."
                        KeyType.ECDSA -> "NIST elliptic curve key, for servers that do not support Ed25519."
                        else -> "Widely compatible with older servers. Larger keys take longer to generate."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onGenerate(name.trim(), type, bits) }, enabled = !generating) {
                if (generating) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Generating…")
                } else {
                    Text("Generate")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !generating) { Text("Cancel") } },
    )
}
