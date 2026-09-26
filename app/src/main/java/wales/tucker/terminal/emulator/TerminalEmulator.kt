package wales.tucker.terminal.emulator

import java.nio.charset.StandardCharsets

/** Callbacks from the emulator to its host. Invoked on the thread that feeds input. */
interface TerminalClient {
    /** Bytes the terminal wants to send back to the remote side (replies to queries). */
    fun onWrite(data: ByteArray)
    fun onTitleChanged(title: String) {}
    fun onBell() {}
    fun onClipboardSet(text: String) {}
}

enum class CursorShape { BLOCK, UNDERLINE, BAR }

enum class MouseMode { NONE, X10, NORMAL, BUTTON_EVENT, ANY_EVENT }

/**
 * An xterm-compatible terminal emulator: parses a byte stream (UTF-8 text and control sequences)
 * and maintains the screen state. Not thread safe; callers synchronize on the instance.
 */
class TerminalEmulator(
    cols: Int,
    rows: Int,
    scrollback: Int = 5000,
    private val client: TerminalClient,
) {
    val mainBuffer = TerminalBuffer(cols, rows, scrollback)
    private val altBuffer = TerminalBuffer(cols, rows, 0)

    var buffer: TerminalBuffer = mainBuffer
        private set
    val isAltScreen: Boolean get() = buffer === altBuffer

    val cols: Int get() = buffer.cols
    val rows: Int get() = buffer.rows

    var cursorX = 0
        private set
    var cursorY = 0
        private set
    private var aboutToWrap = false

    // Current graphic rendition.
    private var fg = TextStyle.COLOR_DEFAULT_FG
    private var bg = TextStyle.COLOR_DEFAULT_BG
    private var attrs = 0
    private val currentStyle: Long get() = TextStyle.encode(fg, bg, attrs)
    private val blankStyle: Long get() = TextStyle.encode(TextStyle.COLOR_DEFAULT_FG, bg, 0)

    // Scroll region, top inclusive and bottom exclusive.
    private var scrollTop = 0
    private var scrollBottom = rows

    private var tabStops = BooleanArray(cols)

    // Modes.
    var applicationCursorKeys = false
        private set
    var applicationKeypad = false
        private set
    private var autoWrap = true
    private var originMode = false
    private var insertMode = false
    var newLineMode = false
        private set
    var cursorVisible = true
        private set
    var reverseVideo = false
        private set
    var bracketedPaste = false
        private set
    var mouseMode = MouseMode.NONE
        private set
    var mouseSgr = false
        private set
    var focusEvents = false
        private set
    var cursorShape = CursorShape.BLOCK
        private set
    var cursorBlink = true
        private set

    var title: String = ""
        private set

    /** Colors: 0..255 palette, then default fg, default bg and cursor. ARGB ints. */
    val palette = IntArray(TextStyle.PALETTE_SIZE)
    private var schemePalette = IntArray(TextStyle.PALETTE_SIZE)

    // Character sets.
    private val charsets = charArrayOf('B', 'B', 'B', 'B')
    private var activeCharset = 0

    private class SavedState {
        var x = 0
        var y = 0
        var fg = TextStyle.COLOR_DEFAULT_FG
        var bg = TextStyle.COLOR_DEFAULT_BG
        var attrs = 0
        var autoWrap = true
        var originMode = false
        var aboutToWrap = false
        val charsets = charArrayOf('B', 'B', 'B', 'B')
        var activeCharset = 0
    }

    private val savedMain = SavedState()
    private val savedAlt = SavedState()

    // Parser state.
    private var state = State.GROUND
    private val params = IntArray(MAX_PARAMS)
    private val subParam = BooleanArray(MAX_PARAMS)
    private var paramCount = 0
    private var paramHasDigits = false
    private var csiPrefix = 0.toChar()
    private var intermediate = StringBuilder()
    private val oscBuffer = StringBuilder()
    private var lastPrinted = -1

    // UTF-8 decoder state.
    private var utf8Needed = 0
    private var utf8Codepoint = 0
    private var utf8Min = 0

    private enum class State { GROUND, ESCAPE, ESCAPE_INTERMEDIATE, CSI, CSI_IGNORE, OSC, OSC_ESC, IGNORE_STRING, IGNORE_STRING_ESC }

    init {
        resetTabStops()
        setColorScheme(ColorSchemes.DEFAULT.palette())
    }

    fun setColorScheme(colors: IntArray) {
        require(colors.size == TextStyle.PALETTE_SIZE)
        schemePalette = colors.copyOf()
        System.arraycopy(colors, 0, palette, 0, colors.size)
    }

    fun setScrollback(lines: Int) {
        mainBuffer.maxHistory = lines
    }

    // region Input

    fun append(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        for (i in offset until offset + length) {
            val b = data[i].toInt() and 0xFF
            decodeByte(b)
        }
    }

    fun append(text: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        append(bytes, 0, bytes.size)
    }

    private fun decodeByte(b: Int) {
        if (utf8Needed == 0) {
            when {
                b < 0x80 -> process(b)
                b and 0xE0 == 0xC0 -> {
                    utf8Needed = 1; utf8Codepoint = b and 0x1F; utf8Min = 0x80
                }
                b and 0xF0 == 0xE0 -> {
                    utf8Needed = 2; utf8Codepoint = b and 0x0F; utf8Min = 0x800
                }
                b and 0xF8 == 0xF0 -> {
                    utf8Needed = 3; utf8Codepoint = b and 0x07; utf8Min = 0x10000
                }
                else -> process(REPLACEMENT)
            }
        } else {
            if (b and 0xC0 != 0x80) {
                // Invalid continuation: emit replacement and reprocess this byte.
                utf8Needed = 0
                process(REPLACEMENT)
                decodeByte(b)
                return
            }
            utf8Codepoint = (utf8Codepoint shl 6) or (b and 0x3F)
            utf8Needed--
            if (utf8Needed == 0) {
                val cp = utf8Codepoint
                if (cp < utf8Min || cp > 0x10FFFF || cp in 0xD800..0xDFFF) process(REPLACEMENT) else process(cp)
            }
        }
    }

    private fun process(cp: Int) {
        // C0 controls are executed in most states without interrupting the sequence.
        if (cp < 0x20 && state != State.OSC && state != State.IGNORE_STRING) {
            when (cp) {
                ESC -> {
                    if (state == State.OSC_ESC || state == State.IGNORE_STRING_ESC) {
                        // Stay; ESC ESC is odd but harmless.
                        return
                    }
                    enterEscape()
                }
                CAN, SUB -> state = State.GROUND
                else -> executeControl(cp)
            }
            return
        }
        when (state) {
            State.GROUND -> if (cp != DEL) printCodepoint(cp)
            State.ESCAPE -> escape(cp)
            State.ESCAPE_INTERMEDIATE -> escapeIntermediate(cp)
            State.CSI -> csi(cp)
            State.CSI_IGNORE -> if (cp in 0x40..0x7E) state = State.GROUND
            State.OSC -> when (cp) {
                BEL -> {
                    dispatchOsc(); state = State.GROUND
                }
                ESC -> state = State.OSC_ESC
                CAN, SUB -> state = State.GROUND
                else -> if (cp >= 0x20 && oscBuffer.length < MAX_OSC) oscBuffer.appendCodePoint(cp)
            }
            State.OSC_ESC -> {
                dispatchOsc()
                state = State.GROUND
                if (cp != '\\'.code) {
                    enterEscape()
                    escape(cp)
                }
            }
            State.IGNORE_STRING -> when (cp) {
                ESC -> state = State.IGNORE_STRING_ESC
                BEL, CAN, SUB -> state = State.GROUND
            }
            State.IGNORE_STRING_ESC -> {
                state = State.GROUND
                if (cp != '\\'.code) {
                    enterEscape()
                    escape(cp)
                }
            }
        }
    }

    private fun enterEscape() {
        state = State.ESCAPE
        intermediate.setLength(0)
    }

    // endregion

    // region Controls

    private fun executeControl(cp: Int) {
        when (cp) {
            BEL -> client.onBell()
            BS -> {
                cursorX = maxOf(0, minOf(cursorX, cols - 1) - 1)
                aboutToWrap = false
            }
            HT -> {
                cursorX = nextTabStop(1)
                aboutToWrap = false
            }
            LF, VT, FF -> {
                lineFeed()
                if (newLineMode) cursorX = 0
            }
            CR -> {
                cursorX = 0
                aboutToWrap = false
            }
            SO -> activeCharset = 1
            SI -> activeCharset = 0
        }
    }

    private fun lineFeed() {
        aboutToWrap = false
        if (cursorY == scrollBottom - 1) {
            scrollUp(1)
        } else if (cursorY < rows - 1) {
            cursorY++
        }
    }

    private fun reverseIndex() {
        aboutToWrap = false
        if (cursorY == scrollTop) {
            buffer.scrollDown(scrollTop, scrollBottom, 1, blankStyle)
        } else if (cursorY > 0) {
            cursorY--
        }
    }

    private fun scrollUp(n: Int) {
        val keep = !isAltScreen && scrollTop == 0
        buffer.scrollUp(scrollTop, scrollBottom, n, blankStyle, keep)
    }

    private fun nextTabStop(count: Int): Int {
        var x = minOf(cursorX, cols - 1)
        var remaining = count
        while (remaining > 0 && x < cols - 1) {
            x++
            if (tabStops[x]) remaining--
        }
        return x
    }

    private fun previousTabStop(count: Int): Int {
        var x = minOf(cursorX, cols - 1)
        var remaining = count
        while (remaining > 0 && x > 0) {
            x--
            if (tabStops[x]) remaining--
        }
        return x
    }

    private fun resetTabStops() {
        tabStops = BooleanArray(cols) { it != 0 && it % 8 == 0 }
    }

    // endregion

    // region Printing

    private fun mapCharset(cp: Int): Int {
        val set = charsets[activeCharset]
        if (set == '0' && cp in 0x5F..0x7E) return DEC_GRAPHICS[cp - 0x5F].code
        if (set == 'A' && cp == '#'.code) return '£'.code
        return cp
    }

    private fun printCodepoint(rawCp: Int) {
        val cp = mapCharset(rawCp)
        val width = WcWidth.width(cp)
        val row: TerminalRow
        if (width == 0) {
            // Combining character: attach to the previous cell.
            val col = if (aboutToWrap) cursorX else cursorX - 1
            if (col >= 0) buffer.row(cursorY).addCombining(col, cp)
            return
        }
        if (aboutToWrap && autoWrap) {
            buffer.row(cursorY).wrapped = true
            lineFeed()
            cursorX = 0
        }
        aboutToWrap = false
        if (width == 2 && cursorX >= cols - 1) {
            if (cols < 2) return
            if (autoWrap) {
                buffer.row(cursorY).clear(cursorX, cols, blankStyle)
                buffer.row(cursorY).wrapped = true
                lineFeed()
                cursorX = 0
            } else {
                cursorX = cols - 2
            }
        }
        row = buffer.row(cursorY)
        if (insertMode) row.insertCells(cursorX, width, blankStyle)
        if (width == 2) row.setWideCell(cursorX, cp, currentStyle) else row.setCell(cursorX, cp, currentStyle)
        lastPrinted = cp
        if (cursorX + width >= cols) {
            cursorX = cols - 1
            aboutToWrap = autoWrap
        } else {
            cursorX += width
        }
    }

    // endregion

    // region Escape sequences

    private fun escape(cp: Int) {
        state = State.GROUND
        when (cp.toChar()) {
            '[' -> {
                state = State.CSI
                paramCount = 0
                paramHasDigits = false
                params.fill(-1)
                subParam.fill(false)
                csiPrefix = 0.toChar()
                intermediate.setLength(0)
            }
            ']' -> {
                state = State.OSC
                oscBuffer.setLength(0)
            }
            'P', 'X', '^', '_' -> state = State.IGNORE_STRING
            '7' -> saveCursor()
            '8' -> restoreCursor()
            'D' -> lineFeed()
            'E' -> {
                lineFeed(); cursorX = 0
            }
            'H' -> if (cursorX < cols) tabStops[cursorX] = true
            'M' -> reverseIndex()
            'c' -> reset()
            '=' -> applicationKeypad = true
            '>' -> applicationKeypad = false
            '\\' -> Unit
            in ' '..'/' -> {
                intermediate.setLength(0)
                intermediate.append(cp.toChar())
                state = State.ESCAPE_INTERMEDIATE
            }
            else -> Unit
        }
    }

    private fun escapeIntermediate(cp: Int) {
        if (cp in 0x20..0x2F) {
            intermediate.append(cp.toChar())
            return
        }
        state = State.GROUND
        val inter = intermediate.firstOrNull() ?: return
        val c = cp.toChar()
        when (inter) {
            '(' -> charsets[0] = c
            ')', '-' -> charsets[1] = c
            '*', '.' -> charsets[2] = c
            '+', '/' -> charsets[3] = c
            '#' -> if (c == '8') decAlignmentTest()
        }
    }

    private fun decAlignmentTest() {
        for (y in 0 until rows) {
            val r = buffer.row(y)
            for (x in 0 until cols) r.setCell(x, 'E'.code, TextStyle.DEFAULT)
            r.wrapped = false
        }
        scrollTop = 0
        scrollBottom = rows
        setCursor(0, 0)
    }

    // endregion

    // region CSI

    private fun csi(cp: Int) {
        val c = cp.toChar()
        when {
            c in '0'..'9' -> {
                if (paramCount == 0) paramCount = 1
                val idx = paramCount - 1
                if (idx < MAX_PARAMS) {
                    val cur = params[idx]
                    val v = (if (cur < 0) 0 else cur) * 10 + (c - '0')
                    params[idx] = minOf(v, 99999)
                }
                paramHasDigits = true
            }
            c == ';' || c == ':' -> {
                if (paramCount == 0) paramCount = 1
                if (paramCount < MAX_PARAMS) {
                    subParam[paramCount] = c == ':'
                    paramCount++
                }
            }
            c in '<'..'?' -> {
                if (paramCount == 0 && csiPrefix == 0.toChar()) csiPrefix = c else state = State.CSI_IGNORE
            }
            c in ' '..'/' -> intermediate.append(c)
            cp in 0x40..0x7E -> {
                state = State.GROUND
                dispatchCsi(c)
            }
            else -> state = State.CSI_IGNORE
        }
    }

    /** Parameter [i], or [def] if absent or zero. */
    private fun arg(i: Int, def: Int = 1): Int {
        if (i >= paramCount) return def
        val v = params[i]
        return if (v <= 0) def else v
    }

    /** Parameter [i], or [def] if absent (zero is kept). */
    private fun argRaw(i: Int, def: Int = 0): Int {
        if (i >= paramCount) return def
        val v = params[i]
        return if (v < 0) def else v
    }

    private fun dispatchCsi(c: Char) {
        val inter = if (intermediate.isEmpty()) 0.toChar() else intermediate[0]
        if (csiPrefix == '?') {
            dispatchPrivateCsi(c, inter)
            return
        }
        if (csiPrefix == '>') {
            when (c) {
                'c' -> if (inter == 0.toChar()) write("\u001b[>1;10;0c")
                'q' -> write("\u001bP>|Terminal\u001b\\")
            }
            return
        }
        if (csiPrefix != 0.toChar()) return

        if (inter == ' ' && c == 'q') {
            when (argRaw(0, 0)) {
                0, 1 -> { cursorShape = CursorShape.BLOCK; cursorBlink = true }
                2 -> { cursorShape = CursorShape.BLOCK; cursorBlink = false }
                3 -> { cursorShape = CursorShape.UNDERLINE; cursorBlink = true }
                4 -> { cursorShape = CursorShape.UNDERLINE; cursorBlink = false }
                5 -> { cursorShape = CursorShape.BAR; cursorBlink = true }
                6 -> { cursorShape = CursorShape.BAR; cursorBlink = false }
            }
            return
        }
        if (inter == '!' && c == 'p') {
            softReset()
            return
        }
        if (inter == '$' && c == 'p') {
            // DECRQM for ANSI modes.
            val mode = argRaw(0, 0)
            val value = when (mode) {
                4 -> if (insertMode) 1 else 2
                20 -> if (newLineMode) 1 else 2
                else -> 0
            }
            write("\u001b[$mode;$value\$y")
            return
        }
        if (inter != 0.toChar()) return

        when (c) {
            '@' -> buffer.row(cursorY).insertCells(minOf(cursorX, cols - 1), arg(0), blankStyle).also { aboutToWrap = false }
            'A' -> moveCursorVertically(-arg(0))
            'B', 'e' -> moveCursorVertically(arg(0))
            'C', 'a' -> {
                cursorX = minOf(cols - 1, cursorX + arg(0)); aboutToWrap = false
            }
            'D' -> {
                cursorX = maxOf(0, minOf(cursorX, cols - 1) - arg(0)); aboutToWrap = false
            }
            'E' -> {
                moveCursorVertically(arg(0)); cursorX = 0
            }
            'F' -> {
                moveCursorVertically(-arg(0)); cursorX = 0
            }
            'G', '`' -> {
                cursorX = (arg(0) - 1).coerceIn(0, cols - 1); aboutToWrap = false
            }
            'H', 'f' -> setCursorOrigin(arg(1) - 1, arg(0) - 1)
            'I' -> {
                cursorX = nextTabStop(arg(0)); aboutToWrap = false
            }
            'J' -> eraseInDisplay(argRaw(0))
            'K' -> eraseInLine(argRaw(0))
            'L' -> if (cursorY in scrollTop until scrollBottom) {
                buffer.scrollDown(cursorY, scrollBottom, arg(0), blankStyle)
                cursorX = 0; aboutToWrap = false
            }
            'M' -> if (cursorY in scrollTop until scrollBottom) {
                buffer.scrollUp(cursorY, scrollBottom, arg(0), blankStyle, false)
                cursorX = 0; aboutToWrap = false
            }
            'P' -> {
                buffer.row(cursorY).deleteCells(minOf(cursorX, cols - 1), arg(0), blankStyle); aboutToWrap = false
            }
            'S' -> buffer.scrollUp(scrollTop, scrollBottom, arg(0), blankStyle, false)
            'T' -> if (paramCount <= 1) buffer.scrollDown(scrollTop, scrollBottom, arg(0), blankStyle)
            'X' -> {
                val x = minOf(cursorX, cols - 1)
                buffer.row(cursorY).clear(x, x + arg(0), blankStyle); aboutToWrap = false
            }
            'Z' -> {
                cursorX = previousTabStop(arg(0)); aboutToWrap = false
            }
            'b' -> if (lastPrinted >= 0) {
                val n = minOf(arg(0), cols * rows)
                repeat(n) { printCodepoint(lastPrinted) }
            }
            'c' -> if (argRaw(0) == 0) write("\u001b[?62;22c")
            'd' -> setCursorOrigin(cursorX, arg(0) - 1)
            'g' -> when (argRaw(0)) {
                0 -> if (cursorX < cols) tabStops[cursorX] = false
                3 -> tabStops.fill(false)
            }
            'h' -> for (i in 0 until maxOf(paramCount, 1)) setAnsiMode(argRaw(i), true)
            'l' -> for (i in 0 until maxOf(paramCount, 1)) setAnsiMode(argRaw(i), false)
            'm' -> selectGraphicRendition()
            'n' -> when (argRaw(0)) {
                5 -> write("\u001b[0n")
                6 -> {
                    val y = if (originMode) cursorY - scrollTop else cursorY
                    write("\u001b[${y + 1};${minOf(cursorX, cols - 1) + 1}R")
                }
            }
            'r' -> {
                val top = arg(0) - 1
                val bottom = arg(1, rows).coerceAtMost(rows)
                if (top < bottom - 1 || (top == 0 && bottom == rows)) {
                    scrollTop = top.coerceIn(0, rows - 1)
                    scrollBottom = bottom
                    setCursorOrigin(0, 0)
                }
            }
            's' -> saveCursor()
            'u' -> restoreCursor()
            't' -> when (argRaw(0)) {
                18 -> write("\u001b[8;$rows;${cols}t")
            }
        }
    }

    private fun dispatchPrivateCsi(c: Char, inter: Char) {
        if (inter == '$' && c == 'p') {
            val mode = argRaw(0, 0)
            val value = when (val v = privateModeValue(mode)) {
                null -> 0
                true -> 1
                false -> 2
            }
            write("\u001b[?$mode;$value\$y")
            return
        }
        if (inter != 0.toChar()) return
        when (c) {
            'h' -> for (i in 0 until maxOf(paramCount, 1)) setPrivateMode(argRaw(i), true)
            'l' -> for (i in 0 until maxOf(paramCount, 1)) setPrivateMode(argRaw(i), false)
            'J' -> eraseInDisplay(argRaw(0))
            'K' -> eraseInLine(argRaw(0))
            'n' -> if (argRaw(0) == 6) {
                val y = if (originMode) cursorY - scrollTop else cursorY
                write("\u001b[?${y + 1};${minOf(cursorX, cols - 1) + 1}R")
            }
        }
    }

    private fun privateModeValue(mode: Int): Boolean? = when (mode) {
        1 -> applicationCursorKeys
        5 -> reverseVideo
        6 -> originMode
        7 -> autoWrap
        12 -> cursorBlink
        25 -> cursorVisible
        47, 1047, 1049 -> isAltScreen
        1000 -> mouseMode == MouseMode.NORMAL
        1002 -> mouseMode == MouseMode.BUTTON_EVENT
        1003 -> mouseMode == MouseMode.ANY_EVENT
        1004 -> focusEvents
        1006 -> mouseSgr
        2004 -> bracketedPaste
        else -> null
    }

    private fun setAnsiMode(mode: Int, on: Boolean) {
        when (mode) {
            4 -> insertMode = on
            20 -> newLineMode = on
        }
    }

    private fun setPrivateMode(mode: Int, on: Boolean) {
        when (mode) {
            1 -> applicationCursorKeys = on
            3 -> {
                // DECCOLM: we cannot change width, but xterm clears the screen and homes.
                eraseInDisplay(2)
                scrollTop = 0; scrollBottom = rows
                setCursor(0, 0)
            }
            5 -> reverseVideo = on
            6 -> {
                originMode = on
                setCursorOrigin(0, 0)
            }
            7 -> {
                autoWrap = on
                if (!on) aboutToWrap = false
            }
            9 -> mouseMode = if (on) MouseMode.X10 else MouseMode.NONE
            12 -> cursorBlink = on
            25 -> cursorVisible = on
            47 -> switchScreen(on, clear = false, saveRestore = false)
            1047 -> switchScreen(on, clear = true, saveRestore = false)
            1048 -> if (on) saveCursor() else restoreCursor()
            1049 -> switchScreen(on, clear = true, saveRestore = true)
            1000 -> mouseMode = if (on) MouseMode.NORMAL else MouseMode.NONE
            1002 -> mouseMode = if (on) MouseMode.BUTTON_EVENT else MouseMode.NONE
            1003 -> mouseMode = if (on) MouseMode.ANY_EVENT else MouseMode.NONE
            1004 -> focusEvents = on
            1006 -> mouseSgr = on
            2004 -> bracketedPaste = on
        }
    }

    private fun switchScreen(toAlt: Boolean, clear: Boolean, saveRestore: Boolean) {
        if (toAlt == isAltScreen) return
        if (toAlt) {
            if (saveRestore) saveCursor()
            buffer = altBuffer
            if (clear) altBuffer.clearAll(blankStyle)
            if (saveRestore) {
                // The alt screen starts with the cursor where it was on the main screen.
                copyState(savedMain, savedAlt)
            }
        } else {
            if (clear) altBuffer.clearAll(TextStyle.DEFAULT)
            buffer = mainBuffer
            if (saveRestore) restoreCursor()
        }
        cursorX = cursorX.coerceIn(0, cols - 1)
        cursorY = cursorY.coerceIn(0, rows - 1)
    }

    private fun copyState(from: SavedState, to: SavedState) {
        to.x = from.x; to.y = from.y; to.fg = from.fg; to.bg = from.bg; to.attrs = from.attrs
        to.autoWrap = from.autoWrap; to.originMode = from.originMode; to.aboutToWrap = from.aboutToWrap
        System.arraycopy(from.charsets, 0, to.charsets, 0, 4)
        to.activeCharset = from.activeCharset
    }

    private fun saveCursor() {
        val s = if (isAltScreen) savedAlt else savedMain
        s.x = cursorX; s.y = cursorY; s.fg = fg; s.bg = bg; s.attrs = attrs
        s.autoWrap = autoWrap; s.originMode = originMode; s.aboutToWrap = aboutToWrap
        System.arraycopy(charsets, 0, s.charsets, 0, 4)
        s.activeCharset = activeCharset
    }

    private fun restoreCursor() {
        val s = if (isAltScreen) savedAlt else savedMain
        cursorX = s.x.coerceIn(0, cols - 1)
        cursorY = s.y.coerceIn(0, rows - 1)
        fg = s.fg; bg = s.bg; attrs = s.attrs
        autoWrap = s.autoWrap; originMode = s.originMode
        aboutToWrap = s.aboutToWrap && autoWrap
        System.arraycopy(s.charsets, 0, charsets, 0, 4)
        activeCharset = s.activeCharset
    }

    private fun moveCursorVertically(delta: Int) {
        aboutToWrap = false
        val top = if (cursorY >= scrollTop) scrollTop else 0
        val bottom = if (cursorY < scrollBottom) scrollBottom else rows
        cursorY = (cursorY + delta).coerceIn(top, bottom - 1)
    }

    private fun setCursor(x: Int, y: Int) {
        cursorX = x.coerceIn(0, cols - 1)
        cursorY = y.coerceIn(0, rows - 1)
        aboutToWrap = false
    }

    private fun setCursorOrigin(x: Int, y: Int) {
        if (originMode) {
            cursorX = x.coerceIn(0, cols - 1)
            cursorY = (y + scrollTop).coerceIn(scrollTop, scrollBottom - 1)
            aboutToWrap = false
        } else {
            setCursor(x, y)
        }
    }

    private fun eraseInDisplay(mode: Int) {
        val style = blankStyle
        when (mode) {
            0 -> {
                eraseInLine(0)
                for (y in cursorY + 1 until rows) buffer.row(y).reset(cols, style)
            }
            1 -> {
                for (y in 0 until cursorY) buffer.row(y).reset(cols, style)
                eraseInLine(1)
            }
            2 -> for (y in 0 until rows) buffer.row(y).reset(cols, style)
            3 -> if (!isAltScreen) mainBuffer.clearHistory()
        }
        aboutToWrap = false
    }

    private fun eraseInLine(mode: Int) {
        val r = buffer.row(cursorY)
        val x = minOf(cursorX, cols - 1)
        when (mode) {
            0 -> {
                r.clear(x, cols, blankStyle); r.wrapped = false
            }
            1 -> r.clear(0, x + 1, blankStyle)
            2 -> {
                r.clear(0, cols, blankStyle); r.wrapped = false
            }
        }
        aboutToWrap = false
    }

    private fun selectGraphicRendition() {
        if (paramCount == 0) {
            fg = TextStyle.COLOR_DEFAULT_FG; bg = TextStyle.COLOR_DEFAULT_BG; attrs = 0
            return
        }
        var i = 0
        while (i < paramCount) {
            val p = argRaw(i, 0)
            // Skip sub-parameters that belong to a parameter we do not understand.
            when (p) {
                0 -> {
                    fg = TextStyle.COLOR_DEFAULT_FG; bg = TextStyle.COLOR_DEFAULT_BG; attrs = 0
                }
                1 -> attrs = attrs or TextStyle.BOLD
                2 -> attrs = attrs or TextStyle.FAINT
                3 -> attrs = attrs or TextStyle.ITALIC
                4 -> {
                    if (i + 1 < paramCount && subParam[i + 1]) {
                        val style = argRaw(i + 1, 1)
                        i++
                        attrs = attrs and (TextStyle.UNDERLINE or TextStyle.DOUBLE_UNDERLINE).inv()
                        when (style) {
                            0 -> Unit
                            2 -> attrs = attrs or TextStyle.DOUBLE_UNDERLINE
                            else -> attrs = attrs or TextStyle.UNDERLINE
                        }
                    } else {
                        attrs = attrs or TextStyle.UNDERLINE
                    }
                }
                5, 6 -> attrs = attrs or TextStyle.BLINK
                7 -> attrs = attrs or TextStyle.INVERSE
                8 -> attrs = attrs or TextStyle.INVISIBLE
                9 -> attrs = attrs or TextStyle.STRIKETHROUGH
                21 -> attrs = attrs or TextStyle.DOUBLE_UNDERLINE
                22 -> attrs = attrs and (TextStyle.BOLD or TextStyle.FAINT).inv()
                23 -> attrs = attrs and TextStyle.ITALIC.inv()
                24 -> attrs = attrs and (TextStyle.UNDERLINE or TextStyle.DOUBLE_UNDERLINE).inv()
                25 -> attrs = attrs and TextStyle.BLINK.inv()
                27 -> attrs = attrs and TextStyle.INVERSE.inv()
                28 -> attrs = attrs and TextStyle.INVISIBLE.inv()
                29 -> attrs = attrs and TextStyle.STRIKETHROUGH.inv()
                in 30..37 -> fg = p - 30
                38, 48, 58 -> {
                    val (color, consumed) = parseExtendedColor(i)
                    if (color >= 0) {
                        if (p == 38) fg = color else if (p == 48) bg = color
                    }
                    i += consumed
                }
                39 -> fg = TextStyle.COLOR_DEFAULT_FG
                in 40..47 -> bg = p - 40
                49 -> bg = TextStyle.COLOR_DEFAULT_BG
                53 -> attrs = attrs or TextStyle.OVERLINE
                55 -> attrs = attrs and TextStyle.OVERLINE.inv()
                in 90..97 -> fg = p - 90 + 8
                in 100..107 -> bg = p - 100 + 8
            }
            i++
            // Ignore stray sub-parameters.
            while (i < paramCount && subParam[i]) i++
        }
    }

    /** Parses 38;5;n / 38;2;r;g;b and colon variants starting at [i]. Returns color and params consumed. */
    private fun parseExtendedColor(i: Int): Pair<Int, Int> {
        if (i + 1 >= paramCount) return -1 to 0
        val colon = subParam[i + 1]
        val kind = argRaw(i + 1, 0)
        if (kind == 5) {
            if (i + 2 >= paramCount) return -1 to 1
            val idx = argRaw(i + 2, 0)
            return (if (idx in 0..255) idx else -1) to 2
        }
        if (kind == 2) {
            if (colon) {
                // 38:2:[colorspace]:r:g:b - count the sub-parameters present.
                var n = 0
                while (i + 2 + n < paramCount && subParam[i + 2 + n]) n++
                val base = if (n >= 4) i + 3 else i + 2
                if (n < 3) return -1 to (1 + n)
                val r = argRaw(base, 0)
                val g = argRaw(base + 1, 0)
                val b = argRaw(base + 2, 0)
                return TextStyle.rgb(r, g, b) to (1 + n)
            }
            if (i + 4 >= paramCount) return -1 to (paramCount - i - 1)
            return TextStyle.rgb(argRaw(i + 2, 0), argRaw(i + 3, 0), argRaw(i + 4, 0)) to 4
        }
        return -1 to 1
    }

    // endregion

    // region OSC

    private fun dispatchOsc() {
        val s = oscBuffer.toString()
        val sep = s.indexOf(';')
        val code = (if (sep < 0) s else s.substring(0, sep)).toIntOrNull() ?: return
        val arg = if (sep < 0) "" else s.substring(sep + 1)
        when (code) {
            0, 2 -> {
                title = arg
                client.onTitleChanged(arg)
            }
            1 -> Unit
            4 -> {
                val parts = arg.split(';')
                var k = 0
                while (k + 1 < parts.size) {
                    val idx = parts[k].toIntOrNull()
                    val spec = parts[k + 1]
                    if (idx != null && idx in 0..255) {
                        if (spec == "?") {
                            write("\u001b]4;$idx;${formatColor(palette[idx])}\u001b\\")
                        } else {
                            parseColor(spec)?.let { palette[idx] = it }
                        }
                    }
                    k += 2
                }
            }
            10, 11, 12 -> {
                val target = when (code) {
                    10 -> TextStyle.COLOR_DEFAULT_FG
                    11 -> TextStyle.COLOR_DEFAULT_BG
                    else -> TextStyle.COLOR_CURSOR
                }
                if (arg == "?") {
                    write("\u001b]$code;${formatColor(palette[target])}\u001b\\")
                } else {
                    parseColor(arg)?.let { palette[target] = it }
                }
            }
            52 -> {
                val data = arg.substringAfter(';', "")
                if (data != "?" && data.isNotEmpty()) {
                    try {
                        val decoded = java.util.Base64.getDecoder().decode(data)
                        client.onClipboardSet(String(decoded, StandardCharsets.UTF_8))
                    } catch (_: IllegalArgumentException) {
                    }
                }
            }
            104 -> {
                if (arg.isEmpty()) {
                    System.arraycopy(schemePalette, 0, palette, 0, 256)
                } else {
                    arg.split(';').mapNotNull { it.toIntOrNull() }.filter { it in 0..255 }
                        .forEach { palette[it] = schemePalette[it] }
                }
            }
            110 -> palette[TextStyle.COLOR_DEFAULT_FG] = schemePalette[TextStyle.COLOR_DEFAULT_FG]
            111 -> palette[TextStyle.COLOR_DEFAULT_BG] = schemePalette[TextStyle.COLOR_DEFAULT_BG]
            112 -> palette[TextStyle.COLOR_CURSOR] = schemePalette[TextStyle.COLOR_CURSOR]
        }
    }

    private fun formatColor(argb: Int): String {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return "rgb:%02x%02x/%02x%02x/%02x%02x".format(r, r, g, g, b, b)
    }

    private fun parseColor(spec: String): Int? {
        try {
            if (spec.startsWith("#")) {
                val hex = spec.substring(1)
                return when (hex.length) {
                    6 -> 0xFF000000.toInt() or hex.toInt(16)
                    3 -> {
                        val r = hex.substring(0, 1).toInt(16) * 17
                        val g = hex.substring(1, 2).toInt(16) * 17
                        val b = hex.substring(2, 3).toInt(16) * 17
                        0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
                    }
                    else -> null
                }
            }
            if (spec.startsWith("rgb:")) {
                val parts = spec.substring(4).split('/')
                if (parts.size != 3) return null
                val comps = parts.map { p ->
                    val v = p.toInt(16)
                    when (p.length) {
                        1 -> v * 17
                        2 -> v
                        3 -> v shr 4
                        4 -> v shr 8
                        else -> return null
                    }
                }
                return 0xFF000000.toInt() or (comps[0] shl 16) or (comps[1] shl 8) or comps[2]
            }
        } catch (_: NumberFormatException) {
        }
        return null
    }

    // endregion

    // region Reset and resize

    private fun softReset() {
        cursorVisible = true
        insertMode = false
        originMode = false
        autoWrap = true
        applicationCursorKeys = false
        applicationKeypad = false
        scrollTop = 0
        scrollBottom = rows
        fg = TextStyle.COLOR_DEFAULT_FG; bg = TextStyle.COLOR_DEFAULT_BG; attrs = 0
        charsets.fill('B')
        activeCharset = 0
        savedMain.x = 0; savedMain.y = 0
        aboutToWrap = false
    }

    fun reset() {
        softReset()
        if (isAltScreen) buffer = mainBuffer
        mainBuffer.clearAll(TextStyle.DEFAULT)
        mainBuffer.clearHistory()
        altBuffer.clearAll(TextStyle.DEFAULT)
        cursorX = 0; cursorY = 0
        newLineMode = false
        reverseVideo = false
        bracketedPaste = false
        mouseMode = MouseMode.NONE
        mouseSgr = false
        focusEvents = false
        cursorShape = CursorShape.BLOCK
        cursorBlink = true
        System.arraycopy(schemePalette, 0, palette, 0, palette.size)
        resetTabStops()
        state = State.GROUND
    }

    fun resize(newCols: Int, newRows: Int) {
        if (newCols < 1 || newRows < 1) return
        if (newCols == cols && newRows == rows) return
        val oldCols = cols
        val cursor = intArrayOf(if (aboutToWrap) cols else cursorX, cursorY)
        if (isAltScreen) {
            // Keep the main screen's cursor consistent too.
            val mainCursor = intArrayOf(savedMain.x, savedMain.y)
            mainBuffer.resize(newCols, newRows, mainCursor, reflow = true)
            savedMain.x = mainCursor[0]; savedMain.y = mainCursor[1]
            altBuffer.resize(newCols, newRows, cursor, reflow = false)
        } else {
            val altCursor = intArrayOf(savedAlt.x, savedAlt.y)
            altBuffer.resize(newCols, newRows, altCursor, reflow = false)
            mainBuffer.resize(newCols, newRows, cursor, reflow = true)
        }
        cursorX = cursor[0]
        cursorY = cursor[1]
        aboutToWrap = false
        scrollTop = 0
        scrollBottom = newRows
        if (newCols != oldCols) {
            val old = tabStops
            tabStops = BooleanArray(newCols) { i -> if (i < old.size) old[i] else i % 8 == 0 && i != 0 }
        }
    }

    // endregion

    // region Output helpers

    private fun write(s: String) {
        client.onWrite(s.toByteArray(StandardCharsets.UTF_8))
    }

    /** Encodes pasted text, honoring bracketed paste mode. */
    fun encodePaste(text: String): ByteArray {
        var t = text.replace("\r\n", "\r").replace('\n', '\r')
        if (bracketedPaste) {
            t = t.replace("\u001b", "")
            t = "\u001b[200~$t\u001b[201~"
        }
        return t.toByteArray(StandardCharsets.UTF_8)
    }

    /**
     * Encodes a mouse event. [button]: 0 left, 1 middle, 2 right, 64 wheel up, 65 wheel down.
     * Coordinates are zero based cell positions. Returns null when mouse reporting is off.
     */
    fun encodeMouse(button: Int, col: Int, row: Int, pressed: Boolean, motion: Boolean = false): ByteArray? {
        if (mouseMode == MouseMode.NONE) return null
        if (motion && mouseMode != MouseMode.BUTTON_EVENT && mouseMode != MouseMode.ANY_EVENT) return null
        if (!pressed && mouseMode == MouseMode.X10) return null
        val x = col.coerceIn(0, cols - 1) + 1
        val y = row.coerceIn(0, rows - 1) + 1
        var b = button
        if (motion) b += 32
        return if (mouseSgr) {
            "\u001b[<$b;$x;${y}${if (pressed) 'M' else 'm'}".toByteArray(StandardCharsets.US_ASCII)
        } else {
            val code = if (pressed || button >= 64) b else 3
            if (x > 223 || y > 223) return null
            byteArrayOf(0x1b, '['.code.toByte(), 'M'.code.toByte(), (32 + code).toByte(), (32 + x).toByte(), (32 + y).toByte())
        }
    }

    fun encodeFocus(focused: Boolean): ByteArray? =
        if (focusEvents) (if (focused) "\u001b[I" else "\u001b[O").toByteArray(StandardCharsets.US_ASCII) else null

    /** Returns the visible screen as text, mostly for tests and accessibility. */
    fun screenText(): String = (0 until rows).joinToString("\n") { buffer.row(it).getText() }

    /**
     * Extracts text between two positions (inclusive), where rows may be negative for history.
     * Soft-wrapped lines are joined without a newline.
     */
    fun getText(startRow: Int, startCol: Int, endRow: Int, endCol: Int): String {
        val sb = StringBuilder()
        val minRow = -buffer.historySize
        for (r in startRow.coerceAtLeast(minRow)..endRow.coerceAtMost(rows - 1)) {
            val line = buffer.lineAt(r)
            val from = if (r == startRow) startCol else 0
            val to = if (r == endRow) endCol + 1 else line.cols
            val last = r == endRow
            sb.append(line.getText(from, to, trimEnd = !line.wrapped || last))
            if (!last && !line.wrapped) sb.append('\n')
        }
        return sb.toString()
    }

    // endregion

    companion object {
        private const val MAX_PARAMS = 32
        private const val MAX_OSC = 8192
        private const val REPLACEMENT = 0xFFFD

        private const val BEL = 0x07
        private const val BS = 0x08
        private const val HT = 0x09
        private const val LF = 0x0A
        private const val VT = 0x0B
        private const val FF = 0x0C
        private const val CR = 0x0D
        private const val SO = 0x0E
        private const val SI = 0x0F
        private const val CAN = 0x18
        private const val SUB = 0x1A
        private const val ESC = 0x1B
        private const val DEL = 0x7F

        /** DEC special graphics for 0x5F..0x7E. */
        private val DEC_GRAPHICS = charArrayOf(
            ' ', '◆', '▒', '␉', '␌', '␍', '␊', '°', '±', '␤', '␋', '┘', '┐', '┌', '└', '┼',
            '⎺', '⎻', '─', '⎼', '⎽', '├', '┤', '┴', '┬', '│', '≤', '≥', 'π', '≠', '£', '·',
        )
    }
}
