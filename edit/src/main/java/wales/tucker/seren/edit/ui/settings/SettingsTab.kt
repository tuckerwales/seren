package wales.tucker.seren.edit.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import wales.tucker.seren.core.content.ContentColorScheme
import wales.tucker.seren.core.content.ContentColorSchemes
import wales.tucker.seren.core.ui.AboutDialog
import wales.tucker.seren.core.ui.AppearanceSection
import wales.tucker.seren.core.ui.CORE_LICENSES
import wales.tucker.seren.core.ui.ColorSchemePicker
import wales.tucker.seren.core.ui.NavRow
import wales.tucker.seren.core.ui.SectionHeader
import wales.tucker.seren.core.ui.SwitchRow
import wales.tucker.seren.core.ui.TextSizeSetting
import wales.tucker.seren.edit.BuildConfig
import wales.tucker.seren.edit.MainActivity
import wales.tucker.seren.edit.data.Settings
import wales.tucker.seren.edit.data.SettingsRepository
import wales.tucker.seren.edit.ui.appContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(settings: Settings) {
    val repo = appContainer().settings
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
            AppearanceSection(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
                onThemeMode = { scope.launch { repo.setThemeMode(it) } },
                onDynamicColor = { scope.launch { repo.setDynamicColor(it) } },
            )

            SectionHeader("Editor")
            ColorSchemePicker(
                selectedId = settings.colorSchemeId,
                onSelect = { scope.launch { repo.setColorScheme(it.id) } },
                preview = ::codePreview,
            )
            TextSizeSetting(
                size = settings.fontSize,
                range = SettingsRepository.MIN_FONT..SettingsRepository.MAX_FONT,
                scheme = ContentColorSchemes.byId(settings.colorSchemeId),
                sample = "fun main() = println(\"Hi\")",
                tip = "Tip: pinch the text to zoom",
                onChange = { scope.launch { repo.setFontSize(it) } },
            )
            SwitchRow("Word wrap", "Wrap long lines to fit the screen", settings.wordWrap) {
                scope.launch { repo.setWordWrap(it) }
            }
            SwitchRow("Line numbers", null, settings.lineNumbers) { scope.launch { repo.setLineNumbers(it) } }
            SwitchRow("Extra keys row", "Tab, arrows, undo, brackets and more above the keyboard", settings.showExtraKeys) {
                scope.launch { repo.setShowExtraKeys(it) }
            }

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

            SectionHeader("About")
            NavRow("Seren Edit ${BuildConfig.VERSION_NAME}", "Open source licenses") { showAbout = true }
        }
    }

    if (showAbout) {
        AboutDialog(
            appName = "Seren Edit",
            version = BuildConfig.VERSION_NAME,
            description = "A text and code editor for Android.",
            licenses = CORE_LICENSES,
            onDismiss = { showAbout = false },
        )
    }
}

/** Two lines of code in [scheme]'s colors, for its card in the color scheme picker. */
private fun codePreview(scheme: ContentColorScheme): List<AnnotatedString> {
    val fg = Color(scheme.foreground)
    return listOf(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color(scheme.ansi[5]))) { append("val ") }
            withStyle(SpanStyle(color = fg)) { append("star = ") }
            withStyle(SpanStyle(color = Color(scheme.ansi[2]))) { append("\"seren\"") }
        },
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color(scheme.ansi[8]))) { append("// ") }
            withStyle(SpanStyle(color = Color(scheme.ansi[4]))) { append("print") }
            withStyle(SpanStyle(color = fg)) { append("(star)") }
        },
    )
}
