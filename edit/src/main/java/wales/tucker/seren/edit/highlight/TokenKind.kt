package wales.tucker.seren.edit.highlight

/**
 * Kinds of highlighted spans. Colors come from [wales.tucker.seren.core.content.ContentColorScheme]
 * ANSI roles via [tokenColor] — no parallel palette.
 */
enum class TokenKind {
    Plain,
    Keyword,
    String,
    Comment,
    Number,
    Type,
    Function,
    Punctuation,
    Tag,
    Attribute,
    Header,
    Link,
}
