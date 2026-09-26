package wales.tucker.terminal.emulator

import java.nio.charset.StandardCharsets

/** Special (non-character) keys the terminal knows how to encode. */
enum class TerminalKey {
    UP, DOWN, LEFT, RIGHT, HOME, END, PAGE_UP, PAGE_DOWN, INSERT, DELETE,
    BACKSPACE, TAB, ENTER, ESCAPE,
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
}

/** Encodes key presses into the byte sequences an xterm-compatible terminal sends. */
object KeyEncoder {
    const val MOD_SHIFT = 1
    const val MOD_ALT = 2
    const val MOD_CTRL = 4

    private const val ESC = "\u001b"

    fun encode(
        key: TerminalKey,
        modifiers: Int,
        appCursorKeys: Boolean,
        newLineMode: Boolean = false,
    ): ByteArray = encodeString(key, modifiers, appCursorKeys, newLineMode).toByteArray(StandardCharsets.UTF_8)

    fun encodeString(
        key: TerminalKey,
        modifiers: Int,
        appCursorKeys: Boolean,
        newLineMode: Boolean = false,
    ): String {
        val modParam = if (modifiers == 0) 0 else 1 + modifiers
        return when (key) {
            TerminalKey.UP -> cursor('A', modParam, appCursorKeys)
            TerminalKey.DOWN -> cursor('B', modParam, appCursorKeys)
            TerminalKey.RIGHT -> cursor('C', modParam, appCursorKeys)
            TerminalKey.LEFT -> cursor('D', modParam, appCursorKeys)
            TerminalKey.HOME -> cursor('H', modParam, appCursorKeys)
            TerminalKey.END -> cursor('F', modParam, appCursorKeys)
            TerminalKey.PAGE_UP -> tilde(5, modParam)
            TerminalKey.PAGE_DOWN -> tilde(6, modParam)
            TerminalKey.INSERT -> tilde(2, modParam)
            TerminalKey.DELETE -> tilde(3, modParam)
            TerminalKey.BACKSPACE -> {
                val base = if (modifiers and MOD_CTRL != 0) "\u0008" else "\u007f"
                if (modifiers and MOD_ALT != 0) ESC + base else base
            }
            TerminalKey.TAB -> when {
                modifiers and MOD_SHIFT != 0 -> "$ESC[Z"
                modifiers and MOD_ALT != 0 -> "$ESC\t"
                else -> "\t"
            }
            TerminalKey.ENTER -> {
                val base = if (newLineMode) "\r\n" else "\r"
                if (modifiers and MOD_ALT != 0) ESC + base else base
            }
            TerminalKey.ESCAPE -> if (modifiers and MOD_ALT != 0) "$ESC$ESC" else ESC
            TerminalKey.F1 -> ss3OrCsi('P', modParam)
            TerminalKey.F2 -> ss3OrCsi('Q', modParam)
            TerminalKey.F3 -> ss3OrCsi('R', modParam)
            TerminalKey.F4 -> ss3OrCsi('S', modParam)
            TerminalKey.F5 -> tilde(15, modParam)
            TerminalKey.F6 -> tilde(17, modParam)
            TerminalKey.F7 -> tilde(18, modParam)
            TerminalKey.F8 -> tilde(19, modParam)
            TerminalKey.F9 -> tilde(20, modParam)
            TerminalKey.F10 -> tilde(21, modParam)
            TerminalKey.F11 -> tilde(23, modParam)
            TerminalKey.F12 -> tilde(24, modParam)
        }
    }

    private fun cursor(final: Char, modParam: Int, app: Boolean): String = when {
        modParam != 0 -> "$ESC[1;$modParam$final"
        app -> "${ESC}O$final"
        else -> "$ESC[$final"
    }

    private fun ss3OrCsi(final: Char, modParam: Int): String =
        if (modParam != 0) "$ESC[1;$modParam$final" else "${ESC}O$final"

    private fun tilde(code: Int, modParam: Int): String =
        if (modParam != 0) "$ESC[$code;$modParam~" else "$ESC[$code~"

    /**
     * Encodes a typed code point with Ctrl/Alt modifiers applied. Returns the bytes to send.
     */
    fun encodeChar(codePoint: Int, modifiers: Int): ByteArray {
        var cp = codePoint
        if (modifiers and MOD_CTRL != 0) {
            val ctrl = controlCode(cp)
            if (ctrl >= 0) cp = ctrl
        }
        val s = String(Character.toChars(cp))
        val out = if (modifiers and MOD_ALT != 0) ESC + s else s
        return out.toByteArray(StandardCharsets.UTF_8)
    }

    /** Maps a character to its control code (Ctrl+key), or -1 if it has none. */
    fun controlCode(cp: Int): Int = when (cp) {
        in 'a'.code..'z'.code -> cp - 'a'.code + 1
        in 'A'.code..'Z'.code -> cp - 'A'.code + 1
        ' '.code, '@'.code, '2'.code -> 0
        '['.code, '3'.code -> 27
        '\\'.code, '4'.code -> 28
        ']'.code, '5'.code -> 29
        '^'.code, '6'.code, '~'.code -> 30
        '_'.code, '7'.code, '/'.code, '-'.code -> 31
        '?'.code, '8'.code -> 127
        else -> -1
    }
}
