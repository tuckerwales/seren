package wales.tucker.seren.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PasswordSealTest {
    private val plain = "{\"hosts\":[]}".toByteArray()

    private fun sealed(password: String = "correct horse") =
        PasswordSeal.seal(JSONObject().put("format", "test").put("version", 3), plain, password.toCharArray())

    @Test
    fun sealsAlongsideTheAppsOwnFields() {
        val root = sealed()
        assertEquals("test", root.getString("format"))
        assertEquals(3, root.getInt("version"))
        assertTrue(PasswordSeal.isSealed(root))
        assertEquals("scrypt", root.getJSONObject("kdf").getString("algorithm"))
        assertEquals(1 shl 15, root.getJSONObject("kdf").getInt("n"))
        assertEquals("AES-256-GCM", root.getJSONObject("cipher").getString("algorithm"))
        assertFalse(root.toString().contains("hosts"))
        assertFalse(PasswordSeal.isSealed(JSONObject().put("encrypted", false)))
    }

    @Test
    fun opensOnlyWithTheRightPassword() {
        val read = PasswordSeal.read(JSONObject(sealed().toString()))
        assertNull(read.open("wrong".toCharArray()))
        assertArrayEquals(plain, read.open("correct horse".toCharArray()))
    }

    @Test
    fun eachSealIsFresh() {
        val a = sealed()
        val b = sealed()
        // A new salt and nonce every time, so the same data never looks the same twice.
        assertFalse(a.getJSONObject("kdf").getString("salt") == b.getJSONObject("kdf").getString("salt"))
        assertFalse(a.getString("data") == b.getString("data"))
    }

    @Test
    fun changedDataIsRefused() {
        val root = sealed()
        val data = Base64.getDecoder().decode(root.getString("data"))
        data[data.size - 1] = (data.last().toInt() xor 1).toByte()
        root.put("data", Base64.getEncoder().encodeToString(data))
        assertNull(PasswordSeal.read(root).open("correct horse".toCharArray()))
    }

    @Test
    fun damagedFilesAreReportedBeforeAskingForAPassword() {
        assertThrows(JSONException::class.java) { PasswordSeal.read(JSONObject().put("encrypted", true)) }
        val badBase64 = sealed().put("data", "not base64!")
        assertThrows(JSONException::class.java) { PasswordSeal.read(badBase64) }
        val otherKdf = sealed().apply { getJSONObject("kdf").put("algorithm", "argon2") }
        assertThrows(JSONException::class.java) { PasswordSeal.read(otherKdf) }
    }
}
