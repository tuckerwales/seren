package wales.tucker.seren.edit.document

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/** A file or folder inside a folder the user gave Seren Edit access to. */
data class FolderEntry(
    val uri: Uri,
    val documentId: String,
    val name: String,
    val isFolder: Boolean,
    val size: Long,
    val lastModified: Long,
)

/** What the editor needs to know about a file before reading it. */
data class DocumentInfo(val name: String, val location: String, val size: Long?)

class FileTooLargeException(val size: Long) : Exception("It's larger than Seren Edit can open")

/**
 * Reads and writes the documents the user picks through the Storage Access Framework. Seren Edit has
 * no storage permission and no network access: it can only reach files the user chose.
 */
class DocumentStore(private val context: Context) {
    private val resolver: ContentResolver get() = context.contentResolver

    suspend fun info(uri: Uri): DocumentInfo = withContext(Dispatchers.IO) {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val file = File(requireNotNull(uri.path))
            return@withContext DocumentInfo(file.name, location(uri), file.length())
        }
        var name: String? = null
        var size: Long? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0)
                    size = if (c.isNull(1)) null else c.getLong(1)
                }
            }
        }
        DocumentInfo(name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Untitled", location(uri), size)
    }

    /** The file's bytes. Refuses files over [MAX_FILE_BYTES], which would be too slow to edit. */
    suspend fun read(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val input = resolver.openInputStream(uri) ?: throw FileNotFoundException("The file is no longer available")
        input.use {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = it.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_FILE_BYTES) throw FileTooLargeException(total)
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
    }

    /** Replaces the file's contents with [bytes]. */
    suspend fun write(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        // "wt" truncates; some providers only know "w", which on those truncates too.
        val output = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "w")
            ?: throw FileNotFoundException("The file is no longer available")
        output.use { it.write(bytes) }
    }

    /** Keeps access to [uri] across restarts, when the app that shared it allows that. */
    fun persistAccess(uri: Uri): Boolean = runCatching {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }.recoverCatching {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.isSuccess

    fun releaseAccess(uri: Uri) {
        runCatching {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    /** The files and folders in folder [documentId] of the folder tree [tree], folders first. */
    suspend fun list(tree: Uri, documentId: String): List<FolderEntry> = withContext(Dispatchers.IO) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val entries = mutableListOf<FolderEntry>()
        resolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                entries += FolderEntry(
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                    documentId = id,
                    name = c.getString(1) ?: id,
                    isFolder = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                    size = if (c.isNull(3)) 0 else c.getLong(3),
                    lastModified = if (c.isNull(4)) 0 else c.getLong(4),
                )
            }
        } ?: throw FileNotFoundException("The folder is no longer available")
        entries.sortedWith(compareBy<FolderEntry> { !it.isFolder }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    /** Creates an empty file called [name] in folder [documentId] of [tree] and returns its URI. */
    suspend fun createFile(tree: Uri, documentId: String, name: String): Uri = withContext(Dispatchers.IO) {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
        DocumentsContract.createDocument(resolver, parent, mimeTypeFor(name), name)
            ?: throw FileNotFoundException("The folder is no longer available")
    }

    /** A folder tree's display name, such as "Documents". */
    suspend fun treeName(tree: Uri): String = withContext(Dispatchers.IO) {
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        runCatching {
            resolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: DocumentsContract.getTreeDocumentId(tree).substringAfterLast(':').substringAfterLast('/').ifEmpty { "Folder" }
    }

    /**
     * Where [uri] lives, for display: "Internal storage/Documents" for local files, otherwise the
     * name of the app that provides it ("Downloads", "Drive").
     */
    fun location(uri: Uri): String {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val parent = File(uri.path.orEmpty()).parent.orEmpty()
            @Suppress("DEPRECATION") // Only to recognise the path, not to access it.
            val storage = Environment.getExternalStorageDirectory().path
            return if (parent == storage || parent.startsWith("$storage/")) "Internal storage" + parent.removePrefix(storage) else parent
        }
        val authority = uri.authority ?: return ""
        if (authority == EXTERNAL_STORAGE_AUTHORITY) {
            val id = runCatching {
                if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.getDocumentId(uri) else DocumentsContract.getTreeDocumentId(uri)
            }.getOrNull()
            if (id != null) return storagePath(id, parentOnly = DocumentsContract.isDocumentUri(context, uri))
        }
        val pm = context.packageManager
        return runCatching { pm.resolveContentProvider(authority, 0)?.loadLabel(pm)?.toString() }.getOrNull() ?: authority
    }

    companion object {
        /** The largest file Seren Edit opens; bigger ones would make typing slow. */
        const val MAX_FILE_BYTES = 2L * 1024 * 1024

        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

        /** "primary:Documents/notes.txt" becomes "Internal storage/Documents" (or ".../notes.txt"). */
        fun storagePath(documentId: String, parentOnly: Boolean): String {
            val volume = documentId.substringBefore(':')
            val path = documentId.substringAfter(':', "")
            val shown = if (parentOnly) path.substringBeforeLast('/', "") else path
            val root = if (volume == "primary") "Internal storage" else volume
            return if (shown.isEmpty()) root else "$root/$shown"
        }

        /**
         * The MIME type to create [name] with. Unknown extensions (".kt", "Makefile") use
         * application/octet-stream, because with text/plain storage providers append ".txt".
         */
        fun mimeTypeFor(name: String): String {
            val extension = name.substringAfterLast('.', "").lowercase()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        }
    }
}
