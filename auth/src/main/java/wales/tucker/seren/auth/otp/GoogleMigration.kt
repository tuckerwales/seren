package wales.tucker.seren.auth.otp

import java.net.URLDecoder
import java.util.Base64

/**
 * The `otpauth-migration://offline?data=...` QR codes Google Authenticator shows under
 * "Transfer accounts". The data is a Base64 protocol buffer:
 *
 * ```
 * message MigrationPayload { repeated OtpParameters otp_parameters = 1; int32 version = 2;
 *   int32 batch_size = 3; int32 batch_index = 4; int32 batch_id = 5; }
 * message OtpParameters { bytes secret = 1; string name = 2; string issuer = 3;
 *   Algorithm algorithm = 4; DigitCount digits = 5; OtpType type = 6; int64 counter = 7; }
 * ```
 */
object GoogleMigration {
    const val SCHEME = "otpauth-migration"

    fun isMigration(text: String): Boolean = text.trim().startsWith("$SCHEME://", ignoreCase = true)

    /** Where a batch sits among the QR codes of one transfer, so people know to scan the rest. */
    data class Batch(val tokens: List<OtpToken>, val skipped: Int, val index: Int, val size: Int)

    /** @throws OtpFormatException if [text] is not a Google Authenticator transfer link. */
    fun parse(text: String): Batch {
        if (!isMigration(text)) throw OtpFormatException("This isn't a Google Authenticator transfer code")
        val query = text.trim().substringAfter('?', "")
        val data = query.split('&').firstOrNull { it.startsWith("data=") }?.substringAfter("data=")
            ?: throw OtpFormatException("The transfer code has no accounts in it")
        val bytes = runCatching {
            val b64 = URLDecoder.decode(data, "UTF-8").replace(' ', '+')
            Base64.getMimeDecoder().decode(b64)
        }.getOrElse { throw OtpFormatException("The transfer code is damaged") }

        val tokens = mutableListOf<OtpToken>()
        var skipped = 0
        var batchSize = 1
        var batchIndex = 0
        try {
            val reader = ProtoReader(bytes)
            while (reader.hasMore()) {
                val (field, wire) = reader.tag()
                when {
                    field == 1 && wire == 2 -> parseParameters(reader.bytes())?.let(tokens::add) ?: skipped++
                    field == 3 && wire == 0 -> batchSize = reader.varint().toInt()
                    field == 4 && wire == 0 -> batchIndex = reader.varint().toInt()
                    else -> reader.skip(wire)
                }
            }
        } catch (e: IndexOutOfBoundsException) {
            throw OtpFormatException("The transfer code is damaged")
        }
        return Batch(tokens, skipped, batchIndex, batchSize.coerceAtLeast(1))
    }

    /** One account, or null for a kind Seren Auth can't use (such as MD5). */
    private fun parseParameters(bytes: ByteArray): OtpToken? {
        val reader = ProtoReader(bytes)
        var secret = ByteArray(0)
        var name = ""
        var issuer = ""
        var algorithm = 1
        var digits = 1
        var type = 2
        var counter = 0L
        while (reader.hasMore()) {
            val (field, wire) = reader.tag()
            when {
                field == 1 && wire == 2 -> secret = reader.bytes()
                field == 2 && wire == 2 -> name = reader.bytes().toString(Charsets.UTF_8)
                field == 3 && wire == 2 -> issuer = reader.bytes().toString(Charsets.UTF_8)
                field == 4 && wire == 0 -> algorithm = reader.varint().toInt()
                field == 5 && wire == 0 -> digits = reader.varint().toInt()
                field == 6 && wire == 0 -> type = reader.varint().toInt()
                field == 7 && wire == 0 -> counter = reader.varint()
                else -> reader.skip(wire)
            }
        }
        if (secret.isEmpty()) return null
        val alg = when (algorithm) {
            0, 1 -> OtpAlgorithm.SHA1
            2 -> OtpAlgorithm.SHA256
            3 -> OtpAlgorithm.SHA512
            else -> return null
        }
        val digitCount = when (digits) {
            0, 1 -> 6
            2 -> 8
            else -> return null
        }
        val otpType = when (type) {
            1 -> OtpType.HOTP
            0, 2 -> OtpType.TOTP
            else -> return null
        }
        // Google Authenticator often puts "Issuer:name" in the name as well.
        val cleanName = if (issuer.isNotEmpty() && name.startsWith("$issuer:")) name.substringAfter(':').trim() else name
        return OtpToken(
            issuer = issuer,
            name = cleanName,
            secret = Base32.encode(secret),
            type = otpType,
            algorithm = alg,
            digits = digitCount,
            counter = counter,
        )
    }

    /** Just enough of the protocol buffer wire format for the payload above. */
    private class ProtoReader(private val data: ByteArray) {
        private var pos = 0

        fun hasMore() = pos < data.size

        fun tag(): Pair<Int, Int> {
            val t = varint()
            return (t ushr 3).toInt() to (t and 7).toInt()
        }

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = data[pos++].toInt()
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                if (shift > 63) throw IndexOutOfBoundsException("varint too long")
            }
        }

        fun bytes(): ByteArray {
            val len = varint().toInt()
            if (len < 0 || pos + len > data.size) throw IndexOutOfBoundsException("length")
            return data.copyOfRange(pos, pos + len).also { pos += len }
        }

        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> pos += 8
                2 -> bytes()
                5 -> pos += 4
                else -> throw IndexOutOfBoundsException("wire type $wire")
            }
            if (pos > data.size) throw IndexOutOfBoundsException("skip")
        }
    }
}
