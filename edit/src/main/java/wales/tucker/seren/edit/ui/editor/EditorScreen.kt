@file:OptIn(ExperimentalFoundationApi::class)

package wales.tucker.seren.edit.ui.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import wales.tucker.seren.core.content.ContentColorSchemes
import wales.tucker.seren.core.ui.StatusDot
import wales.tucker.seren.core.ui.theme.MonoSmall
import wales.tucker.seren.core.ui.theme.StatusColors
import wales.tucker.seren.core.ui.theme.SystemBarAppearance
import wales.tucker.seren.edit.data.Settings
import wales.tucker.seren.edit.data.SettingsRepository
import wales.tucker.seren.edit.ui.appContainer
import wales.tucker.seren.edit.ui.containerViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(uri: Uri, settings: Settings, onClose: () -> Unit) {
    val vm = containerViewModel(key = uri.toString()) { EditorViewModel(it, uri) }
    val repo = appContainer().settings
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    val scheme = remember(settings.colorSchemeId) { ContentColorSchemes.byId(settings.colorSchemeId) }
    val bg = Color(scheme.background)
    val fg = Color(scheme.foreground)
    val accent = Color(scheme.cursor)
    SystemBarAppearance(lightBars = !scheme.isDark)

    val dirty by remember { derivedStateOf { vm.isDirty } }
    val undo = vm.text.undoState
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var showReplace by remember { mutableStateOf(false) }
    var matchCase by remember { mutableStateOf(false) }
    var findStatus by remember { mutableStateOf<String?>(null) }
    val findFocus = remember { FocusRequester() }

    fun runFind(forward: Boolean) {
        if (findQuery.isEmpty()) {
            findStatus = null
            return
        }
        val text = vm.text.text
        val from = if (forward) vm.text.selection.max else vm.text.selection.min
        val range = if (forward) {
            FindReplace.findNext(text, findQuery, from, matchCase)
                ?: FindReplace.findNext(text, findQuery, 0, matchCase)
        } else {
            FindReplace.findPrevious(text, findQuery, from, matchCase)
                ?: FindReplace.findPrevious(text, findQuery, text.length, matchCase)
        }
        if (range == null) {
            findStatus = "No matches"
        } else {
            FindReplace.select(vm.text, range)
            findStatus = null
        }
    }

    fun openFind(withReplace: Boolean) {
        showReplace = withReplace
        findOpen = true
        findStatus = null
        val selected = vm.text.text.substring(vm.text.selection.min, vm.text.selection.max)
        if (selected.isNotEmpty() && !selected.contains('\n')) findQuery = selected
    }
    // Pinch to zoom changes this live; it is stored in the settings when the fingers lift.
    var fontSize by remember(settings.fontSize) { mutableFloatStateOf(settings.fontSize) }

    fun save() {
        scope.launch {
            val error = vm.save()
            snackbar.showSnackbar(if (error == null) "Saved ${vm.name}" else "Couldn't save ${vm.name}: $error")
        }
    }

    fun leave() {
        keyboard?.hide()
        onClose()
    }

    BackHandler(enabled = dirty) { confirmDiscard = true }

    // Start typing straight away in a new, empty file.
    LaunchedEffect(vm.load) {
        if (vm.load == LoadState.Ready && vm.text.text.isEmpty()) focusRequester.requestFocus()
    }

    Box(Modifier.fillMaxSize().background(bg)) {
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
                IconButton(onClick = { if (dirty) confirmDiscard = true else leave() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = fg)
                }
                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (dirty) {
                            StatusDot(StatusColors.Pending)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(vm.name, color = fg, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (dirty) {
                        Text("Unsaved changes", color = fg.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    } else if (vm.location.isNotEmpty()) {
                        Text(vm.location, color = fg.copy(alpha = 0.6f), style = MonoSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (vm.load == LoadState.Ready) {
                    TextButton(
                        onClick = { save() },
                        enabled = dirty && !vm.saving,
                        colors = ButtonDefaults.textButtonColors(contentColor = accent, disabledContentColor = fg.copy(alpha = 0.35f)),
                    ) { Text("Save") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = fg)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Undo") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Undo, null) },
                                enabled = undo.canUndo,
                                onClick = { undo.undo() },
                            )
                            DropdownMenuItem(
                                text = { Text("Redo") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Redo, null) },
                                enabled = undo.canRedo,
                                onClick = { undo.redo() },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Word wrap") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.WrapText, null) },
                                trailingIcon = { if (settings.wordWrap) Icon(Icons.Rounded.Check, contentDescription = "On") },
                                onClick = { scope.launch { repo.setWordWrap(!settings.wordWrap) } },
                            )
                            DropdownMenuItem(
                                text = { Text("Line numbers") },
                                leadingIcon = { Icon(Icons.Rounded.FormatListNumbered, null) },
                                trailingIcon = { if (settings.lineNumbers) Icon(Icons.Rounded.Check, contentDescription = "On") },
                                onClick = { scope.launch { repo.setLineNumbers(!settings.lineNumbers) } },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Find") },
                                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                                onClick = { menuOpen = false; openFind(withReplace = false) },
                            )
                            DropdownMenuItem(
                                text = { Text("Find and replace") },
                                leadingIcon = { Icon(Icons.Rounded.FindReplace, null) },
                                onClick = { menuOpen = false; openFind(withReplace = true) },
                            )
                            HorizontalDivider()
                            // What the file will be saved as, for the curious.
                            DropdownMenuItem(
                                text = { Text("${vm.encoding.label} · ${vm.lineEnding.label}", style = MonoSmall) },
                                enabled = false,
                                onClick = {},
                            )
                        }
                    }
                }
            }

            if (findOpen && vm.load == LoadState.Ready) {
                FindBar(
                    query = findQuery,
                    onQuery = { findQuery = it; findStatus = null },
                    replace = replaceQuery,
                    onReplace = { replaceQuery = it },
                    showReplace = showReplace,
                    onShowReplace = { showReplace = it },
                    matchCase = matchCase,
                    onMatchCase = { matchCase = it },
                    status = findStatus,
                    foreground = fg,
                    accent = accent,
                    background = bg,
                    focusRequester = findFocus,
                    onClose = { findOpen = false; findStatus = null },
                    onFindNext = { runFind(forward = true) },
                    onFindPrevious = { runFind(forward = false) },
                    onReplaceOne = {
                        val sel = vm.text.selection
                        val selected = vm.text.text.substring(sel.min, sel.max)
                        val matches = if (matchCase) selected == findQuery else selected.equals(findQuery, ignoreCase = true)
                        if (matches && findQuery.isNotEmpty()) {
                            FindReplace.replaceSelection(vm.text, replaceQuery)
                        }
                        runFind(forward = true)
                    },
                    onReplaceAll = {
                        val n = FindReplace.replaceAll(vm.text, findQuery, replaceQuery, matchCase)
                        findStatus = when (n) {
                            0 -> "No matches"
                            1 -> "Replaced 1 match"
                            else -> "Replaced $n matches"
                        }
                    },
                )
                LaunchedEffect(findOpen) { if (findOpen) findFocus.requestFocus() }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val load = vm.load) {
                    LoadState.Loading -> CircularProgressIndicator(color = accent, modifier = Modifier.align(Alignment.Center))
                    is LoadState.Failed -> OpenFailed(vm.name, load.reason, fg, onRetry = vm::retry, onClose = ::leave)
                    LoadState.Ready -> CodeEditor(
                        state = vm.text,
                        scheme = scheme,
                        fontSize = fontSize,
                        wordWrap = settings.wordWrap,
                        lineNumbers = settings.lineNumbers,
                        onZoom = { zoom -> fontSize = (fontSize * zoom).coerceIn(SettingsRepository.MIN_FONT, SettingsRepository.MAX_FONT) },
                        onZoomEnd = {
                            val halfSteps = (fontSize * 2).roundToInt() / 2f
                            fontSize = halfSteps
                            scope.launch { repo.setFontSize(halfSteps) }
                        },
                        onKeyEvent = { event -> handleShortcut(event, vm.text, ::save, onFind = { openFind(false) }, onReplace = { openFind(true) }) },
                        focusRequester = focusRequester,
                    )
                }
            }

            AnimatedVisibility(
                visible = settings.showExtraKeys && vm.load == LoadState.Ready && WindowInsets.isImeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                EditorKeysBar(
                    background = bg,
                    foreground = fg,
                    accent = accent,
                    canUndo = undo.canUndo,
                    canRedo = undo.canRedo,
                    onKey = { key -> applyKey(vm.text, key) },
                    onHideKeyboard = {
                        keyboard?.hide()
                        focusManager.clearFocus()
                    },
                )
            }
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes to ${vm.name}?") },
            text = { Text("Your edits since the last save will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    leave()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun OpenFailed(name: String, reason: String, fg: Color, onRetry: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = StatusColors.FailedOnCanvas, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Couldn't open $name", color = fg, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(reason, color = fg.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onClose) { Text("Close", color = fg) }
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

/** Applies an extra keys row key to the text. */
internal fun applyKey(state: TextFieldState, key: EditorKey) {
    val text = state.text
    val selection = state.selection
    when (key) {
        EditorKey.Tab -> insertText(state, TextEditing.indentUnit(text))
        EditorKey.Left -> state.edit { this.selection = TextEditing.left(text, selection) }
        EditorKey.Right -> state.edit { this.selection = TextEditing.right(text, selection) }
        EditorKey.Up -> state.edit { this.selection = TextEditing.up(text, selection) }
        EditorKey.Down -> state.edit { this.selection = TextEditing.down(text, selection) }
        EditorKey.Home -> state.edit { this.selection = TextEditing.home(text, selection) }
        EditorKey.End -> state.edit { this.selection = TextEditing.end(text, selection) }
        EditorKey.Undo -> state.undoState.undo()
        EditorKey.Redo -> state.undoState.redo()
        is EditorKey.Text -> insertText(state, key.text)
    }
}

private fun insertText(state: TextFieldState, text: String) {
    state.edit {
        val start = selection.min
        replace(start, selection.max, text)
        selection = TextRange(start + text.length)
    }
}

/** Hardware keyboard shortcuts: Ctrl+S saves, Ctrl+F/H find, Ctrl+Z/Y undo/redo, Tab indents. */
private fun handleShortcut(
    event: KeyEvent,
    state: TextFieldState,
    save: () -> Unit,
    onFind: () -> Unit = {},
    onReplace: () -> Unit = {},
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when {
        event.isCtrlPressed && event.key == Key.S -> { save(); true }
        event.isCtrlPressed && event.key == Key.F -> { onFind(); true }
        event.isCtrlPressed && event.key == Key.H -> { onReplace(); true }
        event.isCtrlPressed && event.key == Key.Z && !event.isShiftPressed -> { state.undoState.undo(); true }
        event.isCtrlPressed && (event.key == Key.Y || (event.key == Key.Z && event.isShiftPressed)) -> { state.undoState.redo(); true }
        event.key == Key.Tab && !event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed -> {
            insertText(state, TextEditing.indentUnit(state.text))
            true
        }
        else -> false
    }
}

@Composable
private fun FindBar(
    query: String,
    onQuery: (String) -> Unit,
    replace: String,
    onReplace: (String) -> Unit,
    showReplace: Boolean,
    onShowReplace: (Boolean) -> Unit,
    matchCase: Boolean,
    onMatchCase: (Boolean) -> Unit,
    status: String?,
    foreground: Color,
    accent: Color,
    background: Color,
    focusRequester: FocusRequester,
    onClose: () -> Unit,
    onFindNext: () -> Unit,
    onFindPrevious: () -> Unit,
    onReplaceOne: () -> Unit,
    onReplaceAll: () -> Unit,
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = foreground,
        unfocusedTextColor = foreground,
        focusedBorderColor = accent,
        unfocusedBorderColor = foreground.copy(alpha = 0.3f),
        cursorColor = accent,
        focusedLabelColor = accent,
        unfocusedLabelColor = foreground.copy(alpha = 0.6f),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f).focusRequester(focusRequester).heightIn(max = 56.dp),
                singleLine = true,
                label = { Text("Find") },
                colors = fieldColors,
            )
            IconButton(onClick = onFindPrevious) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous", tint = foreground)
            }
            IconButton(onClick = onFindNext) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next", tint = foreground)
            }
            IconButton(onClick = { onShowReplace(!showReplace) }) {
                Icon(Icons.Rounded.FindReplace, contentDescription = "Replace", tint = if (showReplace) accent else foreground)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close find", tint = foreground)
            }
        }
        if (showReplace) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = replace,
                    onValueChange = onReplace,
                    modifier = Modifier.weight(1f).heightIn(max = 56.dp),
                    singleLine = true,
                    label = { Text("Replace") },
                    colors = fieldColors,
                )
                TextButton(onClick = onReplaceOne, colors = ButtonDefaults.textButtonColors(contentColor = accent)) {
                    Text("Replace")
                }
                TextButton(onClick = onReplaceAll, colors = ButtonDefaults.textButtonColors(contentColor = accent)) {
                    Text("All")
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = matchCase, onCheckedChange = onMatchCase)
            Text("Match case", color = foreground.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            if (status != null) {
                Spacer(Modifier.width(12.dp))
                Text(status, color = foreground.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
