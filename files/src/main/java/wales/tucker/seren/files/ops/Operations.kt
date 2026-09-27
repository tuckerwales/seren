package wales.tucker.seren.files.ops

import android.content.Context
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wales.tucker.seren.files.R
import wales.tucker.seren.files.data.BookmarkDao
import wales.tucker.seren.files.data.TrashBin
import wales.tucker.seren.files.data.TrashItem
import wales.tucker.seren.files.fs.Archives
import wales.tucker.seren.files.fs.ConflictPolicy
import wales.tucker.seren.files.fs.FileOps
import wales.tucker.seren.files.fs.PasswordNeededException
import wales.tucker.seren.files.fs.Storage
import wales.tucker.seren.files.fs.volumeFor
import wales.tucker.seren.files.fs.TransferPlan
import java.io.File
import java.io.IOException

/** Files picked with Copy or Move, waiting to be pasted into another folder. */
data class FileClipboard(val files: List<File>, val move: Boolean)

/**
 * A long job in progress, shown as a card with a progress bar and Cancel. [done] and [total] are
 * bytes, or items when [countsItems].
 */
data class Operation(val title: String, val detail: String, val done: Long, val total: Long, val countsItems: Boolean = false)

/** Something to tell the user in the snackbar, with an optional action such as Undo. */
data class Message(val text: String, val action: String? = null, val onAction: () -> Unit = {})

/**
 * Everything that changes files runs here, in a scope that outlives screens, so a copy keeps going
 * when the user moves on. Screens watch [changes] to reload, [current] for progress and [messages]
 * for results.
 */
class Operations(
    private val context: Context,
    private val storage: Storage,
    private val trash: TrashBin,
    private val bookmarks: BookmarkDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val _clipboard = MutableStateFlow<FileClipboard?>(null)
    val clipboard: StateFlow<FileClipboard?> = _clipboard.asStateFlow()

    private val _incoming = MutableStateFlow<List<IncomingFile>?>(null)

    /** Files another app shared, waiting for the user to open a folder and save them there. */
    val incoming: StateFlow<List<IncomingFile>?> = _incoming.asStateFlow()

    private val _passwordNeeded = MutableStateFlow<PasswordNeededException?>(null)

    /** An archive that needs a password to extract, waiting for people to enter it. */
    val passwordNeeded: StateFlow<PasswordNeededException?> = _passwordNeeded.asStateFlow()

    fun clearPasswordNeeded() {
        _passwordNeeded.value = null
    }

    private val _current = MutableStateFlow<Operation?>(null)
    val current: StateFlow<Operation?> = _current.asStateFlow()

    private val _changes = MutableStateFlow(0)

    /** Goes up by one after anything changes files, so lists know to reload. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 8)
    val messages: SharedFlow<Message> = _messages.asSharedFlow()

    private var job: Job? = null

    val busy: Boolean get() = job?.isActive == true

    fun items(count: Int): String = context.resources.getQuantityString(R.plurals.items, count, count)

    private fun say(text: String, action: String? = null, onAction: () -> Unit = {}) {
        _messages.tryEmit(Message(text, action, onAction))
    }

    private fun changed() = _changes.update { it + 1 }

    fun setClipboard(files: List<File>, move: Boolean) {
        _clipboard.value = FileClipboard(files, move)
    }

    fun clearClipboard() {
        _clipboard.value = null
    }

    fun receive(files: List<IncomingFile>) {
        _incoming.value = files.ifEmpty { null }
    }

    fun clearIncoming() {
        _incoming.value = null
    }

    /** Saves the files another app shared into [folder], never replacing what's there. */
    fun saveIncoming(folder: File) {
        val files = _incoming.value ?: return
        runLong("Saving ${items(files.size)}") { progress ->
            val total = files.sumOf { it.size.coerceAtLeast(0) }
            var before = 0L
            val saved = mutableListOf<File>()
            var failed: Pair<String, String>? = null
            for (file in files) {
                try {
                    val input = file.text?.byteInputStream()
                        ?: file.uri?.let { context.contentResolver.openInputStream(it) }
                        ?: throw IOException("It couldn't be read")
                    input.use { saved += FileOps.save(it, folder, file.name) { bytes -> progress(before + bytes, total, file.name) } }
                } catch (e: IOException) {
                    if (failed == null) failed = file.name to (e.message ?: "It couldn't be read")
                } catch (e: SecurityException) {
                    if (failed == null) failed = file.name to "The app that shared it no longer allows reading it"
                }
                before += file.size.coerceAtLeast(0)
            }
            _incoming.value = null
            val where = folder.name.ifEmpty { folder.path }
            say(
                failed?.let { (name, reason) -> "Couldn't save $name. $reason" }
                    ?: "Saved ${saved.singleOrNull()?.name ?: items(saved.size)} to $where",
            )
        }
    }

    /** Runs a long job with a progress card, one at a time. */
    @VisibleForTesting
    internal fun runLong(
        title: String,
        countsItems: Boolean = false,
        block: suspend (progress: (done: Long, total: Long, detail: String) -> Unit) -> Unit,
    ) {
        if (busy) {
            say("Wait for the current operation to finish")
            return
        }
        _current.value = Operation(title, "", 0, 0, countsItems)
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    block { done, total, detail -> _current.value = Operation(title, detail, done, total, countsItems) }
                }
            } catch (e: CancellationException) {
                say("Stopped")
            } catch (e: IOException) {
                say(e.message ?: "Something went wrong")
            } finally {
                _current.value = null
                changed()
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Works out a paste or drop before asking about conflicts; null (with a message) if it can't happen. */
    suspend fun plan(sources: List<File>, destination: File, move: Boolean): TransferPlan? = try {
        withContext(Dispatchers.IO) {
            val volumes = storage.volumes()
            val target = volumeFor(destination, volumes)
            FileOps.plan(sources, destination, move, sameVolume = { target != null && volumeFor(it, volumes) == target })
        }
    } catch (e: IOException) {
        say(e.message ?: "Couldn't ${if (move) "move" else "copy"} those items")
        null
    }

    fun transfer(plan: TransferPlan, policy: ConflictPolicy) {
        val count = plan.sources.size
        val verb = if (plan.move) "Moving" else "Copying"
        runLong("$verb ${items(count)}") { progress ->
            val result = FileOps.transfer(plan, policy) { bytes, name -> progress(bytes, plan.totalBytes, name) }
            if (plan.move) {
                for ((source, target) in result.targets) bookmarks.moveUnder(source.path, target.path, target.name)
                if (_clipboard.value?.move == true) _clipboard.value = null
            }
            val where = plan.destination.name.ifEmpty { plan.destination.path }
            val summary = when {
                result.failures.isNotEmpty() -> {
                    val (name, reason) = result.failures.first()
                    val others = result.failures.size - 1
                    "Couldn't ${if (plan.move) "move" else "copy"} $name. $reason" + if (others > 0) " (and ${items(others)} more)" else ""
                }
                result.done == 0 && result.skipped > 0 -> if (plan.move && plan.sources.all { it.parentFile?.path == plan.destination.path }) {
                    "Already in $where"
                } else {
                    "Skipped ${items(result.skipped)} already in $where"
                }
                else -> "${if (plan.move) "Moved" else "Copied"} ${items(result.done)} to $where"
            }
            say(summary)
        }
    }

    /** Moves [files] to the trash, with Undo; or deletes them for good when [permanently]. */
    fun delete(files: List<File>, permanently: Boolean) {
        if (permanently) {
            runLong("Deleting ${items(files.size)}", countsItems = true) { progress ->
                var failed: String? = null
                var done = 0
                files.forEachIndexed { i, file ->
                    progress(i.toLong(), files.size.toLong(), file.name)
                    if (FileOps.deleteRecursively(file)) done++ else if (failed == null) failed = file.name
                    bookmarks.deleteUnder(file.path)
                }
                say(if (failed != null) "Couldn't delete $failed" else "Deleted ${if (done == 1) files.first().name else items(done)}")
            }
            return
        }
        scope.launch {
            val result = trash.moveToTrash(files)
            changed()
            if (result.failures.isNotEmpty()) {
                val (name, reason) = result.failures.first()
                say("Couldn't move $name to the trash. $reason")
            } else {
                val what = if (result.done.size == 1) result.done.first().name else items(result.done.size)
                say("Moved $what to the trash", "Undo") { restore(result.done, quiet = true) }
            }
        }
    }

    fun restore(items: List<TrashItem>, quiet: Boolean = false) {
        scope.launch {
            val result = trash.restore(items)
            changed()
            when {
                result.failures.isNotEmpty() -> {
                    val (name, reason) = result.failures.first()
                    say("Couldn't restore $name. $reason")
                }
                quiet -> Unit
                result.done.size == 1 -> {
                    val file = result.done.first()
                    val renamed = file.name != items.first().name
                    say(if (renamed) "Restored as ${file.name}" else "Restored ${file.name}")
                }
                else -> say("Restored ${items(result.done.size)}")
            }
        }
    }

    fun deleteForever(items: List<TrashItem>) {
        scope.launch {
            val result = trash.deleteForever(items)
            changed()
            if (result.failures.isNotEmpty()) {
                say("Couldn't delete ${result.failures.first().first}")
            } else {
                say("Deleted ${if (result.done.size == 1) result.done.first().name else items(result.done.size)}")
            }
        }
    }

    fun emptyTrash() {
        scope.launch {
            val result = trash.empty()
            changed()
            say(if (result.failures.isEmpty()) "Emptied the trash" else "Couldn't delete ${result.failures.first().first}")
        }
    }

    fun compress(sources: List<File>, folder: File, name: String) {
        FileOps.validateName(name)?.let { say(it); return }
        val zipName = if (name.endsWith(".zip", ignoreCase = true)) name else "$name.zip"
        val target = File(folder, zipName)
        if (target.exists()) {
            say("$zipName already exists")
            return
        }
        runLong("Compressing ${items(sources.size)}") { progress ->
            val total = sources.sumOf { FileOps.measure(it).bytes }
            Archives.compress(sources, target) { bytes -> progress(bytes, total, zipName) }
            say("Compressed to $zipName")
        }
    }

    /** Extracts [archive] beside it; a [password] opens a protected 7z file. */
    fun extract(archive: File, password: String? = null) {
        _passwordNeeded.value = null
        val folder = archive.parentFile ?: return
        val toFile = Archives.extractsToFile(archive)
        val target = File(folder, FileOps.uniqueName(folder, Archives.extractedName(archive), isDirectory = !toFile))
        runLong("Extracting ${archive.name}") { progress ->
            val total = Archives.progressTotal(archive)
            try {
                Archives.extract(archive, target, password) { bytes -> progress(bytes, total, archive.name) }
            } catch (e: PasswordNeededException) {
                // Asked for on screen, rather than said in the snackbar.
                _passwordNeeded.value = e
                return@runLong
            }
            say(if (toFile) "Extracted ${target.name}" else "Extracted to ${target.name}")
        }
    }

    /** Quick changes that don't need a progress card. Returns the new file, or null after saying why. */
    fun createFolder(parent: File, name: String): File? = quick { FileOps.createFolder(parent, name) }

    fun createFile(parent: File, name: String): File? = quick { FileOps.createFile(parent, name) }

    fun rename(file: File, newName: String): File? = quick {
        val renamed = FileOps.rename(file, newName)
        if (renamed.isDirectory) scope.launch { bookmarks.moveUnder(file.path, renamed.path, renamed.name) }
        renamed
    }

    private fun quick(block: () -> File): File? = try {
        block().also { changed() }
    } catch (e: IOException) {
        say(e.message ?: "Something went wrong")
        null
    }

    /** Lets screens report their own results through the same snackbar. */
    fun tell(text: String) = say(text)
}
