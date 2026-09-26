package wales.tucker.terminal.ui.snippets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import wales.tucker.terminal.data.Snippet
import wales.tucker.terminal.ui.common.Avatar
import wales.tucker.terminal.ui.common.EmptyState
import wales.tucker.terminal.ui.common.containerViewModel
import wales.tucker.terminal.ui.theme.MonoSmall

class SnippetsViewModel(private val container: AppContainer) : ViewModel() {
    val snippets: StateFlow<List<Snippet>> = container.database.snippetDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(snippet: Snippet) = viewModelScope.launch { container.database.snippetDao().upsert(snippet) }
    fun delete(snippet: Snippet) = viewModelScope.launch { container.database.snippetDao().delete(snippet) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetsTab() {
    val vm = containerViewModel { SnippetsViewModel(it) }
    val snippets by vm.snippets.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Snippet?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Snippets", fontWeight = FontWeight.SemiBold) }, windowInsets = WindowInsets.statusBars) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Snippet(name = "", command = "") },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("New snippet") },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (snippets.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = Icons.Rounded.Terminal,
                    title = "No snippets",
                    message = "Save commands you run often and send them to any session from the terminal menu.",
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(snippets, key = { it.id }) { s ->
                    ListItem(
                        headlineContent = { Text(s.name) },
                        supportingContent = { Text(s.command, style = MonoSmall, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Avatar(s.name, MaterialTheme.colorScheme.secondary, icon = Icons.Rounded.Terminal) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (s.autoRun) Icon(Icons.AutoMirrored.Rounded.KeyboardReturn, contentDescription = "Runs immediately", tint = MaterialTheme.colorScheme.outline)
                                IconButton(onClick = { vm.delete(s) }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete") }
                            }
                        },
                        modifier = Modifier.clickable { editing = s },
                    )
                }
            }
        }
    }

    editing?.let { snippet ->
        var name by remember(snippet) { mutableStateOf(snippet.name) }
        var command by remember(snippet) { mutableStateOf(snippet.command) }
        var autoRun by remember(snippet) { mutableStateOf(snippet.autoRun) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (snippet.id == 0L) "New snippet" else "Edit snippet") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = { Text("Command") },
                        textStyle = MonoSmall.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { autoRun = !autoRun }) {
                        Checkbox(checked = autoRun, onCheckedChange = { autoRun = it })
                        Text("Press Enter after sending")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = command.isNotBlank(),
                    onClick = {
                        vm.save(snippet.copy(name = name.trim().ifBlank { command.trim().take(32) }, command = command, autoRun = autoRun))
                        editing = null
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}
