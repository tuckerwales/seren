package wales.tucker.seren.auth.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.Base64

/** Builds transfer codes the way Google Authenticator does, then reads them back. */
class GoogleMigrationTest {
    private class Proto {
        val out = ByteArrayOutputStream()
        fun varint(v: Long) {
            var x = v
            while (x and 0x7F.inv().toLong() != 0L) {
                out.write(((x and 0x7F) or 0x80).toInt())
                x = x ushr 7
            }
            out.write(x.toInt())
        }
        fun int(field: Int, v: Long) = apply { varint((field shl 3).toLong()); varint(v) }
        fun bytes(field: Int, b: ByteArray) = apply { varint(((field shl 3) or 2).toLong()); varint(b.size.toLong()); out.write(b) }
        fun string(field: Int, s: String) = bytes(field, s.toByteArray())
        fun build(): ByteArray = out.toByteArray()
    }

    private fun link(payload: ByteArray) =
        "otpauth-migration://offline?data=" + URLEncoder.encode(Base64.getEncoder().encodeToString(payload), "UTF-8")

    @Test
    fun readsEveryAccountInABatch() {
        val secret = "12345678901234567890".toByteArray()
        val github = Proto().bytes(1, secret).string(2, "GitHub:octocat").string(3, "GitHub").int(4, 1).int(5, 1).int(6, 2).build()
        val server = Proto().bytes(1, secret).string(2, "root@server").int(4, 3).int(5, 2).int(6, 1).int(7, 5).build()
        val md5 = Proto().bytes(1, secret).string(2, "old").int(4, 4).build()
        val payload = Proto().bytes(1, github).bytes(1, server).bytes(1, md5).int(2, 1).int(3, 2).int(4, 1).int(5, 99).build()

        val batch = GoogleMigration.parse(link(payload))
        assertEquals(2, batch.tokens.size)
        assertEquals(1, batch.skipped)
        assertEquals(1, batch.index)
        assertEquals(2, batch.size)
        assertEquals(OtpToken("GitHub", "octocat", Base32.encode(secret)), batch.tokens[0])
        val s = batch.tokens[1]
        assertEquals("root@server", s.name)
        assertEquals(OtpType.HOTP, s.type)
        assertEquals(OtpAlgorithm.SHA512, s.algorithm)
        assertEquals(8, s.digits)
        assertEquals(5, s.counter)
        assertEquals("755224", OtpToken("", "", Base32.encode(secret), OtpType.HOTP).code(0))
    }

    @Test
    fun damagedCodesSaySo() {
        val truncated = Proto().bytes(1, Proto().bytes(1, ByteArray(10)).build()).build().copyOf(6)
        assertEquals(
            "The transfer code is damaged",
            assertThrows(OtpFormatException::class.java) { GoogleMigration.parse(link(truncated)) }.message,
        )
    }
}
