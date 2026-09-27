package wales.tucker.seren.auth.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import wales.tucker.seren.auth.TestApp
import wales.tucker.seren.auth.otp.OtpAlgorithm
import wales.tucker.seren.auth.otp.OtpAuthUri
import wales.tucker.seren.auth.otp.OtpFormatException
import wales.tucker.seren.auth.otp.OtpToken
import wales.tucker.seren.auth.otp.OtpType
import java.util.Base64
import wales.tucker.seren.core.backup.BackupCrypto

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class BackupFormatsTest {
    private val github = OtpToken("GitHub", "octocat", "JBSWY3DPEHPK3PXP")
    private val server = OtpToken("", "root@server", "GEZDGNBVGY3TQOJQ", OtpType.HOTP, OtpAlgorithm.SHA256, 8, counter = 12)
    private val entries = listOf(BackupEntry(github, 2), BackupEntry(server, 5))

    private fun ready(read: ImportRead) = (read as ImportRead.Ready).import

    @Test
    fun plainBackupRoundTrips() {
        val json = SerenBackup.export(entries, password = null)
        assertEquals(false, JSONObject(json).getBoolean("encrypted"))
        val import = ready(Importer.read(json))
        assertEquals(entries, import.entries)
        assertEquals("a Seren Auth backup", import.source)
    }

    @Test
    fun encryptedBackupNeedsTheRightPassword() {
        val json = SerenBackup.export(entries, "correct horse".toCharArray())
        assertFalse("setup keys must not appear in the file", json.contains(github.secret))
        val locked = Importer.read(json) as ImportRead.Locked
        val wrong = assertThrows(OtpFormatException::class.java) { locked.decrypt("wrong".toCharArray()) }
        assertEquals("That password isn't right", wrong.message)
        assertEquals(entries, locked.decrypt("correct horse".toCharArray()).entries)
    }

    @Test
    fun tamperedBackupIsRefused() {
        val root = JSONObject(SerenBackup.export(entries, "pw123456".toCharArray()))
        val data = Base64.getDecoder().decode(root.getString("data"))
        data[0] = (data[0].toInt() xor 1).toByte()
        root.put("data", Base64.getEncoder().encodeToString(data))
        val locked = Importer.read(root.toString()) as ImportRead.Locked
        assertThrows(OtpFormatException::class.java) { locked.decrypt("pw123456".toCharArray()) }
    }

    @Test
    fun readsPlainAegisVaults() {
        val import = ready(Importer.read(aegisPlain().toString()))
        assertEquals("Aegis", import.source)
        assertEquals(listOf(BackupEntry(github), BackupEntry(server)), import.entries)
        assertEquals("Steam codes aren't supported", 1, import.unsupported)
    }

    @Test
    fun readsEncryptedAegisVaults() {
        val vault = aegisEncrypted(aegisPlain().getJSONObject("db"), "test")
        val locked = Importer.read(vault.toString()) as ImportRead.Locked
        assertEquals("Aegis", locked.source)
        assertThrows(OtpFormatException::class.java) { locked.decrypt("nope".toCharArray()) }
        assertEquals(2, locked.decrypt("test".toCharArray()).entries.size)
    }

    @Test
    fun readsAndOtpExports() {
        val json = JSONArray()
            .put(JSONObject().put("secret", "JBSWY3DPEHPK3PXP").put("issuer", "GitHub").put("label", "GitHub - octocat").put("digits", 6).put("type", "TOTP").put("algorithm", "SHA1").put("period", 30))
            .put(JSONObject().put("secret", "JBSWY3DPEHPK3PXP").put("label", "steam").put("type", "STEAM"))
        val import = ready(Importer.read(json.toString()))
        assertEquals(listOf(BackupEntry(github)), import.entries)
        assertEquals(1, import.unsupported)
    }

    @Test
    fun readsListsOfLinks() {
        val text = "\n" + OtpAuthUri.format(github) + "\n\n" + OtpAuthUri.format(server) + "\notpauth://totp/bad?secret=1\n"
        val import = ready(Importer.read(text))
        assertEquals(listOf(github, server), import.entries.map { it.token })
        assertEquals(1, import.unsupported)
    }

    @Test
    fun explainsUnreadableFiles() {
        assertEquals("This isn't a file Seren Auth can import", assertThrows(OtpFormatException::class.java) { Importer.read("hello") }.message)
        assertEquals("This isn't a file Seren Auth can import", assertThrows(OtpFormatException::class.java) { Importer.read("{\"a\":1}") }.message)
        assertEquals("The file is damaged or incomplete", assertThrows(OtpFormatException::class.java) { Importer.read("{\"format\":") }.message)
        val newer = JSONObject().put("format", SerenBackup.FORMAT).put("version", 99).toString()
        assertTrue(assertThrows(OtpFormatException::class.java) { Importer.read(newer) }.message!!.contains("newer version"))
    }

    private fun aegisPlain(): JSONObject {
        fun entry(type: String, issuer: String, name: String, info: JSONObject) =
            JSONObject().put("type", type).put("uuid", java.util.UUID.randomUUID().toString()).put("name", name).put("issuer", issuer)
                .put("note", "").put("icon", JSONObject.NULL).put("info", info)
        val entries = JSONArray()
            .put(entry("totp", "GitHub", "octocat", JSONObject().put("secret", "JBSWY3DPEHPK3PXP").put("algo", "SHA1").put("digits", 6).put("period", 30)))
            .put(entry("hotp", "", "root@server", JSONObject().put("secret", "GEZDGNBVGY3TQOJQ").put("algo", "SHA256").put("digits", 8).put("counter", 12)))
            .put(entry("steam", "Steam", "gamer", JSONObject().put("secret", "JBSWY3DPEHPK3PXP").put("algo", "SHA1").put("digits", 5).put("period", 30)))
        return JSONObject()
            .put("version", 1)
            .put("header", JSONObject().put("slots", JSONObject.NULL).put("params", JSONObject.NULL))
            .put("db", JSONObject().put("version", 3).put("entries", entries))
    }

    /** Encrypts [db] the way Aegis does: a random master key, wrapped by an scrypt password slot. */
    private fun aegisEncrypted(db: JSONObject, password: String): JSONObject {
        fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
        val master = BackupCrypto.randomBytes(32)
        val salt = BackupCrypto.randomBytes(32)
        val params = BackupCrypto.ScryptParams(1 shl 15, 8, 1)
        val derived = BackupCrypto.deriveKey(password.toCharArray(), salt, params)
        val slotNonce = BackupCrypto.randomBytes(12)
        val wrapped = BackupCrypto.encrypt(derived, slotNonce, master)
        val dbNonce = BackupCrypto.randomBytes(12)
        val sealed = BackupCrypto.encrypt(master, dbNonce, db.toString().toByteArray())
        val tag = { b: ByteArray -> b.copyOfRange(b.size - 16, b.size) }
        val body = { b: ByteArray -> b.copyOfRange(0, b.size - 16) }
        val biometricSlot = JSONObject().put("type", 2).put("uuid", "b").put("key", hex(ByteArray(32)))
            .put("key_params", JSONObject().put("nonce", hex(ByteArray(12))).put("tag", hex(ByteArray(16))))
        val passwordSlot = JSONObject().put("type", 1).put("uuid", "p").put("key", hex(body(wrapped)))
            .put("key_params", JSONObject().put("nonce", hex(slotNonce)).put("tag", hex(tag(wrapped))))
            .put("n", params.n).put("r", params.r).put("p", params.p).put("salt", hex(salt)).put("repaired", true)
        return JSONObject()
            .put("version", 1)
            .put(
                "header",
                JSONObject().put("slots", JSONArray().put(biometricSlot).put(passwordSlot))
                    .put("params", JSONObject().put("nonce", hex(dbNonce)).put("tag", hex(tag(sealed)))),
            )
            .put("db", Base64.getEncoder().encodeToString(body(sealed)))
    }
}
