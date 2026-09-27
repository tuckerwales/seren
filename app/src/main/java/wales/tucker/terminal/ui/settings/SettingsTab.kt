package wales.tucker.terminal.ui.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import wales.tucker.terminal.BuildConfig
import wales.tucker.terminal.MainActivity
import wales.tucker.terminal.data.Settings
import wales.tucker.terminal.data.SettingsRepository
import wales.tucker.terminal.data.ThemeMode
import wales.tucker.terminal.emulator.ColorScheme
import wales.tucker.terminal.emulator.ColorSchemes
import wales.tucker.terminal.emulator.CursorShape
import wales.tucker.terminal.ui.common.SectionHeader
import wales.tucker.terminal.ui.common.appContainer
import wales.tucker.terminal.ui.theme.MonoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(settings: Settings, onKnownHosts: () -> Unit, onExtraKeys: () -> Unit) {
    val container = appContainer()
    val repo = container.settings
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Settings", fontWeight = FontWeight.SemiBold) }, windowInsets = WindowInsets.statusBars)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            SectionHeader("Appearance")
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Theme", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                val modes = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { scope.launch { repo.setThemeMode(mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        ) { Text(label) }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow("Dynamic color", "Use colors from your wallpaper", settings.dynamicColor) {
                    scope.launch { repo.setDynamicColor(it) }
                }
            }

            SectionHeader("Terminal")
            Text(
                "Color scheme",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(ColorSchemes.ALL, key = { it.id }) { scheme ->
                    SchemeCard(scheme, selected = settings.colorSchemeId == scheme.id) {
                        scope.launch { repo.setColorScheme(scheme.id) }
                    }
                }
            }

            FontSizeRow(settings.fontSize, ColorSchemes.byId(settings.colorSchemeId)) { size ->
                scope.launch { repo.setFontSize(size) }
            }

            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Cursor", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                val shapes = listOf(CursorShape.BLOCK to "Block", CursorShape.UNDERLINE to "Underline", CursorShape.BAR to "Bar")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    shapes.forEachIndexed { i, (shape, label) ->
                        SegmentedButton(
                            selected = settings.cursorShape == shape,
                            onClick = { scope.launch { repo.setCursorShape(shape) } },
                            shape = SegmentedButtonDefaults.itemShape(i, shapes.size),
                        ) { Text(label) }
                    }
                }
            }
            SwitchRow("Blinking cursor", null, settings.cursorBlink) { scope.launch { repo.setCursorBlink(it) } }

            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Scrollback", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Lines of history kept for each session",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1000, 5000, 10000, 50000).forEach { lines ->
                        FilterChip(
                            selected = settings.scrollback == lines,
                            onClick = { scope.launch { repo.setScrollback(lines) } },
                            label = { Text(if (lines >= 1000) "${lines / 1000}k" else "$lines") },
                        )
                    }
                }
            }
            SwitchRow("Extra keys row", "Esc, Tab, Ctrl, Alt, arrows and more above the keyboard", settings.showExtraKeys) {
                scope.launch { repo.setShowExtraKeys(it) }
            }
            if (settings.showExtraKeys) {
                val hidden = settings.hiddenExtraKeys.size
                NavRow("Customize extra keys", if (hidden == 0) "All keys shown" else "$hidden hidden", onExtraKeys)
            }
            SwitchRow("Volume keys as Ctrl and Alt", "Hold volume down for Ctrl, volume up for Alt", settings.volumeKeysAsModifiers) {
                scope.launch { repo.setVolumeKeysAsModifiers(it) }
            }
            SwitchRow("Keep screen on", "While a terminal is visible", settings.keepScreenOn) {
                scope.launch { repo.setKeepScreenOn(it) }
            }
            SwitchRow("Vibrate on bell", null, settings.vibrateOnBell) { scope.launch { repo.setVibrateOnBell(it) } }

            SectionHeader("Security")
            SwitchRow("App lock", "Require biometrics or your screen lock to open the app, and hide it in recent apps", settings.appLock) { enabled ->
                val activity = context as? MainActivity
                if (enabled && activity != null) {
                    if (!activity.canAuthenticate()) {
                        Toast.makeText(context, "Set up a screen lock or biometrics first", Toast.LENGTH_LONG).show()
                    } else {
                        activity.authenticate { ok -> if (ok) scope.launch { repo.setAppLock(true) } }
                    }
                } else {
                    scope.launch { repo.setAppLock(false) }
                }
            }
            SwitchRow("Confirm before disconnecting", null, settings.confirmDisconnect) {
                scope.launch { repo.setConfirmDisconnect(it) }
            }
            NavRow("Known hosts", "Manage trusted server fingerprints", onKnownHosts)

            SectionHeader("About")
            NavRow("Terminal ${BuildConfig.VERSION_NAME}", "Open source licenses") { showAbout = true }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            icon = { Icon(Icons.Rounded.Info, null) },
            title = { Text("Terminal ${BuildConfig.VERSION_NAME}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A modern SSH client for Android.")
                    Text("Built with:", fontWeight = FontWeight.SemiBold)
                    Text("• JSch (mwiede fork), BSD license")
                    Text("• Bouncy Castle, MIT license")
                    Text("• JetBrains Mono, SIL Open Font License 1.1")
                    Text("• AndroidX and Jetpack Compose, Apache 2.0")
                }
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) }.padding(horizontal = 4.dp),
    )
}

@Composable
private fun NavRow(title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp),
    )
}

@Composable
private fun SchemeCard(scheme: ColorScheme, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = if (selected) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = Color(scheme.background)),
        modifier = Modifier.width(150.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            val fg = Color(scheme.foreground)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color(scheme.ansi[2]))) { append("user@host") }
                    withStyle(SpanStyle(color = fg)) { append(":") }
                    withStyle(SpanStyle(color = Color(scheme.ansi[4]))) { append("~") }
                    withStyle(SpanStyle(color = fg)) { append("$ ls") }
                },
                fontFamily = MonoFamily,
                fontSize = 11.sp,
                maxLines = 1,
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color(scheme.ansi[4]))) { append("src ") }
                    withStyle(SpanStyle(color = Color(scheme.ansi[2]))) { append("run.sh ") }
                    withStyle(SpanStyle(color = Color(scheme.ansi[1]))) { append("x") }
                },
                fontFamily = MonoFamily,
                fontSize = 11.sp,
                maxLines = 1,
            )
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

@Composable
private fun FontSizeRow(size: Float, scheme: ColorScheme, onChange: (Float) -> Unit) {
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
            valueRange = SettingsRepository.MIN_FONT..SettingsRepository.MAX_FONT,
            steps = (SettingsRepository.MAX_FONT - SettingsRepository.MIN_FONT).toInt() - 1,
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(scheme.background)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "$ echo \"Hello, world\"",
                color = Color(scheme.foreground),
                fontFamily = MonoFamily,
                fontSize = value.sp,
                maxLines = 1,
                modifier = Modifier.padding(12.dp),
            )
        }
        Text(
            "Tip: pinch the terminal to zoom",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** "13" or "13.5": pinch to zoom stores half steps, which the slider alone never produces. */
internal fun formatFontSize(size: Float): String {
    val halves = Math.round(size * 2)
    return if (halves % 2 == 0) (halves / 2).toString() else "${halves / 2}.5"
}
