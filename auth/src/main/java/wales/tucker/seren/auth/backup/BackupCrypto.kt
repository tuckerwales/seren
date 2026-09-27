package wales.tucker.seren.auth.backup

import org.bouncycastle.crypto.generators.SCrypt
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Password based encryption for backup files: scrypt to derive a key, AES-256-GCM to encrypt. */
object BackupCrypto {
    data class ScryptParams(val n: Int, val r: Int, val p: Int)

    /** The same cost Aegis uses: 32 MB of memory and well under a second on a phone. */
    val DEFAULT_SCRYPT = ScryptParams(n = 1 shl 15, r = 8, p = 1)

    const val SALT_SIZE = 32
    const val NONCE_SIZE = 12
    const val TAG_BITS = 128

    private val random = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    fun deriveKey(password: CharArray, salt: ByteArray, params: ScryptParams): ByteArray {
        val bytes = String(password).toByteArray(Charsets.UTF_8)
        try {
            return SCrypt.generate(bytes, salt, params.n, params.r, params.p, 32)
        } finally {
            bytes.fill(0)
        }
    }

    fun encrypt(key: ByteArray, nonce: ByteArray, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return cipher.doFinal(plain)
    }

    /** Returns null when [key] is wrong or the data was changed, which GCM can't tell apart. */
    fun decrypt(key: ByteArray, nonce: ByteArray, cipherTextAndTag: ByteArray): ByteArray? {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return try {
            cipher.doFinal(cipherTextAndTag)
        } catch (e: AEADBadTagException) {
            null
        }
    }
}
