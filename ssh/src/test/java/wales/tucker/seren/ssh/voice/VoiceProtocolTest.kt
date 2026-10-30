package wales.tucker.seren.ssh.voice

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceProtocolTest {
    @Test fun quotesPathsAsSingleArguments() {
        assertEquals("exec '/home/me/voice tool' --rpc --cwd '/tmp/a'\"'\"'b'", backendCommand("/home/me/voice tool", "/tmp/a'b"))
        assertEquals("exec 'speakshell' --rpc", backendCommand("speakshell", ""))
        assertEquals("exec '\$(touch nope); bad' --rpc", backendCommand("\$(touch nope); bad", ""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNewlineInExecutable() { backendCommand("bad\ncommand", "") }

    @Test fun waveHeaderDescribesPcmPayload() {
        val audio = wav(byteArrayOf(1, 2, 3, 4))
        val bytes = ByteBuffer.wrap(audio).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(audio.copyOfRange(0, 4)))
        assertEquals("WAVE", String(audio.copyOfRange(8, 12)))
        assertEquals(40, bytes.getInt(4))
        assertEquals(16000, bytes.getInt(24))
        assertEquals(4, bytes.getInt(40))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), audio.copyOfRange(44, 48))
    }

    @Test fun sendsApprovalForStoredIdAndReceivesOutput() {
        val responses = """{"id":1,"result":{"exit_code":0,"output":"hello"}}""" + "\n"
        val output = ByteArrayOutputStream()
        var closed = false
        VoiceProtocol(responses.byteInputStream(), output) { closed = true }.use { protocol ->
            val result = protocol.request("execute", JSONObject().put("proposal_id", "stored-id").put("approved", true))
            assertEquals("hello", result.getString("output"))
            val request = JSONObject(output.toString("UTF-8"))
            assertEquals("execute", request.getString("method"))
            assertEquals("stored-id", request.getJSONObject("params").getString("proposal_id"))
            assertTrue(request.getJSONObject("params").getBoolean("approved"))
            assertFalse(request.getJSONObject("params").has("command"))
        }
        assertTrue(closed)
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsMismatchedResponseId() {
        VoiceProtocol("{\"id\":9,\"result\":{}}\n".byteInputStream(), ByteArrayOutputStream()) {}.use {
            it.request("context")
        }
    }

    @Test(expected = IllegalStateException::class)
    fun surfacesBackendError() {
        VoiceProtocol("{\"id\":1,\"error\":\"Install host models\"}\n".byteInputStream(), ByteArrayOutputStream()) {}.use {
            it.request("plan")
        }
    }
}
