package wales.tucker.seren.ssh.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import wales.tucker.seren.ssh.data.KeyType
import java.io.ByteArrayOutputStream

class SshKeysTest {
    @Before
    fun setUp() = JschAndroidConfig.apply()

    @Test
    fun generatesEd25519() {
        val k = SshKeys.generate(KeyType.ED25519, 256, "me@phone")
        assertEquals(KeyType.ED25519, k.type)
        assertTrue(k.publicKey.startsWith("ssh-ed25519 AAAA"))
        assertTrue(k.publicKey.endsWith("me@phone"))
        assertTrue(k.privateKey.contains("BEGIN OPENSSH PRIVATE KEY"))
        assertTrue(k.fingerprint.startsWith("SHA256:"))
        val blob = SshKeys.parsePublicKeyLine(k.publicKey)!!
        assertEquals("ssh-ed25519", SshKeys.blobKeyType(blob))
        assertEquals(k.fingerprint, SshKeys.fingerprint(blob))
    }

    @Test
    fun generatesEcdsaAndRsa() {
        val ec = SshKeys.generate(KeyType.ECDSA, 384, "c")
        assertTrue(ec.publicKey.startsWith("ecdsa-sha2-nistp384 "))
        assertEquals(384, ec.bits)
        val rsa = SshKeys.generate(KeyType.RSA, 2048, "c")
        assertTrue(rsa.publicKey.startsWith("ssh-rsa "))
        assertEquals(2048, rsa.bits)
    }

    @Test
    fun roundTripsImport() {
        for (type in listOf(KeyType.ED25519, KeyType.ECDSA, KeyType.RSA)) {
            val k = SshKeys.generate(type, if (type == KeyType.RSA) 2048 else 256, "x")
            val imported = SshKeys.import(k.privateKey, null, "x")
            assertEquals(k.fingerprint, imported.fingerprint)
            assertEquals(type, imported.type)
        }
    }

    @Test
    fun importsEncryptedKey() {
        val kp = KeyPair.genKeyPair(JSch(), KeyPair.ED25519)
        val out = ByteArrayOutputStream()
        kp.writeOpenSSHv1PrivateKey(out, "secret".toByteArray())
        val pem = out.toString()
        assertTrue(SshKeys.needsPassphrase(pem))
        try {
            SshKeys.import(pem, null, "")
            throw AssertionError("expected PassphraseRequiredException")
        } catch (_: PassphraseRequiredException) {
        }
        try {
            SshKeys.import(pem, "wrong", "")
            throw AssertionError("expected WrongPassphraseException")
        } catch (_: WrongPassphraseException) {
        }
        val k = SshKeys.import(pem, "secret", "c")
        assertTrue(k.privateKey.contains("BEGIN OPENSSH PRIVATE KEY"))
        assertTrue(!SshKeys.needsPassphrase(k.privateKey))
    }

    @Test(expected = InvalidKeyException::class)
    fun rejectsGarbage() {
        SshKeys.import("not a key", null, "")
    }
}
