package wales.tucker.seren.ssh.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelativeTimeTest {
    private val now = 1_790_000_000_000L

    @Test
    fun startOfSentence() {
        assertEquals("Never", relativeTime(0, now))
        assertEquals("Just now", relativeTime(now - 5_000, now))
        assertEquals("5 min ago", relativeTime(now - 5 * 60_000, now))
    }

    @Test
    fun midSentenceKeepsDatesCapitalised() {
        assertEquals("never", relativeTime(0, now, midSentence = true))
        assertEquals("just now", relativeTime(now - 5_000, now, midSentence = true))
        assertEquals("3 h ago", relativeTime(now - 3 * 3_600_000, now, midSentence = true))
        val date = relativeTime(now - 30L * 86_400_000, now, midSentence = true)
        assertTrue(date, date.first().isUpperCase() || date.first().isDigit())
    }
}
