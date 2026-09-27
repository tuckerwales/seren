package wales.tucker.seren.ssh.emulator

/**
 * A screen of [rows] x [cols] cells plus an optional scrollback history.
 *
 * Screen rows are addressed 0 until [rows]; history rows are addressed with negative indices
 * via [lineAt], -1 being the most recent line scrolled off the top.
 */
class TerminalBuffer(cols: Int, rows: Int, maxHistory: Int) {
    var cols: Int = cols
        private set
    var rows: Int = rows
        private set
    var maxHistory: Int = maxHistory
        set(value) {
            field = value.coerceAtLeast(0)
            trimHistory()
        }

    private var screen: Array<TerminalRow> = Array(rows) { TerminalRow(cols, TextStyle.DEFAULT) }
    private val history = ArrayDeque<TerminalRow>()

    /** Monotonic count of lines pushed into history, lets viewers keep a stable scroll anchor. */
    var linesScrolledIntoHistory: Long = 0
        private set

    val historySize: Int get() = history.size

    fun row(y: Int): TerminalRow = screen[y]

    fun lineAt(index: Int): TerminalRow =
        if (index < 0) history[history.size + index] else screen[index]

    private fun newRow(style: Long): TerminalRow = TerminalRow(cols, style)

    fun clearHistory() {
        history.clear()
    }

    private fun trimHistory() {
        while (history.size > maxHistory) history.removeFirst()
    }

    /** Scrolls rows [top, bottom) up by [n] lines, optionally keeping the lines in history. */
    fun scrollUp(top: Int, bottom: Int, n: Int, style: Long, saveHistory: Boolean) {
        val count = minOf(n, bottom - top)
        if (count <= 0) return
        repeat(count) {
            val removed = screen[top]
            System.arraycopy(screen, top + 1, screen, top, bottom - top - 1)
            val fresh: TerminalRow
            if (saveHistory && maxHistory > 0) {
                history.addLast(removed)
                linesScrolledIntoHistory++
                fresh = if (history.size > maxHistory) {
                    history.removeFirst().also { it.reset(cols, style) }
                } else {
                    newRow(style)
                }
            } else {
                removed.reset(cols, style)
                fresh = removed
            }
            screen[bottom - 1] = fresh
        }
    }

    /** Scrolls rows [top, bottom) down by [n] lines, inserting blank lines at [top]. */
    fun scrollDown(top: Int, bottom: Int, n: Int, style: Long) {
        val count = minOf(n, bottom - top)
        if (count <= 0) return
        repeat(count) {
            val removed = screen[bottom - 1]
            System.arraycopy(screen, top, screen, top + 1, bottom - top - 1)
            removed.reset(cols, style)
            screen[top] = removed
        }
    }

    fun clearAll(style: Long) {
        for (r in screen) r.reset(cols, style)
    }

    /**
     * Resizes the buffer. [cursor] holds (x, y) and is updated in place so the cursor stays on
     * the same content. When [reflow] is true and the width changes, soft-wrapped lines are
     * re-wrapped to the new width.
     */
    fun resize(newCols: Int, newRows: Int, cursor: IntArray, reflow: Boolean) {
        if (newCols == cols && newRows == rows) return
        if (reflow && newCols != cols) {
            reflow(newCols, newRows, cursor)
        } else {
            resizeNoReflow(newCols, newRows, cursor, pullFromHistory = reflow)
        }
    }

    private fun resizeNoReflow(newCols: Int, newRows: Int, cursor: IntArray, pullFromHistory: Boolean) {
        val rowsList = ArrayList<TerminalRow>(screen.asList())
        if (newCols != cols) {
            for (r in rowsList) r.resize(newCols, TextStyle.DEFAULT)
            for (r in history) r.resize(newCols, TextStyle.DEFAULT)
        }
        if (newRows < rows) {
            var toRemove = rows - newRows
            // First drop blank lines below the cursor.
            var y = rowsList.size - 1
            while (toRemove > 0 && y > cursor[1] && rowsList[y].isBlank()) {
                rowsList.removeAt(y)
                y--
                toRemove--
            }
            // Then push lines off the top.
            repeat(toRemove) {
                val r = rowsList.removeAt(0)
                if (pullFromHistory && maxHistory > 0) {
                    history.addLast(r)
                    linesScrolledIntoHistory++
                }
            }
            trimHistory()
            cursor[1] -= toRemove
        } else if (newRows > rows) {
            val extra = newRows - rows
            val pull = if (pullFromHistory) minOf(extra, history.size) else 0
            repeat(pull) {
                rowsList.add(0, history.removeLast())
                linesScrolledIntoHistory--
            }
            cursor[1] += pull
            repeat(extra - pull) { rowsList.add(TerminalRow(newCols, TextStyle.DEFAULT)) }
        }
        cols = newCols
        rows = newRows
        screen = rowsList.toTypedArray()
        cursor[0] = cursor[0].coerceIn(0, newCols - 1)
        cursor[1] = cursor[1].coerceIn(0, newRows - 1)
    }

    private fun reflow(newCols: Int, newRows: Int, cursor: IntArray) {
        val cursorX = cursor[0]
        val cursorY = cursor[1]
        var lastRow = cursorY
        for (y in rows - 1 downTo cursorY + 1) {
            if (!screen[y].isBlank()) {
                lastRow = y
                break
            }
        }
        val src = ArrayList<TerminalRow>(history.size + lastRow + 1)
        src.addAll(history)
        for (y in 0..lastRow) src.add(screen[y])
        val cursorSrc = history.size + cursorY

        val out = ArrayList<TerminalRow>(src.size)
        var outRow = TerminalRow(newCols, TextStyle.DEFAULT)
        var outX = 0
        var newCursorX = 0
        var newCursorY = 0
        var i = 0
        while (i < src.size) {
            var j = i
            while (j < src.size - 1 && src[j].wrapped) j++
            for (k in i..j) {
                val r = src[k]
                var take = if (k < j) r.cols else r.usedLength()
                val hasCursor = k == cursorSrc
                if (hasCursor) take = maxOf(take, minOf(cursorX, r.cols))
                var cursorPlaced = false
                var x = 0
                while (x < take) {
                    val cp = r.text[x]
                    if (cp == TerminalRow.WIDE_TAIL) {
                        x++
                        continue
                    }
                    val w = if (r.isWideAt(x)) 2 else 1
                    if (outX + w > newCols) {
                        outRow.wrapped = true
                        out.add(outRow)
                        outRow = TerminalRow(newCols, TextStyle.DEFAULT)
                        outX = 0
                    }
                    if (hasCursor && !cursorPlaced && x >= cursorX) {
                        newCursorX = outX
                        newCursorY = out.size
                        cursorPlaced = true
                    }
                    if (w == 2 && newCols >= 2) {
                        outRow.setWideCell(outX, cp, r.styles[x])
                    } else {
                        outRow.setCell(outX, if (w == 2) TerminalRow.SPACE else cp, r.styles[x])
                    }
                    r.getCombining(x)?.let { outRow.setCombining(outX, it) }
                    outX += if (newCols >= 2) w else 1
                    x += w
                }
                if (hasCursor && !cursorPlaced) {
                    if (outX >= newCols) {
                        newCursorX = newCols - 1
                    } else {
                        newCursorX = outX
                    }
                    newCursorY = out.size
                }
            }
            out.add(outRow)
            outRow = TerminalRow(newCols, TextStyle.DEFAULT)
            outX = 0
            i = j + 1
        }

        history.clear()
        val total = out.size
        val newScreen = ArrayList<TerminalRow>(newRows)
        if (total <= newRows) {
            newScreen.addAll(out)
            while (newScreen.size < newRows) newScreen.add(TerminalRow(newCols, TextStyle.DEFAULT))
        } else {
            val offset = minOf(total - newRows, newCursorY)
            for (k in 0 until offset) history.addLast(out[k])
            for (k in offset until offset + newRows) newScreen.add(out[k])
            newCursorY -= offset
        }
        trimHistory()
        cols = newCols
        rows = newRows
        screen = newScreen.toTypedArray()
        cursor[0] = newCursorX.coerceIn(0, newCols - 1)
        cursor[1] = newCursorY.coerceIn(0, newRows - 1)
    }
}
