package wales.tucker.seren.ssh.session

import org.junit.Assert.assertEquals
import org.junit.Test

class SnippetInputTest {
    @Test
    fun lineBreaksBecomeEnter() {
        assertEquals("cd /tmp\rls\r", snippetInput("cd /tmp\nls", autoRun = true))
        assertEquals("cd /tmp\rls\r", snippetInput("cd /tmp\r\nls\n", autoRun = true))
        assertEquals("df -h", snippetInput("df -h", autoRun = false))
        assertEquals("a\rb", snippetInput("a\nb\n\n", autoRun = false))
    }
}
