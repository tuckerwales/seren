package wales.tucker.seren.files.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import wales.tucker.seren.core.ui.formatSize
import wales.tucker.seren.files.ops.FileClipboard
import wales.tucker.seren.files.ops.IncomingFile
import wales.tucker.seren.files.ops.Operation

/** A copy, move, compress or extract in progress, with how far it's got and Cancel. */
@Composable
fun OperationCard(op: Operation, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Sync, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(op.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val amount = if (op.total > 0 && op.countsItems) {
                        "${op.done} of ${op.total}"
                    } else if (op.total > 0) {
                        "${formatSize(op.done)} of ${formatSize(op.total)}"
                    } else {
                        ""
                    }
                    Text(
                        listOf(op.detail, amount).filter { it.isNotEmpty() }.joinToString("  ·  "),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, contentDescription = "Cancel") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(end = 12.dp)) {
                if (op.total > 0) {
                    LinearProgressIndicator(progress = { (op.done.toFloat() / op.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Shown while files picked with Copy or Move wait to be pasted into the open folder. */
@Composable
fun PasteBar(clip: FileClipboard, itemsText: (Int) -> String, enabled: Boolean, onPaste: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, contentDescription = "Cancel ${if (clip.move) "move" else "copy"}") }
            Icon(
                if (clip.move) Icons.AutoMirrored.Rounded.DriveFileMove else Icons.Rounded.ContentCopy,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val what = clip.files.singleOrNull()?.name ?: itemsText(clip.files.size)
                Text("${if (clip.move) "Moving" else "Copying"} $what", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Open a folder, then paste", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onPaste, enabled = enabled) { Text("Paste") }
        }
    }
}

/**
 * Shown while files another app shared wait to be saved. In a folder, [onSave] saves them there;
 * elsewhere it's null and the bar says to open a folder.
 */
@Composable
fun SaveBar(files: List<IncomingFile>, itemsText: (Int) -> String, enabled: Boolean, onSave: (() -> Unit)?, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, contentDescription = "Don't save") }
            Icon(Icons.Rounded.SaveAlt, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val what = files.singleOrNull()?.name ?: itemsText(files.size)
                Text("Saving $what", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (onSave != null) "Save here, or open another folder" else "Open a folder to save in",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (onSave != null) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSave, enabled = enabled) { Text("Save here") }
            }
        }
    }
}
