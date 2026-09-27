package wales.tucker.seren.edit.ui.editor

import androidx.compose.ui.text.TextRange

/**
 * Cursor movement and indentation on plain text with "\n" line breaks, for the extra keys row and
 * hardware keys. Movement collapses any selection, as it does in other editors.
 */
object TextEditing {

    fun lineStart(text: CharSequence, offset: Int): Int {
        var i = offset.coerceIn(0, text.length)
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    fun lineEnd(text: CharSequence, offset: Int): Int {
        var i = offset.coerceIn(0, text.length)
        while (i < text.length && text[i] != '\n') i++
        return i
    }

    fun left(text: CharSequence, selection: TextRange): TextRange {
        if (!selection.collapsed) return TextRange(selection.min)
        var i = selection.start
        if (i > 0) i--
        if (i > 0 && Character.isLowSurrogate(text[i]) && Character.isHighSurrogate(text[i - 1])) i--
        return TextRange(i)
    }

    fun right(text: CharSequence, selection: TextRange): TextRange {
        if (!selection.collapsed) return TextRange(selection.max)
        var i = selection.start
        if (i < text.length) i++
        if (i < text.length && Character.isLowSurrogate(text[i]) && Character.isHighSurrogate(text[i - 1])) i++
        return TextRange(i)
    }

    /** One line up, keeping the column where the line is long enough. */
    fun up(text: CharSequence, selection: TextRange): TextRange {
        val offset = selection.min
        val start = lineStart(text, offset)
        if (start == 0) return TextRange(0)
        val column = offset - start
        val previousStart = lineStart(text, start - 1)
        return TextRange(minOf(previousStart + column, start - 1))
    }

    /** One line down, keeping the column where the line is long enough. */
    fun down(text: CharSequence, selection: TextRange): TextRange {
        val offset = selection.max
        val end = lineEnd(text, offset)
        if (end == text.length) return TextRange(text.length)
        val column = offset - lineStart(text, offset)
        val nextStart = end + 1
        return TextRange(minOf(nextStart + column, lineEnd(text, nextStart)))
    }

    /**
     * Home goes to the first non-blank character of the line, or to the very start when already
     * there, like most code editors.
     */
    fun home(text: CharSequence, selection: TextRange): TextRange {
        val offset = selection.min
        val start = lineStart(text, offset)
        var firstNonBlank = start
        while (firstNonBlank < text.length && (text[firstNonBlank] == ' ' || text[firstNonBlank] == '\t')) firstNonBlank++
        return TextRange(if (offset == firstNonBlank) start else firstNonBlank)
    }

    fun end(text: CharSequence, selection: TextRange): TextRange = TextRange(lineEnd(text, selection.max))

    /** The leading spaces and tabs of the line containing [offset]. */
    fun indentOf(text: CharSequence, offset: Int): String {
        val start = lineStart(text, offset)
        var i = start
        while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
        return text.substring(start, i)
    }

    /**
     * What Tab inserts: a tab when most indented lines start with one, otherwise the most common
     * step between space indents (2, 4 or 8), and four spaces for a file with no indentation yet.
     */
    fun indentUnit(text: CharSequence): String {
        var tabs = 0
        var spaces = 0
        val steps = IntArray(9)
        var previous = 0
        var i = 0
        while (i <= text.length) {
            val lineEnd = lineEnd(text, i)
            if (i < text.length && text[i] == '\t') {
                tabs++
            } else {
                var n = 0
                while (i + n < lineEnd && text[i + n] == ' ') n++
                if (n > 0 && i + n < lineEnd) {
                    spaces++
                    val step = n - previous
                    if (step in 1..8) steps[step]++
                }
                if (i + n < lineEnd) previous = n
            }
            i = lineEnd + 1
        }
        if (tabs > spaces) return "\t"
        val best = listOf(2, 4, 8).maxByOrNull { steps[it] }?.takeIf { steps[it] > 0 } ?: 4
        return " ".repeat(best)
    }
}
