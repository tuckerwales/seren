package wales.tucker.seren.files.ui.recent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wales.tucker.seren.core.ui.EmptyState
import wales.tucker.seren.core.ui.LocalMessenger
import wales.tucker.seren.core.ui.SectionHeader
import wales.tucker.seren.core.ui.groupedShape
import wales.tucker.seren.files.AppContainer
import wales.tucker.seren.files.data.Settings
import wales.tucker.seren.files.fs.FileEntry
import wales.tucker.seren.files.fs.Volume
import wales.tucker.seren.files.ui.Navigator
import wales.tucker.seren.files.ui.appContainer
import wales.tucker.seren.files.ui.common.AccessGate
import wales.tucker.seren.files.ui.common.DetailsDialog
import wales.tucker.seren.files.ui.common.FileRow
import wales.tucker.seren.files.ui.common.Opener
import wales.tucker.seren.files.ui.common.SerenFileMenuItems
import wales.tucker.seren.files.ui.common.displayPath
import wales.tucker.seren.files.ui.containerViewModel
import java.util.Calendar

/** Files changed recently, grouped under a heading such as "Today". */
data class RecentGroup(val title: String, val entries: List<FileEntry>)

class RecentViewModel(private val container: AppContainer) : ViewModel() {
    var groups by mutableStateOf<List<RecentGroup>?>(null)
        private set
    var volumes by mutableStateOf<List<Volume>>(emptyList())
        private set
    private var job: Job? = null

    init {
        viewModelScope.launch { container.operations.changes.drop(1).collect { refresh() } }
    }

    fun refresh() {
        if (!container.storage.hasAccess()) return
        job?.cancel()
        job = viewModelScope.launch {
            val now = container.now()
            val (vols, entries) = withContext(Dispatchers.IO) {
                container.storage.volumes() to container.storage.recent(now - DAYS * DAY, LIMIT)
            }
            volumes = vols
            groups = group(entries, now)
        }
    }

    companion object {
        const val DAYS = 30
        const val LIMIT = 200
        private const val DAY = 24L * 60 * 60 * 1000

        /** Splits [entries] (newest first) into Today, Yesterday, This week and Earlier. */
        fun group(entries: List<FileEntry>, now: Long): List<RecentGroup> {
            val today = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val bounds = listOf("Today" to today, "Yesterday" to today - DAY, "This week" to today - 6 * DAY, "Earlier" to Long.MIN_VALUE)
            return bounds.mapIndexedNotNull { i, (title, from) ->
                val newer = if (i == 0) Long.MAX_VALUE else bounds[i - 1].second
                val inGroup = entries.filter { it.modified in from until newer }
                if (inGroup.isEmpty()) null else RecentGroup(title, inGroup)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentTab(settings: Settings, navigator: Navigator) {
    val vm = containerViewModel { RecentViewModel(it) }
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val itemsText: (Int) -> String = appContainer().operations::items
    var details by remember { mutableStateOf<FileEntry?>(null) }
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose {}
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Recent", fontWeight = FontWeight.SemiBold) }, windowInsets = WindowInsets.statusBars)
        AccessGate {
            val groups = vm.groups
            when {
                groups == null -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
                groups.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.History,
                    title = "Nothing recent",
                    message = "Files you download, save or change show up here for ${RecentViewModel.DAYS} days.",
                )
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    groups.forEach { group ->
                        item(key = "header:${group.title}") { SectionHeader(group.title, trailing = group.entries.size.toString()) }
                        itemsIndexed(group.entries, key = { _, e -> e.path }) { i, entry ->
                            FileRow(
                                entry = entry,
                                shape = groupedShape(i, group.entries.size),
                                thumbnails = settings.showThumbnails,
                                itemsText = itemsText,
                                subtitle = displayPath(entry.file.parentFile ?: entry.file, vm.volumes),
                                onClick = {
                                    if (!Opener.open(context, entry.file)) messenger.show("No app on this device can open ${entry.name}")
                                },
                                menu = { close ->
                                    @Composable
                                    fun item(text: String, icon: ImageVector, action: () -> Unit) {
                                        DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = { close(); action() })
                                    }
                                    item("Open with", Icons.AutoMirrored.Rounded.OpenInNew) {
                                        if (!Opener.open(context, entry.file, choose = true)) messenger.show("No app on this device can open ${entry.name}")
                                    }
                                    SerenFileMenuItems(entry, close)
                                    item("Share", Icons.Rounded.Share) { Opener.share(context, listOf(entry.file)) }
                                    item("Show in folder", Icons.Rounded.FolderOpen) { entry.file.parentFile?.let(navigator.openFolder) }
                                    item("Details", Icons.Rounded.Info) { details = entry }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    details?.let { DetailsDialog(it, itemsText, onDismiss = { details = null }) }
}
