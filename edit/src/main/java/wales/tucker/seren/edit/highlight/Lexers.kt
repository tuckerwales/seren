package wales.tucker.seren.edit.highlight

/**
 * Small regex/state lexers for the languages Edit highlights. They emit only non-[TokenKind.Plain]
 * spans so the paint path stays cheap. Not full parsers — good enough for a quiet Prism-style pass.
 */

internal class SpanBuilder {
    private val spans = ArrayList<HighlightedSpan>()
    fun add(start: Int, end: Int, kind: TokenKind) {
        if (end > start && kind != TokenKind.Plain) spans.add(HighlightedSpan(start, end, kind))
    }
    fun build(): List<HighlightedSpan> = spans
}

// --- Kotlin / Java -----------------------------------------------------------

internal object KotlinJavaHighlighter : Highlighter {
    private val keywords = setOf(
        "abstract", "actual", "annotation", "as", "break", "by", "catch", "class", "companion",
        "const", "constructor", "continue", "crossinline", "data", "delegate", "do", "dynamic",
        "else", "enum", "expect", "external", "false", "final", "finally", "for", "fun", "get",
        "if", "import", "in", "infix", "init", "inline", "inner", "interface", "internal", "is",
        "lateinit", "noinline", "null", "object", "open", "operator", "out", "override", "package",
        "private", "protected", "public", "reified", "return", "sealed", "set", "super", "suspend",
        "tailrec", "this", "throw", "true", "try", "typealias", "val", "var", "vararg", "when",
        "where", "while",
        // Java
        "assert", "boolean", "byte", "case", "char", "default", "double", "extends", "float",
        "goto", "implements", "instanceof", "int", "long", "native", "new", "short", "static",
        "strictfp", "switch", "synchronized", "throws", "transient", "void", "volatile",
    )

    override fun highlight(text: String): List<HighlightedSpan> {
        val out = SpanBuilder()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c == '/' && i + 1 < n && text[i + 1] == '/' -> {
                    val start = i
                    i = text.indexOf('\n', i).let { if (it < 0) n else it }
                    out.add(start, i, TokenKind.Comment)
                }
                c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                    val start = i
                    i += 2
                    while (i + 1 < n && !(text[i] == '*' && text[i + 1] == '/')) i++
                    i = (i + 2).coerceAtMost(n)
                    out.add(start, i, TokenKind.Comment)
                }
                c == '"' && i + 2 < n && text[i + 1] == '"' && text[i + 2] == '"' -> {
                    val start = i
                    i += 3
                    while (i + 2 < n && !(text[i] == '"' && text[i + 1] == '"' && text[i + 2] == '"')) {
                        if (text[i] == '\\') i++
                        i++
                    }
                    i = (i + 3).coerceAtMost(n)
                    out.add(start, i, TokenKind.String)
                }
                c == '"' || c == '\'' -> {
                    val start = i
                    val quote = c
                    i++
                    while (i < n && text[i] != quote) {
                        if (text[i] == '\\') i++
                        i++
                        if (i >= n) break
                    }
                    if (i < n) i++
                    out.add(start, i, TokenKind.String)
                }
                c.isDigit() -> {
                    val start = i
                    i++
                    while (i < n && (text[i].isLetterOrDigit() || text[i] == '.' || text[i] == '_')) i++
                    out.add(start, i, TokenKind.Number)
                }
                Character.isJavaIdentifierStart(c) -> {
                    val start = i
                    i++
                    while (i < n && Character.isJavaIdentifierPart(text[i])) i++
                    val word = text.substring(start, i)
                    when {
                        word in keywords -> out.add(start, i, TokenKind.Keyword)
                        word.first().isUpperCase() -> out.add(start, i, TokenKind.Type)
                        i < n && text[i] == '(' -> out.add(start, i, TokenKind.Function)
                    }
                }
                else -> i++
            }
        }
        return out.build()
    }
}

// --- JSON --------------------------------------------------------------------

internal object JsonHighlighter : Highlighter {
    override fun highlight(text: String): List<HighlightedSpan> {
        val out = SpanBuilder()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c == '"' -> {
                    val start = i
                    i = scanJsonString(text, i)
                    // Key if the next non-space is ':'
                    var j = i
                    while (j < n && text[j].isWhitespace()) j++
                    val kind = if (j < n && text[j] == ':') TokenKind.Attribute else TokenKind.String
                    out.add(start, i, kind)
                }
                c == '-' || c.isDigit() -> {
                    val start = i
                    if (c == '-') i++
                    while (i < n && (text[i].isDigit() || text[i] == '.' || text[i] == 'e' || text[i] == 'E' || text[i] == '+' || text[i] == '-')) i++
                    out.add(start, i, TokenKind.Number)
                }
                text.startsWith("true", i) && boundary(text, i, 4) -> {
                    out.add(i, i + 4, TokenKind.Keyword); i += 4
                }
                text.startsWith("false", i) && boundary(text, i, 5) -> {
                    out.add(i, i + 5, TokenKind.Keyword); i += 5
                }
                text.startsWith("null", i) && boundary(text, i, 4) -> {
                    out.add(i, i + 4, TokenKind.Keyword); i += 4
                }
                else -> i++
            }
        }
        return out.build()
    }

    private fun boundary(text: String, i: Int, len: Int): Boolean {
        val end = i + len
        return end >= text.length || !text[end].isLetterOrDigit()
    }
}

private fun scanJsonString(text: String, start: Int): Int {
    var i = start + 1
    val n = text.length
    while (i < n) {
        when (text[i]) {
            '\\' -> i += 2
            '"' -> return i + 1
            else -> i++
        }
    }
    return n
}

// --- XML / HTML --------------------------------------------------------------

internal object XmlHtmlHighlighter : Highlighter {
    override fun highlight(text: String): List<HighlightedSpan> {
        val out = SpanBuilder()
        var i = 0
        val n = text.length
        while (i < n) {
            when {
                text.startsWith("<!--", i) -> {
                    val start = i
                    val end = text.indexOf("-->", i + 4).let { if (it < 0) n else it + 3 }
                    out.add(start, end, TokenKind.Comment)
                    i = end
                }
                text.startsWith("<![CDATA[", i) -> {
                    val start = i
                    val end = text.indexOf("]]>", i + 9).let { if (it < 0) n else it + 3 }
                    out.add(start, end, TokenKind.String)
                    i = end
                }
                text[i] == '<' -> {
                    val start = i
                    i++
                    if (i < n && (text[i] == '/' || text[i] == '!')) i++
                    val nameStart = i
                    while (i < n && (text[i].isLetterOrDigit() || text[i] == ':' || text[i] == '-' || text[i] == '_')) i++
                    if (i > nameStart) out.add(nameStart, i, TokenKind.Tag)
                    while (i < n && text[i] != '>') {
                        while (i < n && text[i].isWhitespace()) i++
                        if (i < n && text[i] == '>') break
                        if (i + 1 < n && text[i] == '/' && text[i + 1] == '>') {
                            i += 2
                            break
                        }
                        val attrStart = i
                        while (i < n && (text[i].isLetterOrDigit() || text[i] == ':' || text[i] == '-' || text[i] == '_')) i++
                        if (i > attrStart) out.add(attrStart, i, TokenKind.Attribute)
                        while (i < n && text[i].isWhitespace()) i++
                        if (i < n && text[i] == '=') {
                            i++
                            while (i < n && text[i].isWhitespace()) i++
                            if (i < n && (text[i] == '"' || text[i] == '\'')) {
                                val q = text[i]
                                val s = i
                                i++
                                while (i < n && text[i] != q) i++
                                if (i < n) i++
                                out.add(s, i, TokenKind.String)
                            }
                        }
                    }
                    if (i < n && text[i] == '>') i++
                    // Don't highlight the '<' '>' as separate — tag name is enough.
                    if (start == i) i++ // safety
                }
                else -> i++
            }
        }
        return out.build()
    }
}

// --- Shell -------------------------------------------------------------------

internal object ShellHighlighter : Highlighter {
    private val keywords = setOf(
        "if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done", "case", "esac",
        "function", "select", "in", "time", "coproc", "[[", "]]",
    )

    override fun highlight(text: String): List<HighlightedSpan> {
        val out = SpanBuilder()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c == '#' && (i == 0 || text[i - 1] == '\n' || text[i - 1].isWhitespace()) -> {
                    // Not inside $# or ${#…} roughly: treat # after whitespace/start as comment.
                    val start = i
                    i = text.indexOf('\n', i).let { if (it < 0) n else it }
                    out.add(start, i, TokenKind.Comment)
                }
                c == '"' || c == '\'' -> {
                    val start = i
                    val q = c
                    i++
                    while (i < n && text[i] != q) {
                        if (q == '"' && text[i] == '\\') i++
                        i++
                        if (i >= n) break
                    }
                    if (i < n) i++
                    out.add(start, i, TokenKind.String)
                }
                c == '`' -> {
                    val start = i
                    i++
                    while (i < n && text[i] != '`') i++
                    if (i < n) i++
                    out.add(start, i, TokenKind.String)
                }
                c.isDigit() -> {
                    val start = i
                    while (i < n && text[i].isDigit()) i++
                    out.add(start, i, TokenKind.Number)
                }
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < n && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                    val word = text.substring(start, i)
                    if (word in keywords) out.add(start, i, TokenKind.Keyword)
                }
                else -> i++
            }
        }
        return out.build()
    }
}

// --- Markdown (light) --------------------------------------------------------

internal object MarkdownHighlighter : Highlighter {
    override fun highlight(text: String): List<HighlightedSpan> {
        val out = SpanBuilder()
        var i = 0
        val n = text.length
        while (i < n) {
            val lineStart = i
            // Headers at line start
            if ((lineStart == 0 || text[lineStart - 1] == '\n') && text[i] == '#') {
                var hashes = 0
                while (i < n && text[i] == '#' && hashes < 6) {
                    i++; hashes++
                }
                if (i < n && (text[i] == ' ' || text[i] == '\t')) {
                    while (i < n && text[i] != '\n') i++
                    out.add(lineStart, i, TokenKind.Header)
                    continue
                } else {
                    i = lineStart
                }
            }
            // Fenced code block
            if ((lineStart == 0 || text.getOrNull(lineStart - 1) == '\n') && text.startsWith("```", i)) {
                val start = i
                i += 3
                while (i < n && text[i] != '\n') i++
                if (i < n) i++
                while (i < n) {
                    val fence = i
                    if ((fence == 0 || text[fence - 1] == '\n') && text.startsWith("```", i)) {
                        while (i < n && text[i] != '\n') i++
                        break
                    }
                    i++
                }
                out.add(start, i, TokenKind.String)
                continue
            }
            // Inline `code`
            if (text[i] == '`') {
                val start = i
                i++
                while (i < n && text[i] != '`' && text[i] != '\n') i++
                if (i < n && text[i] == '`') {
                    i++
                    out.add(start, i, TokenKind.String)
                    continue
                }
                i = start + 1
                continue
            }
            // Links [text](url) or ![alt](url)
            if (text[i] == '[' || (text[i] == '!' && i + 1 < n && text[i + 1] == '[')) {
                val start = i
                if (text[i] == '!') i++
                i++ // [
                while (i < n && text[i] != ']' && text[i] != '\n') i++
                if (i < n && text[i] == ']') {
                    i++
                    if (i < n && text[i] == '(') {
                        i++
                        while (i < n && text[i] != ')' && text[i] != '\n') i++
                        if (i < n && text[i] == ')') {
                            i++
                            out.add(start, i, TokenKind.Link)
                            continue
                        }
                    }
                }
                i = start + 1
                continue
            }
            i++
        }
        return out.build()
    }
}

// --- YAML --------------------------------------------------------------------

internal object YamlHighlighter : Highlighter {
    override fun highlight(text: String): List<HighlightedSpan> {
        val out = SpanBuilder()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c == '#' && (i == 0 || text[i - 1] == '\n' || text[i - 1].isWhitespace()) -> {
                    val start = i
                    i = text.indexOf('\n', i).let { if (it < 0) n else it }
                    out.add(start, i, TokenKind.Comment)
                }
                c == '"' || c == '\'' -> {
                    val start = i
                    val q = c
                    i++
                    while (i < n && text[i] != q) {
                        if (text[i] == '\\') i++
                        i++
                        if (i >= n) break
                    }
                    if (i < n) i++
                    out.add(start, i, TokenKind.String)
                }
                (i == 0 || text[i - 1] == '\n') && (c.isLetter() || c == '_' || c == '.') -> {
                    // Possible key at line start (after indent handled by walking)
                    var j = i
                    // rewind isn't needed — we may be mid-indent. Find key: start of identifier after spaces was already skipped by else.
                    val keyStart = i
                    while (j < n && (text[j].isLetterOrDigit() || text[j] == '_' || text[j] == '-' || text[j] == '.')) j++
                    var k = j
                    while (k < n && (text[k] == ' ' || text[k] == '\t')) k++
                    if (k < n && text[k] == ':') {
                        out.add(keyStart, j, TokenKind.Attribute)
                        i = j
                    } else if (text.startsWith("true", i) && yamlWordEnd(text, i, 4) ||
                        text.startsWith("false", i) && yamlWordEnd(text, i, 5) ||
                        text.startsWith("null", i) && yamlWordEnd(text, i, 4) ||
                        text.startsWith("yes", i) && yamlWordEnd(text, i, 3) ||
                        text.startsWith("no", i) && yamlWordEnd(text, i, 2)
                    ) {
                        val len = when {
                            text.startsWith("false", i) -> 5
                            text.startsWith("true", i) || text.startsWith("null", i) -> 4
                            text.startsWith("yes", i) -> 3
                            else -> 2
                        }
                        out.add(i, i + len, TokenKind.Keyword)
                        i += len
                    } else {
                        i = j
                    }
                }
                (c == '-' && i + 1 < n && text[i + 1].isDigit()) || c.isDigit() -> {
                    val start = i
                    if (c == '-') i++
                    while (i < n && (text[i].isDigit() || text[i] == '.')) i++
                    out.add(start, i, TokenKind.Number)
                }
                else -> {
                    // Keys after list markers / indent: detect "word:" mid-line after whitespace start of token
                    if ((c.isLetter() || c == '_') && (i == 0 || text[i - 1].isWhitespace() || text[i - 1] == '-')) {
                        var j = i
                        while (j < n && (text[j].isLetterOrDigit() || text[j] == '_' || text[j] == '-' || text[j] == '.')) j++
                        var k = j
                        while (k < n && (text[k] == ' ' || text[k] == '\t')) k++
                        if (k < n && text[k] == ':' && (k + 1 >= n || text[k + 1].isWhitespace() || text[k + 1] == '\n')) {
                            out.add(i, j, TokenKind.Attribute)
                            i = j
                            continue
                        }
                    }
                    i++
                }
            }
        }
        return out.build()
    }

    private fun yamlWordEnd(text: String, i: Int, len: Int): Boolean {
        val end = i + len
        return end >= text.length || text[end].isWhitespace() || text[end] == ',' || text[end] == '#' || text[end] == '\n'
    }
}

// --- TOML --------------------------------------------------------------------

internal object TomlHighlighter : Highlighter {
    override fun highlight(text: String): List<HighlightedSpan> {
        val out = SpanBuilder()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c == '#' -> {
                    val start = i
                    i = text.indexOf('\n', i).let { if (it < 0) n else it }
                    out.add(start, i, TokenKind.Comment)
                }
                c == '[' -> {
                    val start = i
                    while (i < n && text[i] != ']' && text[i] != '\n') i++
                    if (i < n && text[i] == ']') i++
                    out.add(start, i, TokenKind.Header)
                }
                c == '"' || c == '\'' -> {
                    // Basic and multiline """ / '''
                    val start = i
                    if (i + 2 < n && text[i + 1] == c && text[i + 2] == c) {
                        val q = c
                        i += 3
                        while (i + 2 < n && !(text[i] == q && text[i + 1] == q && text[i + 2] == q)) {
                            if (text[i] == '\\') i++
                            i++
                        }
                        i = (i + 3).coerceAtMost(n)
                    } else {
                        val q = c
                        i++
                        while (i < n && text[i] != q) {
                            if (text[i] == '\\') i++
                            i++
                            if (i >= n) break
                        }
                        if (i < n) i++
                    }
                    out.add(start, i, TokenKind.String)
                }
                c.isDigit() || (c == '-' && i + 1 < n && text[i + 1].isDigit()) -> {
                    val start = i
                    if (c == '-') i++
                    while (i < n && (text[i].isDigit() || text[i] == '.' || text[i] == '_' || text[i] == 'e' || text[i] == 'E' || text[i] == '+' || text[i] == '-')) i++
                    out.add(start, i, TokenKind.Number)
                }
                text.startsWith("true", i) && tomlBound(text, i, 4) -> {
                    out.add(i, i + 4, TokenKind.Keyword); i += 4
                }
                text.startsWith("false", i) && tomlBound(text, i, 5) -> {
                    out.add(i, i + 5, TokenKind.Keyword); i += 5
                }
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < n && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '-')) i++
                    var k = i
                    while (k < n && text[k].isWhitespace() && text[k] != '\n') k++
                    if (k < n && text[k] == '=') out.add(start, i, TokenKind.Attribute)
                }
                else -> i++
            }
        }
        return out.build()
    }

    private fun tomlBound(text: String, i: Int, len: Int): Boolean {
        val end = i + len
        return end >= text.length || !text[end].isLetterOrDigit()
    }
}


object Highlighters {
    fun forLanguage(language: Language): Highlighter = when (language) {
        Language.Plain -> PlainHighlighter
        Language.KotlinJava -> KotlinJavaHighlighter
        Language.Json -> JsonHighlighter
        Language.XmlHtml -> XmlHtmlHighlighter
        Language.Shell -> ShellHighlighter
        Language.Markdown -> MarkdownHighlighter
        Language.Yaml -> YamlHighlighter
        Language.Toml -> TomlHighlighter
    }
}

private object PlainHighlighter : Highlighter {
    override fun highlight(text: String): List<HighlightedSpan> = emptyList()
}
