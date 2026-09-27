package wales.tucker.seren.edit.ui.editor

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class TextEditingTest {
    private val text = "fun main() {\n    val x = 1\n}\n"

    @Test
    fun leftAndRightStepOverWholeEmoji() {
        val t = "a🌟b"
        assertEquals(TextRange(3), TextEditing.right(t, TextRange(1)))
        assertEquals(TextRange(1), TextEditing.left(t, TextRange(3)))
        assertEquals(TextRange(0), TextEditing.left(t, TextRange(0)))
        assertEquals(TextRange(4), TextEditing.right(t, TextRange(4)))
    }

    @Test
    fun arrowsCollapseASelection() {
        assertEquals(TextRange(2), TextEditing.left(text, TextRange(5, 2)))
        assertEquals(TextRange(5), TextEditing.right(text, TextRange(2, 5)))
    }

    @Test
    fun upAndDownKeepTheColumnWhereTheyCan() {
        // From "val" (column 4 of line 2) up to column 4 of line 1.
        assertEquals(TextRange(4), TextEditing.up(text, TextRange(17)))
        assertEquals(TextRange(17), TextEditing.down(text, TextRange(4)))
        // Line 3 is "}", so column 4 lands at its end.
        assertEquals(TextRange(28), TextEditing.down(text, TextRange(17)))
        assertEquals(TextRange(0), TextEditing.up(text, TextRange(3)))
        assertEquals(TextRange(text.length), TextEditing.down(text, TextRange(text.length - 1)))
    }

    @Test
    fun homeTogglesBetweenIndentAndLineStart() {
        assertEquals(TextRange(17), TextEditing.home(text, TextRange(22)))
        assertEquals(TextRange(13), TextEditing.home(text, TextRange(17)))
        assertEquals(TextRange(26), TextEditing.end(text, TextRange(17)))
    }

    @Test
    fun indentation() {
        assertEquals("    ", TextEditing.indentOf(text, 20))
        assertEquals("    ", TextEditing.indentUnit(text))
        assertEquals("  ", TextEditing.indentUnit("a:\n  b:\n    c: 1\n  d: 2\n"))
        assertEquals("\t", TextEditing.indentUnit("func f() {\n\treturn\n}\n"))
        assertEquals("    ", TextEditing.indentUnit(""))
    }
}
