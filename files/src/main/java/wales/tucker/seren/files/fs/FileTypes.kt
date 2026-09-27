package wales.tucker.seren.files.fs

import android.webkit.MimeTypeMap
import java.util.Locale

/** What a file holds, as far as its name tells: picks its icon, color and how it opens. */
enum class FileKind(val label: String) {
    FOLDER("Folder"),
    IMAGE("Image"),
    VIDEO("Video"),
    AUDIO("Audio"),
    PDF("PDF document"),
    DOCUMENT("Document"),
    TEXT("Text"),
    ARCHIVE("Archive"),
    APP("Android app"),
    OTHER("File"),
}

object FileTypes {
    private val kinds: Map<String, FileKind> = buildMap {
        listOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "avif", "svg", "dng", "raw", "tif", "tiff", "ico")
            .forEach { put(it, FileKind.IMAGE) }
        listOf("mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v", "wmv", "flv", "ts")
            .forEach { put(it, FileKind.VIDEO) }
        listOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "amr", "mid", "midi")
            .forEach { put(it, FileKind.AUDIO) }
        put("pdf", FileKind.PDF)
        listOf("doc", "docx", "odt", "rtf", "xls", "xlsx", "ods", "csv", "ppt", "pptx", "odp", "epub", "mobi", "pages", "numbers", "key")
            .forEach { put(it, FileKind.DOCUMENT) }
        listOf(
            "txt", "md", "markdown", "log", "json", "xml", "yml", "yaml", "toml", "ini", "conf", "cfg", "properties",
            "html", "htm", "css", "js", "ts", "kt", "kts", "java", "py", "rb", "go", "rs", "c", "h", "cpp", "hpp", "cs",
            "swift", "sh", "bash", "zsh", "sql", "gradle", "srt", "vtt", "tex", "env",
        ).forEach { putIfAbsent(it, FileKind.TEXT) }
        listOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "tbz", "tbz2", "xz", "txz", "zst", "jar")
            .forEach { put(it, FileKind.ARCHIVE) }
        listOf("apk", "apks", "xapk", "aab").forEach { put(it, FileKind.APP) }
    }

    /** Types Android's own table may not know, so files still open in the right app. */
    private val extraMimeTypes = mapOf(
        "md" to "text/markdown",
        "markdown" to "text/markdown",
        "log" to "text/plain",
        "yml" to "text/yaml",
        "yaml" to "text/yaml",
        "toml" to "text/plain",
        "kt" to "text/x-kotlin",
        "kts" to "text/x-kotlin",
        "heic" to "image/heic",
        "heif" to "image/heif",
        "avif" to "image/avif",
        "webp" to "image/webp",
        "opus" to "audio/ogg",
        "mkv" to "video/x-matroska",
        "epub" to "application/epub+zip",
        "apk" to "application/vnd.android.package-archive",
        "7z" to "application/x-7z-compressed",
    )

    /** The part after the last dot, in lower case, or "" (".bashrc" has none). */
    fun extension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0 || dot == name.lastIndex) "" else name.substring(dot + 1).lowercase(Locale.ROOT)
    }

    fun kind(name: String, isDirectory: Boolean): FileKind =
        if (isDirectory) FileKind.FOLDER else kinds[extension(name)] ?: FileKind.OTHER

    /** The MIME type to open [name] with, falling back to a generic type for its kind. */
    fun mimeType(name: String): String {
        val ext = extension(name)
        if (ext.isNotEmpty()) {
            runCatching { MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) }.getOrNull()?.let { return it }
            extraMimeTypes[ext]?.let { return it }
        }
        return when (kind(name, isDirectory = false)) {
            FileKind.IMAGE -> "image/*"
            FileKind.VIDEO -> "video/*"
            FileKind.AUDIO -> "audio/*"
            FileKind.PDF -> "application/pdf"
            FileKind.TEXT -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    /** Extensionless and config files that are text too, such as ".bashrc", "Makefile" or "id_rsa.pub". */
    private val textExtensions = setOf("pub", "pem", "crt", "csr", "ppk", "rules", "service", "desktop", "lock", "gitignore")

    /**
     * Whether [name] is worth offering to a text editor: text by its name, or with no extension at
     * all (dotfiles, "Makefile", "config"), which is nearly always text on a phone.
     */
    fun looksLikeText(name: String): Boolean {
        val ext = extension(name)
        return kind(name, isDirectory = false) == FileKind.TEXT || ext.isEmpty() || ext in textExtensions
    }

    /** The type to hand [name] to a text editor with, which only accepts text types. */
    fun textMimeType(name: String): String =
        mimeType(name).takeIf { it.startsWith("text/") || it in editorTypes } ?: "text/plain"

    private val editorTypes = setOf(
        "application/json", "application/xml", "application/javascript", "application/x-sh", "application/x-yaml", "application/toml",
    )

    /** The largest file that could be a private key: keys are a few kilobytes at most. */
    const val MAX_KEY_SIZE = 64 * 1024L

    /** Whether [name] looks like an SSH private key: "id_ed25519", "server.pem", "work.ppk". */
    fun looksLikePrivateKey(name: String, size: Long): Boolean {
        if (size <= 0 || size > MAX_KEY_SIZE) return false
        val lower = name.lowercase(Locale.ROOT)
        return (lower.startsWith("id_") && extension(lower).isEmpty()) || extension(lower) in setOf("pem", "ppk")
    }

    /** Whether Seren Files can make a thumbnail for this kind of file. */
    fun hasThumbnail(kind: FileKind): Boolean = kind == FileKind.IMAGE || kind == FileKind.VIDEO
}
