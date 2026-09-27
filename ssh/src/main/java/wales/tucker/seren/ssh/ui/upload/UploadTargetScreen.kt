package wales.tucker.seren.ssh.ui.upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wales.tucker.seren.core.ui.Avatar
import wales.tucker.seren.core.ui.EmptyState
import wales.tucker.seren.core.ui.GroupedTile
import wales.tucker.seren.core.ui.SectionHeader
import wales.tucker.seren.core.ui.StatusDot
import wales.tucker.seren.core.ui.groupedShape
import wales.tucker.seren.core.ui.theme.accentColor
import wales.tucker.seren.ssh.data.Host
import wales.tucker.seren.ssh.session.SessionState
import wales.tucker.seren.ssh.session.TerminalSession
import wales.tucker.seren.ssh.ui.common.appContainer
import wales.tucker.seren.ssh.ui.hosts.stateColor

/** Files shared with Seren SSH, with their names for the screens that show them. */
data class SharedFiles(val uris: List<Uri>, val names: List<String>) {
    val title: String get() = names.singleOrNull()?.let { "Upload $it" } ?: "Upload ${uris.size} files"

    companion object {
        /** Reads the files' names, which the links themselves rarely show. */
        fun resolve(context: Context, uris: List<Uri>): SharedFiles = SharedFiles(
            uris,
            uris.map { uri ->
                runCatching {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    }
                }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            },
        )
    }
}

/**
 * Where to upload files shared with Seren SSH: a session that is already open, or a saved host
 * to connect to. The SFTP browser then opens so people can pick the folder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadTargetScreen(
    files: SharedFiles,
    onSession: (TerminalSession) -> Unit,
    onHost: (Host) -> Unit,
    onCancel: () -> Unit,
) {
    val container = appContainer()
    val sessions by container.sessionManager.sessions.collectAsStateWithLifecycle()
    val hosts by container.database.hostDao().observeAll().collectAsStateWithLifecycle(initialValue = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(files.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, contentDescription = "Cancel") } },
            )
        },
    ) { padding ->
        val saved = hosts ?: return@Scaffold
        if (sessions.isEmpty() && saved.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Dns,
                title = "No servers yet",
                message = "Add a host in Seren SSH first, then share the files again.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    "Pick a server. Its files open next, so you can choose the folder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            if (sessions.isNotEmpty()) {
                item { SectionHeader("Open sessions", trailing = sessions.size.toString()) }
                itemsIndexed(sessions, key = { _, s -> "session:${s.id}" }) { i, session ->
                    val state by session.state.collectAsStateWithLifecycle()
                    val title by session.title.collectAsStateWithLifecycle()
                    GroupedTile(
                        shape = groupedShape(i, sessions.size),
                        title = title,
                        subtitle = session.spec.subtitle,
                        onClick = { onSession(session) },
                        leading = { StatusDot(stateColor(state)) },
                        meta = when (state) {
                            SessionState.Connected -> "Connected"
                            SessionState.Connecting -> "Connecting"
                            else -> "Disconnected"
                        },
                    )
                }
            }
            if (saved.isNotEmpty()) {
                item { SectionHeader("Hosts", trailing = saved.size.toString()) }
                itemsIndexed(saved, key = { _, h -> "host:${h.id}" }) { i, host ->
                    GroupedTile(
                        shape = groupedShape(i, saved.size),
                        title = host.displayName,
                        subtitle = host.address,
                        onClick = { onHost(host) },
                        leading = { Avatar(host.displayName, accentColor(host.color)) },
                    )
                }
            }
        }
    }
}
