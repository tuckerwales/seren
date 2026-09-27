@file:OptIn(ExperimentalFoundationApi::class)

package wales.tucker.seren.edit.ui.editor

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import wales.tucker.seren.core.ui.theme.AccentColors
import wales.tucker.seren.edit.AppContainer
import wales.tucker.seren.edit.data.RecentFile
import wales.tucker.seren.edit.document.DecodedText
import wales.tucker.seren.edit.document.DocumentStore
import wales.tucker.seren.edit.document.FileTooLargeException
import wales.tucker.seren.edit.document.LineEnding
import wales.tucker.seren.edit.document.NotTextException
import wales.tucker.seren.edit.document.TextCodec
import wales.tucker.seren.edit.document.TextEncoding
import java.io.FileNotFoundException

sealed interface LoadState {
    data object Loading : LoadState
    data object Ready : LoadState

    /** The file couldn't be opened; [reason] finishes the sentence "Couldn't open notes.txt." */
    data class Failed(val reason: String) : LoadState
}

/** One open file: its text, how to write it back, and whether it has unsaved changes. */
class EditorViewModel(private val container: AppContainer, val uri: Uri) : ViewModel() {
    /** The text being edited, with its undo history. Kept here so it survives rotation. */
    val text = TextFieldState()

    var load by mutableStateOf<LoadState>(LoadState.Loading)
        private set
    var name by mutableStateOf(uri.lastPathSegment?.substringAfterLast('/') ?: "Untitled")
        private set
    var location by mutableStateOf("")
        private set
    var saving by mutableStateOf(false)
        private set

    /** The text as last read or saved, to tell whether there are unsaved changes. */
    private var savedText by mutableStateOf("")
    var encoding by mutableStateOf(TextEncoding.UTF_8)
        private set
    var lineEnding by mutableStateOf(LineEnding.LF)
        private set

    /** True when the text differs from the file. Reads snapshot state, so use it in derivedStateOf. */
    val isDirty: Boolean
        get() = load == LoadState.Ready && !text.text.contentEquals(savedText)

    init {
        viewModelScope.launch { open() }
    }

    fun retry() {
        if (load !is LoadState.Failed) return
        load = LoadState.Loading
        viewModelScope.launch { open() }
    }

    private suspend fun open() {
        val store = container.documents
        val result = runCatching {
            val info = store.info(uri)
            name = info.name
            location = info.location
            if ((info.size ?: 0) > DocumentStore.MAX_FILE_BYTES) throw FileTooLargeException(info.size ?: 0)
            TextCodec.decode(store.read(uri))
        }
        result.onSuccess { decoded ->
            show(decoded)
            remember()
        }.onFailure { e ->
            load = LoadState.Failed(
                when (e) {
                    is FileTooLargeException -> "It's larger than ${DocumentStore.MAX_FILE_BYTES / (1024 * 1024)} MB, the most Seren Edit can open."
                    is NotTextException -> "It doesn't look like a text file."
                    is FileNotFoundException, is SecurityException -> "The file was moved, deleted, or Seren Edit no longer has access to it."
                    else -> e.message ?: e.javaClass.simpleName
                },
            )
        }
    }

    private fun show(decoded: DecodedText) {
        encoding = decoded.encoding
        lineEnding = decoded.lineEnding
        savedText = decoded.text
        text.edit {
            replace(0, length, decoded.text)
            selection = TextRange(0)
        }
        text.undoState.clearHistory()
        load = LoadState.Ready
    }

    /** Adds the file to Recent, keeping its color if it was there before. */
    private suspend fun remember() {
        container.documents.persistAccess(uri)
        val dao = container.database.recentFiles()
        val key = uri.toString()
        val color = dao.get(key)?.color ?: AccentColors.indices.random()
        dao.upsert(RecentFile(key, name, location, color, System.currentTimeMillis()))
        // Android caps how many grants an app keeps, so hand back those of files that fell off.
        dao.overflow().forEach { old ->
            dao.delete(old.uri)
            container.documents.releaseAccess(Uri.parse(old.uri))
        }
    }

    /** Writes the text to the file. Returns null on success, or why it failed. */
    suspend fun save(): String? {
        if (load != LoadState.Ready || saving) return null
        saving = true
        val snapshot = text.text.toString()
        return try {
            container.documents.write(uri, TextCodec.encode(snapshot, encoding, lineEnding))
            savedText = snapshot
            null
        } catch (e: Exception) {
            when (e) {
                is FileNotFoundException, is SecurityException -> "Seren Edit no longer has access to it"
                else -> e.message ?: e.javaClass.simpleName
            }
        } finally {
            saving = false
        }
    }
}
