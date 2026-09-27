package wales.tucker.seren.ssh.ui.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import wales.tucker.seren.ssh.data.Settings
import wales.tucker.seren.ssh.data.SettingsRepository
import wales.tucker.seren.ssh.data.Snippet
import wales.tucker.seren.ssh.emulator.ColorSchemes
import wales.tucker.seren.ssh.emulator.CursorShape
import wales.tucker.seren.ssh.emulator.TerminalKey
import wales.tucker.seren.ssh.session.SessionEvent
import wales.tucker.seren.ssh.session.SessionPrompt
import wales.tucker.seren.ssh.session.SessionState
import wales.tucker.seren.ssh.session.TerminalSession
import wales.tucker.seren.ssh.ssh.PasswordResponse
import wales.tucker.seren.ssh.ui.common.StatusDot
import wales.tucker.seren.ssh.ui.common.appContainer
import wales.tucker.seren.ssh.ui.hosts.stateColor
import wales.tucker.seren.ssh.ui.hosts.stateLabel
import wales.tucker.seren.ssh.ui.theme.MonoFamily
import wales.tucker.seren.ssh.ui.theme.MonoSmall
import wales.tucker.seren.ssh.ui.theme.SystemBarAppearance

@Composable
fun TerminalScreen(
    sessionId: Int,
    settings: Settings,
    onBack: () -> Unit,
    onSwitchSession: (Int) -> Unit,
    onOpenSftp: () -> Unit,
    onClosed: () -> Unit,
    onSaveAsHost: () -> Unit = {},
) {
    val container = appContainer()
    val session = remember(sessionId) { container.sessionManager.get(sessionId) }
    if (session == null) {
        LaunchedEffect(Unit) { onClosed() }
        return
    }
    TerminalContent(session, settings, onBack, onSwitchSession, onOpenSftp, onClosed, onSaveAsHost)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerminalContent(
    session: TerminalSession,
    settings: Settings,
    onBack: () -> Unit,
    onSwitchSession: (Int) -> Unit,
    onOpenSftp: () -> Unit,
    onClosed: () -> Unit,
    onSaveAsHost: () -> Unit,
) {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by session.state.collectAsStateWithLifecycle()
    val title by session.title.collectAsStateWithLifecycle()
    val prompt by session.prompt.collectAsStateWithLifecycle()
    val log by session.log.collectAsStateWithLifecycle()
    val linkedHostId by session.hostId.collectAsStateWithLifecycle()
    val sessions by container.sessionManager.sessions.collectAsStateWithLifecycle()
    val snippets by container.database.snippetDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val snackbar = remember { SnackbarHostState() }
    val modifiers = remember { StickyModifiers() }
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var switcherOpen by remember { mutableStateOf(false) }
    var snippetsOpen by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var keyboardShownOnce by remember { mutableStateOf(false) }
    var scrolledBackInHistory by remember { mutableStateOf(false) }

    val scheme = remember(session.spec.colorSchemeId, settings.colorSchemeId) {
        ColorSchemes.byId(session.spec.colorSchemeId ?: settings.colorSchemeId)
    }
    LaunchedEffect(scheme) {
        if (session.appliedSchemeId != scheme.id) {
            synchronized(session.emulator) { session.emulator.setColorScheme(scheme.palette()) }
            session.appliedSchemeId = scheme.id
            terminalView?.invalidate()
        }
    }
    LaunchedEffect(settings.scrollback) {
        synchronized(session.emulator) { session.emulator.setScrollback(settings.scrollback) }
    }

    val bg = Color(scheme.background)
    val fg = Color(scheme.foreground)
    val accent = Color(scheme.cursor)
    SystemBarAppearance(lightBars = bg.luminance() > 0.5f)
    val currentSettings by rememberUpdatedState(settings)

    // Session events: bell, clipboard, messages.
    LaunchedEffect(session) {
        session.events.collect { e ->
            when (e) {
                SessionEvent.Bell -> if (currentSettings.vibrateOnBell) vibrate(context)
                is SessionEvent.Clipboard -> {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("Terminal", e.text))
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                }
                is SessionEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }

    // Leave once the session is closed, whether from this screen, the notification's
    // "Disconnect all" or anywhere else. A closed session cannot be reconnected.
    val closed = sessions.none { it.id == session.id }
    LaunchedEffect(closed) { if (closed) onClosed() }
    if (closed) return

    LaunchedEffect(state) {
        if (state == SessionState.Connected && !keyboardShownOnce) {
            keyboardShownOnce = true
            terminalView?.showKeyboard()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(bg),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        ) {
            // Top bar.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = fg)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { switcherOpen = true }
                        .padding(vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(stateColor(state))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            title,
                            color = fg,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        session.spec.subtitle,
                        color = fg.copy(alpha = 0.6f),
                        style = MonoSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    DropdownMenu(expanded = switcherOpen, onDismissRequest = { switcherOpen = false }) {
                        sessions.forEach { s ->
                            val st by s.state.collectAsState()
                            val t by s.title.collectAsState()
                            val waiting = s.prompt.collectAsState().value != null
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(t, fontWeight = if (s.id == session.id) FontWeight.Bold else FontWeight.Normal)
                                        Text(stateLabel(st, waiting), style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                leadingIcon = { StatusDot(stateColor(st, waiting)) },
                                onClick = {
                                    switcherOpen = false
                                    if (s.id != session.id) onSwitchSession(s.id)
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = { terminalView?.pasteFromClipboard() }) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste", tint = fg)
                }
                if (sessions.size > 1) {
                    IconButton(onClick = { switcherOpen = true }) {
                        BadgedBox(badge = { Badge { Text("${sessions.size}") } }) {
                            Icon(Icons.Rounded.ViewCarousel, contentDescription = "Sessions", tint = fg)
                        }
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = fg)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Snippets") },
                            leadingIcon = { Icon(Icons.Rounded.Terminal, null) },
                            onClick = { menuOpen = false; snippetsOpen = true },
                        )
                        if (linkedHostId <= 0) {
                            DropdownMenuItem(
                                text = { Text("Save as host") },
                                leadingIcon = { Icon(Icons.Rounded.BookmarkAdd, null) },
                                onClick = { menuOpen = false; onSaveAsHost() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Browse files (SFTP)") },
                            leadingIcon = { Icon(Icons.Rounded.Folder, null) },
                            enabled = state == SessionState.Connected,
                            onClick = { menuOpen = false; onOpenSftp() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Larger text") },
                            leadingIcon = { Icon(Icons.Rounded.TextIncrease, null) },
                            onClick = { scope.launch { container.settings.setFontSize(settings.fontSize + 1) } },
                        )
                        DropdownMenuItem(
                            text = { Text("Smaller text") },
                            leadingIcon = { Icon(Icons.Rounded.TextDecrease, null) },
                            onClick = { scope.launch { container.settings.setFontSize(settings.fontSize - 1) } },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Reconnect") },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                            onClick = { menuOpen = false; session.reconnect() },
                        )
                        DropdownMenuItem(
                            text = { Text("Disconnect") },
                            leadingIcon = { Icon(Icons.Rounded.LinkOff, null) },
                            onClick = {
                                menuOpen = false
                                if (settings.confirmDisconnect && state == SessionState.Connected) {
                                    confirmClose = true
                                } else {
                                    container.sessionManager.close(session)
                                }
                            },
                        )
                    }
                }
            }

            // Terminal.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp).clipToBounds(),
                    factory = { ctx ->
                        TerminalView(ctx).also { view ->
                            view.listener = object : TerminalView.Listener {
                                override fun currentModifiers(): Int = modifiers.mask
                                override fun onModifiersConsumed() = modifiers.consume()
                                override fun onFontSizeChanged(sizeSp: Float) {
                                    scope.launch { container.settings.setFontSize(sizeSp) }
                                }

                                override fun onScrolledBackChanged(scrolledBack: Boolean) {
                                    scrolledBackInHistory = scrolledBack
                                }
                            }
                            view.session = session
                            terminalView = view
                        }
                    },
                    update = { view ->
                        view.fontSizeSp = settings.fontSize.coerceIn(SettingsRepository.MIN_FONT, SettingsRepository.MAX_FONT)
                        view.cursorShapeOverride = settings.cursorShape.takeIf { it != CursorShape.BLOCK }
                        view.cursorBlinkEnabled = settings.cursorBlink
                        view.keepScreenOn = settings.keepScreenOn
                        view.volumeKeysAsModifiers = settings.volumeKeysAsModifiers
                        view.session = session
                    },
                )

                ConnectionOverlay(
                    state = state,
                    waitingForUser = prompt != null,
                    title = session.spec.title,
                    log = log,
                    fg = fg,
                    bg = bg,
                    accent = accent,
                    onRetry = { session.reconnect() },
                    onClose = { container.sessionManager.close(session) },
                )

                // Qualified, so the enclosing Column's scoped overload is not picked.
                androidx.compose.animation.AnimatedVisibility(
                    visible = scrolledBackInHistory,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) {
                    SmallFloatingActionButton(
                        onClick = { terminalView?.scrollToBottom() },
                        containerColor = lerpColor(bg, fg, 0.16f),
                        contentColor = fg,
                    ) {
                        Icon(Icons.Rounded.KeyboardDoubleArrowDown, contentDescription = "Scroll to bottom")
                    }
                }

                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }

            AnimatedVisibility(
                visible = state is SessionState.Disconnected,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                DisconnectedBar(
                    reason = (state as? SessionState.Disconnected)?.reason ?: "",
                    bg = bg,
                    fg = fg,
                    accent = accent,
                    onReconnect = { session.reconnect() },
                    onClose = { container.sessionManager.close(session) },
                )
            }

            if (settings.showExtraKeys) {
                ExtraKeysBar(
                    modifiers = modifiers,
                    hidden = settings.hiddenExtraKeys,
                    background = lerpColor(bg, fg, 0.06f),
                    foreground = fg,
                    accent = accent,
                    onKey = { key -> terminalView?.sendKey(key, 0) },
                    onText = { text -> terminalView?.sendText(text) },
                    onToggleKeyboard = {
                        val view = terminalView ?: return@ExtraKeysBar
                        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(view)
                        val visible = insets?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
                        if (visible) view.hideKeyboard() else view.showKeyboard()
                    },
                )
            }
        }
    }

    // Prompts from the connection.
    when (val p = prompt) {
        is SessionPrompt.HostKey -> HostKeyDialog(p)
        is SessionPrompt.Password -> PasswordDialog(p)
        is SessionPrompt.KeyboardInteractive -> KeyboardInteractiveDialog(p)
        null -> Unit
    }

    if (snippetsOpen) {
        SnippetSheet(
            snippets = snippets,
            onDismiss = { snippetsOpen = false },
            onRun = { snippet ->
                snippetsOpen = false
                terminalView?.scrollToBottom()
                session.sendSnippet(snippet.command, snippet.autoRun)
            },
        )
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Disconnect?") },
            text = { Text("The session to ${session.spec.subtitle} will be closed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClose = false
                    container.sessionManager.close(session)
                }) { Text("Disconnect") }
            },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Cancel") } },
        )
    }

    DisposableEffect(Unit) {
        onDispose { terminalView?.clearSelection() }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

private fun vibrate(context: android.content.Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    } ?: return
    vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
}

@Composable
private fun ConnectionOverlay(
    state: SessionState,
    waitingForUser: Boolean,
    title: String,
    log: List<String>,
    fg: Color,
    bg: Color,
    accent: Color,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val visible = state == SessionState.Connecting || state is SessionState.Failed
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(bg.copy(alpha = 0.92f))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.widthIn(max = 420.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state is SessionState.Failed) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Couldn't connect to $title", color = fg, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(state.error, color = fg.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyLarge)
                } else {
                    CircularProgressIndicator(color = accent, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(20.dp))
                    Text(
                        if (waitingForUser) "Waiting for you…" else "Connecting to $title",
                        color = fg,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (log.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Surface(
                        color = fg.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            log.takeLast(8).forEach { line ->
                                Text(line, color = fg.copy(alpha = 0.7f), style = MonoSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (state is SessionState.Failed) {
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onClose) { Text("Close", color = fg) }
                        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = bg)) {
                            Icon(Icons.Rounded.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisconnectedBar(reason: String, bg: Color, fg: Color, accent: Color, onReconnect: () -> Unit, onClose: () -> Unit) {
    // A tint of the terminal's own background, so the bar suits light schemes as well as dark.
    Surface(color = lerpColor(bg, fg, 0.12f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LinkOff, null, tint = fg.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(reason, color = fg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2)
            TextButton(onClick = onClose) { Text("Close", color = fg.copy(alpha = 0.8f)) }
            TextButton(onClick = onReconnect) { Text("Reconnect", color = accent, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun HostKeyDialog(prompt: SessionPrompt.HostKey) {
    val r = prompt.request
    AlertDialog(
        // Back declines; a stray tap outside does not.
        onDismissRequest = { prompt.respond(false) },
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = {
            Icon(
                if (r.changed) Icons.Rounded.GppMaybe else Icons.Rounded.Shield,
                null,
                tint = if (r.changed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                when {
                    r.changed -> "Host key has changed!"
                    r.newKeyType -> "New key type"
                    else -> "Verify host"
                },
            )
        },
        text = {
            // JSch names non-standard ports "[host]:port".
            val host = r.host.removePrefix("[").replace("]:", ":")
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    r.changed -> Text(
                        "The key presented by $host does not match the one saved earlier. " +
                            "Someone could be intercepting your connection, or the server was reinstalled.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    r.newKeyType -> Text(
                        "$host is already trusted, but it presented a ${r.keyType} key it hasn't used before. " +
                            "Servers do this after an upgrade or configuration change. Check the new fingerprint before trusting it.",
                    )
                    else -> Text("This is the first time connecting to $host. Check that the fingerprint matches the server's before trusting it.")
                }
                FingerprintCard(label = "${r.keyType} fingerprint", value = r.fingerprint)
                r.previousFingerprint?.let { FingerprintCard(label = "Previously saved", value = it) }
                r.otherKnownKeys.forEach { FingerprintCard(label = "Already trusted ${it.keyType}", value = it.fingerprint) }
            }
        },
        confirmButton = {
            Button(
                onClick = { prompt.respond(true) },
                colors = if (r.changed) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
            ) { Text(if (r.changed) "Accept new key" else "Trust and connect") }
        },
        dismissButton = { TextButton(onClick = { prompt.respond(false) }) { Text("Cancel") } },
    )
}

@Composable
private fun FingerprintCard(label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MonoSmall)
        }
    }
}

@Composable
private fun PasswordDialog(prompt: SessionPrompt.Password) {
    var password by remember(prompt) { mutableStateOf("") }
    var remember by remember(prompt) { mutableStateOf(false) }
    var visible by remember(prompt) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(prompt) { focus.requestFocus() }
    val submit: () -> Unit = { prompt.respond(PasswordResponse(password, remember)) }
    AlertDialog(
        onDismissRequest = { prompt.respond(null) },
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { Icon(Icons.Rounded.Key, null) },
        title = { Text("Password") },
        text = {
            Column {
                Text(prompt.target, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                prompt.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    isError = prompt.error != null,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = if (visible) "Hide password" else "Show password")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (prompt.canRemember) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { remember = !remember }) {
                        Checkbox(checked = remember, onCheckedChange = { remember = it })
                        Text("Remember password")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = submit) { Text("Connect") }
        },
        dismissButton = { TextButton(onClick = { prompt.respond(null) }) { Text("Cancel") } },
    )
}

@Composable
private fun KeyboardInteractiveDialog(prompt: SessionPrompt.KeyboardInteractive) {
    val answers = remember(prompt) { prompt.prompts.map { mutableStateOf("") } }
    val focus = remember(prompt) { prompt.prompts.map { FocusRequester() } }
    LaunchedEffect(prompt) { focus.firstOrNull()?.requestFocus() }
    val submit: () -> Unit = { prompt.respond(answers.map { it.value }) }
    AlertDialog(
        onDismissRequest = { prompt.respond(null) },
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { Icon(Icons.Rounded.Key, null) },
        title = { Text(prompt.name.ifBlank { "Authentication" }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(prompt.target, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (prompt.instruction.isNotBlank()) Text(prompt.instruction)
                prompt.prompts.forEachIndexed { i, p ->
                    val last = i == prompt.prompts.lastIndex
                    OutlinedTextField(
                        value = answers[i].value,
                        onValueChange = { answers[i].value = it },
                        label = { Text(p.text.trim().trimEnd(':')) },
                        singleLine = true,
                        visualTransformation = if (p.echo) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (p.echo) KeyboardType.Text else KeyboardType.Password,
                            autoCorrectEnabled = false,
                            imeAction = if (last) ImeAction.Done else ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focus.getOrNull(i + 1)?.requestFocus() },
                            onDone = { submit() },
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus[i]),
                    )
                }
            }
        },
        confirmButton = { Button(onClick = submit) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { prompt.respond(null) }) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnippetSheet(snippets: List<Snippet>, onDismiss: () -> Unit, onRun: (Snippet) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Snippets",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (snippets.isEmpty()) {
            Text(
                "No snippets yet. Add frequently used commands in the Snippets tab.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumn(Modifier.padding(bottom = 24.dp)) {
                items(snippets, key = { it.id }) { s ->
                    ListItem(
                        headlineContent = { Text(s.name) },
                        supportingContent = { Text(s.command, fontFamily = MonoFamily, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.Rounded.Terminal, null) },
                        trailingContent = if (!s.autoRun) ({ Text("Insert", style = MaterialTheme.typography.labelSmall) }) else null,
                        modifier = Modifier.clickable { onRun(s) },
                    )
                }
            }
        }
    }
}
