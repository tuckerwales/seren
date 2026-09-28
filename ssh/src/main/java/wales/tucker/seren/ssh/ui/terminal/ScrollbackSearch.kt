package wales.tucker.seren.ssh.ui.terminal

/**
 * Plain (non-regex) find helpers for SSH terminal scrollback.
 *
 * Matches are reported as character offsets within a single line string (as produced by
 * [wales.tucker.seren.ssh.emulator.TerminalRow.getText]). Callers map those offsets to cell
 * columns when highlighting.
 */
data class ScrollbackMatch(
    /** Index into the lines list passed to search (0 = oldest). */
    val line: Int,
    /** Inclusive start character offset within that line. */
    val start: Int,
    /** Exclusive end character offset within that line. */
    val end: Int,
)

object ScrollbackSearch {
    fun indexOf(text: CharSequence, query: String, from: Int, caseSensitive: Boolean, forward: Boolean): Int {
        if (query.isEmpty()) return -1
        val hay = if (caseSensitive) text else text.toString().lowercase()
        val needle = if (caseSensitive) query else query.lowercase()
        return if (forward) {
            hay.indexOf(needle, startIndex = from.coerceIn(0, hay.length))
        } else {
            val start = (from - 1).coerceAtMost(hay.length)
            if (start < 0) -1 else hay.lastIndexOf(needle, startIndex = start)
        }
    }

    fun findNext(
        lines: List<String>,
        query: String,
        fromLine: Int,
        fromStart: Int,
        caseSensitive: Boolean,
    ): ScrollbackMatch? {
        if (query.isEmpty() || lines.isEmpty()) return null
        val startLine = fromLine.coerceIn(0, lines.lastIndex)
        // Current line, at/after fromStart.
        val first = indexOf(lines[startLine], query, fromStart, caseSensitive, forward = true)
        if (first >= 0) return ScrollbackMatch(startLine, first, first + query.length)
        for (i in (startLine + 1)..lines.lastIndex) {
            val at = indexOf(lines[i], query, 0, caseSensitive, forward = true)
            if (at >= 0) return ScrollbackMatch(i, at, at + query.length)
        }
        return null
    }

    fun findPrevious(
        lines: List<String>,
        query: String,
        fromLine: Int,
        fromStart: Int,
        caseSensitive: Boolean,
    ): ScrollbackMatch? {
        if (query.isEmpty() || lines.isEmpty()) return null
        val startLine = fromLine.coerceIn(0, lines.lastIndex)
        // Current line, before fromStart.
        val first = indexOf(lines[startLine], query, fromStart, caseSensitive, forward = false)
        if (first >= 0) return ScrollbackMatch(startLine, first, first + query.length)
        for (i in (startLine - 1) downTo 0) {
            val at = indexOf(lines[i], query, lines[i].length, caseSensitive, forward = false)
            if (at >= 0) return ScrollbackMatch(i, at, at + query.length)
        }
        return null
    }

    /** Every match from top of scrollback to bottom. */
    fun findAll(lines: List<String>, query: String, caseSensitive: Boolean): List<ScrollbackMatch> {
        if (query.isEmpty() || lines.isEmpty()) return emptyList()
        val out = ArrayList<ScrollbackMatch>()
        for (i in lines.indices) {
            var from = 0
            while (true) {
                val at = indexOf(lines[i], query, from, caseSensitive, forward = true)
                if (at < 0) break
                out.add(ScrollbackMatch(i, at, at + query.length))
                from = at + query.length.coerceAtLeast(1)
            }
        }
        return out
    }
}
