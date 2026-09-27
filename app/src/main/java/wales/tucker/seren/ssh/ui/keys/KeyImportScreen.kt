package wales.tucker.seren.ssh.ui.keys

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wales.tucker.seren.ssh.data.SshKey
import wales.tucker.seren.ssh.ssh.InvalidKeyException
import wales.tucker.seren.ssh.ssh.PassphraseRequiredException
import wales.tucker.seren.ssh.ssh.SshKeys
import wales.tucker.seren.ssh.ssh.WrongPassphraseException
import wales.tucker.seren.ssh.ui.common.appContainer
import wales.tucker.seren.ssh.ui.theme.MonoSmall

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyImportScreen(onDone: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var keyText by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var needsPassphrase by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    var displayName: String? = null
                    val size = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE, OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            displayName = c.getString(1)?.substringBeforeLast('.')
                            c.getLong(0)
                        } else {
                            0L
                        }
                    } ?: 0L
                    if (size > 64 * 1024) throw IllegalArgumentException("File is too large to be a private key")
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                    displayName to text
                }
            }
            result.onSuccess { (displayName, text) ->
                if (name.isBlank() && displayName != null) name = displayName
                keyText = text
                needsPassphrase = SshKeys.needsPassphrase(text)
                error = null
            }.onFailure { error = it.message ?: "Could not read file" }
        }
    }

    fun import() {
        busy = true
        error = null
        scope.launch {
            try {
                val material = withContext(Dispatchers.Default) {
                    SshKeys.import(keyText, passphrase.ifEmpty { null }, name)
                }
                withContext(Dispatchers.IO) {
                    container.database.keyDao().insert(
                        SshKey(
                            name = name.trim().ifBlank { "Imported ${material.type.label} key" },
                            type = material.type,
                            bits = material.bits,
                            encryptedPrivateKey = container.secretBox.encryptString(material.privateKey),
                            publicKey = material.publicKey,
                            fingerprint = material.fingerprint,
                        ),
                    )
                }
                onDone()
            } catch (_: PassphraseRequiredException) {
                needsPassphrase = true
                error = "This key is encrypted. Enter its passphrase."
            } catch (_: WrongPassphraseException) {
                error = "Incorrect passphrase"
            } catch (e: InvalidKeyException) {
                error = "Not a valid private key (${e.message})"
            } catch (e: Exception) {
                error = e.message ?: "Import failed"
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import key") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Paste a private key in OpenSSH, PEM or PuTTY format, or pick a file. " +
                    "The key is stored encrypted with a hardware-backed key on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                Icon(Icons.Rounded.FileOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("Choose file")
            }
            OutlinedTextField(
                value = keyText,
                onValueChange = {
                    keyText = it
                    needsPassphrase = SshKeys.needsPassphrase(it)
                    error = null
                },
                label = { Text("Private key") },
                placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----", style = MonoSmall) },
                textStyle = MonoSmall,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
            )
            if (needsPassphrase) {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it; error = null },
                    label = { Text("Passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { import() },
                enabled = keyText.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (busy) "Importing…" else "Import key") }
        }
    }
}
