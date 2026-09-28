package wales.tucker.seren.ssh.session

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import wales.tucker.seren.ssh.MainActivity
import wales.tucker.seren.ssh.SerenApp
import wales.tucker.seren.ssh.TestApp

/** Progress notification for SFTP uploads and downloads: title, bar, Cancel, and done. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class SftpTransferNotifierTest {
    private val app get() = ApplicationProvider.getApplicationContext<SerenApp>()
    private val notifier get() = app.container.transferNotifier
    private val notifications get() = app.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        notifier.clear()
        notifications.cancelAll()
        notifier.sftpVisible = false
    }

    private fun progress(): android.app.Notification? =
        shadowOf(notifications).getNotification(SftpTransferNotifier.NOTIFICATION_ID)

    @Test
    fun uploadShowsDeterminateProgressAndCancel() {
        var cancelled = false
        notifier.update("notes.txt", upload = true, done = 25L * 1024 * 1024, total = 100L * 1024 * 1024, sessionId = 7) {
            cancelled = true
        }
        val shown = progress()
        assertNotNull(shown)
        assertEquals("Uploading notes.txt", shown!!.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("25.0 MB of 100.0 MB", shown.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(25, shown.extras.getInt(Notification.EXTRA_PROGRESS))
        assertFalse(shown.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertEquals("Cancel", shown.actions.single().title)

        val open = shadowOf(shown.contentIntent).savedIntent
        assertEquals(7, open.getIntExtra(MainActivity.EXTRA_SESSION_ID, -1))
        assertTrue(open.getBooleanExtra(MainActivity.EXTRA_OPEN_SFTP, false))

        Robolectric.buildService(SessionService::class.java)
            .withIntent(Intent(app, SessionService::class.java).setAction(SessionService.ACTION_CANCEL_TRANSFER))
            .create()
            .startCommand(0, 1)
        assertTrue(cancelled)
    }

    @Test
    fun unknownSizeIsIndeterminate() {
        notifier.update("stream.bin", upload = true, done = 0, total = -1, sessionId = 1) {}
        val shown = progress()!!
        assertEquals("Uploading stream.bin", shown.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(shown.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
    }

    @Test
    fun downloadUsesDownloadingTitle() {
        notifier.update("report.pdf", upload = false, done = 512, total = 1024, sessionId = 2) {}
        assertEquals("Downloading report.pdf", progress()!!.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun finishClearsProgressAndPostsDoneWhenFilesIsNotOnScreen() {
        notifier.sftpVisible = false
        notifier.update("a.txt", upload = true, done = 1, total = 2, sessionId = 1) {}
        notifier.finish("Uploaded a.txt")
        assertNull(progress())
        val done = shadowOf(notifications).getNotification(SftpTransferNotifier.DONE_ID)
        assertNotNull(done)
        assertEquals("Uploaded a.txt", done!!.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun finishSkipsDoneNotificationWhileFilesScreenCanShowASnackbar() {
        notifier.sftpVisible = true
        notifier.update("a.txt", upload = true, done = 1, total = 2, sessionId = 1) {}
        notifier.finish("Uploaded a.txt")
        assertNull(progress())
        assertNull(shadowOf(notifications).getNotification(SftpTransferNotifier.DONE_ID))
    }
}
