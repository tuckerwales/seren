package wales.tucker.seren.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import wales.tucker.seren.core.ui.theme.MonoFamily

/** Height of an extra keys row above the keyboard; its keys are [KeyCap]s. */
val ExtraKeysRowHeight = 46.dp

/**
 * One key in an extra keys row over a dark canvas: a 10 dp rounded key with a subtle tint of
 * [foreground] and a mono caps [label] (or custom [content]). It fires on release, so scrolling
 * the row does not type keys, and a [repeat] key auto-repeats while held. [active] keys (such as a
 * sticky modifier that is on) are filled with [accent], more strongly when [locked].
 */
@Composable
fun KeyCap(
    label: String?,
    description: String,
    foreground: Color,
    active: Boolean,
    accent: Color,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    repeat: Boolean = false,
    stateDescription: String? = null,
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
            // The press handling below is raw pointer input, which accessibility services can't
            // see; describe the key as a button they can activate.
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = description
                stateDescription?.let { this.stateDescription = it }
                onClick { onPress(); true }
            }
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
