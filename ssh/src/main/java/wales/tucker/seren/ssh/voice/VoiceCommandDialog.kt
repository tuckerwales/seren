package wales.tucker.seren.ssh.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import wales.tucker.seren.core.ui.theme.MonoFamily
import wales.tucker.seren.ssh.session.TerminalSession

@Composable
fun VoiceCommandDialog(session: TerminalSession, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val preferences = remember { context.getSharedPreferences("voice-assistant", Context.MODE_PRIVATE) }
    val hostKey = remember(session) { "${session.spec.target.username}@${session.spec.target.hostname}:${session.spec.target.port}" }
    var executable by remember { mutableStateOf(preferences.getString("$hostKey.executable", "speakshell") ?: "speakshell") }
    var directory by remember { mutableStateOf(preferences.getString("$hostKey.directory", "") ?: "") }
    var text by remember { mutableStateOf("") }
    var proposal by remember { mutableStateOf<JSONObject?>(null) }
    var output by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<VoiceRecorder?>(null) }
    // Mutable holders make teardown independent of delayed coroutine completion.
    val protocol = remember(session) { AtomicReference<VoiceProtocol?>(null) }
    val disposed = remember { AtomicBoolean(false) }
    val scope = rememberCoroutineScope()

    fun resetConnection() {
        protocol.getAndSet(null)?.close()
        proposal = null
    }

    suspend fun connection(): VoiceProtocol = withContext(Dispatchers.IO) {
        protocol.get() ?: VoiceProtocol(session.openVoiceChannel(executable, directory)).also {
            if (disposed.get()) { it.close(); throw CancellationException() }
            protocol.set(it)
            if (disposed.get()) { protocol.getAndSet(null)?.close(); throw CancellationException() }
        }
    }

    fun task(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                message = error.message ?: "Assistant failed"
                resetConnection() // Never retry an execution whose outcome may be unknown.
            } finally {
                busy = false
                recording = false
                recorder = null
            }
        }
    }

    fun record() {
        proposal = null
        output = null
        val capture = VoiceRecorder()
        recorder = capture
        recording = true
        task {
            val audio = withContext(Dispatchers.IO) { capture.record() }
            recording = false
            message = "Transcribing on the SSH host…"
            val client = connection()
            val result = withContext(Dispatchers.IO) {
                client.request("transcribe", JSONObject().put("audio", Base64.encodeToString(audio, Base64.NO_WRAP)))
            }
            text = result.getString("text")
            message = "Review the transcript, then translate it."
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && !busy) record()
        else if (!granted) message = "Microphone permission denied. You can still type a request."
    }

    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                disposed.set(true)
                recorder?.stop()
                protocol.getAndSet(null)?.close()
                onDismiss()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            disposed.set(true)
            recorder?.stop()
            protocol.getAndSet(null)?.close()
            lifecycle.removeObserver(observer)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Voice commands") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(session.spec.subtitle)
                Text("Whisper and Ollama run on this SSH host. No cloud services or API keys.")
                OutlinedTextField(
                    value = executable,
                    onValueChange = { executable = it; resetConnection() },
                    label = { Text("Host speakshell executable") },
                    placeholder = { Text("/home/me/.venv/bin/speakshell") },
                    enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = directory,
                    onValueChange = { directory = it; resetConnection() },
                    label = { Text("Host directory (blank uses login directory)") },
                    enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text("Commands use a fresh shell in this directory, separately from the terminal.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; proposal = null; output = null },
                    label = { Text("What would you like to do?") },
                    enabled = !busy, modifier = Modifier.fillMaxWidth(),
                )
                if (recording) {
                    Button(onClick = { recorder?.stop() }) { Text("Stop recording") }
                    Text("Listening… stops automatically after eight seconds.")
                } else {
                    TextButton(enabled = !busy, onClick = {
                        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) record()
                        else permission.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text("Record voice") }
                }
                TextButton(enabled = !busy && text.isNotBlank(), onClick = {
                    proposal = null
                    output = null
                    preferences.edit().putString("$hostKey.executable", executable)
                        .putString("$hostKey.directory", directory).apply()
                    task {
                        val client = connection()
                        proposal = withContext(Dispatchers.IO) {
                            client.request("plan", JSONObject().put("text", text))
                        }
                    }
                }) { Text("Translate request") }
                if (busy) CircularProgressIndicator()
                message?.let { Text(it) }
                proposal?.let { plan ->
                    val host = plan.getJSONObject("context")
                    Text("Review before running", style = MaterialTheme.typography.titleSmall)
                    Text("${host.getString("system")} · ${host.getString("cwd")}")
                    SelectionContainer { Text(plan.getString("command"), fontFamily = MonoFamily) }
                    Text(plan.getString("explanation"))
                    Text("Runs with your SSH account's permissions. Check the command carefully.", style = MaterialTheme.typography.bodySmall)
                }
                output?.let { SelectionContainer { Text(it, fontFamily = MonoFamily) } }
            }
        },
        confirmButton = {
            Button(enabled = proposal != null && !busy && session.isConnected, onClick = {
                val plan = proposal ?: return@Button
                proposal = null // One-shot approval, including after errors.
                task {
                    val client = protocol.get() ?: error("Assistant disconnected; translate again")
                    val result = withContext(Dispatchers.IO) {
                        client.request("execute", JSONObject().put("proposal_id", plan.getString("id")).put("approved", true))
                    }
                    output = "Exit ${result.getInt("exit_code")}" +
                        (if (result.optBoolean("timed_out")) " (timed out)" else "") + "\n" +
                        result.getString("output") + (if (result.optBoolean("truncated")) "\n[Output truncated]" else "")
                    message = null
                }
            }) { Text("Run approved command") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Close") } },
    )
}
