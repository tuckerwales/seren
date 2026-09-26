package wales.tucker.terminal.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import wales.tucker.terminal.emulator.KeyEncoder
import wales.tucker.terminal.emulator.TerminalKey
import wales.tucker.terminal.ui.theme.MonoFamily

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

    data class Special(override val label: String, val key: TerminalKey, val repeat: Boolean = false) : ExtraKey
    data class Text(override val label: String, val text: String) : ExtraKey
    data object Ctrl : ExtraKey { override val label = "CTRL" }
    data object Alt : ExtraKey { override val label = "ALT" }
}

private val KEYS: List<ExtraKey> = listOf(
    ExtraKey.Special("ESC", TerminalKey.ESCAPE),
    ExtraKey.Special("TAB", TerminalKey.TAB),
    ExtraKey.Ctrl,
    ExtraKey.Alt,
    ExtraKey.Special("←", TerminalKey.LEFT, repeat = true),
    ExtraKey.Special("↓", TerminalKey.DOWN, repeat = true),
    ExtraKey.Special("↑", TerminalKey.UP, repeat = true),
    ExtraKey.Special("→", TerminalKey.RIGHT, repeat = true),
    ExtraKey.Text("-", "-"),
    ExtraKey.Text("/", "/"),
    ExtraKey.Text("|", "|"),
    ExtraKey.Text("~", "~"),
    ExtraKey.Special("HOME", TerminalKey.HOME),
    ExtraKey.Special("END", TerminalKey.END),
    ExtraKey.Special("PGUP", TerminalKey.PAGE_UP, repeat = true),
    ExtraKey.Special("PGDN", TerminalKey.PAGE_DOWN, repeat = true),
    ExtraKey.Special("DEL", TerminalKey.DELETE, repeat = true),
    ExtraKey.Text("\\", "\\"),
    ExtraKey.Text("_", "_"),
    ExtraKey.Text(":", ":"),
    ExtraKey.Text("*", "*"),
    ExtraKey.Text("&", "&"),
    ExtraKey.Text("$", "$"),
    ExtraKey.Text("<", "<"),
    ExtraKey.Text(">", ">"),
    ExtraKey.Text("{", "{"),
    ExtraKey.Text("}", "}"),
    ExtraKey.Text("[", "["),
    ExtraKey.Text("]", "]"),
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
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyCap(
            label = null,
            foreground = foreground,
            active = false,
            accent = accent,
            onPress = { onToggleKeyboard() },
            modifier = Modifier.padding(start = 4.dp),
        ) {
            Icon(Icons.Rounded.Keyboard, contentDescription = "Toggle keyboard", tint = foreground, modifier = Modifier.size(20.dp))
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
            KEYS.forEach { key ->
                val state = when (key) {
                    ExtraKey.Ctrl -> modifiers.ctrl
                    ExtraKey.Alt -> modifiers.alt
                    else -> ModifierState.OFF
                }
                KeyCap(
                    label = key.label,
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

@Composable
private fun KeyCap(
    label: String?,
    foreground: Color,
    active: Boolean,
    accent: Color,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    repeat: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val bg = when {
        active -> accent.copy(alpha = if (locked) 0.9f else 0.55f)
        pressed -> foreground.copy(alpha = 0.22f)
        else -> foreground.copy(alpha = 0.08f)
    }
    Box(
        modifier = modifier
            .height(36.dp)
            .widthIn(min = 42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .pointerInput(repeat) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    // Fire on release so that horizontal scrolling of the bar does not type keys;
                    // repeatable keys also auto-repeat while held.
                    var repeated = false
                    var job: Job? = null
                    if (repeat) {
                        job = scope.launch {
                            delay(400)
                            repeated = true
                            while (true) {
                                onPress()
                                delay(60)
                            }
                        }
                    }
                    val up = waitForUpOrCancellation()
                    job?.cancel()
                    pressed = false
                    if (up != null && !repeated) onPress()
                }
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) {
            content()
        } else if (label != null) {
            Text(
                label,
                color = if (active) Color.White else foreground,
                fontFamily = MonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
    }
}
