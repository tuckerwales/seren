package wales.tucker.seren.edit.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import wales.tucker.seren.core.ui.ExtraKeysRowHeight
import wales.tucker.seren.core.ui.KeyCap

/** What a key in the editor's extra keys row does. */
sealed interface EditorKey {
    data object Tab : EditorKey
    data object Left : EditorKey
    data object Right : EditorKey
    data object Up : EditorKey
    data object Down : EditorKey
    data object Home : EditorKey
    data object End : EditorKey
    data object Undo : EditorKey
    data object Redo : EditorKey
    data class Text(val text: String) : EditorKey
}

private class KeySpec(
    val key: EditorKey,
    val label: String?,
    val description: String,
    val icon: ImageVector? = null,
    val repeat: Boolean = false,
)

private val KEYS = listOf(
    KeySpec(EditorKey.Tab, "TAB", "Tab"),
    KeySpec(EditorKey.Left, "←", "Left arrow", repeat = true),
    KeySpec(EditorKey.Down, "↓", "Down arrow", repeat = true),
    KeySpec(EditorKey.Up, "↑", "Up arrow", repeat = true),
    KeySpec(EditorKey.Right, "→", "Right arrow", repeat = true),
    KeySpec(EditorKey.Home, "HOME", "Home"),
    KeySpec(EditorKey.End, "END", "End"),
    KeySpec(EditorKey.Undo, null, "Undo", icon = Icons.AutoMirrored.Rounded.Undo, repeat = true),
    KeySpec(EditorKey.Redo, null, "Redo", icon = Icons.AutoMirrored.Rounded.Redo, repeat = true),
    KeySpec(EditorKey.Text("("), "(", "Left parenthesis"),
    KeySpec(EditorKey.Text(")"), ")", "Right parenthesis"),
    KeySpec(EditorKey.Text("{"), "{", "Left brace"),
    KeySpec(EditorKey.Text("}"), "}", "Right brace"),
    KeySpec(EditorKey.Text("["), "[", "Left bracket"),
    KeySpec(EditorKey.Text("]"), "]", "Right bracket"),
    KeySpec(EditorKey.Text("<"), "<", "Less than"),
    KeySpec(EditorKey.Text(">"), ">", "Greater than"),
    KeySpec(EditorKey.Text("\""), "\"", "Double quote"),
    KeySpec(EditorKey.Text("'"), "'", "Single quote"),
    KeySpec(EditorKey.Text("="), "=", "Equals"),
    KeySpec(EditorKey.Text(";"), ";", "Semicolon"),
    KeySpec(EditorKey.Text(":"), ":", "Colon"),
    KeySpec(EditorKey.Text("/"), "/", "Slash"),
    KeySpec(EditorKey.Text("\\"), "\\", "Backslash"),
    KeySpec(EditorKey.Text("|"), "|", "Pipe"),
    KeySpec(EditorKey.Text("&"), "&", "Ampersand"),
    KeySpec(EditorKey.Text("#"), "#", "Hash"),
    KeySpec(EditorKey.Text("-"), "-", "Minus"),
    KeySpec(EditorKey.Text("_"), "_", "Underscore"),
    KeySpec(EditorKey.Text("*"), "*", "Asterisk"),
)

/**
 * Keys that phone keyboards make awkward, above the keyboard, styled like Seren SSH's extra keys:
 * Tab, arrows, Home and End, undo and redo, brackets and common symbols.
 */
@Composable
fun EditorKeysBar(
    background: Color,
    foreground: Color,
    accent: Color,
    canUndo: Boolean,
    canRedo: Boolean,
    onKey: (EditorKey) -> Unit,
    onHideKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ExtraKeysRowHeight)
            .background(background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyCap(
            label = null,
            description = "Hide keyboard",
            foreground = foreground,
            active = false,
            accent = accent,
            onPress = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onHideKeyboard()
            },
            modifier = Modifier.padding(start = 4.dp),
        ) {
            Icon(Icons.Rounded.KeyboardHide, contentDescription = null, tint = foreground, modifier = Modifier.size(20.dp))
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KEYS.forEach { spec ->
                val enabled = when (spec.key) {
                    EditorKey.Undo -> canUndo
                    EditorKey.Redo -> canRedo
                    else -> true
                }
                val tint = if (enabled) foreground else foreground.copy(alpha = 0.35f)
                KeyCap(
                    label = spec.label,
                    description = spec.description,
                    foreground = foreground,
                    active = false,
                    accent = accent,
                    repeat = spec.repeat,
                    onPress = {
                        if (enabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onKey(spec.key)
                        }
                    },
                    content = spec.icon?.let { icon -> { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) } },
                )
            }
        }
    }
}
