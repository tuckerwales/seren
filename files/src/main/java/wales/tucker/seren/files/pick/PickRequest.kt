package wales.tucker.seren.files.pick

import android.content.ClipData
import android.content.Context
import android.content.Intent
import wales.tucker.seren.files.fs.FileTypes
import wales.tucker.seren.files.ui.common.Opener
import java.io.File
import java.util.Locale

/**
 * What another app asked for with [Intent.ACTION_GET_CONTENT]: which types of file it can take,
 * and whether it takes several.
 */
data class PickRequest(val mimeTypes: List<String>, val multiple: Boolean) {

    /** Whether a file called [name] is one the app asked for. */
    fun accepts(name: String): Boolean {
        val type = FileTypes.mimeType(name).lowercase(Locale.ROOT)
        return mimeTypes.any { matches(it, type) }
    }

    /** "Choose a photo", "Choose files" and so on, for the top of the screen. */
    val title: String
        get() {
            // Named by family when every type asked for is in one, such as images.
            val kind = mimeTypes.map { it.substringBefore('/') }.distinct().singleOrNull()?.takeIf { it != "*" }
            val noun = when (kind) {
                "image" -> if (multiple) "photos" else "a photo"
                "video" -> if (multiple) "videos" else "a video"
                "audio" -> if (multiple) "audio files" else "an audio file"
                "text" -> if (multiple) "text files" else "a text file"
                else -> if (multiple) "files" else "a file"
            }
            return "Choose $noun"
        }

    /**
     * The result to hand back for [files]: a content link to each, readable by the app that asked
     * and by nothing else, and never a path.
     */
    fun result(context: Context, files: List<File>): Intent {
        require(files.isNotEmpty()) { "Nothing chosen" }
        val uris = files.map { Opener.uri(context, it) }
        return Intent().apply {
            data = uris.first()
            // Several go in the clip, which is also what carries the grant for each of them.
            clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object {
        fun from(intent: Intent?): PickRequest {
            val extra = intent?.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.filter { it.isNotBlank() }.orEmpty()
            val types = extra.ifEmpty { listOfNotNull(intent?.type?.takeIf { it.isNotBlank() }) }.ifEmpty { listOf("*/*") }
            return PickRequest(types.map { it.lowercase(Locale.ROOT) }, intent?.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false) ?: false)
        }

        /** Whether [type] fits [pattern]: an exact type, a whole family such as any image, or anything at all. */
        fun matches(pattern: String, type: String): Boolean = when {
            pattern == "*/*" || pattern == "*" -> true
            pattern.endsWith("/*") -> type.startsWith(pattern.dropLast(1))
            else -> pattern == type
        }
    }
}
