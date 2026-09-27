package wales.tucker.seren.ssh.session

import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import wales.tucker.seren.ssh.MainActivity
import wales.tucker.seren.ssh.SerenApp
import wales.tucker.seren.ssh.TestApp

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class SessionServiceTest {
    private val app get() = ApplicationProvider.getApplicationContext<SerenApp>()
    private val manager get() = app.container.sessionManager

    @After
    fun tearDown() = manager.closeAll()

    private fun pollUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for $what")
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
    }

    @Test
    fun notificationOpensTheSessionAndOffersReconnect() {
        val session = runBlocking { manager.openQuick("user", "127.0.0.1", 1) }
        pollUntil("failure") { session.state.value is SessionState.Failed }

        Robolectric.buildService(SessionService::class.java, Intent(app, SessionService::class.java)).create().startCommand(0, 1)
        val notifications = app.getSystemService(NotificationManager::class.java)
        var notification: android.app.Notification? = null
        pollUntil("notification") {
            notification = shadowOf(notifications).allNotifications.firstOrNull()
            notification?.actions?.any { it.title == "Reconnect" } == true
        }
        val open = shadowOf(notification!!.contentIntent).savedIntent
        assertEquals(session.id, open.getIntExtra(MainActivity.EXTRA_SESSION_ID, -1))
        assertTrue(notification!!.actions.any { it.title == "Disconnect all" })
    }
}
