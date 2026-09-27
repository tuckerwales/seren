package wales.tucker.seren.ssh.emulator

/**
 * One line of terminal cells.
 *
 * Every cell stores a code point and a packed [TextStyle]. A wide character occupies its cell and
 * the following cell, which holds [WIDE_TAIL]. Combining marks attached to a cell are kept in the
 * sparse [combining] map.
 */
class TerminalRow(cols: Int, style: Long) {
    var cols: Int = cols
        private set
    var text: IntArray = IntArray(cols) { SPACE }
        private set
    var styles: LongArray = LongArray(cols) { style }
        private set

    /** True when the line was soft-wrapped into the next line (auto-wrap), used for reflow and copy. */
    var wrapped: Boolean = false

    var combining: HashMap<Int, String>? = null
        private set

    fun reset(newCols: Int, style: Long) {
        if (newCols != cols) {
            cols = newCols
            text = IntArray(newCols)
            styles = LongArray(newCols)
        }
        text.fill(SPACE)
        styles.fill(style)
        wrapped = false
        combining = null
    }

    fun resize(newCols: Int, style: Long) {
        if (newCols == cols) return
        val newText = IntArray(newCols) { SPACE }
        val newStyles = LongArray(newCols) { style }
        val n = minOf(cols, newCols)
        System.arraycopy(text, 0, newText, 0, n)
        System.arraycopy(styles, 0, newStyles, 0, n)
        // Do not leave half of a wide character at the right edge.
        if (n in 1 until cols && newText[n - 1] != WIDE_TAIL && isWideAt(n - 1)) {
            newText[n - 1] = SPACE
        }
        cols = newCols
        text = newText
        styles = newStyles
        combining?.keys?.removeAll { it >= newCols }
    }

    fun isWideAt(col: Int): Boolean = col + 1 < cols && text[col + 1] == WIDE_TAIL

    fun copyFrom(other: TerminalRow) {
        if (other.cols != cols) {
            cols = other.cols
            text = IntArray(cols)
            styles = LongArray(cols)
        }
        System.arraycopy(other.text, 0, text, 0, cols)
        System.arraycopy(other.styles, 0, styles, 0, cols)
        wrapped = other.wrapped
        combining = other.combining?.let { HashMap(it) }
    }

    /** Writes a single cell, repairing any wide character it partially overwrites. */
    fun setCell(col: Int, cp: Int, style: Long) {
        if (col < 0 || col >= cols) return
        if (text[col] == WIDE_TAIL && col > 0) {
            text[col - 1] = SPACE
        } else if (isWideAt(col)) {
            text[col + 1] = SPACE
        }
        text[col] = cp
        styles[col] = style
        combining?.remove(col)
    }

    /** Writes a wide character at [col] and its tail at col + 1. */
    fun setWideCell(col: Int, cp: Int, style: Long) {
        if (col < 0 || col + 1 >= cols) return
        setCell(col, cp, style)
        if (isWideAt(col + 1)) text[col + 2] = SPACE
        text[col + 1] = WIDE_TAIL
        styles[col + 1] = style
        combining?.remove(col + 1)
    }

    fun addCombining(col: Int, cp: Int) {
        if (col < 0 || col >= cols) return
        val target = if (text[col] == WIDE_TAIL && col > 0) col - 1 else col
        val map = combining ?: HashMap<Int, String>().also { combining = it }
        map[target] = (map[target] ?: "") + String(Character.toChars(cp))
    }

    fun getCombining(col: Int): String? = combining?.get(col)

    fun setCombining(col: Int, value: String?) {
        if (value == null) {
            combining?.remove(col)
        } else {
            val map = combining ?: HashMap<Int, String>().also { combining = it }
            map[col] = value
        }
    }

    /** Fills [from, to) with blanks in the given style. */
    fun clear(from: Int, to: Int, style: Long) {
        val start = from.coerceIn(0, cols)
        val end = to.coerceIn(0, cols)
        if (start >= end) return
        // Repair wide characters cut by the boundaries.
        if (start > 0 && text[start] == WIDE_TAIL) text[start - 1] = SPACE
        if (end < cols && text[end] == WIDE_TAIL) text[end] = SPACE
        for (i in start until end) {
            text[i] = SPACE
            styles[i] = style
        }
        combining?.keys?.removeAll { it in start until end }
    }

    fun insertCells(col: Int, count: Int, style: Long) {
        if (col < 0 || col >= cols) return
        val n = minOf(count, cols - col)
        if (text[col] == WIDE_TAIL && col > 0) {
            text[col - 1] = SPACE
            text[col] = SPACE
        }
        System.arraycopy(text, col, text, col + n, cols - col - n)
        System.arraycopy(styles, col, styles, col + n, cols - col - n)
        for (i in col until col + n) {
            text[i] = SPACE
            styles[i] = style
        }
        if (text[cols - 1] != WIDE_TAIL && isWideAtLastPotential()) text[cols - 1] = SPACE
        if (text[0] == WIDE_TAIL) text[0] = SPACE
        combining = combining?.let { map ->
            HashMap<Int, String>().also { out ->
                map.forEach { (k, v) ->
                    if (k < col) out[k] = v else if (k + n < cols) out[k + n] = v
                }
            }
        }
    }

    private fun isWideAtLastPotential(): Boolean {
        // A lead cell at the last column whose tail was shifted out of the row.
        val cp = text[cols - 1]
        return cp != SPACE && WcWidth.width(cp) == 2
    }

    fun deleteCells(col: Int, count: Int, style: Long) {
        if (col < 0 || col >= cols) return
        val n = minOf(count, cols - col)
        if (text[col] == WIDE_TAIL && col > 0) text[col - 1] = SPACE
        System.arraycopy(text, col + n, text, col, cols - col - n)
        System.arraycopy(styles, col + n, styles, col, cols - col - n)
        for (i in cols - n until cols) {
            text[i] = SPACE
            styles[i] = style
        }
        if (text[col] == WIDE_TAIL) text[col] = SPACE
        combining = combining?.let { map ->
            HashMap<Int, String>().also { out ->
                map.forEach { (k, v) ->
                    if (k < col) out[k] = v else if (k >= col + n) out[k - n] = v
                }
            }
        }
    }

    fun isBlank(): Boolean {
        for (i in 0 until cols) {
            if (text[i] != SPACE || styles[i] != TextStyle.DEFAULT) return false
        }
        return true
    }

    /** Number of cells up to and including the last non-blank cell. */
    fun usedLength(): Int {
        for (i in cols - 1 downTo 0) {
            if (text[i] != SPACE || TextStyle.bg(styles[i]) != TextStyle.COLOR_DEFAULT_BG ||
                TextStyle.attrs(styles[i]) and TextStyle.INVERSE != 0
            ) return i + 1
        }
        return 0
    }

    /** Returns the text of columns [from, to), skipping wide tails. */
    fun getText(from: Int = 0, to: Int = cols, trimEnd: Boolean = true): String {
        val sb = StringBuilder()
        val end = to.coerceAtMost(cols)
        for (i in from.coerceAtLeast(0) until end) {
            val cp = text[i]
            if (cp == WIDE_TAIL) continue
            sb.appendCodePoint(cp)
            combining?.get(i)?.let { sb.append(it) }
        }
        return if (trimEnd) sb.trimEnd(' ').toString() else sb.toString()
    }

    companion object {
        const val SPACE = ' '.code
        const val WIDE_TAIL = 0
    }
}
