package wales.tucker.seren.files.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import wales.tucker.seren.core.ui.Avatar
import wales.tucker.seren.core.ui.theme.accentColor
import wales.tucker.seren.files.fs.FileEntry
import wales.tucker.seren.files.fs.FileKind
import wales.tucker.seren.files.fs.FileTypes
import wales.tucker.seren.files.ui.appContainer

val FileKind.icon: ImageVector
    get() = when (this) {
        FileKind.FOLDER -> Icons.Rounded.Folder
        FileKind.IMAGE -> Icons.Rounded.Image
        FileKind.VIDEO -> Icons.Rounded.Movie
        FileKind.AUDIO -> Icons.Rounded.MusicNote
        FileKind.PDF -> Icons.Rounded.PictureAsPdf
        FileKind.DOCUMENT -> Icons.Rounded.Description
        FileKind.TEXT -> Icons.AutoMirrored.Rounded.TextSnippet
        FileKind.ARCHIVE -> Icons.Rounded.FolderZip
        FileKind.APP -> Icons.Rounded.Android
        FileKind.OTHER -> Icons.AutoMirrored.Rounded.InsertDriveFile
    }

/** Each kind keeps one accent from the suite palette, so a kind of file always looks the same. */
val FileKind.accent: Color
    get() = accentColor(
        when (this) {
            FileKind.FOLDER -> 0
            FileKind.IMAGE -> 1
            FileKind.VIDEO, FileKind.PDF -> 2
            FileKind.DOCUMENT -> 3
            FileKind.ARCHIVE -> 4
            FileKind.TEXT, FileKind.APP -> 5
            FileKind.AUDIO -> 6
            FileKind.OTHER -> 7
        },
    )

/**
 * The avatar at the start of a file's row: a thumbnail for photos and videos when [thumbnails] is
 * on, otherwise the kind's icon on its accent. A tick replaces it while the row is [selected].
 */
@Composable
fun FileAvatar(entry: FileEntry, selected: Boolean = false, thumbnails: Boolean = true, size: Dp = 44.dp) {
    if (selected) {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size * 0.5f))
        }
        return
    }
    val kind = entry.kind
    val bitmap = if (thumbnails && FileTypes.hasThumbnail(kind)) rememberThumbnail(entry, size) else null
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)),
        )
    } else {
        Avatar(entry.name, kind.accent, size = size, icon = kind.icon)
    }
}

@Composable
private fun rememberThumbnail(entry: FileEntry, size: Dp): ImageBitmap? {
    val thumbnails = appContainer().thumbnails
    val px = with(LocalDensity.current) { size.roundToPx() }
    val cached = thumbnails.cached(entry, px)
    val loaded by produceState(cached, entry.path, entry.modified, px) {
        if (value == null) value = thumbnails.load(entry, px)
    }
    return loaded
}
