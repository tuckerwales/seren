package wales.tucker.seren.auth.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import wales.tucker.seren.core.ui.theme.AccentColors

/** The eight accent colors as round swatches; the selected one has a check. */
@Composable
fun AccentPicker(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween) {
        AccentColors.forEachIndexed { i, color ->
            val isSelected = i == selected
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(i) }
                    .semantics { contentDescription = ACCENT_NAMES[i] },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Names of the accent colors, in the order of [AccentColors] (see docs/BRAND.md). */
val ACCENT_NAMES = listOf("Indigo", "Teal", "Red", "Amber", "Violet", "Cyan", "Pink", "Slate")
