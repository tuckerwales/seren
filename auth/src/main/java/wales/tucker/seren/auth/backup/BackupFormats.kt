package wales.tucker.seren.auth.backup

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import wales.tucker.seren.auth.otp.Base32
import wales.tucker.seren.auth.otp.GoogleMigration
import wales.tucker.seren.auth.otp.OtpAlgorithm
import wales.tucker.seren.auth.otp.OtpAuthUri
import wales.tucker.seren.auth.otp.OtpFormatException
import wales.tucker.seren.auth.otp.OtpToken
import wales.tucker.seren.auth.otp.OtpType
import java.util.Base64
import wales.tucker.seren.core.backup.BackupCrypto
import wales.tucker.seren.core.backup.PasswordSeal

/** An account as it travels in a backup: its token plus the accent color it had. */
data class BackupEntry(val token: OtpToken, val color: Int? = null)

/** What reading a file or code found, before anything is added. */
data class ParsedImport(
    /** Where the accounts came from, for "Import 3 accounts from Aegis?". */
    val source: String,
    val entries: List<BackupEntry>,
    /** Accounts in the file that Seren Auth can't use, such as MD5 or other non-standard ones. */
    val unsupported: Int = 0,
    /** Anything else worth knowing, such as more Google Authenticator QR codes to scan. */
    val note: String? = null,
)

/** The result of reading a file: either the accounts, or a request for the file's password. */
sealed interface ImportRead {
    data class Ready(val import: ParsedImport) : ImportRead

    /** The file is encrypted; [decrypt] throws [OtpFormatException] for a wrong password. */
    class Locked(val source: String, val decrypt: (CharArray) -> ParsedImport) : ImportRead
}

/**
 * Seren Auth's own backup file, a documented JSON format so people can always leave:
 *
 * ```
 * { "format": "seren-auth-backup", "version": 1, "encrypted": false,
 *   "accounts": [ { "issuer": "GitHub", "name": "you@example.com", "secret": "JBSWY3DPEHPK3PXP",
 *                   "type": "TOTP", "algorithm": "SHA1", "digits": 6, "period": 30, "counter": 0,
 *                   "color": 0 } ] }
 * ```
 *
 * An encrypted backup replaces "accounts" with the same array, sealed with the password the way
 * every Seren backup is ([PasswordSeal]): "kdf" holds the scrypt parameters and a Base64 salt,
 * "cipher" the AES-256-GCM nonce, and "data" the Base64 ciphertext with the tag appended.
 */
object SerenBackup {
    const val FORMAT = "seren-auth-backup"
    const val VERSION = 1
    const val SOURCE = "a Seren Auth backup"

    fun export(entries: List<BackupEntry>, password: CharArray?): String {
        val accounts = JSONArray().apply { entries.forEach { put(entryJson(it)) } }
        val root = JSONObject().put("format", FORMAT).put("version", VERSION)
        if (password == null) {
            return root.put("encrypted", false).put("accounts", accounts).toString(2)
        }
        return PasswordSeal.seal(root, accounts.toString().toByteArray(Charsets.UTF_8), password).toString(2)
    }

    fun read(root: JSONObject): ImportRead {
        if (root.optInt("version", 1) > VERSION) throw OtpFormatException("This backup was made by a newer version of Seren Auth")
        if (!root.optBoolean("encrypted")) {
            return ImportRead.Ready(entries(root.optJSONArray("accounts") ?: JSONArray()))
        }
        val sealed = PasswordSeal.read(root)
        return ImportRead.Locked(SOURCE) { password ->
            val plain = sealed.open(password) ?: throw OtpFormatException("That password isn't right")
            entries(JSONArray(plain.toString(Charsets.UTF_8)))
        }
    }

    private fun entries(array: JSONArray): ParsedImport {
        val out = mutableListOf<BackupEntry>()
        var unsupported = 0
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val token = runCatching {
                OtpToken(
                    issuer = o.optString("issuer"),
                    name = o.optString("name"),
                    secret = o.getString("secret"),
                    type = OtpType.valueOf(o.optString("type", "TOTP")),
                    algorithm = OtpAlgorithm.valueOf(o.optString("algorithm", "SHA1")),
                    digits = o.optInt("digits", OtpToken.DEFAULT_DIGITS),
                    period = o.optInt("period", OtpToken.DEFAULT_PERIOD),
                    counter = o.optLong("counter", 0),
                )
            }.getOrNull()?.let(::validated)
            if (token == null) unsupported++ else out += BackupEntry(token, if (o.has("color")) o.getInt("color") else null)
        }
        return ParsedImport(SOURCE, out, unsupported)
    }

    private fun entryJson(e: BackupEntry): JSONObject = JSONObject()
        .put("issuer", e.token.issuer)
        .put("name", e.token.name)
        .put("secret", e.token.secret)
        .put("type", e.token.type.name)
        .put("algorithm", e.token.algorithm.name)
        .put("digits", e.token.digits)
        .put("period", e.token.period)
        .put("counter", e.token.counter)
        .apply { e.color?.let { put("color", it) } }
}

/**
 * Aegis vault exports, plain or encrypted with a password. The encrypted form wraps a random
 * master key in "slots"; a password slot (type 1) holds it encrypted with an scrypt derived key.
 */
object AegisBackup {
    const val SOURCE = "Aegis"

    fun isAegis(root: JSONObject) = root.has("header") && root.has("db")

    fun read(root: JSONObject): ImportRead {
        val header = root.getJSONObject("header")
        val db = root.get("db")
        if (db is JSONObject) return ImportRead.Ready(entries(db))

        val slots = header.optJSONArray("slots") ?: throw OtpFormatException("This Aegis file is damaged")
        val params = header.getJSONObject("params")
        val dbNonce = hex(params.getString("nonce"))
        val dbData = Base64.getDecoder().decode(db as String) + hex(params.getString("tag"))
        val passwordSlots = (0 until slots.length()).map { slots.getJSONObject(it) }.filter { it.optInt("type") == 1 }
        if (passwordSlots.isEmpty()) throw OtpFormatException("This Aegis file can only be unlocked with a fingerprint on the phone that made it")
        return ImportRead.Locked(SOURCE) { password ->
            val masterKey = passwordSlots.firstNotNullOfOrNull { slot ->
                val keyParams = slot.getJSONObject("key_params")
                val derived = BackupCrypto.deriveKey(
                    password,
                    hex(slot.getString("salt")),
                    BackupCrypto.ScryptParams(slot.getInt("n"), slot.getInt("r"), slot.getInt("p")),
                )
                BackupCrypto.decrypt(derived, hex(keyParams.getString("nonce")), hex(slot.getString("key")) + hex(keyParams.getString("tag")))
                    .also { derived.fill(0) }
            } ?: throw OtpFormatException("That password isn't right")
            val plain = BackupCrypto.decrypt(masterKey, dbNonce, dbData) ?: throw OtpFormatException("This Aegis file is damaged")
            masterKey.fill(0)
            entries(JSONObject(plain.toString(Charsets.UTF_8)))
        }
    }

    private fun entries(db: JSONObject): ParsedImport {
        val array = db.optJSONArray("entries") ?: JSONArray()
        val out = mutableListOf<BackupEntry>()
        var unsupported = 0
        for (i in 0 until array.length()) {
            val e = array.getJSONObject(i)
            val info = e.optJSONObject("info")
            val type = when (e.optString("type").lowercase()) {
                "totp" -> OtpType.TOTP
                "hotp" -> OtpType.HOTP
                "steam" -> OtpType.STEAM
                else -> null
            }
            val algorithm = when (info?.optString("algo", "SHA1")?.uppercase()) {
                "SHA1" -> OtpAlgorithm.SHA1
                "SHA256" -> OtpAlgorithm.SHA256
                "SHA512" -> OtpAlgorithm.SHA512
                else -> null
            }
            val token = if (info == null || type == null || algorithm == null) {
                null
            } else {
                validated(
                    OtpToken(
                        issuer = e.optString("issuer"),
                        name = e.optString("name"),
                        secret = info.optString("secret"),
                        type = type,
                        algorithm = algorithm,
                        digits = info.optInt("digits", OtpToken.DEFAULT_DIGITS),
                        period = info.optInt("period", OtpToken.DEFAULT_PERIOD),
                        counter = info.optLong("counter", 0),
                    ),
                )
            }
            if (token == null) unsupported++ else out += BackupEntry(token)
        }
        return ParsedImport(SOURCE, out, unsupported)
    }

    private fun hex(s: String): ByteArray {
        require(s.length % 2 == 0) { "Odd length hex" }
        return ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}

/** andOTP's plain JSON export: an array of accounts. */
object AndOtpBackup {
    const val SOURCE = "andOTP"

    fun read(array: JSONArray): ImportRead {
        val out = mutableListOf<BackupEntry>()
        var unsupported = 0
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i)
            val type = runCatching { OtpType.valueOf(o?.optString("type", "TOTP")?.uppercase().orEmpty()) }.getOrNull()
            val algorithm = runCatching { OtpAlgorithm.valueOf(o?.optString("algorithm", "SHA1")?.uppercase().orEmpty()) }.getOrNull()
            val token = if (o == null || type == null || algorithm == null) {
                null
            } else {
                val label = o.optString("label")
                val issuer = o.optString("issuer")
                validated(
                    OtpToken(
                        issuer = issuer,
                        // Older andOTP versions kept "Issuer - name" in the label.
                        name = if (issuer.isNotEmpty() && label.startsWith("$issuer - ")) label.substringAfter(" - ") else label,
                        secret = o.optString("secret"),
                        type = type,
                        algorithm = algorithm,
                        digits = o.optInt("digits", OtpToken.DEFAULT_DIGITS),
                        period = o.optInt("period", OtpToken.DEFAULT_PERIOD),
                        counter = o.optLong("counter", 0),
                    ),
                )
            }
            if (token == null) unsupported++ else out += BackupEntry(token)
        }
        return ImportRead.Ready(ParsedImport(SOURCE, out, unsupported))
    }
}

/** Reads any file or scanned text Seren Auth understands. */
object Importer {
    /** Files bigger than this are not backups; refusing them avoids reading a huge file into memory. */
    const val MAX_FILE_BYTES = 5 * 1024 * 1024

    /** @throws OtpFormatException with a reason to show when [text] isn't something Seren Auth can import. */
    fun read(text: String): ImportRead {
        val trimmed = text.trim().removePrefix("﻿")
        try {
            if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                return when {
                    root.optString("format") == SerenBackup.FORMAT -> SerenBackup.read(root)
                    AegisBackup.isAegis(root) -> AegisBackup.read(root)
                    else -> throw OtpFormatException(UNKNOWN)
                }
            }
            if (trimmed.startsWith("[")) return AndOtpBackup.read(JSONArray(trimmed))
        } catch (e: JSONException) {
            throw OtpFormatException("The file is damaged or incomplete")
        } catch (e: IllegalArgumentException) {
            throw OtpFormatException("The file is damaged or incomplete")
        }
        return ImportRead.Ready(readLinks(trimmed))
    }

    /** One otpauth or otpauth-migration link per line, as many apps export them. */
    fun readLinks(text: String): ParsedImport {
        val out = mutableListOf<BackupEntry>()
        var unsupported = 0
        var google = false
        var any = false
        var note: String? = null
        for (line in text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }) {
            when {
                OtpAuthUri.isOtpAuth(line) -> {
                    any = true
                    runCatching { OtpAuthUri.parse(line) }.onSuccess { out += BackupEntry(it) }.onFailure { unsupported++ }
                }
                GoogleMigration.isMigration(line) -> {
                    any = true
                    google = true
                    val batch = GoogleMigration.parse(line)
                    batch.tokens.forEach { out += BackupEntry(it) }
                    unsupported += batch.skipped
                    if (batch.size > 1) note = "This is QR code ${batch.index + 1} of ${batch.size}. Scan the others to bring over the rest."
                }
            }
        }
        if (!any) throw OtpFormatException(UNKNOWN)
        return ParsedImport(if (google) "Google Authenticator" else "a list of otpauth links", out, unsupported, note)
    }

    private const val UNKNOWN = "This isn't a file Seren Auth can import"
}

/** [token] with a normalized key and supported settings, or null if it can't make codes. */
internal fun validated(token: OtpToken): OtpToken? {
    val secret = Base32.normalize(token.secret)
    if (!Base32.isValid(secret)) return null
    if (token.period !in OtpToken.PERIOD_RANGE || token.counter < 0) return null
    return when (token.type) {
        OtpType.STEAM -> token.copy(
            secret = secret,
            issuer = token.issuer.trim(),
            name = token.name.trim(),
            algorithm = OtpAlgorithm.SHA1,
            digits = OtpToken.STEAM_DIGITS,
        )
        OtpType.TOTP, OtpType.HOTP -> {
            if (token.digits !in OtpToken.DIGIT_CHOICES) return null
            token.copy(secret = secret, issuer = token.issuer.trim(), name = token.name.trim())
        }
    }
}
