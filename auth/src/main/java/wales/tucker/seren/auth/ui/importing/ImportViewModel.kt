package wales.tucker.seren.auth.ui.importing

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wales.tucker.seren.auth.AppContainer
import wales.tucker.seren.auth.R
import wales.tucker.seren.auth.backup.ImportRead
import wales.tucker.seren.auth.backup.Importer
import wales.tucker.seren.auth.backup.ParsedImport
import wales.tucker.seren.auth.otp.OtpFormatException
import java.io.IOException

sealed interface ImportState {
    data object Idle : ImportState

    data class NeedsPassword(
        val source: String,
        val decrypt: (CharArray) -> ParsedImport,
        val error: String? = null,
        val working: Boolean = false,
    ) : ImportState

    /** Ready to add [import]; [existing] of its accounts are already in Seren Auth. */
    data class Confirm(val import: ParsedImport, val existing: Int, val working: Boolean = false) : ImportState

    data class Failed(val title: String, val message: String) : ImportState
}

/**
 * Takes a backup file or a scanned transfer code through reading, unlocking and confirming, then
 * adds the accounts. The dialogs for each step are in ImportDialogs.
 */
class ImportViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** One line confirmations for a snackbar, such as "Added 3 accounts". */
    val messages = _messages.receiveAsFlow()

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            val text = try {
                withContext(Dispatchers.IO) { readText(uri) }
            } catch (e: IOException) {
                _state.value = ImportState.Failed("Couldn't import", e.message ?: "The file couldn't be read")
                return@launch
            }
            importText(text)
        }
    }

    fun importText(text: String) {
        viewModelScope.launch {
            try {
                when (val read = withContext(Dispatchers.Default) { Importer.read(text) }) {
                    is ImportRead.Ready -> confirm(read.import)
                    is ImportRead.Locked -> _state.value = ImportState.NeedsPassword(read.source, read.decrypt)
                }
            } catch (e: OtpFormatException) {
                _state.value = ImportState.Failed("Couldn't import", e.message.orEmpty())
            }
        }
    }

    fun unlock(password: CharArray) {
        val current = _state.value as? ImportState.NeedsPassword ?: return
        _state.value = current.copy(working = true, error = null)
        viewModelScope.launch {
            try {
                val import = withContext(Dispatchers.Default) { current.decrypt(password) }
                confirm(import)
            } catch (e: OtpFormatException) {
                _state.value = current.copy(working = false, error = e.message)
            } finally {
                password.fill(' ')
            }
        }
    }

    fun confirm() {
        val current = _state.value as? ImportState.Confirm ?: return
        _state.value = current.copy(working = true)
        viewModelScope.launch {
            val summary = container.accounts.import(current.import.entries)
            _state.value = ImportState.Idle
            val res = container.context.resources
            _messages.send(
                buildString {
                    append(res.getQuantityString(R.plurals.added_accounts, summary.added, summary.added))
                    if (summary.duplicates > 0) {
                        append(", ")
                        append(res.getQuantityString(R.plurals.skipped_existing, summary.duplicates, summary.duplicates))
                    }
                },
            )
        }
    }

    fun dismiss() {
        _state.value = ImportState.Idle
    }

    private suspend fun confirm(import: ParsedImport) {
        if (import.entries.isEmpty()) {
            _state.value = ImportState.Failed(
                "Nothing to import",
                if (import.unsupported > 0) {
                    container.context.resources.getQuantityString(R.plurals.none_supported, import.unsupported, import.unsupported)
                } else {
                    "There are no accounts in it."
                },
            )
            return
        }
        _state.value = ImportState.Confirm(import, container.accounts.countExisting(import.entries))
    }

    private fun readText(uri: Uri): String {
        val resolver = container.context.contentResolver
        val stream = resolver.openInputStream(uri) ?: throw IOException("The file couldn't be opened")
        stream.use { input ->
            val bytes = input.readNBytes(Importer.MAX_FILE_BYTES + 1)
            if (bytes.size > Importer.MAX_FILE_BYTES) throw IOException("The file is too big to be a backup")
            return bytes.toString(Charsets.UTF_8)
        }
    }
}
