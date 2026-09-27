package wales.tucker.seren.files.pick

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wales.tucker.seren.core.ui.Avatar
import wales.tucker.seren.core.ui.EmptyState
import wales.tucker.seren.core.ui.GroupedTile
import wales.tucker.seren.core.ui.LocalMessenger
import wales.tucker.seren.core.ui.LockScreen
import wales.tucker.seren.core.ui.Messenger
import wales.tucker.seren.core.ui.SectionHeader
import wales.tucker.seren.core.ui.formatSize
import wales.tucker.seren.core.ui.groupedShape
import wales.tucker.seren.core.ui.theme.accentColor
import wales.tucker.seren.files.AppContainer
import wales.tucker.seren.files.data.Bookmark
import wales.tucker.seren.files.data.Settings
import wales.tucker.seren.files.fs.AndroidStorage
import wales.tucker.seren.files.fs.FileEntry
import wales.tucker.seren.files.fs.Listing
import wales.tucker.seren.files.fs.QuickFolders
import wales.tucker.seren.files.fs.Volume
import wales.tucker.seren.files.ui.LocalStorageAccess
import wales.tucker.seren.files.ui.StorageAccessState
import wales.tucker.seren.files.ui.appContainer
import wales.tucker.seren.files.ui.common.AccessGate
import wales.tucker.seren.files.ui.common.FileRow
import wales.tucker.seren.files.ui.common.displayPath
import wales.tucker.seren.files.ui.common.folderTitle
import wales.tucker.seren.files.ui.containerViewModel
import java.io.File
import java.io.IOException

/** A place to start browsing from: a volume, one of the usual folders, or a bookmark. */
data class PickerPlace(val title: String, val folder: File, val volume: Volume? = null, val bookmark: Boolean = false)

class PickerViewModel(private val container: AppContainer) : ViewModel() {
    /** The open folder, or null on the first page of places to start from. */
    var folder by mutableStateOf<File?>(null)
        private set
    private val history = ArrayDeque<File?>()

    var entries by mutableStateOf<List<FileEntry>?>(null)
        private set
    var failure by mutableStateOf<String?>(null)
        private set
    var places by mutableStateOf<List<PickerPlace>>(emptyList())
        private set
    var volumes by mutableStateOf<List<Volume>>(emptyList())
        private set

    /** Paths picked so far, when the app takes several files. */
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    private var job: Job? = null

    fun loadPlaces() {
        viewModelScope.launch {
            val marks: List<Bookmark> = container.bookmarks.observe().first()
            val (vols, found) = withContext(Dispatchers.IO) {
                val vols = container.storage.volumes()
                val primary = vols.firstOrNull { it.primary }
                val quick = primary?.let { v ->
                    QuickFolders.mapNotNull { (name, label) -> File(v.root, name).takeIf { it.isDirectory }?.let { PickerPlace(label, it) } }
                }.orEmpty()
                vols to vols.map { PickerPlace(it.name, it.root, volume = it) } + quick +
                    marks.filter { File(it.path).isDirectory }.map { PickerPlace(it.name, File(it.path), bookmark = true) }
            }
            volumes = vols
            places = found
        }
    }

    fun open(dir: File) {
        history.addLast(folder)
        show(dir)
    }

    /** False on the first page, where Back cancels. */
    fun back(): Boolean {
        if (folder == null) return false
        show(history.removeLastOrNull())
        return true
    }

    private fun show(dir: File?) {
        folder = dir
        entries = null
        failure = null
        reload()
    }

    fun reload() {
        val dir = folder ?: return
        job?.cancel()
        job = viewModelScope.launch {
            val settings = container.settings.settings.first()
            val result = withContext(Dispatchers.IO) {
                try {
                    Result.success(Listing.list(dir, settings.sortOrder, settings.showHidden))
                } catch (e: IOException) {
                    Result.failure(e)
                } catch (e: SecurityException) {
                    Result.failure(IOException("Android keeps ${dir.name} private to the apps it belongs to"))
                }
            }
            if (dir != folder) return@launch
            entries = result.getOrNull()
            failure = result.exceptionOrNull()?.message
        }
    }

    fun toggle(entry: FileEntry) {
        selected = if (entry.path in selected) selected - entry.path else selected + entry.path
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerUi(
    request: PickRequest,
    settings: Settings,
    locked: Boolean,
    onUnlock: () -> Unit,
    onPick: (List<File>) -> Unit,
    onCancel: () -> Unit,
) {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messenger = remember { Messenger(scope) }
    var granted by remember { mutableStateOf(container.storage.hasAccess()) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = container.storage.hasAccess()
    }
    val access = StorageAccessState(granted) {
        val intent = AndroidStorage.accessSettingsIntent(context)
        if (intent != null) {
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                messenger.show("Allow all files access for Seren Files in the system settings")
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            permissions.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }
    val vm = containerViewModel { PickerViewModel(it) }
    LifecycleResumeEffect(Unit) {
        granted = container.storage.hasAccess()
        if (granted) {
            vm.loadPlaces()
            vm.reload()
        }
        onPauseOrDispose {}
    }

    CompositionLocalProvider(LocalMessenger provides messenger, LocalStorageAccess provides access) {
        if (locked) {
            LockScreen(appName = "Seren Files", message = "Authenticate to choose a file", onUnlock = onUnlock)
            return@CompositionLocalProvider
        }
        BackHandler { if (!vm.back()) onCancel() }
        val folder = vm.folder
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(request.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (folder != null) {
                                Text(
                                    displayPath(folder, vm.volumes),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (folder == null) {
                            IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Cancel") }
                        } else {
                            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                        }
                    },
                    windowInsets = WindowInsets.statusBars,
                )
            },
            bottomBar = {
                if (request.multiple && vm.selected.isNotEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${vm.selected.size} chosen", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { onPick(vm.selected.map(::File)) }) { Text("Choose") }
                        }
                    }
                }
            },
            snackbarHost = { androidx.compose.material3.SnackbarHost(messenger.host) },
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            AccessGate(Modifier.padding(padding)) {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    if (folder == null) {
                        Places(vm.places, onOpen = vm::open)
                    } else {
                        val entries = vm.entries
                        val failure = vm.failure
                        when {
                            failure != null -> EmptyState(
                                icon = Icons.Rounded.ErrorOutline,
                                title = "Couldn't open ${folderTitle(folder, vm.volumes)}",
                                message = failure,
                            )
                            entries == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                            else -> {
                                // Only files the app can take are shown; folders always are, to look inside.
                                val shown = entries.filter { it.isDirectory || request.accepts(it.name) }
                                if (shown.isEmpty()) {
                                    EmptyState(
                                        icon = Icons.Rounded.FolderOpen,
                                        title = "Nothing to choose here",
                                        message = "This folder has no files of the kind the app asked for.",
                                    )
                                } else {
                                    PickList(
                                        entries = shown,
                                        selected = vm.selected,
                                        thumbnails = settings.showThumbnails,
                                        itemsText = container.operations::items,
                                        onClick = { entry ->
                                            when {
                                                entry.isDirectory -> vm.open(entry.file)
                                                request.multiple -> vm.toggle(entry)
                                                else -> onPick(listOf(entry.file))
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Places(places: List<PickerPlace>, onOpen: (File) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)) {
        listOf(
            "Storage" to places.filter { it.volume != null },
            "Folders" to places.filter { it.volume == null && !it.bookmark },
            "Bookmarks" to places.filter { it.bookmark },
        ).forEach { (header, group) ->
            if (group.isEmpty()) return@forEach
            item(key = "header:$header") { SectionHeader(header) }
            itemsIndexed(group, key = { _, p -> "$header:${p.folder.path}" }) { i, place ->
                val volume = place.volume
                GroupedTile(
                    shape = groupedShape(i, group.size),
                    title = place.title,
                    meta = volume?.takeIf { it.totalBytes > 0 }?.let { "${formatSize(it.freeBytes)} free of ${formatSize(it.totalBytes)}" },
                    onClick = { onOpen(place.folder) },
                    leading = {
                        val icon = when {
                            volume != null -> if (volume.primary) Icons.Rounded.PhoneAndroid else Icons.Rounded.SdCard
                            place.bookmark -> Icons.Rounded.Bookmark
                            else -> Icons.Rounded.FolderOpen
                        }
                        Avatar(place.title, accentColor(if (place.bookmark) 0 else 3), icon = icon)
                    },
                )
            }
        }
    }
}

@Composable
private fun PickList(
    entries: List<FileEntry>,
    selected: Set<String>,
    thumbnails: Boolean,
    itemsText: (Int) -> String,
    onClick: (FileEntry) -> Unit,
) {
    val folders = entries.filter { it.isDirectory }
    val files = entries.filter { !it.isDirectory }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        listOf("Folders" to folders, "Files" to files).forEach { (header, group) ->
            if (group.isEmpty()) return@forEach
            item(key = "header:$header") { SectionHeader(header, trailing = group.size.toString()) }
            itemsIndexed(group, key = { _, e -> e.path }) { i, entry ->
                FileRow(
                    entry = entry,
                    shape = groupedShape(i, group.size),
                    thumbnails = thumbnails,
                    itemsText = itemsText,
                    selected = entry.path in selected,
                    onClick = { onClick(entry) },
                )
            }
        }
    }
}
