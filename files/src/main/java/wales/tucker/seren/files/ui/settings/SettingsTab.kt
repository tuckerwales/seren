package wales.tucker.seren.files.ui.settings

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import wales.tucker.seren.core.ui.AboutDialog
import wales.tucker.seren.core.ui.AppearanceSection
import wales.tucker.seren.core.ui.CORE_LICENSES
import wales.tucker.seren.core.ui.NavRow
import wales.tucker.seren.core.ui.SectionHeader
import wales.tucker.seren.core.ui.SwitchRow
import wales.tucker.seren.files.BuildConfig
import wales.tucker.seren.files.MainActivity
import wales.tucker.seren.files.data.Settings
import wales.tucker.seren.files.data.TrashBin
import wales.tucker.seren.files.ui.LocalStorageAccess
import wales.tucker.seren.files.ui.appContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(settings: Settings) {
    val repo = appContainer().settings
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val access = LocalStorageAccess.current
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

            SectionHeader("Browsing")
            SwitchRow("Show hidden files", "Files and folders whose names start with a dot", settings.showHidden) {
                scope.launch { repo.setShowHidden(it) }
            }
            SwitchRow("Thumbnails", "Show small pictures of photos and videos", settings.showThumbnails) {
                scope.launch { repo.setShowThumbnails(it) }
            }
            SwitchRow(
                "Use the trash",
                "Deleted items can be restored for ${TrashBin.KEEP_DAYS} days. When off, deleting is permanent.",
                settings.useTrash,
            ) { scope.launch { repo.setUseTrash(it) } }

            SectionHeader("Storage access")
            NavRow(
                if (access.granted) "All files access is on" else "All files access is off",
                if (access.granted) {
                    "Seren Files can browse, copy and move everything on shared storage. It has no internet access."
                } else {
                    "Seren Files needs it to browse your files. Tap to allow it."
                },
                onClick = access.request,
            )

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
            NavRow("Seren Files ${BuildConfig.VERSION_NAME}", "Open source licenses") { showAbout = true }
        }
    }

    if (showAbout) {
        AboutDialog(
            appName = "Seren Files",
            version = BuildConfig.VERSION_NAME,
            description = "A file manager for Android.",
            licenses = CORE_LICENSES,
            onDismiss = { showAbout = false },
        )
    }
}
