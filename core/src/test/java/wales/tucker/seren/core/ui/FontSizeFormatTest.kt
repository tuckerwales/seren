package wales.tucker.seren.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FontSizeFormatTest {
    @Test
    fun showsHalfSteps() {
        assertEquals("13", formatFontSize(13f))
        assertEquals("13.5", formatFontSize(13.5f))
        assertEquals("14", formatFontSize(13.99f))
        assertEquals("6", formatFontSize(6f))
    }
}
