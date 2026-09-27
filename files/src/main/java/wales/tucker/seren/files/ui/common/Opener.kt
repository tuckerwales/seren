package wales.tucker.seren.files.ui.common

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import wales.tucker.seren.files.fs.FileTypes
import java.io.File
import wales.tucker.seren.core.suite.Suite
import wales.tucker.seren.core.suite.SuiteApp

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

    /** Opens [file] in Seren Edit, which may save its changes back. */
    fun openInEdit(context: Context, file: File): Boolean {
        val uri = runCatching { uri(context, file) }.getOrElse { return false }
        return Suite.launch(context, Suite.viewIntent(SuiteApp.EDIT, uri, FileTypes.textMimeType(file.name), writable = true))
    }

    /** Hands [files] to Seren SSH, which asks which server to upload them to. */
    fun uploadWithSsh(context: Context, files: List<File>): Boolean {
        val uris = runCatching { files.map { uri(context, it) } }.getOrElse { return false }
        return Suite.launch(context, Suite.sendIntent(SuiteApp.SSH, uris, shareType(files)))
    }

    /** Hands a private key to Seren SSH's Import key, where people check it and import it. */
    fun importKeyToSsh(context: Context, file: File): Boolean {
        val uri = runCatching { uri(context, file) }.getOrElse { return false }
        return Suite.launch(context, Suite.importKeyIntent(uri, file.name))
    }

    /** One type for all of [files]: theirs if they share one, "image/..." for mixed images, and so on. */
    private fun shareType(files: List<File>): String {
        val types = files.map { FileTypes.mimeType(it.name) }.distinct()
        return types.singleOrNull() ?: types.map { it.substringBefore('/') }.distinct().singleOrNull()?.let { "$it/*" } ?: "*/*"
    }

    /** The share sheet for [files], which must all be files rather than folders. */
    fun share(context: Context, files: List<File>) {
        val uris = files.map { uri(context, it) }
        val type = shareType(files)
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
