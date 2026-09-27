package wales.tucker.seren.auth.otp

/**
 * RFC 4648 Base32, the alphabet setup keys are written in. Decoding is forgiving about how people
 * copy keys: case, spaces, dashes and padding are ignored.
 */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** [key] upper case, without spaces, dashes or padding. */
    fun normalize(key: String): String =
        key.filterNot { it.isWhitespace() || it == '-' || it == '=' }.uppercase()

    /** Whether [key] is a non-empty Base32 string once normalized. */
    fun isValid(key: String): Boolean {
        val n = normalize(key)
        return n.isNotEmpty() && n.all { it in ALPHABET } && n.length % 8 !in INVALID_TAIL_LENGTHS
    }

    /** @throws IllegalArgumentException if [key] is not Base32. */
    fun decode(key: String): ByteArray {
        val n = normalize(key)
        require(n.isNotEmpty()) { "Empty key" }
        require(n.length % 8 !in INVALID_TAIL_LENGTHS) { "Wrong length" }
        val out = java.io.ByteArrayOutputStream(n.length * 5 / 8)
        var buffer = 0
        var bits = 0
        for (c in n) {
            val v = ALPHABET.indexOf(c)
            require(v >= 0) { "Not a Base32 character: $c" }
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    /** Unpadded Base32 of [bytes]. */
    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                sb.append(ALPHABET[(buffer shr bits) and 31])
            }
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    // A group of 8 characters encodes 5 bytes; 1, 3 or 6 leftover characters can't come from whole bytes.
    private val INVALID_TAIL_LENGTHS = setOf(1, 3, 6)
}
