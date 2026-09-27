package wales.tucker.seren.auth.ui

import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import wales.tucker.seren.auth.TestApp
import wales.tucker.seren.auth.backup.BackupEntry
import wales.tucker.seren.auth.backup.SerenBackup
import wales.tucker.seren.auth.otp.OtpToken
import wales.tucker.seren.auth.ui.importing.ImportState
import wales.tucker.seren.auth.ui.importing.ImportViewModel
import wales.tucker.seren.auth.ui.settings.BackupViewModel
import wales.tucker.seren.auth.ui.settings.ExportKind
import java.io.File

/**
 * The password protected export and import, driven through their view models. (Robolectric can't
 * settle a dialog holding a text field, so the password dialogs themselves aren't driven here.)
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class BackupFlowTest {
    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val container get() = app.container
    private val github = OtpToken("GitHub", "octocat", "JBSWY3DPEHPK3PXP")
    private val aws = OtpToken("AWS", "admin", "GEZDGNBVGY3TQOJQ")

    /** Runs the main looper and background work until [done]. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    @Test
    fun encryptedExportImportsOnAnotherDevice() {
        runBlocking {
            container.accounts.add(github, 2)
            container.accounts.add(aws, 5)
        }
        val file = File(app.cacheDir, "backup.json")
        val exporter = BackupViewModel(container)
        exporter.pending = ExportKind.Encrypted("correct horse".toCharArray())
        var message: String? = null
        exporter.export(Uri.fromFile(file)) { message = it }
        until { message != null }
        assertEquals("Exported 2 accounts", message)
        assertFalse(file.readText().contains(github.secret))

        // "Another device": start from nothing and import the file.
        runBlocking { container.accounts.all().forEach { container.accounts.delete(it.id) } }
        val importer = ImportViewModel(container)
        importer.importFile(Uri.fromFile(file))
        until { importer.state.value is ImportState.NeedsPassword }

        importer.unlock("wrong password".toCharArray())
        until { (importer.state.value as? ImportState.NeedsPassword)?.let { !it.working && it.error != null } == true }
        assertEquals("That password isn't right", (importer.state.value as ImportState.NeedsPassword).error)

        importer.unlock("correct horse".toCharArray())
        until { importer.state.value is ImportState.Confirm }
        val confirm = importer.state.value as ImportState.Confirm
        assertEquals(2, confirm.import.entries.size)
        assertEquals(0, confirm.existing)

        var added: String? = null
        val job = MainScope().launch { importer.messages.collect { added = it } }
        importer.confirm()
        until { added != null }
        job.cancel()
        assertEquals("Added 2 accounts", added)
        val restored = runBlocking { container.accounts.accounts.first() }
        assertEquals(listOf(aws, github), restored.map { it.token })
        assertEquals(listOf(5, 2), restored.map { it.color })
    }

    @Test
    fun anEmptyPasswordProtectedBackupSaysSo() {
        val importer = ImportViewModel(container)
        importer.importText(SerenBackup.export(emptyList<BackupEntry>(), "pw123456".toCharArray()))
        until { importer.state.value is ImportState.NeedsPassword }
        importer.unlock("pw123456".toCharArray())
        until { importer.state.value is ImportState.Failed }
        assertTrue((importer.state.value as ImportState.Failed).message.contains("no accounts"))
    }
}
