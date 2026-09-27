package wales.tucker.seren.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatSizeTest {
    @Test
    fun sizesUseTheUnitPeopleExpect() {
        assertEquals("0 bytes", formatSize(0))
        assertEquals("1 byte", formatSize(1))
        assertEquals("812 bytes", formatSize(812))
        assertEquals("22 KB", formatSize(22 * 1024 + 100))
        assertEquals("1.4 MB", formatSize((1.4 * 1024 * 1024).toLong()))
        assertEquals("3.2 GB", formatSize((3.2 * 1024 * 1024 * 1024).toLong()))
        assertEquals("2.0 TB", formatSize(2L * 1024 * 1024 * 1024 * 1024))
    }
}
