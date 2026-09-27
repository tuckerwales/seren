package wales.tucker.seren.files.ops

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat
import wales.tucker.seren.files.fs.FileOps
import wales.tucker.seren.files.fs.Volume
import java.io.File

/**
 * Something another app shared with Seren Files to save: a file behind a content link, or shared
 * text, which is saved as a .txt file.
 */
data class IncomingFile(val name: String, val size: Long, val uri: Uri? = null, val text: String? = null)

object Incoming {
    /**
     * What a share ([Intent.ACTION_SEND] or [Intent.ACTION_SEND_MULTIPLE]) holds, or an empty list
     * when there's nothing Seren Files can save. Links to files are accepted only through a
     * content provider, or as a plain path already on shared storage, so another app can't have
     * Seren Files copy out its own private files.
     */
    fun fromIntent(context: Context, intent: Intent, volumes: List<Volume>): List<IncomingFile> {
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> return emptyList()
        }
        if (uris.isEmpty()) {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() } ?: return emptyList()
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() }
            val name = FileOps.safeName(subject?.take(80), fallback = "Shared text").let { if (it.endsWith(".txt", ignoreCase = true)) it else "$it.txt" }
            return listOf(IncomingFile(name, text.toByteArray().size.toLong(), text = text))
        }
        return uris.filter { allowed(context, it, volumes) }.map { describe(context, it) }
    }

    private fun allowed(context: Context, uri: Uri, volumes: List<Volume>): Boolean = when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> true
        ContentResolver.SCHEME_FILE -> {
            val path = uri.path?.let { runCatching { File(it).canonicalPath }.getOrNull() }
            path != null && !path.startsWith(context.applicationInfo.dataDir) && volumes.any { v ->
                val root = runCatching { v.root.canonicalPath }.getOrNull()
                root != null && path.startsWith("$root/")
            }
        }
        else -> false
    }

    /** The name and size the link's provider reports, falling back to the link's last part. */
    private fun describe(context: Context, uri: Uri): IncomingFile {
        var name: String? = null
        var size = -1L
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        name = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(c::getString)
                        size = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getLong) ?: -1L
                    }
                }
            }
        } else if (uri.scheme == ContentResolver.SCHEME_FILE) {
            size = uri.path?.let { File(it).length() } ?: -1L
        }
        return IncomingFile(FileOps.safeName(name ?: uri.lastPathSegment), size, uri = uri)
    }
}
