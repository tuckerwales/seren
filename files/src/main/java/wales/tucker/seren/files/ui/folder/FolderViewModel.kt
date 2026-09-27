package wales.tucker.seren.files.ui.folder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wales.tucker.seren.files.AppContainer
import wales.tucker.seren.files.data.Bookmark
import wales.tucker.seren.files.fs.ConflictPolicy
import wales.tucker.seren.files.fs.FileEntry
import wales.tucker.seren.files.fs.Listing
import wales.tucker.seren.files.fs.Search
import wales.tucker.seren.files.fs.SortOrder
import wales.tucker.seren.files.fs.TransferPlan
import wales.tucker.seren.files.fs.Volume
import java.io.File
import java.io.IOException

sealed interface FolderContents {
    data object Loading : FolderContents
    data class Ready(val entries: List<FileEntry>) : FolderContents
    data class Failed(val reason: String) : FolderContents
}

/**
 * One folder screen. Opening subfolders happens in place and Back retraces them, so the screen
 * keeps its selection and paste bar while people move around.
 */
class FolderViewModel(private val container: AppContainer, start: File) : ViewModel() {
    var folder by mutableStateOf(start)
        private set
    private val history = ArrayDeque<File>()

    var contents by mutableStateOf<FolderContents>(FolderContents.Loading)
        private set

    var volumes by mutableStateOf<List<Volume>>(emptyList())
        private set

    /** Paths of the picked items; picking mode is on while this isn't empty. */
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set

    var bookmarked by mutableStateOf(false)
        private set

    /** Items with the same name as ones being pasted, waiting for the user to choose what to do. */
    var pendingPlan by mutableStateOf<TransferPlan?>(null)
        private set

    val results = mutableStateListOf<FileEntry>()
    var searchRunning by mutableStateOf(false)
        private set
    private var searchJob: Job? = null

    private var order = SortOrder()
    private var showHidden = false
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            volumes = withContext(Dispatchers.IO) { container.storage.volumes() }
        }
        viewModelScope.launch {
            container.operations.changes.drop(1).collect { reload() }
        }
        viewModelScope.launch {
            container.bookmarks.observe().collect { marks -> bookmarked = marks.any { it.path == folder.path } }
        }
    }

    /** Called with the current settings; reloads when the sort order or hidden files change. */
    fun configure(order: SortOrder, showHidden: Boolean) {
        val changed = order != this.order || showHidden != this.showHidden || contents is FolderContents.Loading
        this.order = order
        this.showHidden = showHidden
        if (changed) reload()
    }

    fun reload() {
        loadJob?.cancel()
        val dir = folder
        loadJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    FolderContents.Ready(Listing.list(dir, order, showHidden))
                } catch (e: IOException) {
                    FolderContents.Failed(e.message ?: "Seren Files can't read ${dir.name}")
                } catch (e: SecurityException) {
                    FolderContents.Failed("Android keeps ${dir.name} private to the apps it belongs to")
                }
            }
            if (dir == folder) {
                contents = result
                val present = (result as? FolderContents.Ready)?.entries?.map { it.path }?.toSet().orEmpty()
                selected = selected intersect present
            }
        }
    }

    private fun show(dir: File) {
        folder = dir
        selected = emptySet()
        contents = FolderContents.Loading
        viewModelScope.launch { bookmarked = container.bookmarks.isBookmarked(dir.path) }
        reload()
    }

    fun open(dir: File) {
        history.addLast(folder)
        show(dir)
    }

    /** Goes back to the folder open before this one; false when there's none, so the screen closes. */
    fun back(): Boolean {
        val previous = history.removeLastOrNull() ?: return false
        show(previous)
        return true
    }

    fun toggle(entry: FileEntry) {
        selected = if (entry.path in selected) selected - entry.path else selected + entry.path
    }

    fun selectAll() {
        (contents as? FolderContents.Ready)?.let { ready -> selected = ready.entries.map { it.path }.toSet() }
    }

    fun clearSelection() {
        selected = emptySet()
    }

    fun selectedEntries(): List<FileEntry> =
        (contents as? FolderContents.Ready)?.entries?.filter { it.path in selected }.orEmpty()

    fun search(query: String, showHidden: Boolean) {
        searchJob?.cancel()
        results.clear()
        if (query.isBlank()) {
            searchRunning = false
            return
        }
        searchRunning = true
        searchJob = viewModelScope.launch {
            Search.find(folder, query, showHidden).flowOn(Dispatchers.IO).collect { results += it }
            searchRunning = false
        }
    }

    fun endSearch() {
        searchJob?.cancel()
        results.clear()
        searchRunning = false
    }

    fun paste() {
        val clip = container.operations.clipboard.value ?: return
        viewModelScope.launch {
            val plan = container.operations.plan(clip.files, folder, clip.move) ?: return@launch
            if (plan.conflicts.isEmpty()) {
                container.operations.transfer(plan, ConflictPolicy.KEEP_BOTH)
            } else {
                pendingPlan = plan
            }
        }
    }

    fun dismissPlan() {
        pendingPlan = null
    }

    fun toggleBookmark(dir: File, title: String) {
        viewModelScope.launch {
            if (container.bookmarks.isBookmarked(dir.path)) {
                container.bookmarks.delete(dir.path)
                container.operations.tell("Removed $title from bookmarks")
            } else {
                container.bookmarks.insert(Bookmark(dir.path, title, container.now()))
                container.operations.tell("Added $title to bookmarks")
            }
            if (dir == folder) bookmarked = container.bookmarks.isBookmarked(dir.path)
        }
    }
}
