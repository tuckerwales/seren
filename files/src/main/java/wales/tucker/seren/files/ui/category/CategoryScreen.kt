package wales.tucker.seren.files.ui.category

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wales.tucker.seren.core.ui.EmptyState
import wales.tucker.seren.core.ui.LocalMessenger
import wales.tucker.seren.core.ui.SectionHeader
import wales.tucker.seren.core.ui.formatSize
import wales.tucker.seren.core.ui.groupedShape
import wales.tucker.seren.files.AppContainer
import wales.tucker.seren.files.data.Settings
import wales.tucker.seren.files.fs.Categories
import wales.tucker.seren.files.fs.Category
import wales.tucker.seren.files.fs.FileEntry
import wales.tucker.seren.files.fs.Volume
import wales.tucker.seren.files.ui.Navigator
import wales.tucker.seren.files.ui.appContainer
import wales.tucker.seren.files.ui.common.AccessGate
import wales.tucker.seren.files.ui.common.DeleteDialog
import wales.tucker.seren.files.ui.common.DetailsDialog
import wales.tucker.seren.files.ui.common.FileRow
import wales.tucker.seren.files.ui.common.FoundFileMenuItems
import wales.tucker.seren.files.ui.common.OperationCard
import wales.tucker.seren.files.ui.common.Opener
import wales.tucker.seren.files.ui.common.displayPath
import wales.tucker.seren.files.ui.common.icon
import wales.tucker.seren.files.ui.containerViewModel

class CategoryViewModel(private val container: AppContainer, private val category: Category) : ViewModel() {
    /** The category's files, or null while they're being found. */
    var entries by mutableStateOf<List<FileEntry>?>(null)
        private set
    var volumes by mutableStateOf<List<Volume>>(emptyList())
        private set

    /** Paths of the picked files; picking mode is on while this isn't empty. */
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    private var job: Job? = null

    init {
        viewModelScope.launch { container.operations.changes.drop(1).collect { refresh() } }
    }

    fun refresh() {
        if (!container.storage.hasAccess()) return
        job?.cancel()
        job = viewModelScope.launch {
            val (vols, found) = withContext(Dispatchers.IO) {
                // The media store can lag behind, so files that have gone are left out.
                container.storage.volumes() to Categories.list(container.storage.allFiles(), category).filter { it.file.isFile }
            }
            volumes = vols
            entries = found
            selected = selected intersect found.map { it.path }.toSet()
        }
    }

    fun toggle(entry: FileEntry) {
        selected = if (entry.path in selected) selected - entry.path else selected + entry.path
    }

    fun selectAll() {
        selected = entries.orEmpty().map { it.path }.toSet()
    }

    fun clearSelection() {
        selected = emptySet()
    }

    fun selectedEntries(): List<FileEntry> = entries.orEmpty().filter { it.path in selected }
}

/** Every file of one kind on the device, such as all photos, or the biggest files taking up space. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(category: Category, settings: Settings, navigator: Navigator) {
    val container = appContainer()
    val ops = container.operations
    val vm = containerViewModel(key = "category:${category.name}") { CategoryViewModel(it, category) }
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val operation by ops.current.collectAsStateWithLifecycle()
    val itemsText: (Int) -> String = ops::items
    var details by remember { mutableStateOf<FileEntry?>(null) }
    var deleting by remember { mutableStateOf<List<FileEntry>?>(null) }
    val picking = vm.selected.isNotEmpty()

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose {}
    }
    BackHandler(enabled = picking) { vm.clearSelection() }

    Scaffold(
        topBar = {
            if (picking) {
                TopAppBar(
                    title = { Text("${vm.selected.size} selected") },
                    navigationIcon = { IconButton(onClick = vm::clearSelection) { Icon(Icons.Rounded.Close, "Clear selection") } },
                    actions = {
                        IconButton(onClick = vm::selectAll) { Icon(Icons.Rounded.SelectAll, "Select all") }
                        IconButton(onClick = { Opener.share(context, vm.selectedEntries().map { it.file }) }) { Icon(Icons.Rounded.Share, "Share") }
                        IconButton(onClick = { deleting = vm.selectedEntries() }) { Icon(Icons.Rounded.Delete, "Delete") }
                    },
                    windowInsets = WindowInsets.statusBars,
                )
            } else {
                TopAppBar(
                    title = { Text(category.label) },
                    navigationIcon = { IconButton(onClick = navigator.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                    windowInsets = WindowInsets.statusBars,
                )
            }
        },
        bottomBar = {
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                operation?.let { OperationCard(it, onCancel = ops::cancel) }
            }
        },
        snackbarHost = { SnackbarHost(messenger.host) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        AccessGate(Modifier.padding(padding)) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                val entries = vm.entries
                when {
                    entries == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    entries.isEmpty() -> EmptyState(
                        icon = category.icon,
                        title = "No ${category.label.lowercase()}",
                        message = if (category == Category.LARGE) {
                            "Files of ${formatSize(Categories.LARGE_BYTES)} or more show up here, biggest first."
                        } else {
                            "${category.label} anywhere on this device show up here, newest first."
                        },
                    )
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                        item {
                            Text(
                                if (category == Category.LARGE) "Biggest first. Long press to pick several." else "Newest first. Long press to pick several.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            )
                        }
                        item {
                            val count = if (entries.size >= Categories.LIMIT) "${entries.size}+" else entries.size.toString()
                            SectionHeader("$count files", trailing = formatSize(entries.sumOf { it.size }))
                        }
                        itemsIndexed(entries, key = { _, e -> e.path }) { i, entry ->
                            FileRow(
                                entry = entry,
                                shape = groupedShape(i, entries.size),
                                thumbnails = settings.showThumbnails,
                                itemsText = itemsText,
                                selected = entry.path in vm.selected,
                                subtitle = displayPath(entry.file.parentFile ?: entry.file, vm.volumes),
                                onClick = {
                                    when {
                                        picking -> vm.toggle(entry)
                                        !Opener.open(context, entry.file) -> messenger.show("No app on this device can open ${entry.name}")
                                    }
                                },
                                onLongClick = { vm.toggle(entry) },
                                menu = if (picking) null else { close ->
                                    FoundFileMenuItems(
                                        entry,
                                        close,
                                        navigator,
                                        onDetails = { details = entry },
                                        onDelete = { deleting = listOf(entry) },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    details?.let { DetailsDialog(it, itemsText, onDismiss = { details = null }) }
    deleting?.let { doomed ->
        DeleteDialog(files = doomed.map { it.file }, useTrash = settings.useTrash, itemsText = itemsText, onDismiss = { deleting = null }) {
            deleting = null
            vm.clearSelection()
            ops.delete(doomed.map { it.file }, permanently = !settings.useTrash)
        }
    }
}
