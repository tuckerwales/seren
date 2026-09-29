package wales.tucker.seren.edit.highlight

import androidx.compose.ui.graphics.Color
import wales.tucker.seren.core.content.ContentColorScheme

/** Turns source text into colored spans. Implementations are pure and safe to call off the UI thread. */
fun interface Highlighter {
    fun highlight(text: String): List<HighlightedSpan>
}

/**
 * Maps a token kind to a color from [scheme]. Matches the color-scheme card preview in Settings:
 * keyword magenta, string green, comment bright-black, call/header blue.
 */
fun tokenColor(kind: TokenKind, scheme: ContentColorScheme): Color = when (kind) {
    TokenKind.Plain, TokenKind.Punctuation -> Color(scheme.foreground)
    TokenKind.Keyword -> Color(scheme.ansi[5])
    TokenKind.String -> Color(scheme.ansi[2])
    TokenKind.Comment -> Color(scheme.ansi[8])
    TokenKind.Number -> Color(scheme.ansi[3])
    TokenKind.Type -> Color(scheme.ansi[6])
    TokenKind.Function -> Color(scheme.ansi[4])
    TokenKind.Tag -> Color(scheme.ansi[1])
    TokenKind.Attribute -> Color(scheme.ansi[3])
    TokenKind.Header -> Color(scheme.ansi[4])
    TokenKind.Link -> Color(scheme.ansi[6])
}
