package wales.tucker.seren.auth.otp

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class OtpType { TOTP, HOTP }

enum class OtpAlgorithm(val macName: String, val label: String) {
    SHA1("HmacSHA1", "SHA-1"),
    SHA256("HmacSHA256", "SHA-256"),
    SHA512("HmacSHA512", "SHA-512"),
}

/**
 * Everything needed to make codes for one account, independent of how Seren Auth stores it: what an
 * otpauth link, a QR code or a backup entry carries.
 */
data class OtpToken(
    /** The service, such as "GitHub". May be empty. */
    val issuer: String,
    /** The account at that service, such as "you@example.com". May be empty. */
    val name: String,
    /** The setup key in normalized Base32 (see [Base32.normalize]). */
    val secret: String,
    val type: OtpType = OtpType.TOTP,
    val algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
    val digits: Int = DEFAULT_DIGITS,
    /** Seconds each time based code lasts. */
    val period: Int = DEFAULT_PERIOD,
    /** The next counter value for a counter based (HOTP) account. */
    val counter: Long = 0,
) {
    /** A name to show: the issuer, or the account name when there is no issuer. */
    val title: String get() = issuer.ifBlank { name }

    /** The code at [timeMillis], or for the current counter of a counter based account. */
    fun code(timeMillis: Long): String {
        val key = Base32.decode(secret)
        return when (type) {
            OtpType.TOTP -> Otp.totp(key, timeMillis, period, algorithm, digits)
            OtpType.HOTP -> Otp.hotp(key, counter, algorithm, digits)
        }
    }

    /** Milliseconds until the current time based code changes. */
    fun remainingMillis(timeMillis: Long): Long = Otp.remainingMillis(timeMillis, period)

    companion object {
        const val DEFAULT_DIGITS = 6
        const val DEFAULT_PERIOD = 30
        val DIGIT_CHOICES = listOf(6, 7, 8)
        val PERIOD_RANGE = 1..3600
    }
}

/** HOTP (RFC 4226) and TOTP (RFC 6238). */
object Otp {
    fun hotp(key: ByteArray, counter: Long, algorithm: OtpAlgorithm, digits: Int): String {
        require(digits in 1..9) { "Unsupported number of digits: $digits" }
        val mac = Mac.getInstance(algorithm.macName)
        // An empty key is legal in HMAC but not in SecretKeySpec; a zero byte hashes the same way.
        mac.init(SecretKeySpec(if (key.isEmpty()) ByteArray(1) else key, algorithm.macName))
        val message = ByteArray(8) { i -> (counter ushr (56 - 8 * i)).toByte() }
        val hash = mac.doFinal(message)
        val offset = hash.last().toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        var modulus = 1
        repeat(digits) { modulus *= 10 }
        return (binary % modulus).toString().padStart(digits, '0')
    }

    fun totp(key: ByteArray, timeMillis: Long, period: Int, algorithm: OtpAlgorithm, digits: Int): String =
        hotp(key, timeStep(timeMillis, period), algorithm, digits)

    fun timeStep(timeMillis: Long, period: Int): Long = Math.floorDiv(timeMillis / 1000, period.toLong())

    fun remainingMillis(timeMillis: Long, period: Int): Long {
        val periodMillis = period * 1000L
        return periodMillis - Math.floorMod(timeMillis, periodMillis)
    }
}

/** "123 456" for "123456": two groups, the shorter first, so codes are easy to read out. */
fun formatCode(code: String): String {
    if (code.length < 5) return code
    val split = code.length / 2
    return code.substring(0, split) + " " + code.substring(split)
}
