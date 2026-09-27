package wales.tucker.seren.files.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import wales.tucker.seren.core.ui.LocalMessenger
import wales.tucker.seren.files.fs.FileEntry
import wales.tucker.seren.files.ui.Navigator

/**
 * The menu for a file shown away from its folder, in Recent or a category: open it elsewhere,
 * share it, go to its folder, see its details and, when [onDelete] is given, delete it.
 */
@Composable
fun ColumnScope.FoundFileMenuItems(
    entry: FileEntry,
    close: () -> Unit,
    navigator: Navigator,
    onDetails: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current

    @Composable
    fun item(text: String, icon: ImageVector, action: () -> Unit) {
        DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = { close(); action() })
    }
    item("Open with", Icons.AutoMirrored.Rounded.OpenInNew) {
        if (!Opener.open(context, entry.file, choose = true)) messenger.show("No app on this device can open ${entry.name}")
    }
    SerenFileMenuItems(entry, close)
    item("Share", Icons.Rounded.Share) { Opener.share(context, listOf(entry.file)) }
    item("Show in folder", Icons.Rounded.FolderOpen) { entry.file.parentFile?.let { navigator.reveal(entry.file) } }
    item("Details", Icons.Rounded.Info, onDetails)
    if (onDelete != null) {
        HorizontalDivider()
        item("Delete", Icons.Rounded.Delete, onDelete)
    }
}
