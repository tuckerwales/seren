package wales.tucker.seren.ssh.ui.sftp

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wales.tucker.seren.core.ui.EmptyState
import wales.tucker.seren.core.ui.theme.MonoSmall
import wales.tucker.seren.ssh.AppContainer
import wales.tucker.seren.ssh.session.SftpTransferNotifier
import wales.tucker.seren.ssh.session.TerminalSession
import wales.tucker.seren.ssh.ssh.RemoteFile
import wales.tucker.seren.ssh.ssh.SftpClient
import wales.tucker.seren.ssh.ui.common.appContainer
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import wales.tucker.seren.core.suite.Suite
import wales.tucker.seren.core.suite.SuiteApp
import wales.tucker.seren.core.suite.rememberInstalled
import wales.tucker.seren.ssh.session.SessionState
import wales.tucker.seren.ssh.ui.terminal.SessionPromptDialog
import wales.tucker.seren.ssh.ui.upload.SharedFiles

data class Transfer(val name: String, val upload: Boolean, val done: Long, val total: Long)

/** A finished download: where it was saved, for "Show in Seren Files". */
data class Download(val uri: Uri, val name: String)

class SftpViewModel(private val container: AppContainer, private val sessionId: Int) : ViewModel() {
    val session: TerminalSession? = container.sessionManager.get(sessionId)
    private val notifier: SftpTransferNotifier = container.transferNotifier
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
    val downloads = MutableSharedFlow<Download>(extraBufferCapacity = 4)
    private var home: String = "/"
    private var transferJob: Job? = null

    /** An upload waiting for the user to confirm replacing a file of the same name. */
    data class PendingReplace(val uri: Uri, val name: String)

    private val _pendingReplace = MutableStateFlow<PendingReplace?>(null)
    val pendingReplace = _pendingReplace.asStateFlow()

    init {
        start()
        // Once a dropped connection is back, show the folder again on a fresh channel.
        session?.let { s ->
            viewModelScope.launch {
                var dropped = false
                s.state.collect { state ->
                    when (state) {
                        SessionState.Connected -> if (dropped) {
                            dropped = false
                            val p = _path.value
                            if (client == null || p == null) start() else open(p, record = false)
                        }
                        is SessionState.Disconnected, is SessionState.Failed -> dropped = true
                        SessionState.Connecting -> Unit
                    }
                }
            }
        }
    }

    private fun start() {
        viewModelScope.launch {
            try {
                val s = session ?: throw IllegalStateException("Session is not connected")
                // A session opened to upload shared files may still be connecting, or waiting
                // for a password.
                when (val state = s.state.first { it != SessionState.Connecting }) {
                    is SessionState.Failed -> throw IllegalStateException(state.error)
                    is SessionState.Disconnected -> throw IllegalStateException(state.reason)
                    SessionState.Connected, SessionState.Connecting -> Unit
                }
                val c = s.sftp()
                client = c
                home = c.home()
                navigate(home)
            } catch (e: Exception) {
                _error.value = e.message ?: "Could not start SFTP"
            }
        }
    }

    /**
     * The channel for browsing. It closes with the connection, so it is opened again once the
     * session is connected, rather than failing every later request with "Pipe closed".
     */
    private suspend fun client(): SftpClient {
        client?.takeIf { it.isConnected }?.let { return it }
        val s = session?.takeIf { it.isConnected } ?: throw IOException("Connection lost")
        return s.sftp().also { client = it }
    }

    /** Runs [block] on the browsing channel, and once more on a new one if the channel had closed. */
    private suspend fun <T> browse(block: suspend (SftpClient) -> T): T {
        val c = client()
        return try {
            block(c)
        } catch (e: Exception) {
            if (e is CancellationException || c.isConnected || session?.isConnected != true) throw e
            block(client())
        }
    }

    /** Folders visited before the current one, for Back. */
    private val history = ArrayDeque<String>()
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack = _canGoBack.asStateFlow()

    fun navigate(target: String) = open(target, record = true)

    private fun open(target: String, record: Boolean) {
        if (client == null) return
        viewModelScope.launch {
            _loading.value = true
            try {
                val (resolved, list) = browse { c ->
                    val resolved = runCatching { c.realPath(target) }.getOrDefault(target)
                    resolved to c.list(resolved)
                }
                _files.value = list
                val previous = _path.value
                if (record && previous != null && previous != resolved) history.addLast(previous)
                _canGoBack.value = history.isNotEmpty()
                _path.value = resolved
                _error.value = null
            } catch (e: Exception) {
                // When the connection has gone, the connection lost card says so, and why.
                if (client?.isConnected != false) messages.tryEmit("Cannot open $target: ${e.message}")
                if (_path.value == null) _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun refresh() = _path.value?.let { open(it, record = false) }

    /** Returns to the previously visited folder. */
    fun back() {
        val previous = history.removeLastOrNull() ?: return
        _canGoBack.value = history.isNotEmpty()
        open(previous, record = false)
    }

    fun goHome() = navigate(home)

    private fun op(message: String, block: suspend (SftpClient) -> Unit) {
        if (client == null) return
        viewModelScope.launch {
            try {
                browse(block)
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

    fun chmod(file: RemoteFile, mode: Int) = op("Permissions updated") { it.chmod(file.path, mode) }

    fun download(file: RemoteFile, uri: Uri, context: android.content.Context) {
        val s = session ?: return
        if (transferBusy()) return
        transferJob = viewModelScope.launch {
            publishTransfer(Transfer(file.name, upload = false, done = 0, total = file.size))
            var c: SftpClient? = null
            var complete = false
            var resultMessage: String? = null
            try {
                val channel = s.openSftpChannel().also { c = it }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        channel.download(file.path, out) { done, total ->
                            publishTransfer(Transfer(file.name, false, done, if (total > 0) total else file.size))
                        }
                    } ?: throw IllegalStateException("Cannot write to destination")
                }
                complete = true
                resultMessage = "Downloaded ${file.name}"
                downloads.tryEmit(Download(uri, file.name))
            } catch (e: CancellationException) {
                resultMessage = "Download cancelled"
                messages.tryEmit(resultMessage)
                throw e
            } catch (e: Exception) {
                resultMessage = "Download failed: ${e.message}"
                messages.tryEmit(resultMessage)
            } finally {
                // Don't leave a truncated file behind in the destination folder.
                if (!complete) withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                }
                c?.close()
                endTransfer(resultMessage)
            }
        }
    }

    /** Uploads [uri] into the current folder, asking first if that would replace a file. */
    fun requestUpload(uri: Uri, context: android.content.Context) {
        if (client == null) return
        if (transferBusy()) return
        val dir = _path.value ?: return
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { displayName(context, uri) }
            val exists = name != null && runCatching { browse { it.stat(SftpClient.join(dir, name)) } }.isSuccess
            if (exists) _pendingReplace.value = PendingReplace(uri, name!!) else upload(uri, context)
        }
    }

    fun confirmReplace(context: android.content.Context) {
        val pending = _pendingReplace.value ?: return
        _pendingReplace.value = null
        upload(pending.uri, context)
    }

    fun dismissReplace() {
        _pendingReplace.value = null
        uploadNext()
    }

    /** Files still to upload after the current one, from a share of several. */
    private val queue = ArrayDeque<Uri>()
    private var queueContext: android.content.Context? = null

    /** Uploads [uris] into the current folder one after another, asking before replacing any. */
    fun uploadAll(uris: List<Uri>, context: android.content.Context) {
        queue.addAll(uris)
        queueContext = context.applicationContext
        if (transferJob?.isActive != true && _pendingReplace.value == null) uploadNext()
    }

    private fun uploadNext() {
        val context = queueContext ?: return
        val uri = queue.removeFirstOrNull() ?: return
        requestUpload(uri, context)
    }

    private fun displayName(context: android.content.Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
            if (cur.moveToFirst()) cur.getString(0) else null
        }?.let { sanitizeUploadName(it) } ?: uri.lastPathSegment?.let { sanitizeUploadName(it) }

    private fun upload(uri: Uri, context: android.content.Context) {
        val s = session ?: return
        val dir = _path.value ?: return
        transferJob = viewModelScope.launch {
            var name = sanitizeUploadName(uri.lastPathSegment ?: "upload")
            var c: SftpClient? = null
            var target: String? = null
            // Volatile-style flag: progress callbacks may run on the SFTP thread.
            val written = java.util.concurrent.atomic.AtomicBoolean(false)
            var complete = false
            var resultMessage: String? = null
            try {
                val channel = s.openSftpChannel().also { c = it }
                var size = -1L
                withContext(Dispatchers.IO) {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cur ->
                        if (cur.moveToFirst()) {
                            name = sanitizeUploadName(cur.getString(0) ?: name)
                            size = if (cur.isNull(1)) -1L else cur.getLong(1)
                        }
                    }
                }
                publishTransfer(Transfer(name, upload = true, done = 0, total = size))
                // ContentResolver streams are binder-backed and must be drained on the thread that
                // opened them. JSch reads the upload on SftpClient's dedicated thread, so copy
                // into memory (tiny/normal files) or a cache file first.
                val remotePath = SftpClient.join(dir, name).also { target = it }
                val source = withContext(Dispatchers.IO) { readUploadSource(context, uri, size) }
                try {
                    source.open().use { input ->
                        channel.upload(input, remotePath, source.size) { done, _ ->
                            if (done > 0) written.set(true)
                            publishTransfer(Transfer(name, true, done, source.size))
                        }
                    }
                } finally {
                    source.cleanup()
                }
                complete = true
                resultMessage = "Uploaded $name"
                messages.tryEmit(resultMessage)
            } catch (e: CancellationException) {
                resultMessage = "Upload cancelled"
                messages.tryEmit(resultMessage)
                throw e
            } catch (e: Exception) {
                resultMessage = "Upload failed: ${e.message}"
                messages.tryEmit(resultMessage)
            } finally {
                val partial = target
                val ch = c
                // Only remove a file this upload started writing, never one it failed to open.
                if (!complete && written.get() && partial != null && ch != null && ch.isConnected) {
                    withContext(NonCancellable) {
                        runCatching { ch.delete(ch.stat(partial)) }
                    }
                }
                // Close after any cleanup ops so we do not yank the pipe out from under them.
                c?.close()
                endTransfer(resultMessage)
                // Refresh only while the SSH session is still up; otherwise the connection-lost
                // card already explains why the folder cannot reload.
                if (s.isConnected) refresh()
            }
        }
        transferJob?.invokeOnCompletion { cause ->
            viewModelScope.launch { if (cause is CancellationException) queue.clear() else uploadNext() }
        }
    }


    private fun publishTransfer(t: Transfer?) {
        _transfer.value = t
        if (t == null) return
        val id = session?.id ?: sessionId
        notifier.update(t.name, t.upload, t.done, t.total, id, onCancel = { cancelTransfer() })
    }

    private fun endTransfer(message: String?) {
        _transfer.value = null
        notifier.finish(message)
    }

    private fun transferBusy(): Boolean {
        val busy = transferJob?.isActive == true
        if (busy) messages.tryEmit("Wait for the current transfer to finish")
        return busy
    }

    fun cancelTransfer() {
        queue.clear()
        transferJob?.cancel()
    }

    override fun onCleared() {
        // Don't close the session's shared browsing channel; the session owns that.
        cancelTransfer()
        notifier.clear()
        super.onCleared()
    }
}



/** Strip path components and NULs from a ContentResolver display name before using it remotely. */
private fun sanitizeUploadName(raw: String): String {
    val base = raw.substringAfterLast('/').substringAfterLast('\\').replace("\u0000", "").trim()
    return if (base.isEmpty() || base == "." || base == "..") "upload" else base
}

/** Local bytes or a temp file ready for JSch to read on its SFTP thread. */
private sealed class UploadSource {
    abstract val size: Long
    abstract fun open(): java.io.InputStream
    abstract fun cleanup()

    class Memory(private val bytes: ByteArray) : UploadSource() {
        override val size: Long get() = bytes.size.toLong()
        override fun open() = ByteArrayInputStream(bytes)
        override fun cleanup() = Unit
    }

    class Temp(private val file: File) : UploadSource() {
        override val size: Long get() = file.length()
        override fun open() = file.inputStream()
        override fun cleanup() {
            runCatching { file.delete() }
        }
    }
}

/** Caps in-memory buffering so a huge share cannot blow the heap. */
private const val MAX_IN_MEMORY_UPLOAD = 8L * 1024L * 1024L

private fun readUploadSource(context: android.content.Context, uri: Uri, reportedSize: Long): UploadSource {
    val input = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Cannot read file")
    input.use { stream ->
        if (reportedSize in 0..MAX_IN_MEMORY_UPLOAD) {
            val bytes = if (reportedSize == 0L) ByteArray(0) else stream.readBytes()
            return UploadSource.Memory(bytes)
        }
        // Unknown size or larger than the cap: spool to cache so we never hold it all in RAM.
        val tmp = File.createTempFile("seren-up-", ".bin", context.cacheDir)
        try {
            tmp.outputStream().use { out -> stream.copyTo(out) }
            if (tmp.length() <= MAX_IN_MEMORY_UPLOAD) {
                val bytes = tmp.readBytes()
                tmp.delete()
                return UploadSource.Memory(bytes)
            }
            return UploadSource.Temp(tmp)
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpScreen(
    sessionId: Int,
    onBack: () -> Unit,
    incoming: SharedFiles? = null,
    onIncomingHandled: () -> Unit = {},
) {
    val activity = LocalContext.current as ComponentActivity
    val container = appContainer()
    // Activity-scoped so an upload keeps going (and updating its notification) after leaving Files.
    val vm: SftpViewModel = viewModel(
        viewModelStoreOwner = activity,
        key = "sftp-$sessionId",
        factory = viewModelFactory { initializer { SftpViewModel(container, sessionId) } },
    )
    val sessions by appContainer().sessionManager.sessions.collectAsStateWithLifecycle()
    val closed = sessions.none { it.id == sessionId }
    LaunchedEffect(closed) { if (closed) onBack() }
    val path by vm.path.collectAsStateWithLifecycle()
    val files by vm.files.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val transfer by vm.transfer.collectAsStateWithLifecycle()
    val pendingReplace by vm.pendingReplace.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showHidden by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<RemoteFile?>(null) }
    var permissionsFor by remember { mutableStateOf<RemoteFile?>(null) }
    var deleting by remember { mutableStateOf<RemoteFile?>(null) }
    var pendingDownload by remember { mutableStateOf<RemoteFile?>(null) }

    DisposableEffect(Unit) {
        container.transferNotifier.sftpVisible = true
        onDispose { container.transferNotifier.sftpVisible = false }
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val filesInstalled by rememberUpdatedState(rememberInstalled(SuiteApp.FILES))
    LaunchedEffect(Unit) {
        vm.downloads.collect { d ->
            if (!filesInstalled) {
                snackbar.showSnackbar("Downloaded ${d.name}")
                return@collect
            }
            val result = snackbar.showSnackbar("Downloaded ${d.name}", actionLabel = "Show in Seren Files", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed && !Suite.launch(context, Suite.revealIntent(d.uri, d.name))) {
                snackbar.showSnackbar("Seren Files couldn't show ${d.name}")
            }
        }
    }
    // The connection may still be asking for a password when this screen opens it for a share.
    val prompt = vm.session?.prompt?.collectAsStateWithLifecycle()?.value
    SessionPromptDialog(prompt)
    val lost = when (val state = vm.session?.state?.collectAsStateWithLifecycle()?.value) {
        is SessionState.Disconnected -> state.reason
        is SessionState.Failed -> state.error
        else -> null
    }

    val downloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val file = pendingDownload
        pendingDownload = null
        if (uri != null && file != null) vm.download(file, uri, context)
    }
    fun startDownload(file: RemoteFile) {
        if (transfer != null) {
            scope.launch { snackbar.showSnackbar("Wait for the current transfer to finish") }
        } else {
            pendingDownload = file
            downloadLauncher.launch(file.name)
        }
    }
    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.requestUpload(uri, context)
    }

    val leave = { onBack() }
    val canGoBack by vm.canGoBack.collectAsStateWithLifecycle()
    BackHandler(enabled = canGoBack) { vm.back() }

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
            // One transfer at a time: the progress card takes the button's place.
            if (path != null && transfer == null && incoming == null && lost == null) {
                ExtendedFloatingActionButton(
                    onClick = { uploadLauncher.launch(arrayOf("*/*")) },
                    icon = { Icon(Icons.Rounded.Upload, null) },
                    text = { Text("Upload") },
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
                                onOpen = { if (file.isDirectory) vm.navigate(file.path) else startDownload(file) },
                                onDownload = { startDownload(file) },
                                onRename = { renaming = file },
                                onPermissions = { permissionsFor = file },
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
            if (lost != null && path != null && transfer == null) {
                ConnectionLostCard(
                    reason = lost,
                    onReconnect = { vm.session?.reconnect() },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            } else if (incoming != null && path != null && transfer == null) {
                IncomingCard(
                    files = incoming,
                    folder = path.orEmpty(),
                    onCancel = onIncomingHandled,
                    onUpload = {
                        vm.uploadAll(incoming.uris, context)
                        onIncomingHandled()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
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
    permissionsFor?.let { f ->
        PermissionsDialog(
            file = f,
            onDismiss = { permissionsFor = null },
            onConfirm = { mode -> vm.chmod(f, mode); permissionsFor = null },
        )
    }
    pendingReplace?.let { p ->
        AlertDialog(
            onDismissRequest = { vm.dismissReplace() },
            title = { Text("Replace ${p.name}?") },
            text = { Text("A file with this name already exists in this folder. Uploading will overwrite it.") },
            confirmButton = { TextButton(onClick = { vm.confirmReplace(context) }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { vm.dismissReplace() }) { Text("Cancel") } },
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
    onPermissions: () -> Unit,
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
                        DropdownMenuItem(text = { Text("Permissions") }, leadingIcon = { Icon(Icons.Rounded.Lock, null) }, onClick = { menu = false; onPermissions() })
                        DropdownMenuItem(text = { Text("Copy path") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { menu = false; onCopyPath() })
                        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        },
        modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
    )
}

/** Files shared with Seren SSH, waiting for people to open the folder they belong in. */
@Composable
private fun IncomingCard(files: SharedFiles, folder: String, onCancel: () -> Unit, onUpload: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(files.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "Open the folder they belong in, then upload them to it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(folder, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onUpload) {
                    Icon(Icons.Rounded.Upload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Upload here")
                }
            }
        }
    }
}

@Composable
private fun ConnectionLostCard(reason: String, onReconnect: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Connection lost", style = MaterialTheme.typography.titleMedium)
            Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onReconnect) { Text("Reconnect") }
            }
        }
    }
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

@Composable
private fun PermissionsDialog(file: RemoteFile, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var mode by remember(file.path) { mutableIntStateOf(file.mode and 0x1FF) }
    fun bit(mask: Int) = (mode and mask) != 0
    fun toggle(mask: Int, on: Boolean) {
        mode = if (on) mode or mask else mode and mask.inv()
    }
    val octal = mode.toString(8).padStart(3, '0')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(file.name, style = MaterialTheme.typography.bodyMedium)
                Text("Mode $octal", style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(
                    "Owner" to listOf(0b100_000_000 to "Read", 0b010_000_000 to "Write", 0b001_000_000 to "Execute"),
                    "Group" to listOf(0b100_000 to "Read", 0b010_000 to "Write", 0b001_000 to "Execute"),
                    "Others" to listOf(0b100 to "Read", 0b010 to "Write", 0b001 to "Execute"),
                ).forEach { (label, bits) ->
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        bits.forEach { (mask, name) ->
                            FilterChip(
                                selected = bit(mask),
                                onClick = { toggle(mask, !bit(mask)) },
                                label = { Text(name) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(mode) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

