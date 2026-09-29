package wales.tucker.seren.ssh.ui.settings

import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wales.tucker.seren.core.content.ContentColorScheme
import wales.tucker.seren.core.content.ContentColorSchemes
import wales.tucker.seren.core.ui.AboutDialog
import wales.tucker.seren.core.ui.AppearanceSection
import wales.tucker.seren.core.ui.CORE_LICENSES
import wales.tucker.seren.core.ui.ColorSchemePicker
import wales.tucker.seren.core.ui.NavRow
import wales.tucker.seren.core.ui.NewBackupPasswordDialog
import wales.tucker.seren.core.ui.SectionHeader
import wales.tucker.seren.core.ui.SwitchRow
import wales.tucker.seren.core.ui.TextSizeSetting
import wales.tucker.seren.core.ui.UnlockBackupDialog
import wales.tucker.seren.ssh.BuildConfig
import wales.tucker.seren.ssh.MainActivity
import wales.tucker.seren.ssh.data.Backup
import wales.tucker.seren.ssh.data.Settings
import wales.tucker.seren.ssh.data.SettingsRepository
import wales.tucker.seren.ssh.emulator.CursorShape
import wales.tucker.seren.ssh.ui.common.appContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(settings: Settings, onKnownHosts: () -> Unit, onExtraKeys: () -> Unit) {
    val container = appContainer()
    val repo = container.settings
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }
    val backup = remember(container) { Backup(container.database, container.secretBox) }
    // The password chosen for "Export everything", held only until the file is written.
    var exportPassword by remember { mutableStateOf<CharArray?>(null) }
    var askExportPassword by remember { mutableStateOf(false) }
    var unlock by remember { mutableStateOf<UnlockState?>(null) }

    fun importMessage(r: Backup.ImportResult) = buildString {
        append("Imported ${r.hosts} host${if (r.hosts == 1) "" else "s"}")
        if (r.keys > 0) append(", ${r.keys} key${if (r.keys == 1) "" else "s"}")
        append(" and ${r.snippets} snippet${if (r.snippets == 1) "" else "s"}")
        if (r.skipped > 0) append(", skipped ${r.skipped} already here")
    }

    /** Writes a backup to [uri]: with [password], everything, encrypted; without, hosts and snippets. */
    fun export(uri: Uri, password: CharArray?) {
        scope.launch {
            val message = try {
                val json = withContext(Dispatchers.Default) { backup.export(password) }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } ?: error("Cannot write the file")
                }
                if (password != null) "Hosts, keys, passwords and snippets exported" else "Hosts and snippets exported"
            } catch (e: Exception) {
                "Export failed: ${e.message}"
            } finally {
                password?.fill(' ')
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) export(uri, password = null)
    }
    val exportEverythingLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val password = exportPassword
        exportPassword = null
        when {
            uri == null -> password?.fill(' ')
            // The password is never saved, so it's gone if Android restarted the app meanwhile.
            password == null -> {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                Toast.makeText(context, "Export stopped. Choose a password again to export everything.", Toast.LENGTH_LONG).show()
            }
            else -> export(uri, password)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val message = runCatching {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Cannot read the file")
                }
                if (backup.isEncrypted(json)) {
                    unlock = UnlockState(json)
                    return@launch
                }
                importMessage(backup.import(json))
            }.getOrElse { "Import failed: ${it.message}" }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    fun unlockBackup(password: CharArray) {
        val state = unlock ?: return
        unlock = state.copy(working = true, error = null)
        scope.launch {
            try {
                val result = withContext(Dispatchers.Default) { backup.import(state.json, password) }
                unlock = null
                Toast.makeText(context, importMessage(result), Toast.LENGTH_LONG).show()
            } catch (e: Backup.WrongPasswordException) {
                unlock = state.copy(working = false, error = e.message)
            } catch (e: Exception) {
                unlock = null
                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                password.fill(' ')
            }
        }
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

            SectionHeader("Terminal")
            ColorSchemePicker(
                selectedId = settings.colorSchemeId,
                onSelect = { scope.launch { repo.setColorScheme(it.id) } },
                preview = ::terminalPreview,
            )

            TextSizeSetting(
                size = settings.fontSize,
                range = SettingsRepository.MIN_FONT..SettingsRepository.MAX_FONT,
                scheme = ContentColorSchemes.byId(settings.colorSchemeId),
                sample = "$ echo \"Hello, world\"",
                tip = "Tip: pinch the terminal to zoom",
                onChange = { scope.launch { repo.setFontSize(it) } },
            )

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
            SwitchRow("Confirm before disconnecting", "When closing a connected session", settings.confirmDisconnect) {
                scope.launch { repo.setConfirmDisconnect(it) }
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
                    container.agent.wipe()
                    scope.launch { repo.setAppLock(false) }
                }
            }
            val agentUnlocked by container.agent.unlocked.collectAsStateWithLifecycle()
            if (settings.appLock) {
                if (agentUnlocked) {
                    NavRow(
                        "SSH agent",
                        "Unlocked · ${container.agent.keyCount} key${if (container.agent.keyCount == 1) "" else "s"} in memory",
                    ) {}
                    NavRow("Lock agent now", "Wipe unlocked keys from memory so forwarded sign requests stop") {
                        container.agent.wipe()
                        Toast.makeText(context, "Agent locked", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    NavRow("SSH agent", "Locked · tap to unlock keys for agent forwarding") {
                        val activity = context as? MainActivity ?: return@NavRow
                        activity.unlockAgent { ok ->
                            Toast.makeText(
                                context,
                                if (ok) "Agent unlocked" else "Could not unlock the agent. Is App Lock set up?",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            } else {
                NavRow("SSH agent", "Turn on App Lock to unlock keys for agent forwarding") {
                    Toast.makeText(context, "Turn on App Lock in Settings first", Toast.LENGTH_LONG).show()
                }
            }
            NavRow("Known hosts", "Manage trusted server fingerprints", onKnownHosts)

            SectionHeader("Backup")
            NavRow("Export hosts and snippets", "Passwords and keys are not included") {
                exportLauncher.launch("seren-ssh-backup.json")
            }
            NavRow("Export everything", "Hosts, snippets, saved passwords and keys, protected by a password you choose") {
                // With app lock on, the keys only leave after people prove it's them (as in Seren Auth).
                val activity = context as? MainActivity
                if (settings.appLock && activity != null && activity.canAuthenticate()) {
                    activity.authenticate { ok -> if (ok) askExportPassword = true }
                } else {
                    askExportPassword = true
                }
            }
            NavRow("Import a backup", "Adds hosts, keys and snippets alongside the ones already here") {
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }

            SectionHeader("About")
            NavRow("Seren SSH ${BuildConfig.VERSION_NAME}", "Open source licenses") { showAbout = true }
        }
    }

    if (askExportPassword) {
        NewBackupPasswordDialog(
            message = "The backup holds your private keys and saved passwords. You'll need this password to import it, and if you forget it, the backup can't be opened.",
            onExport = { password ->
                askExportPassword = false
                exportPassword = password
                exportEverythingLauncher.launch("seren-ssh-backup.json")
            },
            onDismiss = { askExportPassword = false },
        )
    }
    unlock?.let { state ->
        UnlockBackupDialog(
            message = "This Seren SSH backup is encrypted. Enter the password it was exported with.",
            onUnlock = ::unlockBackup,
            onDismiss = { unlock = null },
            error = state.error,
            working = state.working,
        )
    }

    if (showAbout) {
        AboutDialog(
            appName = "Seren SSH",
            version = BuildConfig.VERSION_NAME,
            description = "A modern SSH client for Android.",
            licenses = listOf("JSch (mwiede fork), BSD license", "Bouncy Castle, MIT license") + CORE_LICENSES,
            onDismiss = { showAbout = false },
        )
    }
}

/** An encrypted backup waiting for its password. */
private data class UnlockState(val json: String, val error: String? = null, val working: Boolean = false)

/** Two lines of a shell session in [scheme]'s colors, for its card in the color scheme picker. */
private fun terminalPreview(scheme: ContentColorScheme): List<AnnotatedString> {
    val fg = Color(scheme.foreground)
    return listOf(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color(scheme.ansi[2]))) { append("user@host") }
            withStyle(SpanStyle(color = fg)) { append(":") }
            withStyle(SpanStyle(color = Color(scheme.ansi[4]))) { append("~") }
            withStyle(SpanStyle(color = fg)) { append("$ ls") }
        },
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color(scheme.ansi[4]))) { append("src ") }
            withStyle(SpanStyle(color = Color(scheme.ansi[2]))) { append("run.sh ") }
            withStyle(SpanStyle(color = Color(scheme.ansi[1]))) { append("x") }
        },
    )
}
