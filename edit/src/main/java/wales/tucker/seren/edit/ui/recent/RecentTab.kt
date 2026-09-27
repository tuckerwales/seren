package wales.tucker.seren.edit.ui.recent

import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import wales.tucker.seren.core.ui.Avatar
import wales.tucker.seren.core.ui.EmptyState
import wales.tucker.seren.core.ui.GroupedTile
import wales.tucker.seren.core.ui.SectionHeader
import wales.tucker.seren.core.ui.groupedShape
import wales.tucker.seren.core.ui.relativeTime
import wales.tucker.seren.core.ui.theme.accentColor
import wales.tucker.seren.edit.AppContainer
import wales.tucker.seren.edit.data.RecentFile
import wales.tucker.seren.edit.ui.containerViewModel

class RecentViewModel(private val container: AppContainer) : ViewModel() {
    val files: StateFlow<List<RecentFile>?> = container.database.recentFiles().observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Takes [file] off the list; the file itself stays where it is. */
    fun remove(file: RecentFile) {
        viewModelScope.launch {
            val uri = Uri.parse(file.uri)
            container.database.recentFiles().delete(file.uri)
            container.documents.releaseAccess(uri)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentTab(onOpenFile: (Uri) -> Unit) {
    val vm = containerViewModel { RecentViewModel(it) }
    val files = vm.files.collectAsStateWithLifecycle().value

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Recent", fontWeight = FontWeight.SemiBold) }, windowInsets = WindowInsets.statusBars)
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            when {
                files == null -> Unit
                files.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Rounded.History,
                        title = "No recent files",
                        message = "Files you open or create show up here, so you can get back to them in one tap.",
                    )
                }
                else -> {
                    item { SectionHeader("Files", trailing = files.size.toString()) }
                    itemsIndexed(files, key = { _, f -> f.uri }) { index, file ->
                        GroupedTile(
                            shape = groupedShape(index, files.size),
                            title = file.name,
                            subtitle = file.location,
                            meta = "Opened ${relativeTime(file.lastOpened, midSentence = true)}",
                            onClick = { onOpenFile(Uri.parse(file.uri)) },
                            leading = { Avatar(file.name, accentColor(file.color)) },
                            menu = { close ->
                                DropdownMenuItem(
                                    text = { Text("Remove from recent") },
                                    leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null) },
                                    onClick = { close(); vm.remove(file) },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
