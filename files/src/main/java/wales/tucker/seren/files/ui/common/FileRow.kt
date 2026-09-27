package wales.tucker.seren.files.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import wales.tucker.seren.core.ui.GroupedTile
import wales.tucker.seren.core.ui.formatSize
import wales.tucker.seren.core.ui.relativeTime
import wales.tucker.seren.files.fs.FileEntry

/** "12 items · 5 min ago" for a folder, "2.4 MB · 3 d ago" for a file. */
fun entryMeta(entry: FileEntry, itemsText: (Int) -> String): String {
    val amount = when {
        !entry.isDirectory -> formatSize(entry.size)
        entry.childCount >= 0 -> itemsText(entry.childCount)
        else -> null
    }
    return listOfNotNull(amount, relativeTime(entry.modified)).joinToString("  ·  ")
}

/** A file or folder in a grouped list. [subtitle] is a mono path, for search results and recent files. */
@Composable
fun FileRow(
    entry: FileEntry,
    shape: Shape,
    thumbnails: Boolean,
    itemsText: (Int) -> String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    subtitle: String? = null,
    menu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
) {
    GroupedTile(
        shape = shape,
        title = entry.name,
        subtitle = subtitle,
        meta = entryMeta(entry, itemsText),
        onClick = onClick,
        onLongClick = onLongClick,
        selected = selected,
        leading = { FileAvatar(entry, selected = selected, thumbnails = thumbnails) },
        menu = menu,
    )
}
