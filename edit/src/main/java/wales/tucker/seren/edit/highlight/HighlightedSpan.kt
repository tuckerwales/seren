package wales.tucker.seren.edit.highlight

/** A half-open range `[start, end)` of [kind] in the document. */
data class HighlightedSpan(val start: Int, val end: Int, val kind: TokenKind) {
    init {
        require(start >= 0 && end >= start) { "bad span $start..$end" }
    }
}
