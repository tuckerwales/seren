package wales.tucker.seren.core.ui

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wales.tucker.seren.core.content.ContentColorScheme
import wales.tucker.seren.core.content.ContentColorSchemes
import wales.tucker.seren.core.ui.theme.MonoFamily
import wales.tucker.seren.core.ui.theme.ThemeMode

/** The line every About dialog, README and store listing tells about the suite. */
const val SUITE_STORY = "Seren is Welsh for star. Seren apps are free, open source (Apache License 2.0), and have no ads and no tracking."

/**
 * The Appearance section that opens every app's settings: Theme, then Dynamic color on Android
 * 12 and newer.
 */
@Composable
fun AppearanceSection(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
) {
    SectionHeader("Appearance")
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text("Theme", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        val modes = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = themeMode == mode,
                    onClick = { onThemeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                ) { Text(label) }
            }
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SwitchRow("Dynamic color", "Use colors from your wallpaper", dynamicColor, onChange = onDynamicColor)
    }
}

/**
 * A horizontal row of content color scheme cards. Each card shows [preview] (two short lines of
 * sample content in the scheme's colors), a row of swatches and the scheme name.
 */
@Composable
fun ColorSchemePicker(
    selectedId: String,
    onSelect: (ContentColorScheme) -> Unit,
    preview: (ContentColorScheme) -> List<AnnotatedString>,
) {
    Text(
        "Color scheme",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    Spacer(Modifier.height(8.dp))
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(ContentColorSchemes.ALL, key = { it.id }) { scheme ->
            ColorSchemeCard(scheme, preview(scheme), selected = selectedId == scheme.id) { onSelect(scheme) }
        }
    }
}

@Composable
private fun ColorSchemeCard(scheme: ContentColorScheme, preview: List<AnnotatedString>, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = if (selected) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = Color(scheme.background)),
        modifier = Modifier.width(150.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            val fg = Color(scheme.foreground)
            preview.forEach { line ->
                Text(line, color = fg, fontFamily = MonoFamily, fontSize = 11.sp, maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (i in 1..6) {
                    Spacer(
                        Modifier
                            .size(12.dp)
                            .background(Color(scheme.ansi[i]), RoundedCornerShape(3.dp)),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(scheme.name, color = fg, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * The content text size slider with a live preview of [sample] in [scheme], and a [tip] under it
 * (for example how to pinch to zoom).
 */
@Composable
fun TextSizeSetting(
    size: Float,
    range: ClosedFloatingPointRange<Float>,
    scheme: ContentColorScheme,
    sample: String,
    tip: String?,
    onChange: (Float) -> Unit,
) {
    var value by remember(size) { mutableFloatStateOf(size) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Text size", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${formatFontSize(value)} sp", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value) },
            valueRange = range,
            steps = (range.endInclusive - range.start).toInt() - 1,
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(scheme.background)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                sample,
                color = Color(scheme.foreground),
                fontFamily = MonoFamily,
                fontSize = value.sp,
                maxLines = 1,
                modifier = Modifier.padding(12.dp),
            )
        }
        if (tip != null) {
            Text(
                tip,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** "13" or "13.5": pinch to zoom stores half steps, which the slider alone never produces. */
fun formatFontSize(size: Float): String {
    val halves = Math.round(size * 2)
    return if (halves % 2 == 0) (halves / 2).toString() else "${halves / 2}.5"
}

/**
 * The About dialog: [appName] and [version] as the title, one sentence about the app, the suite
 * story and every bundled library with its license.
 */
@Composable
fun AboutDialog(appName: String, version: String, description: String, licenses: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Info, null) },
        title = { Text("$appName $version") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description)
                Text(SUITE_STORY)
                Text("Built with:", fontWeight = FontWeight.SemiBold)
                licenses.forEach { Text("• $it") }
                Text(
                    "Seren itself is licensed under the Apache License 2.0.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Libraries every Seren app bundles through Seren Core, for the About dialog. */
val CORE_LICENSES = listOf(
    "JetBrains Mono, SIL Open Font License 1.1",
    "AndroidX and Jetpack Compose, Apache 2.0",
)
