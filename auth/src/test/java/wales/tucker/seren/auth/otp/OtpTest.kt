package wales.tucker.seren.auth.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpTest {
    private val sha1Key = "12345678901234567890".toByteArray()
    private val sha256Key = "12345678901234567890123456789012".toByteArray()
    private val sha512Key = ("1234567890".repeat(6) + "1234").toByteArray()

    @Test
    fun hotpMatchesRfc4226() {
        val expected = listOf("755224", "287082", "359152", "969429", "338314", "254676", "287922", "162583", "399871", "520489")
        expected.forEachIndexed { counter, code ->
            assertEquals(code, Otp.hotp(sha1Key, counter.toLong(), OtpAlgorithm.SHA1, 6))
        }
    }

    @Test
    fun totpMatchesRfc6238() {
        val vectors = listOf(
            59L to listOf("94287082", "46119246", "90693936"),
            1111111109L to listOf("07081804", "68084774", "25091201"),
            1111111111L to listOf("14050471", "67062674", "99943326"),
            1234567890L to listOf("89005924", "91819424", "93441116"),
            2000000000L to listOf("69279037", "90698825", "38618901"),
            20000000000L to listOf("65353130", "77737706", "47863826"),
        )
        for ((seconds, codes) in vectors) {
            val t = seconds * 1000
            assertEquals(codes[0], Otp.totp(sha1Key, t, 30, OtpAlgorithm.SHA1, 8))
            assertEquals(codes[1], Otp.totp(sha256Key, t, 30, OtpAlgorithm.SHA256, 8))
            assertEquals(codes[2], Otp.totp(sha512Key, t, 30, OtpAlgorithm.SHA512, 8))
        }
    }

    @Test
    fun steamMatchesKnownVectors() {
        // Same HMAC truncation as RFC 4226/6238, re-encoded with Steam's alphabet.
        // Vectors computed from key "12345678901234567890" (RFC test key).
        val vectors = listOf(
            59L to "PV9M4",
            1_111_111_109L to "PY4YB",
            1_234_567_890L to "VHHQY",
            2_000_000_000L to "9N776",
        )
        for ((seconds, code) in vectors) {
            assertEquals(code, Otp.steam(sha1Key, seconds * 1000, 30))
        }
        // Round-trip: the truncated integer for counter 1 is the HOTP input that yields 287082.
        assertEquals(1_094_287_082, Otp.truncated(sha1Key, 1, OtpAlgorithm.SHA1))
        assertEquals("PV9M4", Otp.encodeSteam(1_094_287_082))
    }

    @Test
    fun steamCodesUseTheAlphabetAndLength() {
        val token = OtpToken("Steam", "gamer", Base32.encode(sha1Key), OtpType.STEAM, digits = OtpToken.STEAM_DIGITS)
        val code = token.code(59_000)
        assertEquals(5, code.length)
        assertTrue(code.all { it in OtpToken.STEAM_ALPHABET })
        assertEquals("PV9M4", code)
        assertEquals(1_000, token.remainingMillis(59_000))
    }

    @Test
    fun tokenMakesCodesFromItsBase32Key() {
        val token = OtpToken("Test", "me", Base32.encode(sha1Key), digits = 6)
        assertEquals("287082", token.code(59_000))
        assertEquals(1_000, token.remainingMillis(59_000))
        assertEquals(30_000, token.remainingMillis(60_000))
        val hotp = token.copy(type = OtpType.HOTP, counter = 3)
        assertEquals("969429", hotp.code(0))
    }

    @Test
    fun codesAreGroupedForReading() {
        assertEquals("123 456", formatCode("123456"))
        assertEquals("123 4567", formatCode("1234567"))
        assertEquals("1234 5678", formatCode("12345678"))
        assertEquals("PV9M4", formatCode("PV9M4"))
    }
}
