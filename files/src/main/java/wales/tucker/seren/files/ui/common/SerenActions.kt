package wales.tucker.seren.files.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import wales.tucker.seren.core.suite.SuiteApp
import wales.tucker.seren.core.suite.rememberInstalled
import wales.tucker.seren.core.ui.LocalMessenger
import wales.tucker.seren.files.fs.FileEntry
import wales.tucker.seren.files.fs.FileTypes

/**
 * What the other Seren apps can do with a file, shown only for the ones that are installed:
 * "Open in Seren Edit" for text, "Import into Seren SSH" for a private key and "Upload with
 * Seren SSH" for anything.
 */
@Composable
fun SerenFileMenuItems(entry: FileEntry, close: () -> Unit) {
    if (entry.isDirectory) return
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val edit = rememberInstalled(SuiteApp.EDIT)
    val ssh = rememberInstalled(SuiteApp.SSH)

    @Composable
    fun item(text: String, icon: ImageVector, app: SuiteApp, action: () -> Boolean) {
        DropdownMenuItem(
            text = { Text(text) },
            leadingIcon = { Icon(icon, null) },
            onClick = {
                close()
                if (!action()) messenger.show("${app.appName} couldn't open ${entry.name}")
            },
        )
    }
    if (edit && FileTypes.looksLikeText(entry.name)) {
        item("Open in Seren Edit", Icons.Rounded.EditNote, SuiteApp.EDIT) { Opener.openInEdit(context, entry.file) }
    }
    if (ssh && FileTypes.looksLikePrivateKey(entry.name, entry.size)) {
        item("Import into Seren SSH", Icons.Rounded.Key, SuiteApp.SSH) { Opener.importKeyToSsh(context, entry.file) }
    }
    if (ssh) {
        item("Upload with Seren SSH", Icons.Rounded.CloudUpload, SuiteApp.SSH) { Opener.uploadWithSsh(context, listOf(entry.file)) }
    }
}
