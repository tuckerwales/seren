package wales.tucker.seren.files.ui.common

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import wales.tucker.seren.files.fs.FileTypes
import java.io.File

/** Hands files to other apps through a content link, never a raw path. */
object Opener {
    fun uri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /**
     * Opens [file] in the app people picked for its type, or asks which app when [choose] is true.
     * Returns false when no app can open it.
     */
    fun open(context: Context, file: File, choose: Boolean = false): Boolean {
        val uri = runCatching { uri(context, file) }.getOrElse { return false }
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, FileTypes.mimeType(file.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val intent = if (choose) Intent.createChooser(view, "Open ${file.name} with") else view
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /** The share sheet for [files], which must all be files rather than folders. */
    fun share(context: Context, files: List<File>) {
        val uris = files.map { uri(context, it) }
        val types = files.map { FileTypes.mimeType(it.name) }.distinct()
        val type = types.singleOrNull() ?: types.map { it.substringBefore('/') }.distinct().singleOrNull()?.let { "$it/*" } ?: "*/*"
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.type = type
        send.clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val title = if (files.size == 1) "Share ${files.single().name}" else "Share ${files.size} files"
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
