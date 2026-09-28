package wales.tucker.seren.ssh.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Find bar for SSH terminal scrollback. Kept separate from Edit's find UI: no replace, same
 * Material3 patterns (case toggle, next/prev, Esc to close).
 */
@Composable
fun ScrollbackFindBar(
    query: String,
    onQuery: (String) -> Unit,
    matchCase: Boolean,
    onMatchCase: (Boolean) -> Unit,
    status: String?,
    foreground: Color,
    accent: Color,
    background: Color,
    focusRequester: FocusRequester,
    onClose: () -> Unit,
    onFindNext: () -> Unit,
    onFindPrevious: () -> Unit,
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = foreground,
        unfocusedTextColor = foreground,
        focusedBorderColor = accent,
        unfocusedBorderColor = foreground.copy(alpha = 0.3f),
        cursorColor = accent,
        focusedLabelColor = accent,
        unfocusedLabelColor = foreground.copy(alpha = 0.6f),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .heightIn(max = 56.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when {
                            event.key == Key.Escape -> {
                                onClose()
                                true
                            }
                            event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                                if (event.isShiftPressed) onFindPrevious() else onFindNext()
                                true
                            }
                            else -> false
                        }
                    },
                singleLine = true,
                label = { Text("Find in scrollback") },
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onFindNext() }),
            )
            IconButton(onClick = onFindPrevious) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous match", tint = foreground)
            }
            IconButton(onClick = onFindNext) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next match", tint = foreground)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close find", tint = foreground)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = matchCase, onCheckedChange = onMatchCase)
            Text("Match case", color = foreground.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            if (status != null) {
                Spacer(Modifier.width(12.dp))
                Text(status, color = foreground.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
