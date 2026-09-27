package wales.tucker.seren.auth.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import wales.tucker.seren.auth.otp.OtpAlgorithm
import wales.tucker.seren.auth.otp.OtpToken

class AccountFormTest {
    @Test
    fun aPastedOtpauthLinkFillsInEveryField() {
        val form = AccountForm(issuer = "typed", color = 3).withSecret(
            "otpauth://totp/alice?issuer=Example&secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&algorithm=SHA256&digits=8&period=60",
        )
        assertEquals("Example", form.issuer)
        assertEquals("alice", form.name)
        assertEquals("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP", form.secret)
        assertEquals(OtpAlgorithm.SHA256, form.algorithm)
        assertEquals(8, form.digits)
        assertEquals("60", form.period)
        // A color the user already picked stays.
        assertEquals(3, form.color)
        assertNull(form.secretError)
    }

    @Test
    fun aPlainKeyChangesOnlyTheKey() {
        val form = AccountForm(issuer = "GitHub", name = "octocat").withSecret("jbsw y3dp")
        assertEquals(AccountForm(issuer = "GitHub", name = "octocat", secret = "jbsw y3dp"), form)
    }

    @Test
    fun aLinkThatCantBeReadIsExplained() {
        val form = AccountForm().withSecret("otpauth://totp/x?secret=JBSWY3DP&digits=5")
        assertEquals("otpauth://totp/x?secret=JBSWY3DP&digits=5", form.secret)
        assertEquals("Couldn't read the link. Seren Auth doesn't support 5 digit codes", form.secretError)
        assertNull(form.token())
        assertEquals(OtpToken.DEFAULT_DIGITS, form.digits)
    }
}
