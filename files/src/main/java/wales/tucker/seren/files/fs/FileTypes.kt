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
        listOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst", "jar")
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

    /** Whether Seren Files can make a thumbnail for this kind of file. */
    fun hasThumbnail(kind: FileKind): Boolean = kind == FileKind.IMAGE || kind == FileKind.VIDEO
}
