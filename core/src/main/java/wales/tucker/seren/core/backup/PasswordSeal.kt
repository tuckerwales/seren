package wales.tucker.seren.core.backup

import org.json.JSONException
import org.json.JSONObject
import java.util.Base64

/**
 * The password protection every Seren backup file shares, so a backup made by one app is locked
 * the same way as any other and one description covers them all:
 *
 * ```
 * { "format": "...", "version": 1, "encrypted": true,
 *   "kdf": { "algorithm": "scrypt", "n": 32768, "r": 8, "p": 1, "salt": "<Base64>" },
 *   "cipher": { "algorithm": "AES-256-GCM", "nonce": "<Base64>" },
 *   "data": "<Base64 ciphertext with the tag appended>" }
 * ```
 *
 * Each app decides what goes in "data" (the same JSON it would write unencrypted) and keeps its
 * own "format" and "version" alongside.
 */
object PasswordSeal {
    /** Encrypts [plain] with [password] and adds it to [root], which is returned. */
    fun seal(root: JSONObject, plain: ByteArray, password: CharArray): JSONObject {
        val params = BackupCrypto.DEFAULT_SCRYPT
        val salt = BackupCrypto.randomBytes(BackupCrypto.SALT_SIZE)
        val nonce = BackupCrypto.randomBytes(BackupCrypto.NONCE_SIZE)
        val key = BackupCrypto.deriveKey(password, salt, params)
        val data = try {
            BackupCrypto.encrypt(key, nonce, plain)
        } finally {
            key.fill(0)
        }
        val b64 = Base64.getEncoder()
        return root
            .put("encrypted", true)
            .put(
                "kdf",
                JSONObject().put("algorithm", "scrypt").put("n", params.n).put("r", params.r).put("p", params.p)
                    .put("salt", b64.encodeToString(salt)),
            )
            .put("cipher", JSONObject().put("algorithm", "AES-256-GCM").put("nonce", b64.encodeToString(nonce)))
            .put("data", b64.encodeToString(data))
    }

    fun isSealed(root: JSONObject): Boolean = root.optBoolean("encrypted")

    /**
     * Reads the sealed part of [root] without the password, so a damaged file is reported before
     * anyone is asked for one.
     * @throws JSONException if the sealed part is missing or damaged.
     */
    fun read(root: JSONObject): Sealed = try {
        val kdf = root.getJSONObject("kdf")
        if (kdf.optString("algorithm", "scrypt") != "scrypt") throw JSONException("Unknown key derivation ${kdf.optString("algorithm")}")
        val b64 = Base64.getDecoder()
        Sealed(
            params = BackupCrypto.ScryptParams(kdf.getInt("n"), kdf.getInt("r"), kdf.getInt("p")),
            salt = b64.decode(kdf.getString("salt")),
            nonce = b64.decode(root.getJSONObject("cipher").getString("nonce")),
            data = b64.decode(root.getString("data")),
        )
    } catch (e: IllegalArgumentException) {
        throw JSONException("Damaged encrypted data")
    }

    class Sealed internal constructor(
        private val params: BackupCrypto.ScryptParams,
        private val salt: ByteArray,
        private val nonce: ByteArray,
        private val data: ByteArray,
    ) {
        /** The plain data, or null when [password] is wrong (or the file was changed). */
        fun open(password: CharArray): ByteArray? {
            val key = BackupCrypto.deriveKey(password, salt, params)
            return try {
                BackupCrypto.decrypt(key, nonce, data)
            } finally {
                key.fill(0)
            }
        }
    }
}
