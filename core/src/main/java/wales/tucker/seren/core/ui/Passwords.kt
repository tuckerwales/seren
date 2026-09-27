package wales.tucker.seren.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** The shortest password a backup file may be protected with. */
const val MIN_BACKUP_PASSWORD = 8

/**
 * An outlined password field with a show/hide eye. [onReveal], when given, is asked before the
 * text is shown (for example to confirm the user's identity) and calls back to show it.
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onReveal: ((show: () -> Unit) -> Unit)? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        textStyle = textStyle,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = keyboardOptions.copy(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        keyboardActions = keyboardActions,
        trailingIcon = {
            IconButton(onClick = {
                if (visible || onReveal == null) visible = !visible else onReveal { visible = true }
            }) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "Hide $label" else "Show $label",
                )
            }
        },
        modifier = modifier,
    )
}

/** Asks for a new password, twice, to protect a backup file with. */
@Composable
fun NewBackupPasswordDialog(
    onExport: (CharArray) -> Unit,
    onDismiss: () -> Unit,
    message: String = "You'll need it to import the backup. If you forget it, the backup can't be opened.",
) {
    var password by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    val tooShort = password.length < MIN_BACKUP_PASSWORD
    val mismatch = password != repeat
    val submit = {
        tried = true
        if (!tooShort && !mismatch) onExport(password.toCharArray())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text("Choose a password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message)
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    error = if (tried && tooShort) "Use at least $MIN_BACKUP_PASSWORD characters" else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                PasswordField(
                    value = repeat,
                    onValueChange = { repeat = it },
                    label = "Password again",
                    error = if (tried && !tooShort && mismatch) "The passwords don't match" else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = submit) { Text("Export") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Asks for the password an encrypted file was exported with. [error] shows under the field (for a
 * wrong password) and [working] shows progress while the password is checked.
 */
@Composable
fun UnlockBackupDialog(
    message: String,
    onUnlock: (CharArray) -> Unit,
    onDismiss: () -> Unit,
    error: String? = null,
    working: Boolean = false,
) {
    var password by rememberSaveable { mutableStateOf("") }
    val submit = { if (password.isNotEmpty() && !working) onUnlock(password.toCharArray()) }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text("Enter the password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message)
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    error = error,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = password.isNotEmpty() && !working) {
                if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Unlock")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel") } },
    )
}
