package wales.tucker.seren.auth.ui.accounts

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import wales.tucker.seren.auth.R
import wales.tucker.seren.auth.data.AccountRepository
import wales.tucker.seren.auth.otp.CopiedSetup
import wales.tucker.seren.core.ui.Avatar
import wales.tucker.seren.core.ui.theme.MonoSmall
import wales.tucker.seren.core.ui.theme.accentColor

/**
 * Looks at the clipboard for a setup link or key to offer. Only the clip's description is looked
 * at until it changes, since reading the text itself makes Android 12 and later tell the user
 * Seren Auth pasted from the clipboard.
 */
fun AccountsViewModel.checkClipboard(context: Context) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val description = runCatching { clipboard.primaryClipDescription }.getOrNull()
    checkCopied(description?.timestamp ?: 0L) {
        if (description == null || !description.hasMimeType("text/*")) return@checkCopied null
        val item = runCatching { clipboard.primaryClip }.getOrNull()?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        // Text only: coercing a content URI to text would read a whole file on the main thread.
        (item?.text ?: item?.uri?.takeIf { description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST) })?.toString()
    }
}

/** Offers to add the setup link or key the user just copied, above their accounts. */
@Composable
fun CopiedSetupCard(copied: CopiedSetup, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val (label, title, detail) = when (copied) {
        is CopiedSetup.Link -> Triple("Copied setup link", copied.token.title.ifBlank { "Unnamed account" }, copied.token.name.takeIf { copied.token.issuer.isNotBlank() })
        is CopiedSetup.Transfer -> Triple(
            "Copied Google Authenticator transfer",
            pluralStringResource(R.plurals.accounts_count, copied.tokens.size, copied.tokens.size),
            null,
        )
        is CopiedSetup.Key -> Triple("Copied setup key", "New account", null)
    }
    val action = when (copied) {
        is CopiedSetup.Transfer -> pluralStringResource(R.plurals.import_accounts, copied.tokens.size, copied.tokens.size)
        else -> "Add"
    }

    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CardShape)
            .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
            when (copied) {
                is CopiedSetup.Link -> Avatar(title, accentColor(AccountRepository.defaultColor(copied.token)))
                is CopiedSetup.Transfer -> Avatar(title, MaterialTheme.colorScheme.primary, icon = Icons.Rounded.ContentPaste)
                is CopiedSetup.Key -> Avatar(title, MaterialTheme.colorScheme.primary, icon = Icons.Rounded.Key)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!detail.isNullOrBlank()) {
                    Text(detail, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = onDismiss) { Text("Not now") }
            Button(onClick = onAdd) { Text(action) }
        }
    }
}

/** Matches the account cards below it, so it reads as one more card rather than a banner. */
private val CardShape = RoundedCornerShape(20.dp)
