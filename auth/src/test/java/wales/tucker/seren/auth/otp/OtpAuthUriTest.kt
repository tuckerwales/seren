package wales.tucker.seren.auth.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OtpAuthUriTest {
    @Test
    fun parsesTheExampleFromTheKeyUriFormat() {
        val t = OtpAuthUri.parse("otpauth://totp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP&issuer=Example")
        assertEquals(OtpToken("Example", "alice@google.com", "JBSWY3DPEHPK3PXP"), t)
    }

    @Test
    fun parsesALinkWithTheIssuerOnlyInTheQuery() {
        val t = OtpAuthUri.parse(
            "otpauth://totp/alice?issuer=Example&secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&algorithm=SHA1&digits=6&period=30",
        )
        assertEquals(OtpToken("Example", "alice", "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"), t)
    }

    @Test
    fun readsEveryParameter() {
        val t = OtpAuthUri.parse(
            "OTPAUTH://HOTP/ACME%20Co:john.doe%40email.com?secret=hxdmvjecjjwsrb3hwizr4ifugftmxboz" +
                "&algorithm=SHA256&digits=8&counter=42&issuer=ACME+Co",
        )
        assertEquals("ACME Co", t.issuer)
        assertEquals("john.doe@email.com", t.name)
        assertEquals("HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ", t.secret)
        assertEquals(OtpType.HOTP, t.type)
        assertEquals(OtpAlgorithm.SHA256, t.algorithm)
        assertEquals(8, t.digits)
        assertEquals(42, t.counter)
    }

    @Test
    fun takesTheIssuerFromTheLabelWhenTheParameterIsMissing() {
        val t = OtpAuthUri.parse("otpauth://totp/GitHub:octocat?secret=JBSWY3DPEHPK3PXP&period=60")
        assertEquals("GitHub", t.issuer)
        assertEquals("octocat", t.name)
        assertEquals(60, t.period)
        // A plus in the label is a plus; only the query uses it for a space.
        assertEquals("a+b@x.com", OtpAuthUri.parse("otpauth://totp/a+b@x.com?secret=JBSWY3DPEHPK3PXP").name)
    }

    @Test
    fun formatRoundTrips() {
        val tokens = listOf(
            OtpToken("ACME Co", "john doe+1@x.com", "JBSWY3DPEHPK3PXP", OtpType.TOTP, OtpAlgorithm.SHA512, 8, 45),
            OtpToken("", "server", "JBSWY3DPEHPK3PXP", OtpType.HOTP, counter = 7),
            OtpToken("Odd: name", "me", "JBSWY3DPEHPK3PXP"),
        )
        for (t in tokens) assertEquals(t, OtpAuthUri.parse(OtpAuthUri.format(t)))
    }

    @Test
    fun explainsWhatIsWrong() {
        fun message(link: String) = assertThrows(OtpFormatException::class.java) { OtpAuthUri.parse(link) }.message
        assertEquals("The link has no setup key", message("otpauth://totp/x?issuer=y"))
        assertEquals("The setup key in the link isn't valid", message("otpauth://totp/x?secret=189"))
        assertEquals("Seren Auth doesn't support \"steam\" codes", message("otpauth://steam/x?secret=JBSWY3DP"))
        assertEquals("Seren Auth doesn't support the MD5 algorithm", message("otpauth://totp/x?secret=JBSWY3DP&algorithm=MD5"))
        assertEquals("Seren Auth doesn't support 5 digit codes", message("otpauth://totp/x?secret=JBSWY3DP&digits=5"))
        assertEquals("This isn't an otpauth link", message("https://example.com"))
    }
}
