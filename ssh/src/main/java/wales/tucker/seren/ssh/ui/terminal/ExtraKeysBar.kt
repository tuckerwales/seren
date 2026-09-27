package wales.tucker.seren.ssh.ui.terminal

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
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import wales.tucker.seren.core.ui.ExtraKeysRowHeight
import wales.tucker.seren.core.ui.KeyCap
import wales.tucker.seren.ssh.emulator.KeyEncoder
import wales.tucker.seren.ssh.emulator.TerminalKey

enum class ModifierState { OFF, ONCE, LOCKED }

/** Sticky Ctrl/Alt state shared between the extra keys bar and the terminal view. */
class StickyModifiers {
    var ctrl by mutableStateOf(ModifierState.OFF)
    var alt by mutableStateOf(ModifierState.OFF)

    val mask: Int
        get() = (if (ctrl != ModifierState.OFF) KeyEncoder.MOD_CTRL else 0) or
            (if (alt != ModifierState.OFF) KeyEncoder.MOD_ALT else 0)

    fun consume() {
        if (ctrl == ModifierState.ONCE) ctrl = ModifierState.OFF
        if (alt == ModifierState.ONCE) alt = ModifierState.OFF
    }

    fun cycle(state: ModifierState): ModifierState = when (state) {
        ModifierState.OFF -> ModifierState.ONCE
        ModifierState.ONCE -> ModifierState.LOCKED
        ModifierState.LOCKED -> ModifierState.OFF
    }
}

private sealed interface ExtraKey {
    val label: String

    /** What screen readers announce. */
    val description: String get() = label

    data class Special(
        override val label: String,
        val key: TerminalKey,
        val repeat: Boolean = false,
        override val description: String = label,
    ) : ExtraKey
    data class Text(override val label: String, val text: String, override val description: String = label) : ExtraKey
    data object Ctrl : ExtraKey {
        override val label = "CTRL"
        override val description = "Control"
    }
    data object Alt : ExtraKey { override val label = "ALT" }
}

private val KEYS: List<ExtraKey> = listOf(
    ExtraKey.Special("ESC", TerminalKey.ESCAPE, description = "Escape"),
    ExtraKey.Special("TAB", TerminalKey.TAB, description = "Tab"),
    ExtraKey.Ctrl,
    ExtraKey.Alt,
    ExtraKey.Special("←", TerminalKey.LEFT, repeat = true, description = "Left arrow"),
    ExtraKey.Special("↓", TerminalKey.DOWN, repeat = true, description = "Down arrow"),
    ExtraKey.Special("↑", TerminalKey.UP, repeat = true, description = "Up arrow"),
    ExtraKey.Special("→", TerminalKey.RIGHT, repeat = true, description = "Right arrow"),
    ExtraKey.Text("-", "-", "Minus"),
    ExtraKey.Text("/", "/", "Slash"),
    ExtraKey.Text("|", "|", "Pipe"),
    ExtraKey.Text("~", "~", "Tilde"),
    ExtraKey.Special("HOME", TerminalKey.HOME, description = "Home"),
    ExtraKey.Special("END", TerminalKey.END, description = "End"),
    ExtraKey.Special("PGUP", TerminalKey.PAGE_UP, repeat = true, description = "Page up"),
    ExtraKey.Special("PGDN", TerminalKey.PAGE_DOWN, repeat = true, description = "Page down"),
    ExtraKey.Special("DEL", TerminalKey.DELETE, repeat = true, description = "Delete"),
    ExtraKey.Text("\\", "\\", "Backslash"),
    ExtraKey.Text("_", "_", "Underscore"),
    ExtraKey.Text(":", ":", "Colon"),
    ExtraKey.Text("*", "*", "Asterisk"),
    ExtraKey.Text("&", "&", "Ampersand"),
    ExtraKey.Text("$", "$", "Dollar"),
    ExtraKey.Text("<", "<", "Less than"),
    ExtraKey.Text(">", ">", "Greater than"),
    ExtraKey.Text("{", "{", "Left brace"),
    ExtraKey.Text("}", "}", "Right brace"),
    ExtraKey.Text("[", "[", "Left bracket"),
    ExtraKey.Text("]", "]", "Right bracket"),
    ExtraKey.Special("F1", TerminalKey.F1),
    ExtraKey.Special("F2", TerminalKey.F2),
    ExtraKey.Special("F3", TerminalKey.F3),
    ExtraKey.Special("F4", TerminalKey.F4),
    ExtraKey.Special("F5", TerminalKey.F5),
    ExtraKey.Special("F6", TerminalKey.F6),
    ExtraKey.Special("F7", TerminalKey.F7),
    ExtraKey.Special("F8", TerminalKey.F8),
    ExtraKey.Special("F9", TerminalKey.F9),
    ExtraKey.Special("F10", TerminalKey.F10),
    ExtraKey.Special("F11", TerminalKey.F11),
    ExtraKey.Special("F12", TerminalKey.F12),
)

/** A key the user can show or hide in settings: its label (the stored id) and spoken name. */
data class ExtraKeyChoice(val label: String, val description: String)

val EXTRA_KEY_CHOICES: List<ExtraKeyChoice> = KEYS.map { ExtraKeyChoice(it.label, it.description) }

@Composable
fun ExtraKeysBar(
    modifiers: StickyModifiers,
    background: Color,
    foreground: Color,
    accent: Color,
    onKey: (TerminalKey) -> Unit,
    onText: (String) -> Unit,
    onToggleKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
    hidden: Set<String> = emptySet(),
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
            description = "Toggle keyboard",
            foreground = foreground,
            active = false,
            accent = accent,
            onPress = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleKeyboard()
            },
            modifier = Modifier.padding(start = 4.dp),
        ) {
            Icon(Icons.Rounded.Keyboard, contentDescription = null, tint = foreground, modifier = Modifier.size(20.dp))
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
            KEYS.filterNot { it.label in hidden }.forEach { key ->
                val state = when (key) {
                    ExtraKey.Ctrl -> modifiers.ctrl
                    ExtraKey.Alt -> modifiers.alt
                    else -> ModifierState.OFF
                }
                KeyCap(
                    label = key.label,
                    description = key.description,
                    stateDescription = when {
                        key != ExtraKey.Ctrl && key != ExtraKey.Alt -> null
                        state == ModifierState.OFF -> "Off"
                        state == ModifierState.ONCE -> "On for the next key"
                        else -> "Locked on"
                    },
                    foreground = foreground,
                    active = state != ModifierState.OFF,
                    locked = state == ModifierState.LOCKED,
                    accent = accent,
                    repeat = (key as? ExtraKey.Special)?.repeat == true,
                    onPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        when (key) {
                            ExtraKey.Ctrl -> modifiers.ctrl = modifiers.cycle(modifiers.ctrl)
                            ExtraKey.Alt -> modifiers.alt = modifiers.cycle(modifiers.alt)
                            is ExtraKey.Special -> onKey(key.key)
                            is ExtraKey.Text -> onText(key.text)
                        }
                    },
                )
            }
        }
    }
}
