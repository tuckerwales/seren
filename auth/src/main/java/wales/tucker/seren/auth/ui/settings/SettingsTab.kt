package wales.tucker.seren.auth.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wales.tucker.seren.auth.AppContainer
import wales.tucker.seren.auth.BuildConfig
import wales.tucker.seren.auth.MainActivity
import wales.tucker.seren.auth.R
import wales.tucker.seren.auth.backup.BackupEntry
import wales.tucker.seren.auth.backup.SerenBackup
import wales.tucker.seren.auth.data.Settings
import wales.tucker.seren.auth.otp.OtpAuthUri
import wales.tucker.seren.auth.ui.appContainer
import wales.tucker.seren.core.ui.LocalMessenger
import wales.tucker.seren.auth.ui.containerViewModel
import wales.tucker.seren.auth.ui.rememberIdentityCheck
import wales.tucker.seren.core.ui.AboutDialog
import wales.tucker.seren.core.ui.AppearanceSection
import wales.tucker.seren.core.ui.CORE_LICENSES
import wales.tucker.seren.core.ui.NavRow
import wales.tucker.seren.core.ui.NewBackupPasswordDialog
import wales.tucker.seren.core.ui.SectionHeader
import wales.tucker.seren.core.ui.SwitchRow
import java.io.IOException
import java.time.LocalDate

/** The ways to export: Seren Auth's own backup, encrypted or not, or a plain list of links. */
sealed interface ExportKind {
    class Encrypted(val password: CharArray) : ExportKind
    data object Links : ExportKind
}

class BackupViewModel(private val container: AppContainer) : ViewModel() {
    var pending: ExportKind? = null
    var working by mutableStateOf(false)
        private set

    /** Writes the export chosen in [pending] to [uri], then calls [onDone] with a message. */
    fun export(uri: Uri, onDone: (String) -> Unit) {
        val kind = pending ?: return
        pending = null
        working = true
        viewModelScope.launch {
            val message = try {
                val accounts = container.accounts.all()
                val text = withContext(Dispatchers.Default) { render(kind, accounts.map { BackupEntry(it.token, it.color) }) }
                withContext(Dispatchers.IO) {
                    val out = container.context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("The file couldn't be opened")
                    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                container.context.resources.getQuantityString(R.plurals.exported_accounts, accounts.size, accounts.size)
            } catch (e: IOException) {
                "Couldn't export. ${e.message ?: "The file couldn't be written."}"
            } finally {
                if (kind is ExportKind.Encrypted) kind.password.fill(' ')
                working = false
            }
            onDone(message)
        }
    }

    fun cancel() {
        (pending as? ExportKind.Encrypted)?.password?.fill(' ')
        pending = null
    }

    companion object {
        fun render(kind: ExportKind, entries: List<BackupEntry>): String = when (kind) {
            is ExportKind.Encrypted -> SerenBackup.export(entries, kind.password)
            ExportKind.Links -> entries.joinToString("\n", postfix = "\n") { OtpAuthUri.format(it.token) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(settings: Settings, onImport: () -> Unit) {
    val repo = appContainer().settings
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val identityCheck = rememberIdentityCheck()
    val backup = containerViewModel { BackupViewModel(it) }
    var showAbout by remember { mutableStateOf(false) }
    var askPassword by rememberSaveable { mutableStateOf(false) }
    var warnLinks by rememberSaveable { mutableStateOf(false) }

    val today = LocalDate.now().toString()
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) backup.cancel() else backup.export(uri, messenger::show)
    }
    val saveLinks = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) backup.cancel() else backup.export(uri, messenger::show)
    }

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

            SectionHeader("Codes")
            SwitchRow("Hide codes", "Show a code only after you tap it", settings.hideCodes) {
                scope.launch { repo.setHideCodes(it) }
            }
            SwitchRow("Show next code", "Show the next code when the current one is about to change", settings.showNextCode) {
                scope.launch { repo.setShowNextCode(it) }
            }

            SectionHeader("Adding accounts")
            SwitchRow(
                "Offer copied setup links",
                "When you come back to Seren Auth, offer to add a setup link or key you copied",
                settings.offerCopied,
            ) { scope.launch { repo.setOfferCopied(it) } }

            SectionHeader("Security")
            SwitchRow(
                "App lock",
                "Require biometrics or your screen lock to open the app, show setup keys or export",
                settings.appLock,
            ) { enabled ->
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
            SwitchRow(
                "Block screenshots",
                "Keep codes out of screenshots, screen recordings and recent apps",
                settings.blockScreenshots,
            ) { scope.launch { repo.setBlockScreenshots(it) } }

            SectionHeader("Backup")
            NavRow("Export backup", "An encrypted file, protected by a password you choose") {
                identityCheck("Confirm it's you to export your accounts") { askPassword = true }
            }
            NavRow("Export as otpauth links", "A plain text file most authenticator apps can import") {
                identityCheck("Confirm it's you to export your accounts") { warnLinks = true }
            }
            NavRow("Import", "From Seren Auth, Aegis, andOTP, Google Authenticator or a list of otpauth links", onImport)

            SectionHeader("About")
            NavRow("Seren Auth ${BuildConfig.VERSION_NAME}", "Open source licenses") { showAbout = true }
        }
    }

    if (askPassword) {
        NewBackupPasswordDialog(
            onExport = { password ->
                askPassword = false
                backup.pending = ExportKind.Encrypted(password)
                saveBackup.launch("seren-auth-backup-$today.json")
            },
            onDismiss = { askPassword = false },
        )
    }
    if (warnLinks) {
        AlertDialog(
            onDismissRequest = { warnLinks = false },
            icon = { Icon(Icons.Rounded.Warning, null) },
            title = { Text("Export without encryption?") },
            text = {
                Text(
                    "The file will hold your setup keys in plain text. Anyone who gets it can make your codes, so " +
                        "delete it once you've imported it somewhere else.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    warnLinks = false
                    backup.pending = ExportKind.Links
                    saveLinks.launch("seren-auth-links-$today.txt")
                }) { Text("Export") }
            },
            dismissButton = { TextButton(onClick = { warnLinks = false }) { Text("Cancel") } },
        )
    }
    if (backup.working) {
        AlertDialog(
            onDismissRequest = {},
            icon = { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) },
            title = { Text("Exporting…") },
            text = { Text("Encrypting your accounts. This takes a moment.") },
            confirmButton = {},
        )
    }
    if (showAbout) {
        AboutDialog(
            appName = "Seren Auth",
            version = BuildConfig.VERSION_NAME,
            description = "An authenticator for two-factor sign in codes, with no network access at all.",
            licenses = CORE_LICENSES + AUTH_LICENSES,
            onDismiss = { showAbout = false },
        )
    }
}

/** Libraries Seren Auth bundles beyond Seren Core's, for the About dialog. */
val AUTH_LICENSES = listOf(
    "ZXing, Apache 2.0",
    "CameraX, Apache 2.0",
    "Bouncy Castle, MIT License",
)

