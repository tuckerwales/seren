package wales.tucker.seren.auth.ui.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wales.tucker.seren.auth.R
import wales.tucker.seren.auth.ui.common.PasswordField

/** The dialog for whichever step of an import [vm] is at, if any. */
@Composable
fun ImportDialogs(vm: ImportViewModel) {
    when (val state = vm.state.collectAsStateWithLifecycle().value) {
        ImportState.Idle -> Unit
        is ImportState.NeedsPassword -> PasswordDialog(state, onUnlock = vm::unlock, onDismiss = vm::dismiss)
        is ImportState.Confirm -> ConfirmDialog(state, onConfirm = vm::confirm, onDismiss = vm::dismiss)
        is ImportState.Failed -> AlertDialog(
            onDismissRequest = vm::dismiss,
            icon = { Icon(Icons.Rounded.ErrorOutline, null) },
            title = { Text(state.title) },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text("Close") } },
        )
    }
}

@Composable
private fun PasswordDialog(state: ImportState.NeedsPassword, onUnlock: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    val submit = { if (password.isNotEmpty() && !state.working) onUnlock(password.toCharArray()) }
    AlertDialog(
        onDismissRequest = { if (!state.working) onDismiss() },
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text("Enter the password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("This file from ${state.source} is encrypted. Enter the password it was exported with.")
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    error = state.error,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = password.isNotEmpty() && !state.working) {
                if (state.working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Unlock")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.working) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmDialog(state: ImportState.Confirm, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val import = state.import
    val total = import.entries.size
    val new = total - state.existing
    if (new == 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Rounded.FileDownload, null) },
            title = { Text("Nothing new to import") },
            text = { Text(pluralStringResource(R.plurals.all_existing, total, total, import.source)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = { if (!state.working) onDismiss() },
        icon = { Icon(Icons.Rounded.FileDownload, null) },
        title = { Text(pluralStringResource(R.plurals.import_question, new, new, import.source)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.existing > 0) Text(pluralStringResource(R.plurals.existing_skipped, state.existing, state.existing))
                if (import.unsupported > 0) Text(pluralStringResource(R.plurals.unsupported_skipped, import.unsupported, import.unsupported))
                Text("Setup keys are stored encrypted with a hardware-backed key on this device.")
                import.note?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.working) {
                if (state.working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Import")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.working) { Text("Cancel") } },
    )
}
