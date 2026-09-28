package wales.tucker.seren.edit.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FindReplaceTest {
    @Test
    fun findsCaseInsensitive() {
        val range = FindReplace.findNext("Hello HELLO", "hello", 0, caseSensitive = false)!!
        assertEquals(0, range.min)
        assertEquals(5, range.max)
        val next = FindReplace.findNext("Hello HELLO", "hello", range.max, caseSensitive = false)!!
        assertEquals(6, next.min)
    }

    @Test
    fun findsPrevious() {
        val range = FindReplace.findPrevious("abc abc", "abc", 7, caseSensitive = true)!!
        assertEquals(4, range.min)
        assertNull(FindReplace.findPrevious("abc", "abc", 0, caseSensitive = true))
    }

    @Test
    fun caseSensitiveMisses() {
        assertNull(FindReplace.findNext("Hello", "hello", 0, caseSensitive = true))
    }
}
