package wales.tucker.terminal.ui

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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import wales.tucker.terminal.MainActivity
import wales.tucker.terminal.TerminalApp
import wales.tucker.terminal.TestApp
import wales.tucker.terminal.data.Host

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
}
