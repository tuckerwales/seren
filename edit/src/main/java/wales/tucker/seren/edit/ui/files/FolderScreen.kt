package wales.tucker.seren.edit.ui.files

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import wales.tucker.seren.core.ui.Avatar
import wales.tucker.seren.core.ui.EmptyState
import wales.tucker.seren.core.ui.GroupedTile
import wales.tucker.seren.core.ui.formatSize
import wales.tucker.seren.core.ui.groupedShape
import wales.tucker.seren.core.ui.relativeTime
import wales.tucker.seren.core.ui.theme.MonoSmall
import wales.tucker.seren.edit.AppContainer
import wales.tucker.seren.edit.document.DocumentStore
import wales.tucker.seren.edit.document.FolderEntry
import wales.tucker.seren.edit.ui.containerViewModel

sealed interface FolderContents {
    data object Loading : FolderContents
    data class Ready(val entries: List<FolderEntry>) : FolderContents
    data class Failed(val reason: String) : FolderContents
}

class FolderViewModel(private val container: AppContainer, val tree: Uri, val documentId: String) : ViewModel() {
    var contents by mutableStateOf<FolderContents>(FolderContents.Loading)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            contents = runCatching { container.documents.list(tree, documentId) }
                .fold({ FolderContents.Ready(it) }, { FolderContents.Failed("Seren Edit no longer has access to this folder, or it was moved.") })
        }
    }

    /** Creates [name] in this folder; returns its URI, or null with the reason in [onError]. */
    suspend fun create(name: String, onError: (String) -> Unit): Uri? =
        runCatching { container.documents.createFile(tree, documentId, name) }
            .onSuccess { refresh() }
            .onFailure { onError(it.message ?: "Couldn't create $name") }
            .getOrNull()
}

/**
 * The files and folders inside folder [documentId] of a folder tree the user added. [title] is the
 * folder's name and [path] where it is, shown as the mono subtitle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    tree: Uri,
    documentId: String,
    title: String,
    path: String,
    onBack: () -> Unit,
    onOpenFile: (Uri) -> Unit,
    onOpenFolder: (documentId: String, name: String) -> Unit,
) {
    val vm = containerViewModel(key = "$tree/$documentId") { FolderViewModel(it, tree, documentId) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var creating by rememberSaveable { mutableStateOf(false) }

    // Pick up files created or changed while the editor was open.
    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(path, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                windowInsets = WindowInsets.statusBars,
            )
        },
        floatingActionButton = {
            if (vm.contents is FolderContents.Ready) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(Icons.AutoMirrored.Rounded.NoteAdd, null) },
                    text = { Text("New file") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val contents = vm.contents) {
                FolderContents.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is FolderContents.Failed -> EmptyState(
                    icon = Icons.Rounded.ErrorOutline,
                    title = "Couldn't open $title",
                    message = contents.reason,
                    action = { Button(onClick = vm::refresh) { Text("Retry") } },
                )
                is FolderContents.Ready -> if (contents.entries.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.FolderOpen,
                        title = "Empty folder",
                        message = "Create a file here with New file.",
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
                        itemsIndexed(contents.entries, key = { _, e -> e.documentId }) { index, entry ->
                            EntryTile(entry, index, contents.entries.size) {
                                if (entry.isFolder) onOpenFolder(entry.documentId, entry.name) else onOpenFile(entry.uri)
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        NewFileDialog(
            onDismiss = { creating = false },
            onCreate = { name ->
                creating = false
                scope.launch {
                    vm.create(name) { reason -> scope.launch { snackbar.showSnackbar(reason) } }?.let(onOpenFile)
                }
            },
        )
    }
}

@Composable
private fun EntryTile(entry: FolderEntry, index: Int, count: Int, onClick: () -> Unit) {
    val color = if (entry.isFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    GroupedTile(
        shape = groupedShape(index, count),
        title = entry.name,
        subtitle = if (entry.isFolder) null else formatSize(entry.size),
        meta = entry.lastModified.takeIf { it > 0 }?.let { "Changed ${relativeTime(it, midSentence = true)}" },
        onClick = onClick,
        leading = { Avatar(entry.name, color, icon = if (entry.isFolder) Icons.Rounded.Folder else Icons.Rounded.Description) },
    )
}

@Composable
private fun NewFileDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Rounded.NoteAdd, null) },
        title = { Text("New file") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("File name") },
                placeholder = { Text("notes.txt", style = MonoSmall) },
                singleLine = true,
                textStyle = MonoSmall.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
            )
        },
        confirmButton = { TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The storage path to show under a folder's name, from its tree and document id. */
internal fun folderPath(tree: Uri, documentId: String, fallback: String): String =
    if (tree.authority == "com.android.externalstorage.documents") DocumentStore.storagePath(documentId, parentOnly = false) else fallback
