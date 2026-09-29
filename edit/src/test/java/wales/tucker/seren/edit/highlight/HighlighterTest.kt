package wales.tucker.seren.edit.highlight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlighterTest {

    private fun kinds(language: Language, text: String): List<Pair<String, TokenKind>> =
        Highlighters.forLanguage(language).highlight(text).map { text.substring(it.start, it.end) to it.kind }

    private fun has(language: Language, text: String, fragment: String, kind: TokenKind) {
        val spans = kinds(language, text)
        assertTrue(
            "expected $kind for \"$fragment\" in $spans",
            spans.any { it.first == fragment && it.second == kind },
        )
    }

    @Test
    fun detectsByExtension() {
        assertEquals(Language.KotlinJava, detectLanguage("Main.kt"))
        assertEquals(Language.KotlinJava, detectLanguage("build.gradle.kts"))
        assertEquals(Language.KotlinJava, detectLanguage("Foo.java"))
        assertEquals(Language.Json, detectLanguage("package.json"))
        assertEquals(Language.XmlHtml, detectLanguage("layout.xml"))
        assertEquals(Language.XmlHtml, detectLanguage("index.html"))
        assertEquals(Language.Shell, detectLanguage("run.sh"))
        assertEquals(Language.Markdown, detectLanguage("README.md"))
        assertEquals(Language.Yaml, detectLanguage("config.yml"))
        assertEquals(Language.Toml, detectLanguage("Cargo.toml"))
        assertEquals(Language.Plain, detectLanguage("notes.txt"))
    }

    @Test
    fun sniffsShebangAndXml() {
        assertEquals(Language.Shell, detectLanguage("run", "#!/usr/bin/env bash\necho hi\n"))
        assertEquals(Language.XmlHtml, detectLanguage("file", "<?xml version=\"1.0\"?>\n<root/>\n"))
        assertEquals(Language.Plain, detectLanguage("file", "just text\n"))
    }

    @Test
    fun plainEmitsNoSpans() {
        assertTrue(Highlighters.forLanguage(Language.Plain).highlight("anything").isEmpty())
    }

    @Test
    fun kotlinKeywordsStringsComments() {
        val src = """
            // greeting
            fun main() {
                val star = "seren"
                /* block */
            }
        """.trimIndent()
        has(Language.KotlinJava, src, "// greeting", TokenKind.Comment)
        has(Language.KotlinJava, src, "fun", TokenKind.Keyword)
        has(Language.KotlinJava, src, "val", TokenKind.Keyword)
        has(Language.KotlinJava, src, "\"seren\"", TokenKind.String)
        has(Language.KotlinJava, src, "/* block */", TokenKind.Comment)
        has(Language.KotlinJava, src, "main", TokenKind.Function)
    }

    @Test
    fun jsonStringsKeysAndLiterals() {
        val src = """{"name": "seren", "n": 1, "ok": true, "x": null}"""
        has(Language.Json, src, "\"name\"", TokenKind.Attribute)
        has(Language.Json, src, "\"seren\"", TokenKind.String)
        has(Language.Json, src, "1", TokenKind.Number)
        has(Language.Json, src, "true", TokenKind.Keyword)
        has(Language.Json, src, "null", TokenKind.Keyword)
    }

    @Test
    fun xmlCommentsAndTags() {
        val src = """<!-- hi --><note id="1">ok</note>"""
        has(Language.XmlHtml, src, "<!-- hi -->", TokenKind.Comment)
        has(Language.XmlHtml, src, "note", TokenKind.Tag)
        has(Language.XmlHtml, src, "id", TokenKind.Attribute)
        has(Language.XmlHtml, src, "\"1\"", TokenKind.String)
    }

    @Test
    fun shellCommentsAndStrings() {
        val src = """
            # setup
            if true; then
              echo "hello"
            fi
        """.trimIndent()
        has(Language.Shell, src, "# setup", TokenKind.Comment)
        has(Language.Shell, src, "if", TokenKind.Keyword)
        has(Language.Shell, src, "then", TokenKind.Keyword)
        has(Language.Shell, src, "fi", TokenKind.Keyword)
        has(Language.Shell, src, "\"hello\"", TokenKind.String)
    }

    @Test
    fun markdownHeadersFencesAndLinks() {
        val src = """
            # Title
            See [docs](https://example.com) and `code`.
            ```
            plain
            ```
        """.trimIndent()
        has(Language.Markdown, src, "# Title", TokenKind.Header)
        has(Language.Markdown, src, "[docs](https://example.com)", TokenKind.Link)
        has(Language.Markdown, src, "`code`", TokenKind.String)
        assertTrue(kinds(Language.Markdown, src).any { it.second == TokenKind.String && it.first.contains("plain") })
    }

    @Test
    fun yamlAndTomlAreCheap() {
        has(Language.Yaml, "name: seren # app\n", "name", TokenKind.Attribute)
        has(Language.Yaml, "name: seren # app\n", "# app", TokenKind.Comment)
        has(Language.Toml, "[package]\nname = \"seren\"\n", "[package]", TokenKind.Header)
        has(Language.Toml, "[package]\nname = \"seren\"\n", "name", TokenKind.Attribute)
        has(Language.Toml, "name = \"seren\"\n", "\"seren\"", TokenKind.String)
    }
}
