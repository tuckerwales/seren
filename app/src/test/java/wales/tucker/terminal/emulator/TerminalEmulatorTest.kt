package wales.tucker.terminal.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalEmulatorTest {

    private val written = StringBuilder()
    private var title = ""
    private var bells = 0

    private val client = object : TerminalClient {
        override fun onWrite(data: ByteArray) {
            written.append(String(data, Charsets.UTF_8))
        }

        override fun onTitleChanged(title: String) {
            this@TerminalEmulatorTest.title = title
        }

        override fun onBell() {
            bells++
        }
    }

    private fun emu(cols: Int = 10, rows: Int = 5, scrollback: Int = 100) =
        TerminalEmulator(cols, rows, scrollback, client)

    private fun TerminalEmulator.line(y: Int) = buffer.row(y).getText()

    @Test
    fun printsTextAndMovesCursor() {
        val e = emu()
        e.append("hello")
        assertEquals("hello", e.line(0))
        assertEquals(5, e.cursorX)
        assertEquals(0, e.cursorY)
    }

    @Test
    fun crlfMovesToNextLine() {
        val e = emu()
        e.append("ab\r\ncd")
        assertEquals("ab", e.line(0))
        assertEquals("cd", e.line(1))
        assertEquals(2, e.cursorX)
    }

    @Test
    fun autoWrapAtEndOfLine() {
        val e = emu(cols = 5)
        e.append("abcdefg")
        assertEquals("abcde", e.line(0))
        assertEquals("fg", e.line(1))
        assertTrue(e.buffer.row(0).wrapped)
        assertEquals(2, e.cursorX)
        assertEquals(1, e.cursorY)
    }

    @Test
    fun pendingWrapIsDeferredUntilNextChar() {
        val e = emu(cols = 5)
        e.append("abcde")
        assertEquals(4, e.cursorX)
        assertEquals(0, e.cursorY)
        e.append("\r\n")
        assertEquals(1, e.cursorY)
        assertEquals("", e.line(1))
    }

    @Test
    fun noAutoWrapOverwritesLastColumn() {
        val e = emu(cols = 5)
        e.append("\u001b[?7labcdefg")
        assertEquals("abcdg", e.line(0))
        assertEquals("", e.line(1))
    }

    @Test
    fun scrollsIntoHistory() {
        val e = emu(rows = 3)
        e.append("1\r\n2\r\n3\r\n4\r\n5")
        assertEquals("3", e.line(0))
        assertEquals("5", e.line(2))
        assertEquals(2, e.buffer.historySize)
        assertEquals("2", e.buffer.lineAt(-1).getText())
        assertEquals("1", e.buffer.lineAt(-2).getText())
    }

    @Test
    fun historyIsCapped() {
        val e = emu(rows = 2, scrollback = 3)
        for (i in 1..10) e.append("$i\r\n")
        assertEquals(3, e.buffer.historySize)
        assertEquals("9", e.buffer.lineAt(-1).getText())
    }

    @Test
    fun cursorPositioning() {
        val e = emu()
        e.append("\u001b[3;4HX")
        assertEquals("   X", e.line(2))
        e.append("\u001b[H")
        assertEquals(0, e.cursorX)
        assertEquals(0, e.cursorY)
        e.append("\u001b[99;99H")
        assertEquals(9, e.cursorX)
        assertEquals(4, e.cursorY)
    }

    @Test
    fun relativeCursorMovement() {
        val e = emu()
        e.append("\u001b[3B\u001b[4C")
        assertEquals(4, e.cursorX)
        assertEquals(3, e.cursorY)
        e.append("\u001b[2A\u001b[D")
        assertEquals(3, e.cursorX)
        assertEquals(1, e.cursorY)
        e.append("\u001b[10D")
        assertEquals(0, e.cursorX)
    }

    @Test
    fun eraseInLine() {
        val e = emu()
        e.append("abcdefgh\u001b[4G\u001b[K")
        assertEquals("abc", e.line(0))
        e.append("\r\nabcdefgh\u001b[4G\u001b[1K")
        assertEquals("    efgh", e.line(1))
        e.append("\u001b[2K")
        assertEquals("", e.line(1))
    }

    @Test
    fun eraseInDisplay() {
        val e = emu(rows = 3)
        e.append("aaa\r\nbbb\r\nccc\u001b[2;2H\u001b[J")
        assertEquals("aaa", e.line(0))
        assertEquals("b", e.line(1))
        assertEquals("", e.line(2))
        e.append("\u001b[2J")
        assertEquals("", e.screenText().trim())
    }

    @Test
    fun eraseUsesBackgroundColor() {
        val e = emu()
        e.append("\u001b[41m\u001b[2K")
        assertEquals(1, TextStyle.bg(e.buffer.row(0).styles[5]))
    }

    @Test
    fun sgrColorsAndAttributes() {
        val e = emu()
        e.append("\u001b[1;31;42mA\u001b[0mB\u001b[38;5;200;48;2;1;2;3mC\u001b[38:2::10:20:30mD\u001b[94mE")
        val r = e.buffer.row(0)
        assertEquals(1, TextStyle.fg(r.styles[0]))
        assertEquals(2, TextStyle.bg(r.styles[0]))
        assertTrue(TextStyle.attrs(r.styles[0]) and TextStyle.BOLD != 0)
        assertEquals(TextStyle.DEFAULT, r.styles[1])
        assertEquals(200, TextStyle.fg(r.styles[2]))
        assertEquals(TextStyle.rgb(1, 2, 3), TextStyle.bg(r.styles[2]))
        assertEquals(TextStyle.rgb(10, 20, 30), TextStyle.fg(r.styles[3]))
        assertEquals(12, TextStyle.fg(r.styles[4]))
    }

    @Test
    fun underlineSubparameters() {
        val e = emu()
        e.append("\u001b[4:3mA\u001b[4:0mB\u001b[4:2mC")
        val r = e.buffer.row(0)
        assertTrue(TextStyle.attrs(r.styles[0]) and TextStyle.UNDERLINE != 0)
        assertEquals(0, TextStyle.attrs(r.styles[1]))
        assertTrue(TextStyle.attrs(r.styles[2]) and TextStyle.DOUBLE_UNDERLINE != 0)
    }

    @Test
    fun scrollRegion() {
        val e = emu(rows = 5)
        e.append("1\r\n2\r\n3\r\n4\r\n5")
        e.append("\u001b[2;4r") // region rows 2..4, cursor homes
        assertEquals(0, e.cursorY)
        e.append("\u001b[4;1H\nX")
        assertEquals("1", e.line(0))
        assertEquals("3", e.line(1))
        assertEquals("4", e.line(2))
        assertEquals("X", e.line(3))
        assertEquals("5", e.line(4))
        assertEquals(0, e.buffer.historySize)
    }

    @Test
    fun insertAndDeleteLines() {
        val e = emu(rows = 4)
        e.append("a\r\nb\r\nc\r\nd\u001b[2;1H\u001b[L")
        assertEquals(listOf("a", "", "b", "c"), (0..3).map { e.line(it) })
        e.append("\u001b[2M")
        assertEquals(listOf("a", "c", "", ""), (0..3).map { e.line(it) })
    }

    @Test
    fun insertAndDeleteChars() {
        val e = emu()
        e.append("abcdef\u001b[3G\u001b[2@")
        assertEquals("ab  cdef", e.line(0))
        e.append("\u001b[3P")
        assertEquals("abdef", e.line(0))
        e.append("\u001b[1G\u001b[2X")
        assertEquals("  def", e.line(0))
    }

    @Test
    fun insertMode() {
        val e = emu()
        e.append("abc\u001b[1G\u001b[4hX\u001b[4lY")
        assertEquals("XYbc", e.line(0))
    }

    @Test
    fun reverseIndexScrollsDownAtTop() {
        val e = emu(rows = 3)
        e.append("a\r\nb\r\nc\u001b[H\u001bM")
        assertEquals(listOf("", "a", "b"), (0..2).map { e.line(it) })
    }

    @Test
    fun alternateScreenPreservesMainScreen() {
        val e = emu()
        e.append("main\u001b[?1049h")
        assertTrue(e.isAltScreen)
        assertEquals("", e.line(0))
        e.append("alt")
        e.append("\u001b[?1049l")
        assertFalse(e.isAltScreen)
        assertEquals("main", e.line(0))
        assertEquals(4, e.cursorX)
    }

    @Test
    fun alternateScreenDoesNotAddHistory() {
        val e = emu(rows = 2)
        e.append("\u001b[?1049h1\r\n2\r\n3\r\n4")
        assertEquals(0, e.mainBuffer.historySize)
    }

    @Test
    fun saveAndRestoreCursor() {
        val e = emu()
        e.append("\u001b[2;3H\u001b[31m\u001b7\u001b[H\u001b[0m\u001b8X")
        assertEquals("  X", e.line(1))
        assertEquals(1, TextStyle.fg(e.buffer.row(1).styles[2]))
    }

    @Test
    fun tabs() {
        val e = emu(cols = 20)
        e.append("a\tb\tc")
        assertEquals("a       b       c", e.line(0))
        e.append("\u001b[3g\r\u001b[5C\u001bH\r\tX")
        assertEquals(5, e.cursorX - 1)
    }

    @Test
    fun backspaceMovesLeft() {
        val e = emu()
        e.append("abc\u0008\u0008X")
        assertEquals("aXc", e.line(0))
    }

    @Test
    fun wideCharacters() {
        val e = emu(cols = 6)
        e.append("a中b")
        val r = e.buffer.row(0)
        assertEquals('中'.code, r.text[1])
        assertEquals(TerminalRow.WIDE_TAIL, r.text[2])
        assertEquals("a中b", r.getText())
        assertEquals(4, e.cursorX)
    }

    @Test
    fun wideCharacterWrapsWhenNoRoom() {
        val e = emu(cols = 4)
        e.append("abc中")
        assertEquals("abc", e.line(0))
        assertEquals("中", e.line(1))
    }

    @Test
    fun overwritingHalfOfWideCharClearsIt() {
        val e = emu(cols = 6)
        e.append("中\u001b[2GX")
        assertEquals(" X", e.line(0))
    }

    @Test
    fun emojiAndCombining() {
        val e = emu()
        e.append("éx")
        assertEquals("éx", e.line(0))
        assertEquals(2, e.cursorX)
        e.append("\r\n😀!")
        assertEquals(3, e.cursorX)
    }

    @Test
    fun utf8SplitAcrossAppends() {
        val e = emu()
        val bytes = "é中".toByteArray()
        for (b in bytes) e.append(byteArrayOf(b))
        assertEquals("é中", e.line(0))
    }

    @Test
    fun invalidUtf8ProducesReplacement() {
        val e = emu()
        e.append(byteArrayOf(0xC3.toByte(), 'a'.code.toByte()))
        assertEquals("�a", e.line(0))
    }

    @Test
    fun decSpecialGraphics() {
        val e = emu()
        e.append("\u001b(0lqk\u001b(Bq")
        assertEquals("┌─┐q", e.line(0))
    }

    @Test
    fun oscTitle() {
        val e = emu()
        e.append("\u001b]0;my title\u0007x")
        assertEquals("my title", title)
        e.append("\u001b]2;other\u001b\\")
        assertEquals("other", title)
        assertEquals("x", e.line(0))
    }

    @Test
    fun deviceStatusReports() {
        val e = emu()
        e.append("\u001b[3;5H\u001b[6n")
        assertEquals("\u001b[3;5R", written.toString())
        written.setLength(0)
        e.append("\u001b[c")
        assertTrue(written.startsWith("\u001b[?"))
        written.setLength(0)
        e.append("\u001b[5n")
        assertEquals("\u001b[0n", written.toString())
    }

    @Test
    fun oscColorQuery() {
        val e = emu()
        e.setColorScheme(ColorSchemes.DEFAULT.palette())
        e.append("\u001b]11;?\u0007")
        assertTrue(written.toString().startsWith("\u001b]11;rgb:"))
    }

    @Test
    fun bell() {
        val e = emu()
        e.append("\u0007")
        assertEquals(1, bells)
    }

    @Test
    fun modesAreTracked() {
        val e = emu()
        e.append("\u001b[?1h\u001b[?2004h\u001b[?25l\u001b[?1002h\u001b[?1006h")
        assertTrue(e.applicationCursorKeys)
        assertTrue(e.bracketedPaste)
        assertFalse(e.cursorVisible)
        assertEquals(MouseMode.BUTTON_EVENT, e.mouseMode)
        assertTrue(e.mouseSgr)
        e.append("\u001b[?1l\u001b[?2004l\u001b[?25h\u001b[?1002l")
        assertFalse(e.applicationCursorKeys)
        assertFalse(e.bracketedPaste)
        assertTrue(e.cursorVisible)
        assertEquals(MouseMode.NONE, e.mouseMode)
    }

    @Test
    fun cursorShape() {
        val e = emu()
        e.append("\u001b[6 q")
        assertEquals(CursorShape.BAR, e.cursorShape)
        assertFalse(e.cursorBlink)
        e.append("\u001b[3 q")
        assertEquals(CursorShape.UNDERLINE, e.cursorShape)
        assertTrue(e.cursorBlink)
    }

    @Test
    fun mouseEncoding() {
        val e = emu()
        assertNull(e.encodeMouse(0, 1, 1, true))
        e.append("\u001b[?1000h\u001b[?1006h")
        assertEquals("\u001b[<0;3;2M", String(e.encodeMouse(0, 2, 1, true)!!))
        assertEquals("\u001b[<0;3;2m", String(e.encodeMouse(0, 2, 1, false)!!))
        e.append("\u001b[?1006l")
        val legacy = e.encodeMouse(64, 0, 0, true)!!
        assertEquals(32 + 64, legacy[3].toInt())
    }

    @Test
    fun bracketedPaste() {
        val e = emu()
        assertEquals("a\rb", String(e.encodePaste("a\nb")))
        e.append("\u001b[?2004h")
        assertEquals("\u001b[200~a\rb\u001b[201~", String(e.encodePaste("a\r\nb\u001b")))
    }

    @Test
    fun repeatLastCharacter() {
        val e = emu()
        e.append("a\u001b[3b")
        assertEquals("aaaa", e.line(0))
    }

    @Test
    fun csiWithIntermediateControlChars() {
        val e = emu()
        e.append("ab\u001b[\r2Cx")
        // CR executes inside the sequence, then the CSI finishes.
        assertEquals("abx", e.line(0))
    }

    @Test
    fun unknownSequencesAreIgnored() {
        val e = emu()
        e.append("\u001bP1\$tx\u001b\\a\u001b[?999hb\u001b_apc\u001b\\c")
        assertEquals("abc", e.line(0))
    }

    @Test
    fun fullResetClearsEverything() {
        val e = emu()
        e.append("\u001b[31mtext\u001b[?1049h\u001bc")
        assertFalse(e.isAltScreen)
        assertEquals("", e.screenText().trim())
        e.append("x")
        assertEquals(TextStyle.DEFAULT, e.buffer.row(0).styles[0])
    }

    @Test
    fun originMode() {
        val e = emu(rows = 5)
        e.append("\u001b[2;4r\u001b[?6h\u001b[1;1HX")
        assertEquals("X", e.line(1))
        e.append("\u001b[9;1HY")
        assertEquals("Y", e.line(3))
    }

    @Test
    fun getTextJoinsWrappedLines() {
        val e = emu(cols = 4)
        e.append("abcdef\r\nxy")
        assertEquals("abcdef\nxy", e.getText(0, 0, 2, 3))
    }

    @Test
    fun resizeRowsShrinkKeepsCursorLine() {
        val e = emu(cols = 10, rows = 5)
        e.append("1\r\n2\r\n3\r\n4\r\n5")
        e.resize(10, 3)
        assertEquals(listOf("3", "4", "5"), (0..2).map { e.line(it) })
        assertEquals(2, e.cursorY)
        assertEquals(2, e.buffer.historySize)
        e.resize(10, 5)
        assertEquals(listOf("1", "2", "3", "4", "5"), (0..4).map { e.line(it) })
        assertEquals(4, e.cursorY)
    }

    @Test
    fun resizeRowsShrinkDropsBlankLinesBelowCursor() {
        val e = emu(cols = 10, rows = 5)
        e.append("prompt")
        e.resize(10, 2)
        assertEquals("prompt", e.line(0))
        assertEquals(0, e.cursorY)
        assertEquals(0, e.buffer.historySize)
    }

    @Test
    fun reflowOnWidthChange() {
        val e = emu(cols = 10, rows = 4)
        e.append("0123456789abcde\r\nxyz")
        assertEquals("0123456789", e.line(0))
        assertEquals("abcde", e.line(1))
        e.resize(20, 4)
        assertEquals("0123456789abcde", e.line(0))
        assertEquals("xyz", e.line(1))
        assertEquals(1, e.cursorY)
        assertEquals(3, e.cursorX)
        e.resize(5, 4)
        assertEquals("01234", e.line(0))
        assertEquals("56789", e.line(1))
        assertEquals("abcde", e.line(2))
        assertEquals("xyz", e.line(3))
        assertEquals(3, e.cursorY)
        assertEquals(3, e.cursorX)
    }

    @Test
    fun reflowPushesIntoHistory() {
        val e = emu(cols = 6, rows = 2)
        e.append("aaaaaabbbbbb\r\nc")
        e.resize(3, 2)
        assertEquals("bbb", e.line(0))
        assertEquals("c", e.line(1))
        assertEquals(3, e.buffer.historySize)
    }

    @Test
    fun resizeInAltScreen() {
        val e = emu(cols = 10, rows = 5)
        e.append("main\u001b[?1049h\u001b[Halt")
        e.resize(6, 3)
        assertTrue(e.isAltScreen)
        assertEquals("alt", e.line(0))
        e.append("\u001b[?1049l")
        assertEquals("main", e.line(0))
    }

    @Test
    fun scrollUpAndDownCommands() {
        val e = emu(rows = 3)
        e.append("a\r\nb\r\nc\u001b[S")
        assertEquals(listOf("b", "c", ""), (0..2).map { e.line(it) })
        e.append("\u001b[2T")
        assertEquals(listOf("", "", "b"), (0..2).map { e.line(it) })
    }

    @Test
    fun windowSizeReport() {
        val e = emu(cols = 80, rows = 24)
        e.append("\u001b[18t")
        assertEquals("\u001b[8;24;80t", written.toString())
    }
}
