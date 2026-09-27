package wales.tucker.seren.ssh.ui.common

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/** How long a sensitive clip stays on the clipboard before it is cleared. */
const val SENSITIVE_CLIP_TIMEOUT_MS = 60_000L

/**
 * Copies [text] to the clipboard. A [sensitive] clip is flagged so Android 13+ hides it in the
 * clipboard preview and keyboards do not suggest it, and is cleared again after
 * [SENSITIVE_CLIP_TIMEOUT_MS] if it is still on the clipboard.
 */
fun copyToClipboard(context: Context, label: String, text: String, sensitive: Boolean = false) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(label, text)
    if (sensitive) {
        clip.description.extras = PersistableBundle().apply {
            val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ClipDescription.EXTRA_IS_SENSITIVE
            } else {
                "android.content.extra.IS_SENSITIVE"
            }
            putBoolean(key, true)
        }
    }
    clipboard.setPrimaryClip(clip)
    if (sensitive) {
        Handler(Looper.getMainLooper()).postDelayed({
            val current = runCatching { clipboard.primaryClip }.getOrNull()
            if (current?.description?.label == label && current.itemCount > 0 && current.getItemAt(0).text?.toString() == text) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }, SENSITIVE_CLIP_TIMEOUT_MS)
    }
}
