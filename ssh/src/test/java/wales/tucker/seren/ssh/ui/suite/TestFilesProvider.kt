package wales.tucker.seren.ssh.ui.suite

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/** Serves files from a folder by content link, as Seren Files' FileProvider does. */
class TestFilesProvider : ContentProvider() {
    private val folder get() = File(context!!.cacheDir, "shared").apply { mkdirs() }

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val file = File(folder, uri.lastPathSegment!!)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(columns.map { if (it == OpenableColumns.SIZE) file.length() else file.name })
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(File(folder, uri.lastPathSegment!!), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun getType(uri: Uri) = "application/octet-stream"
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0

    companion object {
        const val AUTHORITY = "wales.tucker.seren.ssh.test.files"

        fun share(context: android.content.Context, name: String, text: String): Uri {
            File(context.cacheDir, "shared").apply { mkdirs() }.resolve(name).writeText(text)
            return Uri.parse("content://$AUTHORITY/$name")
        }
    }
}
