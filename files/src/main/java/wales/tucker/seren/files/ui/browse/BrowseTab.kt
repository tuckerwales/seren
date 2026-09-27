package wales.tucker.seren.files.ui.browse

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import wales.tucker.seren.core.ui.Avatar
import wales.tucker.seren.core.ui.GroupedTile
import wales.tucker.seren.core.ui.SectionHeader
import wales.tucker.seren.core.ui.formatSize
import wales.tucker.seren.core.ui.groupedShape
import wales.tucker.seren.core.ui.theme.accentColor
import wales.tucker.seren.files.AppContainer
import wales.tucker.seren.files.data.Bookmark
import wales.tucker.seren.files.data.Settings
import wales.tucker.seren.files.fs.Categories
import wales.tucker.seren.files.fs.CategorySummary
import wales.tucker.seren.files.fs.QuickFolders
import wales.tucker.seren.files.fs.Volume
import wales.tucker.seren.files.ui.Navigator
import wales.tucker.seren.files.ui.appContainer
import wales.tucker.seren.files.ui.common.AccessGate
import wales.tucker.seren.files.ui.common.SaveBar
import wales.tucker.seren.files.ui.common.accent
import wales.tucker.seren.files.ui.common.icon
import wales.tucker.seren.files.ui.common.displayPath
import wales.tucker.seren.files.ui.containerViewModel
import java.io.File

/** One of the folders people look for first, such as Downloads, if it exists on this device. */
data class QuickFolder(val label: String, val folder: File, val count: Int)

data class BrowseState(
    val volumes: List<Volume> = emptyList(),
    val folders: List<QuickFolder> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    /** Null while the device's files are still being gathered. */
    val categories: List<CategorySummary>? = null,
)

class BrowseViewModel(private val container: AppContainer) : ViewModel() {
    private val volumes = MutableStateFlow<List<Volume>>(emptyList())
    private val categories = MutableStateFlow<List<CategorySummary>?>(null)
    private var categoriesJob: Job? = null

    init {
        viewModelScope.launch { container.operations.changes.drop(1).collect { loadCategories() } }
    }

    val state: StateFlow<BrowseState?> = combine(volumes, container.bookmarks.observe(), container.operations.changes, categories) { vols, marks, _, cats ->
        val primary = vols.firstOrNull { it.primary }
        val folders = primary?.let { v ->
            QuickFolders.mapNotNull { (name, label) ->
                val folder = File(v.root, name)
                if (folder.isDirectory) QuickFolder(label, folder, folder.list()?.count { !it.startsWith(".") } ?: 0) else null
            }
        }.orEmpty()
        // Bookmarks to folders that are gone (deleted, or in the trash) are hidden until they're back.
        BrowseState(vols, folders, marks.filter { File(it.path).isDirectory }, cats)
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { volumes.value = container.storage.volumes() }
        loadCategories()
    }

    private fun loadCategories() {
        if (!container.storage.hasAccess()) return
        categoriesJob?.cancel()
        categoriesJob = viewModelScope.launch(Dispatchers.IO) {
            categories.value = runCatching { Categories.summarize(container.storage.allFiles()) }.getOrNull() ?: categories.value
        }
    }

    fun removeBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            container.bookmarks.delete(bookmark.path)
            container.operations.tell("Removed ${bookmark.name} from bookmarks")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseTab(settings: Settings, navigator: Navigator) {
    val vm = containerViewModel { BrowseViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val ops = appContainer().operations
    val itemsText: (Int) -> String = ops::items
    val incoming by ops.incoming.collectAsStateWithLifecycle()
    // Space and cards change while the app is away (an SD card goes in, a download finishes).
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose {}
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Seren Files", fontWeight = FontWeight.SemiBold) },
            actions = {
                val primary = state?.volumes?.firstOrNull()
                if (primary != null) IconButton(onClick = { navigator.search(primary.root) }) { Icon(Icons.Rounded.Search, "Search") }
            },
            windowInsets = WindowInsets.statusBars,
        )
        AccessGate {
            val s = state ?: return@AccessGate
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)) {
                incoming?.let { files ->
                    item(key = "incoming") { SaveBar(files, itemsText, enabled = false, onSave = null, onCancel = ops::clearIncoming) }
                }
                item { SectionHeader("Storage") }
                s.volumes.forEach { volume ->
                    item(key = "volume:${volume.root.path}") {
                        StorageCard(volume) { navigator.openFolder(volume.root) }
                    }
                }
                if (s.folders.isNotEmpty()) {
                    item { SectionHeader("Folders") }
                    itemsIndexed(s.folders, key = { _, f -> "quick:${f.folder.path}" }) { i, quick ->
                        val (icon, accent) = quickFolderLook(quick.folder.name)
                        GroupedTile(
                            shape = groupedShape(i, s.folders.size),
                            title = quick.label,
                            subtitle = displayPath(quick.folder, s.volumes),
                            onClick = { navigator.openFolder(quick.folder) },
                            leading = { Avatar(quick.label, accentColor(accent), icon = icon) },
                        )
                    }
                }
                if (s.bookmarks.isNotEmpty()) {
                    item { SectionHeader("Bookmarks", trailing = s.bookmarks.size.toString()) }
                    itemsIndexed(s.bookmarks, key = { _, b -> "bookmark:${b.path}" }) { i, bookmark ->
                        GroupedTile(
                            shape = groupedShape(i, s.bookmarks.size),
                            title = bookmark.name,
                            subtitle = displayPath(File(bookmark.path), s.volumes),
                            onClick = { navigator.openFolder(File(bookmark.path)) },
                            leading = { Avatar(bookmark.name, accentColor(0), icon = Icons.Rounded.Bookmark) },
                            menu = { close ->
                                DropdownMenuItem(
                                    text = { Text("Remove bookmark") },
                                    leadingIcon = { Icon(Icons.Rounded.BookmarkRemove, null) },
                                    onClick = { close(); vm.removeBookmark(bookmark) },
                                )
                            },
                        )
                    }
                }
                val cats = s.categories.orEmpty()
                if (cats.isNotEmpty()) {
                    item { SectionHeader("Categories") }
                    itemsIndexed(cats, key = { _, c -> "category:${c.category.name}" }) { i, summary ->
                        GroupedTile(
                            shape = groupedShape(i, cats.size),
                            title = summary.category.label,
                            meta = "${itemsText(summary.count)}  ·  ${formatSize(summary.bytes)}",
                            onClick = { navigator.openCategory(summary.category) },
                            leading = { Avatar(summary.category.label, summary.category.accent, icon = summary.category.icon) },
                        )
                    }
                }
            }
        }
    }
}

private fun quickFolderLook(name: String): Pair<ImageVector, Int> = when (name) {
    "Download" -> Icons.Rounded.Download to 0
    "Documents" -> Icons.Rounded.Description to 3
    "DCIM" -> Icons.Rounded.PhotoCamera to 1
    "Pictures" -> Icons.Rounded.Image to 5
    "Music" -> Icons.Rounded.MusicNote to 6
    "Movies" -> Icons.Rounded.Movie to 2
    else -> Icons.Rounded.Bookmark to 7
}

/** A hero card for a volume: its name, how much is free and a bar of how full it is. */
@Composable
private fun StorageCard(volume: Volume, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                    Icon(
                        if (volume.primary) Icons.Rounded.PhoneAndroid else Icons.Rounded.SdCard,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(volume.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    if (volume.totalBytes > 0) {
                        Text(
                            "${formatSize(volume.freeBytes)} free of ${formatSize(volume.totalBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (volume.totalBytes > 0) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { volume.usedBytes.toFloat() / volume.totalBytes },
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${formatSize(volume.usedBytes)} used",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
