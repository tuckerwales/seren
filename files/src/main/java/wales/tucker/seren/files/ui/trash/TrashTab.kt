package wales.tucker.seren.files.ui.trash

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import wales.tucker.seren.core.ui.EmptyState
import wales.tucker.seren.core.ui.GroupedTile
import wales.tucker.seren.core.ui.SectionHeader
import wales.tucker.seren.core.ui.formatSize
import wales.tucker.seren.core.ui.groupedShape
import wales.tucker.seren.core.ui.relativeTime
import wales.tucker.seren.files.AppContainer
import wales.tucker.seren.files.data.TrashBin
import wales.tucker.seren.files.data.TrashItem
import wales.tucker.seren.files.fs.FileEntry
import wales.tucker.seren.files.ui.appContainer
import wales.tucker.seren.files.ui.common.AccessGate
import wales.tucker.seren.files.ui.common.FileAvatar
import wales.tucker.seren.files.ui.common.displayPath
import wales.tucker.seren.files.ui.containerViewModel
import java.io.File

class TrashViewModel(container: AppContainer) : ViewModel() {
    val items: StateFlow<List<TrashItem>?> = container.trash.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** How many whole days [item] has left before it's deleted for good. */
fun daysLeft(item: TrashItem, now: Long): Int {
    val left = item.deletedAt + TrashBin.KEEP_MILLIS - now
    return ((left + DAY - 1) / DAY).toInt().coerceIn(0, TrashBin.KEEP_DAYS)
}

private const val DAY = 24L * 60 * 60 * 1000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashTab() {
    val container = appContainer()
    val ops = container.operations
    val vm = containerViewModel { TrashViewModel(it) }
    val items = vm.items.collectAsStateWithLifecycle().value
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TrashItem?>(null) }
    var confirmRestore by remember { mutableStateOf<TrashItem?>(null) }
    val volumes = remember { container.storage.volumes() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Trash", fontWeight = FontWeight.SemiBold) },
            actions = {
                if (!items.isNullOrEmpty()) {
                    IconButton(onClick = { confirmEmpty = true }) { Icon(Icons.Rounded.DeleteSweep, "Empty trash") }
                }
            },
            windowInsets = WindowInsets.statusBars,
        )
        AccessGate {
            when {
                items == null -> Unit
                items.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.Delete,
                    title = "Trash is empty",
                    message = "Things you delete stay here for ${TrashBin.KEEP_DAYS} days, so you can restore them.",
                )
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    item {
                        Text(
                            "Items are deleted for good after ${TrashBin.KEEP_DAYS} days.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }
                    item { SectionHeader("In the trash", trailing = formatSize(items.sumOf { it.size })) }
                    itemsIndexed(items, key = { _, item -> item.id }) { i, item ->
                        val entry = FileEntry(item.storedPath, item.name, item.isDirectory, item.size, item.deletedAt)
                        val left = daysLeft(item, container.now())
                        GroupedTile(
                            shape = groupedShape(i, items.size),
                            title = item.name,
                            subtitle = displayPath(File(item.originalFolder), volumes),
                            meta = listOfNotNull(
                                if (item.isDirectory) null else formatSize(item.size),
                                "Deleted ${relativeTime(item.deletedAt, container.now(), midSentence = true)}",
                                if (left <= 1) "last day" else "$left days left",
                            ).joinToString("  ·  "),
                            onClick = { confirmRestore = item },
                            leading = { FileAvatar(entry, thumbnails = false) },
                            menu = { close ->
                                DropdownMenuItem(
                                    text = { Text("Restore") },
                                    leadingIcon = { Icon(Icons.Rounded.RestoreFromTrash, null) },
                                    onClick = { close(); ops.restore(listOf(item)) },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete permanently") },
                                    leadingIcon = { Icon(Icons.Rounded.DeleteForever, null) },
                                    onClick = { close(); confirmDelete = item },
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    if (confirmEmpty && items != null) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, null) },
            title = { Text("Empty the trash?") },
            text = { Text("${ops.items(items.size).replaceFirstChar { it.uppercase() }} will be deleted for good. This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmEmpty = false; ops.emptyTrash() }) { Text("Empty trash") } },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
        )
    }
    confirmRestore?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            icon = { Icon(Icons.Rounded.RestoreFromTrash, null) },
            title = { Text("Restore ${item.name}?") },
            text = { Text("It goes back to ${displayPath(File(item.originalFolder), volumes)}.") },
            confirmButton = { TextButton(onClick = { confirmRestore = null; ops.restore(listOf(item)) }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } },
        )
    }
    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(Icons.Rounded.DeleteForever, null) },
            title = { Text("Delete ${item.name} permanently?") },
            text = { Text("It will be deleted for good${if (item.isDirectory) ", with everything inside" else ""}. This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = null; ops.deleteForever(listOf(item)) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}
