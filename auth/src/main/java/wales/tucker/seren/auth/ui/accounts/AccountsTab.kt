package wales.tucker.seren.auth.ui.accounts

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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import wales.tucker.seren.auth.AppContainer
import wales.tucker.seren.auth.data.Account
import wales.tucker.seren.auth.data.Settings
import wales.tucker.seren.auth.otp.CopiedSetup
import wales.tucker.seren.auth.otp.OtpAuthUri
import wales.tucker.seren.auth.otp.OtpType
import wales.tucker.seren.auth.otp.formatCode
import wales.tucker.seren.auth.ui.AddActions
import wales.tucker.seren.auth.ui.appContainer
import wales.tucker.seren.core.ui.LocalMessenger
import wales.tucker.seren.auth.ui.containerViewModel
import wales.tucker.seren.auth.ui.rememberIdentityCheck
import wales.tucker.seren.auth.ui.scan.QrCodeImage
import wales.tucker.seren.core.ui.Avatar
import wales.tucker.seren.core.ui.EmptyState
import wales.tucker.seren.core.ui.SectionHeader
import wales.tucker.seren.core.ui.copyToClipboard
import wales.tucker.seren.core.ui.pillFieldColors
import wales.tucker.seren.core.ui.theme.MonoFamily
import wales.tucker.seren.core.ui.theme.MonoSmall
import wales.tucker.seren.core.ui.theme.accentColor

class AccountsViewModel(private val container: AppContainer) : ViewModel() {
    val accounts: StateFlow<List<Account>?> = container.accounts.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(account: Account) {
        viewModelScope.launch { container.accounts.delete(account.id) }
    }

    fun restore(account: Account) {
        viewModelScope.launch { container.accounts.restore(account) }
    }

    fun nextCode(account: Account) {
        viewModelScope.launch { container.accounts.nextCounter(account.id) }
    }

    /** A setup link or key the user copied, offered on a card above their accounts. */
    var copied by mutableStateOf<CopiedSetup?>(null)
        private set

    /** When the clip last looked at was copied, so each copy is read and offered only once. */
    private var copiedAt: Long? = null

    /** A hash of the clip last read, for when Android doesn't say when it was copied. */
    private var copiedHash: Int? = null

    /**
     * Offers what [read] finds on a clipboard copied at [timestamp], unless that copy was already
     * looked at. A timestamp of 0 means Android didn't say, so the clip is read and compared.
     */
    fun checkCopied(timestamp: Long, read: () -> String?) {
        if (timestamp != 0L && timestamp == copiedAt) return
        copiedAt = timestamp
        val text = read()
        if (timestamp == 0L && text.hashCode() == copiedHash) return
        copiedHash = text.hashCode()
        val found = text?.let(CopiedSetup::find)
        if (found == null) {
            copied = null
            return
        }
        viewModelScope.launch {
            // Nothing to offer for accounts that are already here.
            val known = container.accounts.all().mapTo(HashSet()) { it.token.secret }
            copied = when (found) {
                is CopiedSetup.Transfer -> found.copy(tokens = found.tokens.filter { it.secret !in known }).takeIf { it.tokens.isNotEmpty() }
                else -> found.takeIf { found.secrets.none(known::contains) }
            }
        }
    }

    fun dismissCopied() {
        copied = null
    }
}

/** Accounts whose service or account name contains [query]. */
fun filterAccounts(accounts: List<Account>, query: String): List<Account> {
    val q = query.trim()
    if (q.isEmpty()) return accounts
    return accounts.filter { it.token.issuer.contains(q, ignoreCase = true) || it.token.name.contains(q, ignoreCase = true) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsTab(settings: Settings, actions: AddActions, onEdit: (Long) -> Unit) {
    val vm = containerViewModel { AccountsViewModel(it) }
    val accounts = vm.accounts.collectAsStateWithLifecycle().value
    val clock = appContainer().clock
    val now by clock.ticks.collectAsStateWithLifecycle(clock.now())
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val identityCheck = rememberIdentityCheck()

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var revealedId by rememberSaveable { mutableLongStateOf(-1L) }
    var pendingDelete by remember { mutableStateOf<Account?>(null) }
    var showQr by remember { mutableStateOf<Account?>(null) }
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val copied = vm.copied.takeIf { settings.offerCopied }

    // Android only lets the app in front read the clipboard, so look each time the window gets
    // focus back: coming back from a browser, the notification shade or split screen.
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused, settings.offerCopied) {
        if (focused && settings.offerCopied) vm.checkClipboard(context)
    }

    fun copy(account: Account) {
        val code = account.token.code(clock.now())
        copyToClipboard(context, "Seren Auth code", code, sensitive = true)
        revealedId = account.id
        messenger.show("Copied the ${account.token.title} code")
    }

    Scaffold(
        topBar = {
            if (searching) {
                SearchBar(query, onQuery = { query = it }, onClose = { searching = false; query = "" })
            } else {
                TopAppBar(
                    title = { Text("Seren Auth", fontWeight = FontWeight.SemiBold) },
                    actions = {
                        if (!accounts.isNullOrEmpty()) {
                            IconButton(onClick = { searching = true }) { Icon(Icons.Rounded.Search, "Search") }
                        }
                    },
                    windowInsets = WindowInsets.statusBars,
                )
            }
        },
        floatingActionButton = {
            if (!accounts.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Add account") },
                    expanded = fabExpanded,
                )
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            if (copied != null) {
                item(key = "copied") {
                    CopiedSetupCard(
                        copied,
                        onAdd = {
                            vm.dismissCopied()
                            actions.addCopied(copied)
                        },
                        onDismiss = vm::dismissCopied,
                    )
                }
            }
            when {
                accounts == null -> Unit
                accounts.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Rounded.Pin,
                        title = "No accounts yet",
                        message = "Turn on two-factor authentication on a site, then scan the QR code it shows or enter its setup key.",
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showAdd = true }) {
                                Icon(Icons.Rounded.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add account")
                            }
                            OutlinedButton(onClick = actions.importFile) { Text("Import from a file") }
                        }
                    }
                }
                else -> {
                    val shown = filterAccounts(accounts, query)
                    if (shown.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Rounded.SearchOff,
                                title = "No matches",
                                message = "No account or service has \"${query.trim()}\" in its name.",
                            )
                        }
                    } else {
                        item { SectionHeader("Accounts", trailing = shown.size.toString()) }
                        items(shown, key = { it.id }) { account ->
                            AccountTile(
                                account = account,
                                now = now,
                                hidden = settings.hideCodes && revealedId != account.id,
                                showNextCode = settings.showNextCode,
                                onClick = { copy(account) },
                                onNextCode = { vm.nextCode(account) },
                                onEdit = { onEdit(account.id) },
                                onShowQr = { identityCheck("Confirm it's you to show the setup key") { showQr = account } },
                                onDelete = { pendingDelete = account },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddAccountSheet(actions, onDismiss = { showAdd = false })
    }

    pendingDelete?.let { account ->
        DeleteAccountDialog(
            account = account,
            onDelete = {
                pendingDelete = null
                vm.delete(account)
                messenger.show("Deleted ${account.token.title}", action = "Undo") { vm.restore(account) }
            },
            onDismiss = { pendingDelete = null },
        )
    }

    showQr?.let { account -> ShowQrDialog(account, onDismiss = { showQr = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("Search accounts") },
                singleLine = true,
                shape = CircleShape,
                colors = pillFieldColors(MaterialTheme.colorScheme.surfaceContainerHigh),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear search") }
                },
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp).focusRequester(focus),
            )
        },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close search") } },
        windowInsets = WindowInsets.statusBars,
    )
}

/**
 * One account: who it's for, the current code in large mono digits and how long it has left.
 * Tapping copies the code.
 */
@Composable
fun AccountTile(
    account: Account,
    now: Long,
    hidden: Boolean,
    showNextCode: Boolean,
    onClick: () -> Unit,
    onNextCode: () -> Unit,
    onEdit: () -> Unit,
    onShowQr: () -> Unit,
    onDelete: () -> Unit,
) {
    val token = account.token
    val periodMillis = token.period * 1000L
    val step = if (token.type == OtpType.TOTP) now / periodMillis else token.counter
    val code = remember(token, step) { token.code(now) }
    val remaining = token.remainingMillis(now)
    val totp = token.type == OtpType.TOTP
    val urgent = totp && remaining <= URGENT_MILLIS
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClickLabel = "Copy code", onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(token.title, accentColor(account.color))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                token.title.ifBlank { "Unnamed account" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (token.issuer.isNotBlank() && token.name.isNotBlank()) {
                Text(
                    token.name,
                    style = MonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (hidden) hiddenCode(token.digits) else formatCode(code),
                fontFamily = MonoFamily,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { contentDescription = if (hidden) "Code hidden, tap to show and copy" else "Code ${code.toCharArray().joinToString(" ")}" },
            )
            if (totp && showNextCode && !hidden && remaining <= NEXT_CODE_MILLIS) {
                val next = remember(token, step) { token.code(now + periodMillis) }
                Text("Next ${formatCode(next)}", style = MonoSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        Spacer(Modifier.width(8.dp))
        if (totp) {
            CountdownRing(remaining, periodMillis)
        } else {
            IconButton(onClick = onNextCode) { Icon(Icons.Rounded.Refresh, "Next code", tint = MaterialTheme.colorScheme.primary) }
        }
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                    onClick = { menuOpen = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text("Show QR code") },
                    leadingIcon = { Icon(Icons.Rounded.QrCode2, null) },
                    onClick = { menuOpen = false; onShowQr() },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

/**
 * Accounts are standalone cards 12 dp apart rather than a grouped list: each one is tall and led by
 * a large code, and tight 2 dp gaps made neighbouring codes run together.
 */
private val TileShape = RoundedCornerShape(20.dp)

/** The next code appears under the current one once this little time is left. */
const val NEXT_CODE_MILLIS = 7_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountSheet(actions: AddActions, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun pick(action: () -> Unit) {
        scope.launch { state.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Text(
            "Add account",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        )
        SheetItem(Icons.Rounded.QrCodeScanner, "Scan QR code", "Use the camera on the code the site shows") { pick(actions.scan) }
        SheetItem(Icons.Rounded.Image, "Scan from an image", "A screenshot or photo of a QR code") { pick(actions.pickImage) }
        SheetItem(Icons.Rounded.Keyboard, "Enter setup key", "Type or paste the key or otpauth link the site gives you") { pick(actions.enterKey) }
        SheetItem(Icons.Rounded.FileOpen, "Import from a file", "A backup from Seren Auth, Aegis or andOTP, or a list of otpauth links") {
            pick(actions.importFile)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SheetItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}

@Composable
fun DeleteAccountDialog(account: Account, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val title = account.token.title.ifBlank { "this account" }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, null) },
        title = { Text("Delete $title?") },
        text = {
            Text(
                "Seren Auth will stop making codes for it. Before you delete it, turn off two-factor authentication " +
                    "on $title or make sure you have another way to sign in, such as recovery codes.",
            )
        },
        confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The account's setup QR code, for moving it to another device or app. */
@Composable
fun ShowQrDialog(account: Account, onDismiss: () -> Unit) {
    val title = account.token.title.ifBlank { "this account" }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.QrCode2, null) },
        title = { Text("Scan to add $title") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QrCodeImage(
                    OtpAuthUri.format(account.token),
                    description = "QR code for $title",
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                )
                Text("Anyone who scans this can make codes for $title. Show it only to your own devices.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
