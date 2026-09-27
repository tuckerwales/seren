package wales.tucker.seren.files.ui.common

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import wales.tucker.seren.files.MainActivity
import wales.tucker.seren.files.R
import java.io.File

/** Folders pinned to the home screen, which open straight into Seren Files. */
object Shortcuts {
    /** Opens the folder at [EXTRA_PATH]. Only folders on the device's storage are opened. */
    const val ACTION_OPEN_FOLDER = "wales.tucker.seren.files.action.OPEN_FOLDER"
    const val EXTRA_PATH = "wales.tucker.seren.files.extra.PATH"

    fun openFolderIntent(context: Context, folder: File): Intent =
        Intent(ACTION_OPEN_FOLDER)
            .setClass(context, MainActivity::class.java)
            .putExtra(EXTRA_PATH, folder.path)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /**
     * Asks the launcher to pin [folder] to the home screen as [title]; it shows its own
     * confirmation. False when the launcher can't pin shortcuts.
     */
    fun pinFolder(context: Context, folder: File, title: String): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val shortcut = ShortcutInfoCompat.Builder(context, "folder:${folder.path}")
            .setShortLabel(title.ifEmpty { folder.path })
            .setLongLabel(title.ifEmpty { folder.path })
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_shortcut_folder))
            .setIntent(openFolderIntent(context, folder))
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
}
