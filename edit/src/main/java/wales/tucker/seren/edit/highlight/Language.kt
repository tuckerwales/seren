package wales.tucker.seren.edit.highlight

/** Languages Seren Edit can color. [Plain] means no spans — current single-color behavior. */
enum class Language {
    Plain,
    KotlinJava,
    Json,
    XmlHtml,
    Shell,
    Markdown,
    Yaml,
    Toml,
}

/**
 * Picks a language from [fileName] (extension), with a light content sniff for shebang and XML
 * when the extension is unknown.
 */
fun detectLanguage(fileName: String, content: String = ""): Language {
    val base = fileName.substringAfterLast('/').substringAfterLast('\\')
    val ext = base.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    when (ext) {
        "kt", "kts", "java" -> return Language.KotlinJava
        "json" -> return Language.Json
        "xml", "html", "htm", "svg", "xhtml" -> return Language.XmlHtml
        "sh", "bash", "zsh" -> return Language.Shell
        "md", "markdown" -> return Language.Markdown
        "yml", "yaml" -> return Language.Yaml
        "toml" -> return Language.Toml
    }
    val head = content.take(256).trimStart()
    return when {
        head.startsWith("#!") && Regex("""\b(ba|z)?sh\b""").containsMatchIn(head.takeWhile { it != '\n' }) ->
            Language.Shell
        head.startsWith("<?xml", ignoreCase = true) ||
            head.startsWith("<!DOCTYPE", ignoreCase = true) -> Language.XmlHtml
        else -> Language.Plain
    }
}
