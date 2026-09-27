package wales.tucker.seren.files.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import wales.tucker.seren.core.ui.EmptyState
import wales.tucker.seren.files.ui.LocalStorageAccess

/**
 * Shows [content] once Seren Files may use shared storage; until then, says why it needs access
 * and asks for it.
 */
@Composable
fun AccessGate(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val access = LocalStorageAccess.current
    if (access.granted) {
        content()
    } else {
        EmptyState(
            icon = Icons.Rounded.FolderOpen,
            title = "Allow access to your files",
            message = "Seren Files needs all files access to browse, copy and move what's on this device. " +
                "It has no internet access, so nothing ever leaves your phone.",
            modifier = modifier,
        ) {
            Button(onClick = access.request) { Text("Allow access") }
        }
    }
}
