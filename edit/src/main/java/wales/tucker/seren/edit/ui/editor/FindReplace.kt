package wales.tucker.seren.edit.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange

/** Plain (non-regex) find/replace helpers for the editor. */
object FindReplace {
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

    fun findNext(text: CharSequence, query: String, from: Int, caseSensitive: Boolean): TextRange? {
        val i = indexOf(text, query, from, caseSensitive, forward = true)
        return if (i < 0) null else TextRange(i, i + query.length)
    }

    fun findPrevious(text: CharSequence, query: String, from: Int, caseSensitive: Boolean): TextRange? {
        val i = indexOf(text, query, from, caseSensitive, forward = false)
        return if (i < 0) null else TextRange(i, i + query.length)
    }

    fun select(state: TextFieldState, range: TextRange) {
        state.edit { selection = range }
    }

    fun replaceSelection(state: TextFieldState, replacement: String) {
        state.edit {
            val start = selection.min
            replace(start, selection.max, replacement)
            selection = TextRange(start + replacement.length)
        }
    }

    /** Replaces every match of [query]; returns how many were replaced. */
    fun replaceAll(state: TextFieldState, query: String, replacement: String, caseSensitive: Boolean): Int {
        if (query.isEmpty()) return 0
        val text = state.text.toString()
        val ranges = mutableListOf<IntRange>()
        var from = 0
        while (true) {
            val i = indexOf(text, query, from, caseSensitive, forward = true)
            if (i < 0) break
            ranges.add(i until i + query.length)
            from = i + query.length.coerceAtLeast(1)
        }
        if (ranges.isEmpty()) return 0
        // Replace from the end so earlier offsets stay valid.
        state.edit {
            for (r in ranges.asReversed()) {
                replace(r.first, r.last + 1, replacement)
            }
            selection = TextRange(0)
        }
        return ranges.size
    }
}
