package wales.tucker.terminal.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import wales.tucker.terminal.data.Settings
import wales.tucker.terminal.ui.common.appContainer
import wales.tucker.terminal.ui.terminal.EXTRA_KEY_CHOICES
import wales.tucker.terminal.ui.theme.MonoSmall

/** Picks which keys appear in the terminal's extra keys row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtraKeysScreen(settings: Settings, onBack: () -> Unit) {
    val repo = appContainer().settings
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extra keys") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                actions = {
                    TextButton(
                        onClick = { scope.launch { repo.resetExtraKeys() } },
                        enabled = settings.hiddenExtraKeys.isNotEmpty(),
                    ) { Text("Show all") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    "Choose the keys shown above the keyboard. The keyboard toggle is always shown.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            items(EXTRA_KEY_CHOICES, key = { it.label }) { key ->
                val visible = key.label !in settings.hiddenExtraKeys
                val toggle = { v: Boolean -> scope.launch { repo.setExtraKeyVisible(key.label, v) }; Unit }
                ListItem(
                    headlineContent = { Text(key.label, style = MonoSmall.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize)) },
                    supportingContent = if (key.description != key.label) ({ Text(key.description) }) else null,
                    trailingContent = { Switch(checked = visible, onCheckedChange = toggle) },
                    modifier = Modifier.clickable { toggle(!visible) },
                )
            }
        }
    }
}
