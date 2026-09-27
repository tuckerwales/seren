package wales.tucker.terminal.ui.sftp

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wales.tucker.terminal.AppContainer
import wales.tucker.terminal.session.TerminalSession
import wales.tucker.terminal.ssh.RemoteFile
import wales.tucker.terminal.ssh.SftpClient
import wales.tucker.terminal.ui.common.EmptyState
import wales.tucker.terminal.ui.common.appContainer
import wales.tucker.terminal.ui.common.containerViewModel
import wales.tucker.terminal.ui.theme.MonoSmall
import java.text.DateFormat
import java.util.Date

data class Transfer(val name: String, val upload: Boolean, val done: Long, val total: Long)

class SftpViewModel(container: AppContainer, sessionId: Int) : ViewModel() {
    val session: TerminalSession? = container.sessionManager.get(sessionId)
    private var client: SftpClient? = null

    private val _path = MutableStateFlow<String?>(null)
    val path = _path.asStateFlow()
    private val _files = MutableStateFlow<List<RemoteFile>>(emptyList())
    val files = _files.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _transfer = MutableStateFlow<Transfer?>(null)
    val transfer = _transfer.asStateFlow()
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private var home: String = "/"
    private var transferJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val c = session?.sftp() ?: throw IllegalStateException("Session is not connected")
                client = c
                home = c.home()
                navigate(home)
            } catch (e: Exception) {
                _error.value = e.message ?: "Could not start SFTP"
            }
        }
    }

    fun navigate(target: String) {
        val c = client ?: return
        viewModelScope.launch {
            _loading.value = true
            try {
                val resolved = runCatching { c.realPath(target) }.getOrDefault(target)
                _files.value = c.list(resolved)
                _path.value = resolved
                _error.value = null
            } catch (e: Exception) {
                messages.tryEmit("Cannot open $target: ${e.message}")
                if (_path.value == null) _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun refresh() = _path.value?.let { navigate(it) }

    fun up(): Boolean {
        val p = _path.value ?: return false
        if (p == "/") return false
        navigate(SftpClient.parent(p))
        return true
    }

    fun goHome() = navigate(home)

    private fun op(message: String, block: suspend (SftpClient) -> Unit) {
        val c = client ?: return
        viewModelScope.launch {
            try {
                block(c)
                messages.tryEmit(message)
            } catch (e: Exception) {
                messages.tryEmit("Failed: ${e.message}")
            }
            refresh()
        }
    }

    fun mkdir(name: String) = _path.value?.let { p -> op("Folder created") { it.mkdir(SftpClient.join(p, name)) } }

    fun rename(file: RemoteFile, newName: String) = op("Renamed") {
        it.rename(file.path, SftpClient.join(SftpClient.parent(file.path), newName))
    }

    fun delete(file: RemoteFile) = op("Deleted ${file.name}") { it.delete(file) }

    fun download(file: RemoteFile, uri: Uri, context: android.content.Context) {
        val s = session ?: return
        transferJob = viewModelScope.launch {
            _transfer.value = Transfer(file.name, upload = false, done = 0, total = file.size)
            var c: SftpClient? = null
            var complete = false
            try {
                val channel = s.openSftpChannel().also { c = it }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        channel.download(file.path, out) { done, total ->
                            _transfer.value = Transfer(file.name, false, done, if (total > 0) total else file.size)
                        }
                    } ?: throw IllegalStateException("Cannot write to destination")
                }
                complete = true
                messages.tryEmit("Downloaded ${file.name}")
            } catch (e: CancellationException) {
                messages.tryEmit("Download cancelled")
                throw e
            } catch (e: Exception) {
                messages.tryEmit("Download failed: ${e.message}")
            } finally {
                // Don't leave a truncated file behind in the destination folder.
                if (!complete) withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                }
                c?.close()
                _transfer.value = null
            }
        }
    }

    fun upload(uri: Uri, context: android.content.Context) {
        val s = session ?: return
        val dir = _path.value ?: return
        transferJob = viewModelScope.launch {
            var name = "upload"
            var c: SftpClient? = null
            var target: String? = null
            var written = false
            var complete = false
            try {
                val channel = s.openSftpChannel().also { c = it }
                var size = -1L
                withContext(Dispatchers.IO) {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cur ->
                        if (cur.moveToFirst()) {
                            name = cur.getString(0) ?: name
                            size = if (cur.isNull(1)) -1L else cur.getLong(1)
                        }
                    }
                }
                _transfer.value = Transfer(name, upload = true, done = 0, total = size)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        channel.upload(input, SftpClient.join(dir, name).also { target = it }, size) { done, _ ->
                            if (done > 0) written = true
                            _transfer.value = Transfer(name, true, done, size)
                        }
                    } ?: throw IllegalStateException("Cannot read file")
                }
                complete = true
                messages.tryEmit("Uploaded $name")
            } catch (e: CancellationException) {
                messages.tryEmit("Upload cancelled")
                throw e
            } catch (e: Exception) {
                messages.tryEmit("Upload failed: ${e.message}")
            } finally {
                val partial = target
                val ch = c
                // Only remove a file this upload started writing, never one it failed to open.
                if (!complete && written && partial != null && ch != null) withContext(NonCancellable) {
                    runCatching { ch.delete(ch.stat(partial)) }
                }
                c?.close()
                _transfer.value = null
                refresh()
            }
        }
    }

    fun cancelTransfer() {
        transferJob?.cancel()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpScreen(sessionId: Int, onBack: () -> Unit) {
    val vm = containerViewModel(key = "sftp-$sessionId") { SftpViewModel(it, sessionId) }
    val sessions by appContainer().sessionManager.sessions.collectAsStateWithLifecycle()
    val closed = sessions.none { it.id == sessionId }
    LaunchedEffect(closed) { if (closed) onBack() }
    val path by vm.path.collectAsStateWithLifecycle()
    val files by vm.files.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val transfer by vm.transfer.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showHidden by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<RemoteFile?>(null) }
    var deleting by remember { mutableStateOf<RemoteFile?>(null) }
    var pendingDownload by remember { mutableStateOf<RemoteFile?>(null) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val downloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val file = pendingDownload
        pendingDownload = null
        if (uri != null && file != null) vm.download(file, uri, context)
    }
    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.upload(uri, context)
    }

    var confirmLeave by remember { mutableStateOf(false) }
    val leave = { if (transfer != null) confirmLeave = true else onBack() }
    BackHandler(enabled = path != null && path != "/") { vm.up() }
    // Declared last so it takes precedence while a transfer is running.
    BackHandler(enabled = transfer != null) { confirmLeave = true }

    val visible = if (showHidden) files else files.filterNot { it.name.startsWith(".") }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Files")
                            vm.session?.spec?.subtitle?.let {
                                Text(it, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                    actions = {
                        IconButton(onClick = { vm.goHome() }) { Icon(Icons.Rounded.Home, contentDescription = "Home") }
                        IconButton(onClick = { showHidden = !showHidden }) {
                            Icon(if (showHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "Toggle hidden files")
                        }
                        IconButton(onClick = { newFolder = true }, enabled = path != null) {
                            Icon(Icons.Rounded.CreateNewFolder, contentDescription = "New folder")
                        }
                    },
                )
                path?.let { Breadcrumbs(it, onNavigate = { p -> vm.navigate(p) }) }
            }
        },
        floatingActionButton = {
            if (path != null) {
                ExtendedFloatingActionButton(
                    onClick = { uploadLauncher.launch(arrayOf("*/*")) },
                    icon = { Icon(Icons.Rounded.Upload, null) },
                    text = { Text("Upload") },
                    expanded = transfer == null,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                error != null && path == null -> EmptyState(
                    icon = Icons.Rounded.FolderOpen,
                    title = "SFTP unavailable",
                    message = error ?: "",
                )
                path == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> PullToRefreshBox(isRefreshing = loading, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
                        if (visible.isEmpty() && !loading) {
                            item {
                                val hidden = files.size - visible.size
                                if (hidden > 0) {
                                    EmptyState(
                                        icon = Icons.Rounded.VisibilityOff,
                                        title = "Only hidden files",
                                        message = "$hidden hidden item${if (hidden == 1) "" else "s"}. Tap the eye icon to show them.",
                                    )
                                } else {
                                    EmptyState(icon = Icons.Rounded.FolderOpen, title = "Empty folder", message = "Upload a file or create a folder.")
                                }
                            }
                        }
                        items(visible, key = { it.path }) { file ->
                            FileRow(
                                file = file,
                                onOpen = { if (file.isDirectory) vm.navigate(file.path) else { pendingDownload = file; downloadLauncher.launch(file.name) } },
                                onDownload = { pendingDownload = file; downloadLauncher.launch(file.name) },
                                onRename = { renaming = file },
                                onDelete = { deleting = file },
                                onCopyPath = {
                                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Path", file.path))
                                    Toast.makeText(context, "Path copied", Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(transfer != null, modifier = Modifier.align(Alignment.BottomCenter)) {
                transfer?.let { TransferCard(it, onCancel = { vm.cancelTransfer() }) }
            }
        }
    }

    if (newFolder) {
        NameDialog(title = "New folder", initial = "", confirm = "Create", onDismiss = { newFolder = false }) { name ->
            vm.mkdir(name); newFolder = false
        }
    }
    renaming?.let { f ->
        NameDialog(title = "Rename", initial = f.name, confirm = "Rename", onDismiss = { renaming = null }) { name ->
            vm.rename(f, name); renaming = null
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Cancel transfer?") },
            text = { Text("Leaving this screen stops the ${if (transfer?.upload == true) "upload" else "download"} in progress.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    vm.cancelTransfer()
                    onBack()
                }) { Text("Cancel transfer") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Keep going") } },
        )
    }
    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text("Delete ${f.name}?") },
            text = { Text(if (f.isDirectory) "The folder and everything in it will be permanently deleted." else "The file will be permanently deleted.") },
            confirmButton = { TextButton(onClick = { vm.delete(f); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Breadcrumbs(path: String, onNavigate: (String) -> Unit) {
    val parts = path.split('/').filter { it.isNotEmpty() }
    val scroll = rememberScrollState()
    LaunchedEffect(path) { scroll.animateScrollTo(scroll.maxValue) }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(onClick = { onNavigate("/") }, label = { Text("/", style = MonoSmall) })
        parts.forEachIndexed { i, part ->
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
            val target = "/" + parts.take(i + 1).joinToString("/")
            AssistChip(onClick = { onNavigate(target) }, label = { Text(part, style = MonoSmall) })
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: RemoteFile,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCopyPath: () -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val date = if (file.modifiedAt > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(file.modifiedAt)) else ""
            val size = if (file.isDirectory) "" else Formatter.formatShortFileSize(context, file.size) + "  ·  "
            Text("$size$date", style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = {
            Box {
                Icon(
                    if (file.isDirectory) Icons.Rounded.Folder else Icons.AutoMirrored.Rounded.InsertDriveFile,
                    contentDescription = null,
                    tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
                if (file.isLink) {
                    Icon(Icons.Rounded.Link, null, Modifier.size(14.dp).align(Alignment.BottomEnd), tint = MaterialTheme.colorScheme.tertiary)
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(file.permissions, style = MonoSmall, color = MaterialTheme.colorScheme.outline)
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (!file.isDirectory) {
                            DropdownMenuItem(text = { Text("Download") }, leadingIcon = { Icon(Icons.Rounded.Download, null) }, onClick = { menu = false; onDownload() })
                        }
                        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Copy path") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { menu = false; onCopyPath() })
                        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        },
        modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
    )
}

@Composable
private fun TransferCard(t: Transfer, onCancel: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (t.upload) Icons.Rounded.Upload else Icons.Rounded.Download, null)
                Spacer(Modifier.width(12.dp))
                Text(
                    "${if (t.upload) "Uploading" else "Downloading"} ${t.name}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Formatter.formatShortFileSize(context, t.done) + if (t.total > 0) " / " + Formatter.formatShortFileSize(context, t.total) else "",
                    style = MaterialTheme.typography.labelMedium,
                )
                IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, contentDescription = "Cancel transfer") }
            }
            Spacer(Modifier.size(12.dp))
            if (t.total > 0) {
                LinearProgressIndicator(progress = { (t.done.toFloat() / t.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val valid = name.isNotBlank() && !name.contains('/')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
                isError = name.contains('/'),
            )
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onConfirm(name.trim()) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
