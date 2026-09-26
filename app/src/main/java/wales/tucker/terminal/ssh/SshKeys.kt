package wales.tucker.terminal.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.KeyPair
import wales.tucker.terminal.data.KeyType
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

/** Key material produced by generating or importing a key. */
data class KeyMaterial(
    val type: KeyType,
    val bits: Int,
    /** Unencrypted private key in OpenSSH format. */
    val privateKey: String,
    /** authorized_keys line. */
    val publicKey: String,
    val fingerprint: String,
)

class PassphraseRequiredException : Exception("This key is protected by a passphrase")
class WrongPassphraseException : Exception("The passphrase is incorrect")
class InvalidKeyException(message: String, cause: Throwable? = null) : Exception(message, cause)

object SshKeys {

    fun generate(type: KeyType, bits: Int, comment: String): KeyMaterial {
        val jsch = JSch()
        val kp = when (type) {
            KeyType.ED25519 -> KeyPair.genKeyPair(jsch, KeyPair.ED25519)
            KeyType.ECDSA -> KeyPair.genKeyPair(jsch, KeyPair.ECDSA, bits)
            KeyType.RSA -> KeyPair.genKeyPair(jsch, KeyPair.RSA, bits)
            KeyType.OTHER -> throw IllegalArgumentException("Cannot generate keys of type OTHER")
        } ?: throw InvalidKeyException("Key generation failed")
        try {
            return toMaterial(kp, comment)
        } finally {
            kp.dispose()
        }
    }

    /**
     * Parses a private key in OpenSSH, PEM or PuTTY format. If the key is encrypted, the
     * [passphrase] is used to decrypt it; the returned material is always unencrypted.
     */
    fun import(privateKey: String, passphrase: String?, comment: String): KeyMaterial {
        val jsch = JSch()
        val kp = try {
            KeyPair.load(jsch, privateKey.trim().toByteArray(Charsets.UTF_8), null)
        } catch (e: JSchException) {
            throw InvalidKeyException(e.message ?: "Unrecognized key format", e)
        }
        try {
            if (kp.isEncrypted) {
                if (passphrase.isNullOrEmpty()) throw PassphraseRequiredException()
                if (!kp.decrypt(passphrase)) throw WrongPassphraseException()
            }
            return toMaterial(kp, comment.ifBlank { kp.publicKeyComment ?: "" })
        } finally {
            kp.dispose()
        }
    }

    fun needsPassphrase(privateKey: String): Boolean = try {
        val kp = KeyPair.load(JSch(), privateKey.trim().toByteArray(Charsets.UTF_8), null)
        kp.isEncrypted.also { kp.dispose() }
    } catch (_: JSchException) {
        false
    }

    private fun toMaterial(kp: KeyPair, comment: String): KeyMaterial {
        val prv = ByteArrayOutputStream()
        kp.writeOpenSSHv1PrivateKey(prv, null)
        val pub = ByteArrayOutputStream()
        kp.writePublicKey(pub, comment)
        val blob = kp.publicKeyBlob ?: throw InvalidKeyException("Could not derive public key")
        val type = when (kp.keyType) {
            KeyPair.ED25519 -> KeyType.ED25519
            KeyPair.ECDSA -> KeyType.ECDSA
            KeyPair.RSA -> KeyType.RSA
            else -> KeyType.OTHER
        }
        val bits = if (type == KeyType.ED25519) 256 else kp.keySize
        return KeyMaterial(
            type = type,
            bits = bits,
            privateKey = prv.toString(Charsets.UTF_8.name()),
            publicKey = pub.toString(Charsets.UTF_8.name()).trim(),
            fingerprint = fingerprint(blob),
        )
    }

    /** OpenSSH style SHA256 fingerprint of a public key blob. */
    fun fingerprint(blob: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(blob)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
    }

    /** Reads the key type name (the first SSH string) from a public key blob. */
    fun blobKeyType(blob: ByteArray): String {
        if (blob.size < 4) return "unknown"
        val len = ((blob[0].toInt() and 0xFF) shl 24) or ((blob[1].toInt() and 0xFF) shl 16) or
            ((blob[2].toInt() and 0xFF) shl 8) or (blob[3].toInt() and 0xFF)
        if (len <= 0 || 4 + len > blob.size) return "unknown"
        return String(blob, 4, len, Charsets.US_ASCII)
    }

    /** Parses an authorized_keys style line into its blob, or null. */
    fun parsePublicKeyLine(line: String): ByteArray? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 2) return null
        return try {
            Base64.getDecoder().decode(parts[1])
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
