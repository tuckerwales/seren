package wales.tucker.seren.auth.ui.accounts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import wales.tucker.seren.core.ui.theme.MonoFamily

/** Codes in their last seconds turn to the error color (the seconds are always shown too). */
const val URGENT_MILLIS = 5_000L

/**
 * A ring that empties as the current code runs out, with the seconds left in the middle. It is
 * told the time once a second and glides between ticks.
 */
@Composable
fun CountdownRing(remainingMillis: Long, periodMillis: Long, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val progress = remember { Animatable(remainingMillis.toFloat() / periodMillis) }
    LaunchedEffect(remainingMillis, periodMillis) {
        progress.snapTo(remainingMillis.toFloat() / periodMillis)
        val next = (remainingMillis - 1000).coerceAtLeast(0).toFloat() / periodMillis
        progress.animateTo(next, tween(1000, easing = LinearEasing))
    }
    val seconds = ((remainingMillis + 999) / 1000).toInt()
    val urgent = remainingMillis <= URGENT_MILLIS
    val color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier.size(size).clearAndSetSemantics { contentDescription = "$seconds seconds left" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, -360f * progress.value, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(
            seconds.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = MonoFamily,
            fontWeight = FontWeight.SemiBold,
            color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What a hidden code shows: a dot per digit, grouped like the code. */
fun hiddenCode(digits: Int): String {
    val dots = "•".repeat(digits)
    val split = digits / 2
    return if (digits < 5) dots else dots.substring(0, split) + " " + dots.substring(split)
}
