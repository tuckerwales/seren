package wales.tucker.terminal.ui

import android.net.Uri
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import wales.tucker.terminal.MainActivity
import wales.tucker.terminal.SshLink
import wales.tucker.terminal.TerminalApp
import wales.tucker.terminal.TestApp
import wales.tucker.terminal.data.Host
import wales.tucker.terminal.ssh.JschAndroidConfig
import wales.tucker.terminal.ui.terminal.TerminalView

/** Drives the real UI through flows that do not need an SSH server. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppBehaviourTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val container get() = ApplicationProvider.getApplicationContext<TerminalApp>().container

    @After
    fun tearDown() {
        container.sessionManager.closeAll()
    }

    private fun exists(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    /**
     * Polls [condition] while advancing the Compose clock by hand, since the connecting spinner is
     * an infinite animation that never lets Compose report idle.
     */
    private fun pollUntil(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        compose.mainClock.autoAdvance = false
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for $what")
            compose.mainClock.advanceTimeBy(50)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        compose.mainClock.advanceTimeBy(50)
    }

    @Test
    fun unlockingReturnsToTheOpenScreen() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Known hosts").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("No known hosts") }

        compose.activity.locked = true
        compose.waitUntil(5_000) { exists("Terminal is locked") }
        compose.activity.locked = false
        compose.waitUntil(5_000) { exists("No known hosts") }
    }

    /** Quick connects to a closed local port, which fails straight away. */
    private fun openFailingSession() {
        compose.onNode(hasSetTextAction() and hasText("user@host:port")).performTextInput("user@127.0.0.1:1")
        compose.onNodeWithContentDescription("Connect").performClick()
        pollUntil("connection failure") { exists("Couldn't connect") }
    }

    @Test
    fun closingSessionsFromTheNotificationLeavesTheTerminal() {
        openFailingSession()
        container.sessionManager.closeAll()
        pollUntil("hosts screen") { exists("Quick connect") }
    }

    private fun lightStatusBarIcons(): Boolean {
        val window = compose.activity.window
        return WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars
    }

    @Test
    fun statusBarIconsFollowTheTerminalBackground() {
        // The app is in its light theme; the default terminal scheme is dark.
        compose.waitForIdle()
        assertTrue(lightStatusBarIcons())
        openFailingSession()
        assertFalse(lightStatusBarIcons())
        container.sessionManager.closeAll()
        // Restored once the terminal's exit animation has finished.
        pollUntil("light status bar icons") { lightStatusBarIcons() }
    }

    @Test
    fun hostsOnlyShowTheConnectedDotForConnectedSessions() {
        runBlocking { container.database.hostDao().insert(Host(nickname = "closed-port", hostname = "127.0.0.1", port = 1, username = "user")) }
        compose.waitUntil(5_000) { exists("closed-port") }
        compose.onNodeWithText("closed-port").performClick()
        pollUntil("connection failure") { exists("Couldn't connect") }
        compose.onNodeWithContentDescription("Back").performClick()
        pollUntil("hosts screen") { exists("Quick connect") }
        assertTrue(compose.onAllNodesWithContentDescription("Connected").fetchSemanticsNodes().isEmpty())
    }

    private val sshHost: String? = System.getenv("SSH_TEST_HOST")
    private val sshPort get() = System.getenv("SSH_TEST_PORT")?.toInt() ?: 22
    private val sshUser get() = System.getenv("SSH_TEST_USER") ?: "testuser"

    @Test
    fun sessionCardsShowWhenTheyAreWaitingForInput() {
        assumeTrue("SSH_TEST_HOST not set", sshHost != null)
        JschAndroidConfig.apply()
        // Opened without visiting the terminal, so nothing answers the host key prompt.
        runBlocking { container.sessionManager.openQuick(sshUser, sshHost!!, sshPort) }
        pollUntil("waiting for input") { exists("Waiting for your input") }
    }

    @Test
    fun linksWithoutAUserFillInQuickConnect() {
        val link = SshLink.parse(Uri.parse("ssh://example.com:2222"))!!
        assertEquals("", link.username)
        compose.activity.deepLinks.trySend(link)
        compose.waitUntil(5_000) { exists("@example.com:2222") }
        assertTrue(container.sessionManager.sessions.value.isEmpty())

        val withUser = SshLink.parse(Uri.parse("ssh://bob@[fe80::1]:2200"))!!
        assertEquals(SshLink("bob", "fe80::1", 2200), withUser)
        assertEquals("[fe80::1]:2200", withUser.address)
    }

    @Test
    fun notificationTapsOpenTheSession() {
        val session = runBlocking { container.sessionManager.openQuick("user", "127.0.0.1", 1) }
        compose.waitForIdle()
        assertFalse(exists("Couldn't connect"))
        compose.activity.sessionLinks.trySend(session.id)
        pollUntil("terminal") { exists("Couldn't connect") }
    }

    private fun findTerminalView(v: android.view.View): TerminalView? {
        if (v is TerminalView) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findTerminalView(v.getChildAt(i))?.let { return it }
        return null
    }

    @Test
    fun scrollingBackShowsAJumpToBottomButton() {
        openFailingSession()
        val session = container.sessionManager.sessions.value.single()
        synchronized(session.emulator) { repeat(200) { session.emulator.append("line $it\r\n") } }
        val view = findTerminalView(compose.activity.window.decorView)!!
        assertFalse(compose.onAllNodesWithContentDescription("Scroll to bottom").fetchSemanticsNodes().isNotEmpty())

        // Drag down, as a finger scrolling back through the history does.
        val x = view.width / 2f
        var y = view.height / 4f
        val start = android.os.SystemClock.uptimeMillis()
        view.dispatchTouchEvent(android.view.MotionEvent.obtain(start, start, android.view.MotionEvent.ACTION_DOWN, x, y, 0))
        repeat(10) { i ->
            y += view.height / 20f
            view.dispatchTouchEvent(android.view.MotionEvent.obtain(start, start + 10L * (i + 1), android.view.MotionEvent.ACTION_MOVE, x, y, 0))
        }
        view.dispatchTouchEvent(android.view.MotionEvent.obtain(start, start + 200, android.view.MotionEvent.ACTION_UP, x, y, 0))
        pollUntil("jump button") { compose.onAllNodesWithContentDescription("Scroll to bottom").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithContentDescription("Scroll to bottom").performClick()
        pollUntil("button hidden") { compose.onAllNodesWithContentDescription("Scroll to bottom").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun extraKeysCanBeHiddenInSettings() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Customize extra keys").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("Choose the keys") }
        compose.onNodeWithText("Pipe").performClick()
        compose.waitUntil(5_000) { runBlocking { container.settings.settings.first().hiddenExtraKeys } == setOf("|") }
        compose.onNodeWithText("Show all").performClick()
        compose.waitUntil(5_000) { runBlocking { container.settings.settings.first().hiddenExtraKeys }.isEmpty() }
    }

    @Test
    fun tappingAnOpenHostOffersToSwitchToIt() {
        assumeTrue("SSH_TEST_HOST not set", sshHost != null)
        JschAndroidConfig.apply()
        val host = runBlocking {
            val id = container.database.hostDao().insert(Host(nickname = "test-server", hostname = sshHost!!, port = sshPort, username = sshUser))
            container.database.hostDao().get(id)!!
        }
        // Left waiting at the host key prompt, so it stays connecting.
        val session = runBlocking { container.sessionManager.open(host) }
        pollUntil("host row") { exists("test-server") && exists("Waiting for your input") }

        // The first match is the session card; the second is the host row.
        compose.onAllNodesWithText("test-server")[1].performClick()
        pollUntil("already open dialog") { exists("is already open") }
        compose.onNodeWithText("Switch to it").performClick()
        pollUntil("terminal") { exists("Trust and connect") }
        assertEquals(listOf(session), container.sessionManager.sessions.value)
    }

    @Test
    fun backClosesTheHostSearch() {
        compose.onNodeWithContentDescription("Search").performClick()
        compose.waitUntil(5_000) { exists("Search hosts") }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { exists("Quick connect") && !exists("Search hosts") }
        assertFalse(compose.activity.isFinishing)
    }

    @Test
    fun closingAConnectedSessionCardAsksFirst() {
        assumeTrue("SSH_TEST_HOST not set", sshHost != null)
        JschAndroidConfig.apply()
        val password = System.getenv("SSH_TEST_PASSWORD") ?: "testpass"
        val session = runBlocking { container.sessionManager.openQuick(sshUser, sshHost!!, sshPort) }
        pollUntil("connected", 20_000) {
            when (val p = session.prompt.value) {
                is wales.tucker.terminal.session.SessionPrompt.HostKey -> p.respond(true)
                is wales.tucker.terminal.session.SessionPrompt.Password -> p.respond(wales.tucker.terminal.ssh.PasswordResponse(password, false))
                is wales.tucker.terminal.session.SessionPrompt.KeyboardInteractive -> p.respond(p.prompts.map { password })
                null -> Unit
            }
            session.state.value == wales.tucker.terminal.session.SessionState.Connected && exists("Connected")
        }
        compose.onNodeWithContentDescription("Close session").performClick()
        pollUntil("confirmation") { exists("Disconnect?") }
        compose.onNodeWithText("Cancel").performClick()
        pollUntil("dialog closed") { !exists("Disconnect?") }
        assertEquals(listOf(session), container.sessionManager.sessions.value)
        compose.onNodeWithContentDescription("Close session").performClick()
        pollUntil("confirmation") { exists("Disconnect?") }
        compose.onNodeWithText("Disconnect").performClick()
        pollUntil("closed") { container.sessionManager.sessions.value.isEmpty() }
    }

    @Test
    fun hostEditorGuardsUnsavedChangesAndPointsAtErrors() {
        compose.onNodeWithText("Add your first host").performClick()
        compose.waitUntil(5_000) { exists("New host") }
        compose.onNode(hasSetTextAction() and hasText("Host")).performTextInput("example.com")

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { exists("Discard changes?") }
        compose.onNodeWithText("Keep editing").performClick()

        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { exists("Required") }
        assertTrue(runBlocking { container.database.hostDao().observeAll().first() }.isEmpty())

        compose.onNode(hasSetTextAction() and hasText("Username")).performTextInput("me")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { exists("Quick connect") }
        assertEquals(listOf("example.com"), runBlocking { container.database.hostDao().observeAll().first() }.map { it.hostname })
    }

    @Test
    fun quickConnectSessionsCanBeSavedAsHosts() {
        openFailingSession()
        val session = container.sessionManager.sessions.value.single()
        compose.onNodeWithContentDescription("More").performClick()
        pollUntil("menu") { exists("Save as host") }
        compose.onNodeWithText("Save as host").performClick()
        pollUntil("editor") { exists("New host") }
        compose.onNodeWithText("Save").performClick()
        pollUntil("saved") { session.hostId.value > 0 }

        val host = runBlocking { container.database.hostDao().get(session.hostId.value)!! }
        assertEquals(Triple("user", "127.0.0.1", 1), Triple(host.username, host.hostname, host.port))
        assertEquals(wales.tucker.terminal.data.AuthType.NONE, host.authType)
        pollUntil("back on the terminal") { !exists("New host") }
        compose.onNodeWithContentDescription("More").performClick()
        pollUntil("menu") { exists("Reconnect") }
        assertFalse(exists("Save as host"))
    }

    @Test
    fun savingAQuickConnectKeepsTheTypedPassword() {
        assumeTrue("SSH_TEST_HOST not set", sshHost != null)
        JschAndroidConfig.apply()
        val password = System.getenv("SSH_TEST_PASSWORD") ?: "testpass"
        val session = runBlocking { container.sessionManager.openQuick(sshUser, sshHost!!, sshPort) }
        pollUntil("connected", 20_000) {
            when (val p = session.prompt.value) {
                is wales.tucker.terminal.session.SessionPrompt.HostKey -> p.respond(true)
                is wales.tucker.terminal.session.SessionPrompt.Password -> p.respond(wales.tucker.terminal.ssh.PasswordResponse(password, false))
                is wales.tucker.terminal.session.SessionPrompt.KeyboardInteractive -> p.respond(p.prompts.map { password })
                null -> Unit
            }
            session.state.value == wales.tucker.terminal.session.SessionState.Connected
        }
        val form = wales.tucker.terminal.ui.hosts.HostEditorViewModel(container, null, false, session.id).form.value
        assertEquals(password, form.password)
        assertEquals(wales.tucker.terminal.data.AuthType.PASSWORD, form.authType)
        assertEquals(sshPort.toString(), form.port)
    }

    @Test
    fun deletedSnippetsCanBeRestored() {
        runBlocking { container.database.snippetDao().upsert(wales.tucker.terminal.data.Snippet(name = "Disk usage", command = "df -h")) }
        compose.onNodeWithText("Snippets").performClick()
        compose.waitUntil(5_000) { exists("df -h") }
        compose.onNodeWithContentDescription("Delete Disk usage").performClick()
        compose.waitUntil(5_000) { exists("Deleted Disk usage") && !exists("df -h") }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5_000) { exists("df -h") }
        assertEquals(1, runBlocking { container.database.snippetDao().observeAll().first() }.size)
    }
}
