package wales.tucker.seren.files.ui.folder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import wales.tucker.seren.core.ui.EmptyState
import wales.tucker.seren.core.ui.LocalMessenger
import wales.tucker.seren.core.ui.SectionHeader
import wales.tucker.seren.core.ui.copyToClipboard
import wales.tucker.seren.core.ui.groupedShape
import wales.tucker.seren.core.ui.pillFieldColors
import wales.tucker.seren.core.ui.theme.MonoSmall
import wales.tucker.seren.files.data.Settings
import wales.tucker.seren.files.fs.Archives
import wales.tucker.seren.files.fs.FileEntry
import wales.tucker.seren.files.fs.SortBy
import wales.tucker.seren.files.fs.SortOrder
import wales.tucker.seren.files.fs.Volume
import wales.tucker.seren.files.ui.appContainer
import wales.tucker.seren.files.ui.common.AccessGate
import wales.tucker.seren.files.ui.common.ConflictDialog
import wales.tucker.seren.files.ui.common.DeleteDialog
import wales.tucker.seren.files.ui.common.DetailsDialog
import wales.tucker.seren.files.ui.common.FileRow
import wales.tucker.seren.files.ui.common.FileTile
import wales.tucker.seren.files.ui.common.NameDialog
import wales.tucker.seren.files.ui.common.Opener
import wales.tucker.seren.files.ui.common.OperationCard
import wales.tucker.seren.files.ui.common.PasteBar
import wales.tucker.seren.files.ui.common.SaveBar
import wales.tucker.seren.files.ui.common.displayPath
import wales.tucker.seren.files.ui.common.folderTitle
import wales.tucker.seren.files.ui.containerViewModel
import java.io.File
import kotlinx.coroutines.delay
import wales.tucker.seren.core.suite.SuiteApp
import wales.tucker.seren.core.suite.rememberInstalled
import wales.tucker.seren.files.ui.common.SerenFileMenuItems

/** A dialog the folder screen is showing, about [entries] (usually the picked items, or one row's). */
private sealed interface FolderDialog {
    data object NewFolder : FolderDialog
    data object NewFile : FolderDialog
    data object Sort : FolderDialog
    data class Rename(val entry: FileEntry) : FolderDialog
    data class Delete(val entries: List<FileEntry>) : FolderDialog
    data class Details(val entry: FileEntry) : FolderDialog
    data class Compress(val entries: List<FileEntry>) : FolderDialog
    data class Extract(val entry: FileEntry) : FolderDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(start: File, startSearching: Boolean, settings: Settings, onClose: () -> Unit, highlight: String? = null) {
    val container = appContainer()
    val ops = container.operations
    val vm = containerViewModel(key = "folder:${start.path}") { FolderViewModel(it, start) }
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val scope = rememberCoroutineScope()
    val clipboard by ops.clipboard.collectAsStateWithLifecycle()
    val incoming by ops.incoming.collectAsStateWithLifecycle()
    val operation by ops.current.collectAsStateWithLifecycle()
    val itemsText: (Int) -> String = ops::items

    var searching by rememberSaveable { mutableStateOf(startSearching) }
    var query by rememberSaveable { mutableStateOf("") }
    var dialog by remember { mutableStateOf<FolderDialog?>(null) }
    // Each folder keeps its scroll position, so Back lands where people left off.
    val listStates = remember { mutableMapOf<String, LazyListState>() }
    val listState = listStates.getOrPut(vm.folder.path) { LazyListState() }
    val gridStates = remember { mutableMapOf<String, LazyGridState>() }
    val gridState = gridStates.getOrPut(vm.folder.path) { LazyGridState() }
    val atTop by remember(listState, gridState, settings.gridView) {
        derivedStateOf { if (settings.gridView) gridState.firstVisibleItemIndex == 0 else listState.firstVisibleItemIndex == 0 }
    }

    LaunchedEffect(settings.sortOrder, settings.showHidden) { vm.configure(settings.sortOrder, settings.showHidden) }
    // Other apps may have changed things while Seren Files was in the background.
    LifecycleResumeEffect(Unit) {
        vm.reload()
        onPauseOrDispose {}
    }

    // An item another app asked to show: scrolled to and picked out for a moment.
    var flash by remember { mutableStateOf(highlight?.let { File(start, it).path }) }
    LaunchedEffect(vm.contents, flash) {
        val path = flash ?: return@LaunchedEffect
        val ready = vm.contents as? FolderContents.Ready ?: return@LaunchedEffect
        if (vm.folder != start) {
            flash = null
            return@LaunchedEffect
        }
        listPosition(ready.entries, path)?.let { if (settings.gridView) gridState.animateScrollToItem(it) else listState.animateScrollToItem(it) }
        delay(HIGHLIGHT_MS)
        flash = null
    }

    val title = folderTitle(vm.folder, vm.volumes)
    val picking = vm.selected.isNotEmpty()

    fun openEntry(entry: FileEntry) {
        when {
            entry.isDirectory -> {
                listStates.remove(entry.path)
                gridStates.remove(entry.path)
                if (searching) {
                    searching = false
                    query = ""
                    vm.endSearch()
                }
                vm.open(entry.file)
            }
            Archives.canExtract(entry.name) -> dialog = FolderDialog.Extract(entry)
            !Opener.open(context, entry.file) -> messenger.show("No app on this device can open ${entry.name}")
        }
    }

    fun share(entries: List<FileEntry>) {
        if (entries.any { it.isDirectory }) {
            messenger.show("Folders can't be shared. Compress them into a zip first.")
        } else {
            Opener.share(context, entries.map { it.file })
        }
    }

    fun pick(entries: List<FileEntry>, move: Boolean) {
        ops.setClipboard(entries.map { it.file }, move)
        vm.clearSelection()
    }

    BackHandler {
        when {
            picking -> vm.clearSelection()
            searching -> {
                searching = false
                query = ""
                vm.endSearch()
            }
            !vm.back() -> onClose()
        }
    }

    Scaffold(
        topBar = {
            Column {
                when {
                    picking -> SelectionTopBar(
                        count = vm.selected.size,
                        onClear = vm::clearSelection,
                        onSelectAll = vm::selectAll,
                    )
                    searching -> SearchTopBar(
                        query = query,
                        placeholder = "Search in $title",
                        onQuery = {
                            query = it
                            vm.search(it, settings.showHidden)
                        },
                        onClose = {
                            searching = false
                            query = ""
                            vm.endSearch()
                        },
                    )
                    else -> TopAppBar(
                        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(onClick = { if (!vm.back()) onClose() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                        },
                        actions = {
                            IconButton(onClick = { searching = true }) { Icon(Icons.Rounded.Search, "Search") }
                            IconButton(onClick = { scope.launch { container.settings.setGridView(!settings.gridView) } }) {
                                if (settings.gridView) {
                                    Icon(Icons.AutoMirrored.Rounded.ViewList, "List view")
                                } else {
                                    Icon(Icons.Rounded.GridView, "Grid view")
                                }
                            }
                            FolderMenu(
                                bookmarked = vm.bookmarked,
                                showHidden = settings.showHidden,
                                onNewFile = { dialog = FolderDialog.NewFile },
                                onSort = { dialog = FolderDialog.Sort },
                                onShowHidden = { scope.launch { container.settings.setShowHidden(!settings.showHidden) } },
                                onBookmark = { vm.toggleBookmark(vm.folder, title) },
                                onSelectAll = vm::selectAll,
                                onCopyPath = {
                                    copyToClipboard(context, "Path", vm.folder.path)
                                    messenger.show("Copied the path")
                                },
                            )
                        },
                        windowInsets = WindowInsets.statusBars,
                    )
                }
                if (!searching) Breadcrumbs(vm.folder, vm.volumes, onNavigate = { dir -> if (dir != vm.folder) vm.open(dir) })
            }
        },
        bottomBar = {
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                operation?.let { OperationCard(it, onCancel = ops::cancel) }
                if (picking) {
                    val entries = vm.selectedEntries()
                    SelectionBar(
                        entries = entries,
                        onCopy = { pick(entries, move = false) },
                        onMove = { pick(entries, move = true) },
                        onShare = { share(entries) },
                        onDelete = { dialog = FolderDialog.Delete(entries) },
                        onRename = { entries.singleOrNull()?.let { dialog = FolderDialog.Rename(it) } },
                        onCompress = { dialog = FolderDialog.Compress(entries) },
                        onDetails = { entries.singleOrNull()?.let { dialog = FolderDialog.Details(it) } },
                        onOpenWith = {
                            entries.singleOrNull()?.let { entry ->
                                vm.clearSelection()
                                if (!Opener.open(context, entry.file, choose = true)) messenger.show("No app on this device can open ${entry.name}")
                            }
                        },
                        onExtract = {
                            entries.singleOrNull()?.let { entry ->
                                vm.clearSelection()
                                ops.extract(entry.file)
                            }
                        },
                        onBookmark = {
                            entries.singleOrNull()?.let { entry ->
                                vm.clearSelection()
                                vm.toggleBookmark(entry.file, entry.name)
                            }
                        },
                        onUpload = {
                            if (entries.any { it.isDirectory }) {
                                messenger.show("Folders can't be uploaded. Compress them into a zip first.")
                            } else if (!Opener.uploadWithSsh(context, entries.map { it.file })) {
                                messenger.show("Seren SSH couldn't open them")
                            } else {
                                vm.clearSelection()
                            }
                        },
                    )
                } else {
                    incoming?.let { files ->
                        SaveBar(
                            files = files,
                            itemsText = itemsText,
                            enabled = operation == null && vm.contents is FolderContents.Ready,
                            onSave = { ops.saveIncoming(vm.folder) },
                            onCancel = ops::clearIncoming,
                        )
                    }
                    clipboard?.let { clip ->
                        PasteBar(
                            clip = clip,
                            itemsText = itemsText,
                            enabled = operation == null && vm.contents is FolderContents.Ready,
                            onPaste = vm::paste,
                            onCancel = ops::clearClipboard,
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!picking && !searching && clipboard == null && incoming == null && operation == null && vm.contents is FolderContents.Ready) {
                ExtendedFloatingActionButton(
                    onClick = { dialog = FolderDialog.NewFolder },
                    icon = { Icon(Icons.Rounded.CreateNewFolder, null) },
                    text = { Text("New folder") },
                    expanded = atTop,
                )
            }
        },
        snackbarHost = { SnackbarHost(messenger.host) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        AccessGate(Modifier.padding(padding)) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (searching) {
                    SearchResults(
                        query = query,
                        results = vm.results,
                        running = vm.searchRunning,
                        root = vm.folder,
                        volumes = vm.volumes,
                        thumbnails = settings.showThumbnails,
                        itemsText = itemsText,
                        onOpen = ::openEntry,
                    )
                } else {
                    when (val contents = vm.contents) {
                        FolderContents.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        is FolderContents.Failed -> EmptyState(
                            icon = Icons.Rounded.ErrorOutline,
                            title = "Couldn't open $title",
                            message = contents.reason,
                            action = { Button(onClick = vm::reload) { Text("Retry") } },
                        )
                        is FolderContents.Ready -> if (contents.entries.isEmpty()) {
                            EmptyState(
                                icon = Icons.Rounded.FolderOpen,
                                title = "Empty folder",
                                message = if (clipboard != null) "Paste here, or create a folder with New folder." else "Create a folder here with New folder.",
                            )
                        } else if (settings.gridView) {
                            FolderGrid(
                                entries = contents.entries,
                                state = gridState,
                                selected = vm.selected,
                                highlighted = flash,
                                thumbnails = settings.showThumbnails,
                                itemsText = itemsText,
                                onClick = { entry -> if (picking) vm.toggle(entry) else openEntry(entry) },
                                onLongClick = vm::toggle,
                            )
                        } else {
                            FolderList(
                                entries = contents.entries,
                                state = listState,
                                selected = vm.selected,
                                highlighted = flash,
                                thumbnails = settings.showThumbnails,
                                itemsText = itemsText,
                                onClick = { entry -> if (picking) vm.toggle(entry) else openEntry(entry) },
                                onLongClick = vm::toggle,
                                menu = { entry, close ->
                                    EntryMenu(
                                        entry = entry,
                                        close = close,
                                        onOpenWith = {
                                            if (!Opener.open(context, entry.file, choose = true)) messenger.show("No app on this device can open ${entry.name}")
                                        },
                                        onShare = { share(listOf(entry)) },
                                        onCopy = { pick(listOf(entry), move = false) },
                                        onMove = { pick(listOf(entry), move = true) },
                                        onRename = { dialog = FolderDialog.Rename(entry) },
                                        onCompress = { dialog = FolderDialog.Compress(listOf(entry)) },
                                        onExtract = { ops.extract(entry.file) },
                                        onBookmark = { vm.toggleBookmark(entry.file, entry.name) },
                                        onDetails = { dialog = FolderDialog.Details(entry) },
                                        onDelete = { dialog = FolderDialog.Delete(listOf(entry)) },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        FolderDialog.NewFolder -> NameDialog(
            title = "New folder",
            initial = "",
            confirm = "Create",
            icon = Icons.Rounded.CreateNewFolder,
            onDismiss = { dialog = null },
        ) { name ->
            dialog = null
            ops.createFolder(vm.folder, name)?.let { messenger.show("Created ${it.name}") }
        }
        FolderDialog.NewFile -> NameDialog(
            title = "New file",
            initial = "",
            confirm = "Create",
            icon = Icons.AutoMirrored.Rounded.NoteAdd,
            onDismiss = { dialog = null },
        ) { name ->
            dialog = null
            ops.createFile(vm.folder, name)?.let { messenger.show("Created ${it.name}") }
        }
        FolderDialog.Sort -> SortDialog(
            order = settings.sortOrder,
            onDismiss = { dialog = null },
            onChange = { scope.launch { container.settings.setSortOrder(it) } },
        )
        is FolderDialog.Rename -> NameDialog(
            title = "Rename ${d.entry.name}",
            initial = d.entry.name,
            confirm = "Rename",
            icon = Icons.Rounded.DriveFileRenameOutline,
            keepExtension = !d.entry.isDirectory,
            onDismiss = { dialog = null },
        ) { name ->
            dialog = null
            vm.clearSelection()
            ops.rename(d.entry.file, name)
        }
        is FolderDialog.Delete -> DeleteDialog(
            files = d.entries.map { it.file },
            useTrash = settings.useTrash,
            itemsText = itemsText,
            onDismiss = { dialog = null },
        ) {
            dialog = null
            vm.clearSelection()
            ops.delete(d.entries.map { it.file }, permanently = !settings.useTrash)
        }
        is FolderDialog.Details -> DetailsDialog(d.entry, itemsText, onDismiss = { dialog = null })
        is FolderDialog.Compress -> NameDialog(
            title = "Compress ${d.entries.singleOrNull()?.name ?: itemsText(d.entries.size)}",
            initial = (d.entries.singleOrNull()?.name?.substringBeforeLast('.')?.ifEmpty { null } ?: "Archive") + ".zip",
            confirm = "Compress",
            icon = Icons.Rounded.FolderZip,
            label = "Zip file name",
            onDismiss = { dialog = null },
        ) { name ->
            dialog = null
            vm.clearSelection()
            ops.compress(d.entries.map { it.file }, vm.folder, name)
        }
        is FolderDialog.Extract -> AlertDialog(
            onDismissRequest = { dialog = null },
            icon = { Icon(Icons.Rounded.Unarchive, null) },
            title = { Text("Extract ${d.entry.name}?") },
            text = { Text("Its contents go into a new folder beside it. To open it in another app instead, use Open with.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    ops.extract(d.entry.file)
                }) { Text("Extract") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }

    vm.pendingPlan?.let { plan ->
        ConflictDialog(
            plan = plan,
            itemsText = itemsText,
            onDismiss = vm::dismissPlan,
            onConfirm = { policy ->
                vm.dismissPlan()
                ops.transfer(plan, policy)
            },
        )
    }
}

@Composable
private fun FolderList(
    entries: List<FileEntry>,
    state: LazyListState,
    selected: Set<String>,
    highlighted: String?,
    thumbnails: Boolean,
    itemsText: (Int) -> String,
    onClick: (FileEntry) -> Unit,
    onLongClick: (FileEntry) -> Unit,
    menu: @Composable (FileEntry, close: () -> Unit) -> Unit,
) {
    val folders = entries.filter { it.isDirectory }
    val files = entries.filter { !it.isDirectory }
    val picking = selected.isNotEmpty()
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 96.dp)) {
        listOf("Folders" to folders, "Files" to files).forEach { (header, group) ->
            if (group.isEmpty()) return@forEach
            item(key = "header:$header") { SectionHeader(header, trailing = group.size.toString()) }
            itemsIndexed(group, key = { _, e -> e.path }) { i, entry ->
                FileRow(
                    entry = entry,
                    shape = groupedShape(i, group.size),
                    thumbnails = thumbnails,
                    itemsText = itemsText,
                    selected = entry.path in selected || entry.path == highlighted,
                    onClick = { onClick(entry) },
                    onLongClick = { onLongClick(entry) },
                    menu = if (picking) null else { close -> menu(entry, close) },
                )
            }
        }
    }
}

/** Folders then files as a grid of tiles, each group under a header that spans the width. */
@Composable
private fun FolderGrid(
    entries: List<FileEntry>,
    state: LazyGridState,
    selected: Set<String>,
    highlighted: String?,
    thumbnails: Boolean,
    itemsText: (Int) -> String,
    onClick: (FileEntry) -> Unit,
    onLongClick: (FileEntry) -> Unit,
) {
    val folders = entries.filter { it.isDirectory }
    val files = entries.filter { !it.isDirectory }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
    ) {
        listOf("Folders" to folders, "Files" to files).forEach { (header, group) ->
            if (group.isEmpty()) return@forEach
            item(key = "header:$header", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(header, trailing = group.size.toString())
            }
            items(group, key = { it.path }) { entry ->
                FileTile(
                    entry = entry,
                    thumbnails = thumbnails,
                    itemsText = itemsText,
                    selected = entry.path in selected || entry.path == highlighted,
                    onClick = { onClick(entry) },
                    onLongClick = { onLongClick(entry) },
                )
            }
        }
    }
}

/** How long a revealed item stays picked out. */
private const val HIGHLIGHT_MS = 2_500L

/** Where the item at [path] is in [FolderList]: folders, then files, each under a header. */
internal fun listPosition(entries: List<FileEntry>, path: String): Int? {
    var position = 0
    for (group in listOf(entries.filter { it.isDirectory }, entries.filter { !it.isDirectory })) {
        if (group.isEmpty()) continue
        position++ // The group's header.
        val at = group.indexOfFirst { it.path == path }
        if (at >= 0) return position + at
        position += group.size
    }
    return null
}

@Composable
private fun SearchResults(
    query: String,
    results: List<FileEntry>,
    running: Boolean,
    root: File,
    volumes: List<Volume>,
    thumbnails: Boolean,
    itemsText: (Int) -> String,
    onOpen: (FileEntry) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            query.isBlank() -> EmptyState(
                icon = Icons.Rounded.Search,
                title = "Search by name",
                message = "Finds files and folders in ${folderTitle(root, volumes)} and every folder inside it.",
            )
            results.isEmpty() && !running -> EmptyState(
                icon = Icons.Rounded.SearchOff,
                title = "No matches",
                message = "Nothing in ${folderTitle(root, volumes)} has \"${query.trim()}\" in its name.",
            )
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    val count = if (results.size >= wales.tucker.seren.files.fs.Search.LIMIT) "${results.size}+" else results.size.toString()
                    SectionHeader("Results", trailing = count)
                }
                itemsIndexed(results, key = { _, e -> e.path }) { i, entry ->
                    FileRow(
                        entry = entry,
                        shape = groupedShape(i, results.size),
                        thumbnails = thumbnails,
                        itemsText = itemsText,
                        subtitle = displayPath(entry.file.parentFile ?: root, volumes),
                        onClick = { onOpen(entry) },
                    )
                }
            }
        }
    }
}

/** The path from the volume down to [folder], each part a chip that opens it. */
@Composable
private fun Breadcrumbs(folder: File, volumes: List<Volume>, onNavigate: (File) -> Unit) {
    val volume = volumes.filter { wales.tucker.seren.files.fs.FileOps.isInside(folder, it.root) }.maxByOrNull { it.root.path.length }
    val base = volume?.root ?: File("/")
    val parts = folder.path.removePrefix(base.path).split('/').filter { it.isNotEmpty() }
    val scroll = rememberScrollState()
    LaunchedEffect(folder) { scroll.animateScrollTo(scroll.maxValue) }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(onClick = { onNavigate(base) }, label = { Text(volume?.name ?: "/", style = MonoSmall) })
        var dir = base
        parts.forEach { part ->
            dir = File(dir, part)
            val target = dir
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
            AssistChip(onClick = { onNavigate(target) }, label = { Text(part, style = MonoSmall) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(count: Int, onClear: () -> Unit, onSelectAll: () -> Unit) {
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = { IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, contentDescription = "Clear selection") } },
        actions = { IconButton(onClick = onSelectAll) { Icon(Icons.Rounded.SelectAll, contentDescription = "Select all") } },
        windowInsets = WindowInsets.statusBars,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(query: String, placeholder: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                shape = CircleShape,
                colors = pillFieldColors(MaterialTheme.colorScheme.surfaceContainerHigh),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear search") }
                },
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp).focusRequester(focus),
            )
        },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close search") } },
        windowInsets = WindowInsets.statusBars,
    )
}

@Composable
private fun FolderMenu(
    bookmarked: Boolean,
    showHidden: Boolean,
    onNewFile: () -> Unit,
    onSort: () -> Unit,
    onShowHidden: () -> Unit,
    onBookmark: () -> Unit,
    onSelectAll: () -> Unit,
    onCopyPath: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Rounded.MoreVert, contentDescription = "More")
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable
            fun item(text: String, icon: ImageVector, trailing: ImageVector? = null, action: () -> Unit) {
                DropdownMenuItem(
                    text = { Text(text) },
                    leadingIcon = { Icon(icon, null) },
                    trailingIcon = trailing?.let { { Icon(it, null) } },
                    onClick = { open = false; action() },
                )
            }
            item("New file", Icons.AutoMirrored.Rounded.NoteAdd, action = onNewFile)
            item("Sort by", Icons.AutoMirrored.Rounded.Sort, action = onSort)
            item("Show hidden files", Icons.Rounded.Visibility, trailing = if (showHidden) Icons.Rounded.Check else null, action = onShowHidden)
            item(if (bookmarked) "Remove bookmark" else "Add to bookmarks", if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, action = onBookmark)
            item("Select all", Icons.Rounded.SelectAll, action = onSelectAll)
            item("Copy path", Icons.Rounded.ContentCopy, action = onCopyPath)
        }
    }
}

/** The actions for one row, from its three dot menu. Delete comes last. */
@Composable
private fun EntryMenu(
    entry: FileEntry,
    close: () -> Unit,
    onOpenWith: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onCompress: () -> Unit,
    onExtract: () -> Unit,
    onBookmark: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
) {
    @Composable
    fun item(text: String, icon: ImageVector, action: () -> Unit) {
        DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = { close(); action() })
    }
    if (!entry.isDirectory) {
        item("Open with", Icons.AutoMirrored.Rounded.OpenInNew, onOpenWith)
        SerenFileMenuItems(entry, close)
        item("Share", Icons.Rounded.Share, onShare)
    }
    item("Copy", Icons.Rounded.ContentCopy, onCopy)
    item("Move", Icons.AutoMirrored.Rounded.DriveFileMove, onMove)
    item("Rename", Icons.Rounded.DriveFileRenameOutline, onRename)
    item("Compress", Icons.Rounded.FolderZip, onCompress)
    if (!entry.isDirectory && Archives.canExtract(entry.name)) item("Extract", Icons.Rounded.Unarchive, onExtract)
    if (entry.isDirectory) item("Add to bookmarks", Icons.Rounded.BookmarkBorder, onBookmark)
    item("Details", Icons.Rounded.Info, onDetails)
    HorizontalDivider()
    item("Delete", Icons.Rounded.Delete, onDelete)
}

/** What people can do with the picked items, along the bottom while picking. */
@Composable
private fun SelectionBar(
    entries: List<FileEntry>,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onCompress: () -> Unit,
    onDetails: () -> Unit,
    onOpenWith: () -> Unit,
    onExtract: () -> Unit,
    onBookmark: () -> Unit,
    onUpload: () -> Unit,
) {
    val ssh = rememberInstalled(SuiteApp.SSH)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            BarAction(Icons.Rounded.ContentCopy, "Copy", onCopy)
            BarAction(Icons.AutoMirrored.Rounded.DriveFileMove, "Move", onMove)
            BarAction(Icons.Rounded.Share, "Share", onShare)
            BarAction(Icons.Rounded.Delete, "Delete", onDelete)
            var more by remember { mutableStateOf(false) }
            Box {
                BarAction(Icons.Rounded.MoreVert, "More") { more = true }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    val single = entries.size == 1
                    val only = entries.singleOrNull()
                    if (only != null && !only.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Open with") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                            onClick = { more = false; onOpenWith() },
                        )
                    }
                    if (only != null && !only.isDirectory && Archives.canExtract(only.name)) {
                        DropdownMenuItem(
                            text = { Text("Extract") },
                            leadingIcon = { Icon(Icons.Rounded.Unarchive, null) },
                            onClick = { more = false; onExtract() },
                        )
                    }
                    if (only != null && only.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Add to bookmarks") },
                            leadingIcon = { Icon(Icons.Rounded.BookmarkBorder, null) },
                            onClick = { more = false; onBookmark() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                        enabled = single,
                        onClick = { more = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Compress") },
                        leadingIcon = { Icon(Icons.Rounded.FolderZip, null) },
                        onClick = { more = false; onCompress() },
                    )
                    DropdownMenuItem(
                        text = { Text("Details") },
                        leadingIcon = { Icon(Icons.Rounded.Info, null) },
                        enabled = single,
                        onClick = { more = false; onDetails() },
                    )
                    if (ssh) {
                        DropdownMenuItem(
                            text = { Text("Upload with Seren SSH") },
                            leadingIcon = { Icon(Icons.Rounded.CloudUpload, null) },
                            onClick = { more = false; onUpload() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        }
    }
}

/** Picks what to sort by and which way, with words that fit each choice ("Newest first"). */
@Composable
private fun SortDialog(order: SortOrder, onDismiss: () -> Unit, onChange: (SortOrder) -> Unit) {
    var by by remember { mutableStateOf(order.by) }
    var descending by remember { mutableStateOf(order.descending) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Rounded.Sort, null) },
        title = { Text("Sort by") },
        text = {
            Column {
                SortBy.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(selected = by == option, role = Role.RadioButton) { by = option }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = by == option, onClick = null, modifier = Modifier.padding(horizontal = 8.dp))
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                val (up, down) = directionLabels(by)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    listOf(false to up, true to down).forEachIndexed { i, (desc, label) ->
                        SegmentedButton(
                            selected = descending == desc,
                            onClick = { descending = desc },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label, maxLines = 1) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onChange(SortOrder(by, descending))
                onDismiss()
            }) { Text("Sort") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun directionLabels(by: SortBy): Pair<String, String> = when (by) {
    SortBy.NAME, SortBy.TYPE -> "A to Z" to "Z to A"
    SortBy.MODIFIED -> "Oldest first" to "Newest first"
    SortBy.SIZE -> "Smallest first" to "Largest first"
}
