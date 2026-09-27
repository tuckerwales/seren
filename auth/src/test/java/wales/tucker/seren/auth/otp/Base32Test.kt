package wales.tucker.seren.auth.otp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Base32Test {
    private val rfc4648 = listOf(
        "f" to "MY", "fo" to "MZXQ", "foo" to "MZXW6", "foob" to "MZXW6YQ", "fooba" to "MZXW6YTB", "foobar" to "MZXW6YTBOI",
    )

    @Test
    fun encodesAndDecodesRfc4648Vectors() {
        for ((plain, encoded) in rfc4648) {
            assertEquals(encoded, Base32.encode(plain.toByteArray()))
            assertArrayEquals(plain.toByteArray(), Base32.decode(encoded))
        }
    }

    @Test
    fun decodingForgivesHowKeysAreCopied() {
        assertArrayEquals("foobar".toByteArray(), Base32.decode("mzxw 6ytb-oi======"))
        assertEquals("JBSWY3DPEHPK3PXP", Base32.normalize("jbsw y3dp ehpk 3pxp"))
    }

    @Test
    fun rejectsWhatIsNotBase32() {
        assertFalse(Base32.isValid(""))
        assertFalse(Base32.isValid("JBSWY3DP1"))
        assertFalse(Base32.isValid("ABC"))
        assertTrue(Base32.isValid("JBSWY3DPEHPK3PXP"))
    }
}
