package wales.tucker.seren.ssh.voice

import org.json.JSONObject
import wales.tucker.seren.ssh.ssh.ExecChannel
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal fun backendCommand(executable: String, cwd: String): String {
    require(executable.isNotBlank() && !executable.any { it == '\u0000' || it == '\n' || it == '\r' }) {
        "Enter the path to the host's speakshell executable"
    }
    require(!cwd.any { it == '\u0000' || it == '\n' || it == '\r' }) { "Invalid working directory" }
    fun quote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"
    return "exec ${quote(executable)} --rpc" + if (cwd.isBlank()) "" else " --cwd ${quote(cwd)}"
}

/** One instance per open dialog. Never writes commands into the terminal's input stream. */
internal class VoiceProtocol(
    inputStream: java.io.InputStream,
    outputStream: java.io.OutputStream,
    private val disconnect: () -> Unit,
) : Closeable {
    constructor(channel: ExecChannel) : this(channel.input, channel.output, { channel.close() })
    private val input = inputStream.bufferedReader(Charsets.UTF_8)
    private val output = outputStream.bufferedWriter(Charsets.UTF_8)
    private val timer = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "voice-timeout").apply { isDaemon = true }
    }
    private var sequence = 0

    @Synchronized
    fun request(method: String, params: JSONObject = JSONObject()): JSONObject {
        val id = ++sequence
        // Closing the channel also unblocks a stalled read on an unresponsive host/model.
        val timeout = timer.schedule({ disconnect() }, 240, TimeUnit.SECONDS)
        try {
            val line = JSONObject().put("id", id).put("method", method).put("params", params).toString()
            output.write(line)
            output.newLine()
            output.flush()
            val response = StringBuilder()
            while (true) {
                val value = input.read()
                check(value >= 0) { "Assistant disconnected. Check the executable path and host setup." }
                if (value == '\n'.code) break
                check(response.length < 8 * 1024 * 1024) { "Assistant response too large" }
                response.append(value.toChar())
            }
            val json = JSONObject(response.toString())
            check(json.getInt("id") == id) { "Unexpected assistant response" }
            check(!json.has("error")) { json.optString("error", "Assistant failed") }
            val result = json.getJSONObject("result")
            when (method) {
                "plan" -> {
                    check(result.getString("id").isNotBlank()) { "Missing proposal ID" }
                    check(result.getString("command").isNotBlank()) { "Missing command" }
                    result.getString("explanation")
                    result.getJSONObject("context").getString("cwd")
                    result.getJSONObject("context").getString("system")
                }
                "transcribe" -> check(result.getString("text").isNotBlank()) { "No speech detected" }
                "execute" -> { result.getInt("exit_code"); result.getString("output") }
            }
            return result
        } finally {
            timeout.cancel(false)
        }
    }

    override fun close() {
        disconnect()
        timer.shutdownNow()
    }
}
