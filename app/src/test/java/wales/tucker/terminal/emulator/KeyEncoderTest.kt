package wales.tucker.terminal.emulator

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyEncoderTest {
    private fun enc(key: TerminalKey, mods: Int = 0, app: Boolean = false) =
        KeyEncoder.encodeString(key, mods, app)

    @Test
    fun arrows() {
        assertEquals("\u001b[A", enc(TerminalKey.UP))
        assertEquals("\u001bOA", enc(TerminalKey.UP, app = true))
        assertEquals("\u001b[1;5C", enc(TerminalKey.RIGHT, KeyEncoder.MOD_CTRL))
        assertEquals("\u001b[1;2D", enc(TerminalKey.LEFT, KeyEncoder.MOD_SHIFT))
    }

    @Test
    fun editingKeys() {
        assertEquals("\u001b[3~", enc(TerminalKey.DELETE))
        assertEquals("\u001b[5;3~", enc(TerminalKey.PAGE_UP, KeyEncoder.MOD_ALT))
        assertEquals("\u007f", enc(TerminalKey.BACKSPACE))
        assertEquals("\u001b[Z", enc(TerminalKey.TAB, KeyEncoder.MOD_SHIFT))
        assertEquals("\r", enc(TerminalKey.ENTER))
    }

    @Test
    fun functionKeys() {
        assertEquals("\u001bOP", enc(TerminalKey.F1))
        assertEquals("\u001b[15~", enc(TerminalKey.F5))
        assertEquals("\u001b[24;5~", enc(TerminalKey.F12, KeyEncoder.MOD_CTRL))
    }

    @Test
    fun controlCharacters() {
        assertArrayEquals(byteArrayOf(3), KeyEncoder.encodeChar('c'.code, KeyEncoder.MOD_CTRL))
        assertArrayEquals(byteArrayOf(3), KeyEncoder.encodeChar('C'.code, KeyEncoder.MOD_CTRL))
        assertArrayEquals(byteArrayOf(0), KeyEncoder.encodeChar(' '.code, KeyEncoder.MOD_CTRL))
        assertArrayEquals(byteArrayOf(27, 'x'.code.toByte()), KeyEncoder.encodeChar('x'.code, KeyEncoder.MOD_ALT))
        assertArrayEquals(byteArrayOf(27, 1), KeyEncoder.encodeChar('a'.code, KeyEncoder.MOD_ALT or KeyEncoder.MOD_CTRL))
    }
}
