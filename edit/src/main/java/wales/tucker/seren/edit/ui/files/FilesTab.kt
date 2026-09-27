package wales.tucker.seren.edit.ui.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import wales.tucker.seren.core.ui.Avatar
import wales.tucker.seren.core.ui.GroupedTile
import wales.tucker.seren.core.ui.SectionHeader
import wales.tucker.seren.core.ui.groupedShape
import wales.tucker.seren.core.ui.pillFieldColors
import wales.tucker.seren.core.ui.theme.AccentColors
import wales.tucker.seren.core.ui.theme.MonoSmall
import wales.tucker.seren.core.ui.theme.accentColor
import wales.tucker.seren.edit.AppContainer
import wales.tucker.seren.edit.data.Folder
import wales.tucker.seren.edit.document.DocumentStore
import wales.tucker.seren.edit.ui.containerViewModel

class FilesViewModel(private val container: AppContainer) : ViewModel() {
    val folders: StateFlow<List<Folder>> = container.database.folders().observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addFolder(tree: Uri) {
        viewModelScope.launch {
            val store = container.documents
            store.persistAccess(tree)
            val dao = container.database.folders()
            val color = dao.get(tree.toString())?.color ?: AccentColors.indices.random()
            dao.upsert(Folder(tree.toString(), store.treeName(tree), store.location(tree), color, System.currentTimeMillis()))
        }
    }

    fun forget(folder: Folder) {
        viewModelScope.launch {
            container.documents.releaseAccess(Uri.parse(folder.uri))
            container.database.folders().delete(folder.uri)
        }
    }
}

/**
 * Creates a document whose MIME type matches the name's extension, so storage providers keep the
 * name as typed rather than appending ".txt".
 */
class CreateTextDocument : ActivityResultContracts.CreateDocument("text/plain") {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).setType(DocumentStore.mimeTypeFor(input))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesTab(onOpenFile: (Uri) -> Unit, onOpenFolder: (Folder) -> Unit) {
    val vm = containerViewModel { FilesViewModel(it) }
    val folders by vm.folders.collectAsStateWithLifecycle()
    var pendingForget by remember { mutableStateOf<Folder?>(null) }
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onOpenFile) }
    val createFile = rememberLauncherForActivityResult(CreateTextDocument()) { uri -> uri?.let(onOpenFile) }
    val addFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::addFolder) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Seren Edit", fontWeight = FontWeight.SemiBold) }, windowInsets = WindowInsets.statusBars)
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { openFile.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Rounded.FileOpen, null) },
                text = { Text("Open file") },
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
            item { NewFileCard(onCreate = { name -> createFile.launch(name) }) }
            item { SectionHeader("Folders", trailing = folders.size.takeIf { it > 0 }?.toString()) }
            val count = folders.size + 1
            itemsIndexed(folders, key = { _, f -> f.uri }) { index, folder ->
                GroupedTile(
                    shape = groupedShape(index, count),
                    title = folder.name,
                    subtitle = folder.location,
                    onClick = { onOpenFolder(folder) },
                    leading = { Avatar(folder.name, accentColor(folder.color), icon = Icons.Rounded.Folder) },
                    menu = { close ->
                        DropdownMenuItem(
                            text = { Text("Forget folder") },
                            leadingIcon = { Icon(Icons.Rounded.LinkOff, null) },
                            onClick = { close(); pendingForget = folder },
                        )
                    },
                )
            }
            item(key = "add-folder") {
                GroupedTile(
                    shape = groupedShape(count - 1, count),
                    title = "Add folder",
                    subtitle = null,
                    meta = if (folders.isEmpty()) "Browse and edit the files in a folder you choose" else null,
                    onClick = { addFolder.launch(null) },
                    leading = { Avatar("", MaterialTheme.colorScheme.primary, icon = Icons.Rounded.CreateNewFolder) },
                )
            }
        }
    }

    pendingForget?.let { folder ->
        AlertDialog(
            onDismissRequest = { pendingForget = null },
            icon = { Icon(Icons.Rounded.LinkOff, null) },
            title = { Text("Forget ${folder.name}?") },
            text = { Text("Seren Edit will no longer have access to this folder. The folder and its files stay where they are.") },
            confirmButton = { TextButton(onClick = { vm.forget(folder); pendingForget = null }) { Text("Forget") } },
            dismissButton = { TextButton(onClick = { pendingForget = null }) { Text("Cancel") } },
        )
    }
}

/** The hero card: name a new file, then pick where to save it. */
@Composable
private fun NewFileCard(onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    fun submit() {
        onCreate(name.trim().ifEmpty { "untitled.txt" })
        name = ""
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.NoteAdd, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("New file", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Name it, then choose where to save it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            val monoField = MonoSmall.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize)
            TextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("notes.txt", style = monoField, color = MaterialTheme.colorScheme.outline) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                trailingIcon = {
                    FilledIconButton(onClick = { submit() }, modifier = Modifier.padding(end = 6.dp).size(44.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Create")
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
