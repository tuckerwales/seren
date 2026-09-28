package wales.tucker.seren.ssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollbackSearchTest {
    @Test
    fun findsCaseInsensitive() {
        val lines = listOf("Hello HELLO", "other")
        val first = ScrollbackSearch.findNext(lines, "hello", 0, 0, caseSensitive = false)!!
        assertEquals(0, first.line)
        assertEquals(0, first.start)
        assertEquals(5, first.end)
        val second = ScrollbackSearch.findNext(lines, "hello", 0, first.end, caseSensitive = false)!!
        assertEquals(6, second.start)
    }

    @Test
    fun findsPrevious() {
        val lines = listOf("abc abc", "zzz")
        val match = ScrollbackSearch.findPrevious(lines, "abc", 0, 7, caseSensitive = true)!!
        assertEquals(4, match.start)
        assertNull(ScrollbackSearch.findPrevious(lines, "abc", 0, 0, caseSensitive = true))
    }

    @Test
    fun caseSensitiveMisses() {
        assertNull(ScrollbackSearch.findNext(listOf("Hello"), "hello", 0, 0, caseSensitive = true))
    }

    @Test
    fun crossesLinesForward() {
        val lines = listOf("aaa", "bbb find me", "ccc")
        val match = ScrollbackSearch.findNext(lines, "find", 0, 0, caseSensitive = false)!!
        assertEquals(1, match.line)
        assertEquals(4, match.start)
    }

    @Test
    fun crossesLinesBackward() {
        val lines = listOf("first hit", "middle", "last")
        val match = ScrollbackSearch.findPrevious(lines, "hit", 2, 0, caseSensitive = false)!!
        assertEquals(0, match.line)
        assertEquals(6, match.start)
    }

    @Test
    fun findAllOrdersTopToBottom() {
        val lines = listOf("x a", "a y a", "z")
        val all = ScrollbackSearch.findAll(lines, "a", caseSensitive = true)
        assertEquals(3, all.size)
        assertEquals(listOf(0 to 2, 1 to 0, 1 to 4), all.map { it.line to it.start })
    }

    @Test
    fun emptyQueryYieldsNothing() {
        assertTrue(ScrollbackSearch.findAll(listOf("abc"), "", caseSensitive = false).isEmpty())
        assertNull(ScrollbackSearch.findNext(listOf("abc"), "", 0, 0, caseSensitive = false))
    }
}
