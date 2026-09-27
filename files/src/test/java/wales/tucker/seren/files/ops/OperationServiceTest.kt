package wales.tucker.seren.files.ops

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import wales.tucker.seren.files.TestApp

/** The notification that keeps long jobs going: what it shows, Cancel, and how it ends. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class OperationServiceTest {
    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val ops get() = app.container.operations
    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java))

    private fun settle(until: () -> Boolean = { true }) {
        val deadline = System.currentTimeMillis() + 5_000
        do {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        } while (!until() && System.currentTimeMillis() < deadline)
    }

    /** Starts a job that reports some progress, then waits for [finish] (or to be cancelled). */
    private fun startJob(finish: CompletableDeferred<Unit>) {
        ops.runLong("Copying 3 items") { progress ->
            progress(25L * 1024 * 1024, 100L * 1024 * 1024, "film.mp4")
            finish.await()
            ops.tell("Copied 3 items to Movies")
        }
        settle { ops.current.value?.detail == "film.mp4" }
    }

    @Test
    fun aLongJobStartsTheServiceWithItsProgress() {
        val finish = CompletableDeferred<Unit>()
        startJob(finish)
        val started = shadowOf(app).nextStartedService
        assertEquals(OperationService::class.java.name, started.component!!.className)

        val service = Robolectric.buildService(OperationService::class.java).create().get()
        val shown = shadowOf(service).lastForegroundNotification!!
        assertEquals("Copying 3 items", shown.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("film.mp4  ·  25.0 MB of 100.0 MB", shown.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(25, shown.extras.getInt(Notification.EXTRA_PROGRESS))
        assertEquals("Cancel", shown.actions.single().title)

        // Finishing with the app on screen: the snackbar says so, not a notification.
        app.container.appVisible = true
        finish.complete(Unit)
        settle { ops.current.value == null }
        settle()
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertNull(notifications.getNotification(OperationService.DONE_ID))
    }

    @Test
    fun cancelFromTheNotificationStopsTheJob() {
        startJob(CompletableDeferred())
        val controller = Robolectric.buildService(OperationService::class.java).create()
        app.container.appVisible = false
        controller.withIntent(Intent(app, OperationService::class.java).setAction(OperationService.ACTION_CANCEL)).startCommand(0, 1)
        settle { ops.current.value == null }
        settle()
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        // Away from the app, how it ended is a notification.
        val done = notifications.getNotification(OperationService.DONE_ID)
        assertNotNull(done)
        assertEquals("Stopped", done.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun jobsThatCountItemsSaySo() {
        val op = Operation("Deleting 4 items", "old", done = 1, total = 4, countsItems = true)
        assertEquals("old  ·  1 of 4", OperationService.progressText(op))
        OperationService.createChannels(app)
        val n = OperationService.progressNotification(app, Operation("Extracting a.zip", "", 0, 0))
        assertTrue(n.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertEquals(NotificationCompat.CATEGORY_PROGRESS, n.category)
    }
}
