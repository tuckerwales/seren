package wales.tucker.seren.core.suite

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SuiteTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val uri = Uri.parse("content://wales.tucker.seren.files.files/root/storage/emulated/0/notes.txt")

    private fun install(app: SuiteApp) {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = app.packageName })
    }

    @Test
    fun knowsWhichAppsAreInstalled() {
        assertFalse(Suite.isInstalled(context, SuiteApp.EDIT))
        install(SuiteApp.EDIT)
        assertTrue(Suite.isInstalled(context, SuiteApp.EDIT))
        assertFalse(Suite.isInstalled(context, SuiteApp.SSH))
    }

    @Test
    fun appsAreFoundByPackage() {
        assertEquals(SuiteApp.FILES, SuiteApp.of("wales.tucker.seren.files"))
        assertNull(SuiteApp.of("com.example.other"))
        assertNull(SuiteApp.of(null))
    }

    @Test
    fun viewingGrantsOnlyWhatIsAskedFor() {
        val read = Suite.viewIntent(SuiteApp.EDIT, uri, "text/plain")
        assertEquals(Intent.ACTION_VIEW, read.action)
        assertEquals("wales.tucker.seren.edit", read.`package`)
        assertEquals(uri, read.data)
        assertEquals("text/plain", read.type)
        assertTrue(read.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, read.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        val write = Suite.viewIntent(SuiteApp.EDIT, uri, "text/plain", writable = true)
        assertTrue(write.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
    }

    @Test
    fun sendingOneOrManyCarriesEveryLinkInTheClip() {
        val one = Suite.sendIntent(SuiteApp.SSH, listOf(uri), "text/plain")
        assertEquals(Intent.ACTION_SEND, one.action)
        assertEquals("wales.tucker.seren.ssh", one.`package`)
        assertEquals(1, one.clipData!!.itemCount)
        assertTrue(one.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)

        val other = Uri.parse("content://wales.tucker.seren.files.files/root/storage/emulated/0/photo.jpg")
        val many = Suite.sendIntent(SuiteApp.SSH, listOf(uri, other), "*/*")
        assertEquals(Intent.ACTION_SEND_MULTIPLE, many.action)
        assertEquals(2, many.clipData!!.itemCount)
        assertEquals(other, many.clipData!!.getItemAt(1).uri)
        @Suppress("DEPRECATION")
        assertEquals(listOf(uri, other), many.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM))
    }

    @Test
    fun serenActionsGoToTheRightApp() {
        val key = Suite.importKeyIntent(uri, "id_ed25519")
        assertEquals(Suite.ACTION_IMPORT_KEY, key.action)
        assertEquals("wales.tucker.seren.ssh", key.`package`)
        assertEquals("id_ed25519", key.getStringExtra(Suite.EXTRA_DISPLAY_NAME))
        assertTrue(key.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)

        val reveal = Suite.revealIntent(uri, "notes.txt")
        assertEquals(Suite.ACTION_REVEAL, reveal.action)
        assertEquals("wales.tucker.seren.files", reveal.`package`)
        // Seren Files only reads the link itself, so it gets no access to the file.
        assertEquals(0, reveal.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    @Test
    fun launchingAMissingAppReportsFailure() {
        shadowOf(context).checkActivities(true)
        assertFalse(Suite.launch(context, Suite.revealIntent(uri)))
    }
}
