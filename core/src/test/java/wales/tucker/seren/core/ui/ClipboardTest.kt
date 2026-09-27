package wales.tucker.seren.core.ui

import android.app.Application
import android.content.ClipDescription
import android.content.ClipboardManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ClipboardTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val clipboard = context.getSystemService(ClipboardManager::class.java)

    private fun clipText() = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

    @Test
    fun sensitiveClipsAreFlaggedAndCleared() {
        copyToClipboard(context, "Private key", "secret", sensitive = true)
        assertEquals("secret", clipText())
        assertTrue(clipboard.primaryClipDescription!!.extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))

        ShadowLooper.idleMainLooper(SENSITIVE_CLIP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        assertNull(clipText())
    }

    @Test
    fun sensitiveClipsAreNotClearedOnceReplaced() {
        copyToClipboard(context, "Private key", "secret", sensitive = true)
        copyToClipboard(context, "Public key", "public")
        ShadowLooper.idleMainLooper(SENSITIVE_CLIP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        assertEquals("public", clipText())
        assertFalse(clipboard.primaryClipDescription!!.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) ?: false)
    }
}
