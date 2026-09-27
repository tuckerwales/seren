package wales.tucker.seren.files.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

/** "12 items" for a folder, "2.4 MB" for a file: the short line under a grid tile. */
fun entryAmount(entry: FileEntry, itemsText: (Int) -> String): String = when {
    !entry.isDirectory -> formatSize(entry.size)
    entry.childCount >= 0 -> itemsText(entry.childCount)
    else -> ""
}

/**
 * A file or folder in the grid view: a large thumbnail or icon, its name and its size. Long press
 * picks it; the selection bar has the actions a row's menu would.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileTile(
    entry: FileEntry,
    thumbnails: Boolean,
    itemsText: (Int) -> String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selected: Boolean = false,
) {
    val onColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else Color.Unspecified
    Column(
        Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FileAvatar(entry, selected = selected, thumbnails = thumbnails, size = 80.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            entry.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = onColor,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            entryAmount(entry, itemsText),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) onColor else MaterialTheme.colorScheme.outline,
            maxLines = 1,
        )
    }
}
