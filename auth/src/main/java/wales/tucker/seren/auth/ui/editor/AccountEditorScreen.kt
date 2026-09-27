package wales.tucker.seren.auth.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wales.tucker.seren.auth.data.Account
import wales.tucker.seren.auth.otp.OtpAlgorithm
import wales.tucker.seren.auth.otp.OtpToken
import wales.tucker.seren.auth.otp.OtpType
import wales.tucker.seren.auth.otp.formatCode
import wales.tucker.seren.auth.ui.accounts.CountdownRing
import wales.tucker.seren.auth.ui.accounts.DeleteAccountDialog
import wales.tucker.seren.auth.ui.accounts.ShowQrDialog
import wales.tucker.seren.auth.ui.appContainer
import wales.tucker.seren.auth.ui.common.AccentPicker
import wales.tucker.seren.core.ui.LocalMessenger
import wales.tucker.seren.core.ui.PasswordField
import wales.tucker.seren.core.ui.launch
import wales.tucker.seren.auth.ui.containerViewModel
import wales.tucker.seren.auth.ui.rememberIdentityCheck
import wales.tucker.seren.core.ui.SectionHeader
import wales.tucker.seren.core.ui.theme.MonoFamily
import wales.tucker.seren.core.ui.theme.MonoMedium

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditorScreen(id: Long?, link: String?, onClose: () -> Unit) {
    val container = appContainer()
    val vm = containerViewModel(key = "editor-$id-$link") { EditorViewModel(it, id, link) }
    val messenger = LocalMessenger.current
    val identityCheck = rememberIdentityCheck()
    val form = vm.form
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf<Account?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(form.hasAdvanced) }
    // A saved account loads after the first frame; open Advanced if it uses anything unusual.
    LaunchedEffect(vm.loaded) { if (vm.loaded && vm.form.hasAdvanced) advanced = true }

    fun leave() {
        if (vm.dirty) confirmDiscard = true else onClose()
    }
    BackHandler(enabled = vm.dirty) { confirmDiscard = true }

    val save = {
        vm.save { message ->
            messenger.show(message)
            onClose()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "Add account" else "Edit account", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = save, enabled = vm.loaded && !vm.saving) { Text("Save") }
                    if (!vm.isNew) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, "More")
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Show QR code") },
                                    leadingIcon = { Icon(Icons.Rounded.QrCode2, null) },
                                    onClick = {
                                        menuOpen = false
                                        identityCheck("Confirm it's you to show the setup key") { showQr = vm.account }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                    onClick = { menuOpen = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                },
                windowInsets = WindowInsets.statusBars,
            )
        },
        snackbarHost = { SnackbarHost(messenger.host) },
    ) { padding ->
        if (!vm.loaded) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.padding(48.dp))
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            val errors = vm.showErrors
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = form.issuer,
                    onValueChange = { v -> vm.update { it.copy(issuer = v) } },
                    label = { Text("Service") },
                    placeholder = { Text("GitHub") },
                    singleLine = true,
                    isError = errors && form.nameError != null,
                    supportingText = { Text(if (errors && form.nameError != null) form.nameError!! else "The site or app the codes are for") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { v -> vm.update { it.copy(name = v) } },
                    label = { Text("Account") },
                    placeholder = { Text("you@example.com", style = MonoMedium) },
                    singleLine = true,
                    textStyle = MonoMedium,
                    supportingText = { Text("Your username or email there") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                val keyError = when {
                    vm.duplicateOf != null -> "This setup key is already in Seren Auth as ${vm.duplicateOf}"
                    errors -> form.secretError
                    form.secret.isNotBlank() && form.secret.length >= 8 -> form.secretError
                    else -> null
                }
                PasswordField(
                    value = form.secret,
                    onValueChange = { v -> vm.update { it.withSecret(v) } },
                    label = "Setup key",
                    error = keyError,
                    supporting = "Stored encrypted with a hardware-backed key on this device",
                    textStyle = MonoMedium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    // Anyone holding an unlocked phone shouldn't be able to read off a saved key.
                    onReveal = if (vm.isNew) null else { show -> identityCheck("Confirm it's you to show the setup key", show) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            form.token()?.let { token -> CodePreview(token, container.clock.ticks) }

            SectionHeader("Color")
            AccentPicker(
                selected = form.effectiveColor,
                onSelect = { c -> vm.update { it.copy(color = c) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { advanced = !advanced }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Advanced", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Only change these if the site tells you to",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (advanced) "Hide advanced" else "Show advanced")
            }
            AnimatedVisibility(advanced) {
                AdvancedFields(form, errors, onChange = vm::update)
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(if (vm.isNew) "Discard this account?" else "Discard changes to ${vm.account?.token?.title ?: "this account"}?") },
            text = { Text(if (vm.isNew) "It hasn't been added to Seren Auth yet." else "Your changes haven't been saved.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
    if (confirmDelete) {
        vm.account?.let { account ->
            DeleteAccountDialog(
                account = account,
                onDelete = {
                    confirmDelete = false
                    vm.delete { deleted ->
                        messenger.show("Deleted ${deleted.token.title}", action = "Undo") {
                            messenger.launch { container.accounts.restore(deleted) }
                        }
                        onClose()
                    }
                },
                onDismiss = { confirmDelete = false },
            )
        }
    }
    showQr?.let { ShowQrDialog(it, onDismiss = { showQr = null }) }
}

@Composable
private fun CodePreview(token: OtpToken, ticks: kotlinx.coroutines.flow.Flow<Long>) {
    val clock = appContainer().clock
    val now by ticks.collectAsStateWithLifecycle(clock.now())
    val code = remember(token, now / 1000) { runCatching { token.code(now) }.getOrNull() } ?: return
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (token.type == OtpType.TOTP) "Current code" else "Code for counter ${token.counter}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatCode(code),
                    fontFamily = MonoFamily,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Check it matches what the site asks for",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (token.type == OtpType.TOTP) CountdownRing(token.remainingMillis(now), token.period * 1000L)
        }
    }
}

@Composable
private fun AdvancedFields(form: AccountForm, errors: Boolean, onChange: ((AccountForm) -> AccountForm) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Choice("Type", listOf(OtpType.TOTP to "Time based", OtpType.HOTP to "Counter based"), form.type) { v -> onChange { it.copy(type = v) } }
        Choice("Algorithm", OtpAlgorithm.entries.map { it to it.label }, form.algorithm) { v -> onChange { it.copy(algorithm = v) } }
        Choice("Digits", OtpToken.DIGIT_CHOICES.map { it to it.toString() }, form.digits) { v -> onChange { it.copy(digits = v) } }
        if (form.type == OtpType.TOTP) {
            OutlinedTextField(
                value = form.period,
                onValueChange = { v -> onChange { it.copy(period = v.filter(Char::isDigit).take(4)) } },
                label = { Text("Period") },
                suffix = { Text("seconds") },
                singleLine = true,
                isError = errors && form.periodError != null,
                supportingText = { Text(if (errors && form.periodError != null) form.periodError!! else "How long each code lasts, usually 30 seconds") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = form.counter,
                onValueChange = { v -> onChange { it.copy(counter = v.filter(Char::isDigit).take(18)) } },
                label = { Text("Counter") },
                singleLine = true,
                textStyle = MonoMedium,
                isError = errors && form.counterError != null,
                supportingText = { Text(if (errors && form.counterError != null) form.counterError!! else "Moves on by one each time you ask for the next code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choice(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (value, text) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(text) }
            }
        }
    }
}
