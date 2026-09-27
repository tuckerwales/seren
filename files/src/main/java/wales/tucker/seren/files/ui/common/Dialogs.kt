package wales.tucker.seren.files.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wales.tucker.seren.core.ui.PasswordField
import wales.tucker.seren.core.ui.copyToClipboard
import wales.tucker.seren.core.ui.formatSize
import wales.tucker.seren.core.ui.theme.MonoSmall
import wales.tucker.seren.files.fs.ConflictPolicy
import wales.tucker.seren.files.fs.FileEntry
import wales.tucker.seren.files.fs.FileKind
import wales.tucker.seren.files.fs.FileOps
import wales.tucker.seren.files.fs.FileTypes
import wales.tucker.seren.files.fs.PasswordNeededException
import wales.tucker.seren.files.fs.TransferPlan
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Asks for a name, for New folder, New file, Rename and Compress. The part before the extension
 * starts selected, so typing replaces the name but keeps ".jpg".
 */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    label: String = "Name",
    keepExtension: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val selectEnd = if (keepExtension) initial.lastIndexOf('.').takeIf { it > 0 } ?: initial.length else initial.length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, selectEnd))) }
    val name = value.text.trim()
    val problem = if (value.text.isEmpty()) null else FileOps.validateName(name)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val submit = { if (name.isNotEmpty() && problem == null) onConfirm(name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(it, null) } },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                isError = problem != null,
                supportingText = problem?.let { { Text(it) } },
                textStyle = MonoSmall.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = submit, enabled = name.isNotEmpty() && problem == null) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Asks for the password to a protected archive, saying so when the last one didn't open it. */
@Composable
fun ArchivePasswordDialog(request: PasswordNeededException, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    val submit = { if (password.isNotEmpty()) onConfirm(password) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text("Password for ${request.archive.name}") },
        text = {
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                error = if (request.wrong && password.isEmpty()) "That password didn't open it. Try again." else null,
                supporting = if (request.wrong) null else "It's protected, so its contents need a password to extract.",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = submit, enabled = password.isNotEmpty()) { Text("Extract") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Asks what to do when items being pasted have the same names as items already there. */
@Composable
fun ConflictDialog(plan: TransferPlan, itemsText: (Int) -> String, onDismiss: () -> Unit, onConfirm: (ConflictPolicy) -> Unit) {
    var policy by rememberSaveable { mutableStateOf(ConflictPolicy.REPLACE) }
    val where = plan.destination.name
    val count = plan.conflicts.size
    val single = plan.conflicts.singleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.FileCopy, null) },
        title = { Text(if (single != null) "Replace ${single.name}?" else "Replace ${itemsText(count)}?") },
        text = {
            Column {
                Text(
                    if (single != null) {
                        "$where already has ${if (single.isDirectory) "a folder" else "a file"} with this name."
                    } else {
                        "$where already has ${itemsText(count)} with the same names."
                    },
                )
                Spacer(Modifier.height(12.dp))
                val options = listOf(
                    ConflictPolicy.REPLACE to "Replace" to "Files are overwritten; folders are merged",
                    ConflictPolicy.KEEP_BOTH to "Keep both" to "New ones get a number, like \"photo (1).jpg\"",
                    ConflictPolicy.SKIP to "Skip" to "Leave what's there and don't paste these",
                )
                options.forEach { (pair, subtitle) ->
                    val (option, label) = pair
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(selected = policy == option, role = Role.RadioButton) { policy = option }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = policy == option, onClick = null, modifier = Modifier.padding(horizontal = 8.dp))
                        Column {
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(policy) }) { Text(if (plan.move) "Move" else "Copy") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Confirms deleting [files]: to the trash when [useTrash], or for good. */
@Composable
fun DeleteDialog(files: List<File>, useTrash: Boolean, itemsText: (Int) -> String, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val what = files.singleOrNull()?.name ?: itemsText(files.size)
    val hasFolder = files.any { it.isDirectory }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (useTrash) Icons.Rounded.Delete else Icons.Rounded.DeleteForever, null) },
        title = { Text(if (useTrash) "Move $what to the trash?" else "Delete $what?") },
        text = {
            Text(
                if (useTrash) {
                    "You can restore ${if (files.size == 1) "it" else "them"} from the trash for 30 days."
                } else {
                    "${if (files.size == 1) "It" else "They"} will be deleted for good" +
                        (if (hasFolder) ", with everything inside." else ".") + " This can't be undone."
                },
            )
        },
        confirmButton = { TextButton(onClick = onDelete) { Text(if (useTrash) "Move to trash" else "Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Everything about one file or folder: type, size, dates, where it is, and a checksum on request. */
@Composable
fun DetailsDialog(entry: FileEntry, itemsText: (Int) -> String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = entry.file
    val size by produceState<String?>(null, entry.path) {
        value = withContext(Dispatchers.IO) {
            if (entry.isDirectory) {
                val m = FileOps.measure(file)
                val inside = m.files + m.folders - 1
                "${formatSize(m.bytes)}, ${itemsText(inside)}"
            } else {
                formatSize(entry.size)
            }
        }
    }
    var checksum by remember { mutableStateOf<String?>(null) }
    var hashing by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Info, null) },
        title = { Text(entry.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailRow("Type", if (entry.kind == FileKind.FOLDER) "Folder" else "${entry.kind.label} (${FileTypes.mimeType(entry.name)})")
                DetailRow("Size", size ?: "Counting…")
                DetailRow("Changed", DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.modified)))
                Column {
                    Text("Where", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    MonoBlock(file.parent.orEmpty()) { copyToClipboard(context, "Path", entry.path) }
                }
                if (!entry.isDirectory) {
                    Column {
                        Text("SHA-256", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        val sum = checksum
                        if (sum != null) {
                            MonoBlock(sum) { copyToClipboard(context, "SHA-256", sum) }
                        } else {
                            TextButton(
                                enabled = !hashing,
                                onClick = {
                                    hashing = true
                                    scope.launch {
                                        checksum = withContext(Dispatchers.IO) { runCatching { FileOps.sha256(file) }.getOrNull() } ?: "Couldn't read the file"
                                        hashing = false
                                    }
                                },
                            ) { Text(if (hashing) "Calculating…" else "Calculate") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Mono text on a detail block, with a copy button. */
@Composable
private fun MonoBlock(text: String, onCopy: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClickLabel = "Copy", onClick = onCopy)
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MonoSmall, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, "Copy") }
    }
}
