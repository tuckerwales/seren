package wales.tucker.seren.ssh.ui.hosts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import wales.tucker.seren.ssh.data.AuthType

class HostFormForwardAgentTest {

    @Test
    fun forwardAgentDefaultsOff() {
        assertFalse(HostForm().forwardAgent)
    }

    @Test
    fun validFormKeepsForwardAgentFlag() {
        val form = HostForm(
            hostname = "example.com",
            username = "deploy",
            authType = AuthType.PASSWORD,
            forwardAgent = true,
        )
        assertTrue(form.isValid)
        assertTrue(form.forwardAgent)
    }

    @Test
    fun invalidHostnameStillCarriesForwardAgent() {
        val form = HostForm(hostname = "", username = "u", forwardAgent = true)
        assertFalse(form.isValid)
        assertEquals(true, form.forwardAgent)
    }
}
