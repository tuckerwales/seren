package wales.tucker.seren.files.fs

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File

/**
 * Works out which file a link from another app points at, so Seren Files can show where it is
 * (for "Show in Seren Files" after a download in Seren SSH). Only the link is read, never the
 * file, and only files on the device's storage volumes are shown.
 */
object Reveal {
    const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
    const val DOWNLOADS = "com.android.providers.downloads.documents"
    const val MEDIA = "media"

    /** The file [uri] names, or null when it isn't a file on one of [volumes]. */
    fun locate(context: Context, uri: Uri, volumes: List<Volume>, displayName: String? = null): File? {
        val file = when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path?.let(::File)
            ContentResolver.SCHEME_CONTENT -> fromContent(context, uri, volumes, displayName)
            else -> null
        } ?: return null
        return file.takeIf { onVolume(it, volumes) }
    }

    private fun fromContent(context: Context, uri: Uri, volumes: List<Volume>, displayName: String?): File? {
        val authority = uri.authority ?: return null
        return when (authority) {
            EXTERNAL_STORAGE -> documentId(uri)?.let { fromStorageId(it, volumes) }
            DOWNLOADS -> documentId(uri)?.let { fromDownloadsId(context, it, volumes, displayName) }
            MEDIA -> dataColumn(context, uri)
            "${context.packageName}.files" -> fromOwnProvider(uri, volumes)
            else -> null
        }
    }

    /** "primary:Download/notes.txt", "1234-ABCD:Photos/a.jpg" or "home:notes.txt" (Documents). */
    internal fun fromStorageId(id: String, volumes: List<Volume>): File? {
        val root = id.substringBefore(':', "")
        val path = id.substringAfter(':', "")
        if (root.isEmpty()) return null
        val primary = volumes.firstOrNull { it.primary }?.root ?: return null
        val base = when (root) {
            "primary" -> primary
            "home" -> File(primary, "Documents")
            else -> volumes.firstOrNull { it.root.name.equals(root, ignoreCase = true) }?.root ?: return null
        }
        return if (path.isEmpty()) base else File(base, path)
    }

    /** "raw:/storage/emulated/0/Download/a.txt", "msf:123" (a media store id) or a plain number. */
    private fun fromDownloadsId(context: Context, id: String, volumes: List<Volume>, displayName: String?): File? {
        if (id.startsWith("raw:")) return File(id.removePrefix("raw:"))
        if (id.startsWith("msf:")) {
            id.removePrefix("msf:").toLongOrNull()
                ?.let { dataColumn(context, ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), it)) }
                ?.let { return it }
        }
        // Older download links are only numbers: look for the name in Download.
        val primary = volumes.firstOrNull { it.primary }?.root ?: return null
        return displayName?.let { File(File(primary, "Download"), it) }?.takeIf { it.exists() }
    }

    /** Seren Files' own links: "/storage/<path on internal storage>" or "/volumes/<path under /storage>". */
    internal fun fromOwnProvider(uri: Uri, volumes: List<Volume>): File? {
        val segments = uri.pathSegments
        if (segments.size < 2) return null
        val rest = segments.drop(1).joinToString("/")
        return when (segments.first()) {
            "storage" -> volumes.firstOrNull { it.primary }?.let { File(it.root, rest) }
            "volumes" -> File("/storage", rest)
            else -> null
        }
    }

    private fun documentId(uri: Uri): String? {
        // content://<authority>/document/<id> or .../tree/<id>/document/<id>
        val segments = uri.pathSegments
        val at = segments.lastIndexOf("document")
        return if (at >= 0 && at + 1 < segments.size) segments[at + 1] else null
    }

    @Suppress("DEPRECATION") // DATA is the only way to get a path, and works with all files access.
    private fun dataColumn(context: Context, uri: Uri): File? = runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let(::File) else null
        }
    }.getOrNull()

    private fun onVolume(file: File, volumes: List<Volume>): Boolean {
        val path = runCatching { file.canonicalPath }.getOrNull() ?: return false
        return volumes.any { v ->
            val root = runCatching { v.root.canonicalPath }.getOrNull() ?: return@any false
            path == root || path.startsWith("$root/")
        }
    }
}
