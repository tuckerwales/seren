package wales.tucker.seren.auth.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URLEncoder
import java.util.Base64

class CopiedSetupTest {
    private val link = "otpauth://totp/GitHub:octocat?secret=JBSWY3DPEHPK3PXP&issuer=GitHub"
    private val github = OtpToken("GitHub", "octocat", "JBSWY3DPEHPK3PXP")

    @Test
    fun findsALinkOnItsOwnOrInsideText() {
        assertEquals(CopiedSetup.Link(link, github), CopiedSetup.find(link))
        assertEquals(CopiedSetup.Link(link, github), CopiedSetup.find("  $link\n"))
        assertEquals(CopiedSetup.Link(link, github), CopiedSetup.find("Your setup link is $link."))
        assertEquals(CopiedSetup.Link(link, github), CopiedSetup.find("Can't scan it? Use this link ($link)"))
        assertEquals(CopiedSetup.Link(link, github), CopiedSetup.find("<a href=\"$link\">Add to your app</a>"))
    }

    @Test
    fun skipsLinksItCantAdd() {
        assertNull(CopiedSetup.find("otpauth://totp/GitHub?issuer=GitHub"))
        assertNull(CopiedSetup.find("otpauth://motp/GitHub?secret=JBSWY3DPEHPK3PXP"))
        // A broken link doesn't hide a good one after it.
        assertEquals(CopiedSetup.Link(link, github), CopiedSetup.find("otpauth://totp/Old?secret=1 or $link"))
    }

    @Test
    fun findsATransferWithAccountsInIt() {
        val secret = "12345678901234567890".toByteArray()
        val params = byteArrayOf(0x0A, secret.size.toByte()) + secret + byteArrayOf(0x12, 3) + "Box".toByteArray()
        val payload = byteArrayOf(0x0A, params.size.toByte()) + params
        val transfer = "otpauth-migration://offline?data=" + URLEncoder.encode(Base64.getEncoder().encodeToString(payload), "UTF-8")

        val found = CopiedSetup.find("Transfer: $transfer") as CopiedSetup.Transfer
        assertEquals(transfer, found.link)
        assertEquals(listOf("Box"), found.tokens.map { it.name })
        assertNull(CopiedSetup.find("otpauth-migration://offline?data="))
    }

    @Test
    fun findsASetupKeyWhenItsTheWholeClip() {
        assertEquals(CopiedSetup.Key("JBSWY3DPEHPK3PXP"), CopiedSetup.find("JBSWY3DPEHPK3PXP"))
        assertEquals(CopiedSetup.Key("JBSWY3DPEHPK3PXP"), CopiedSetup.find(" jbsw y3dp ehpk 3pxp "))
        assertEquals(CopiedSetup.Key("JBSWY3DPEHPK3PXP"), CopiedSetup.find("JBSW-Y3DP-EHPK-3PXP"))
        assertEquals(CopiedSetup.Key("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"), CopiedSetup.find("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"))
    }

    @Test
    fun ordinaryTextIsntASetupKey() {
        listOf(
            "",
            "123456",
            "123 456",
            "JBSWY3DPEHPK", // too short
            "Your key is JBSWY3DPEHPK3PXP",
            "supercalifragilistic", // letters only
            "2345672345672345", // digits only
            "d41d8cd98f00b204e9800998ecf8427e", // hex has 0, 1, 8 and 9
            "https://example.com/JBSWY3DPEHPK3PXP",
        ).forEach { assertNull(it, CopiedSetup.find(it)) }
    }

    @Test
    fun hugeClipsArentSearched() {
        assertNull(CopiedSetup.find("x".repeat(CopiedSetup.MAX_LENGTH) + link))
    }
}
