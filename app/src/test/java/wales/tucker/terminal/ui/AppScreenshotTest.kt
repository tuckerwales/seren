package wales.tucker.terminal.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import wales.tucker.terminal.MainActivity
import wales.tucker.terminal.TerminalApp
import wales.tucker.terminal.TestApp
import wales.tucker.terminal.data.AuthType
import wales.tucker.terminal.data.ForwardType
import wales.tucker.terminal.data.Host
import wales.tucker.terminal.data.KeyType
import wales.tucker.terminal.data.PortForward
import wales.tucker.terminal.data.Snippet
import wales.tucker.terminal.data.SshKey
import wales.tucker.terminal.session.SessionPrompt
import wales.tucker.terminal.session.SessionState
import wales.tucker.terminal.ssh.JschAndroidConfig
import wales.tucker.terminal.ssh.SshKeys

/**
 * Renders the main screens with Robolectric's native graphics and saves screenshots to
 * app/build/screenshots. The end-to-end test drives the real UI against an sshd when
 * SSH_TEST_HOST is set.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<TerminalApp>()
    private val container get() = app.container

    private fun shot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/screenshots/$name.png")
    }

    private fun seed() = runBlocking {
        val db = container.database
        val key = SshKeys.generate(KeyType.ED25519, 256, "pixel")
        val keyId = db.keyDao().insert(
            SshKey(
                name = "Pixel 9", type = key.type, bits = key.bits,
                encryptedPrivateKey = container.secretBox.encryptString(key.privateKey),
                publicKey = key.publicKey, fingerprint = key.fingerprint,
            ),
        )
        val web = db.hostDao().insert(
            Host(nickname = "web-01", hostname = "web01.example.com", username = "deploy", authType = AuthType.KEY, keyId = keyId, color = 0, group = "Production", lastConnectedAt = System.currentTimeMillis() - 3_600_000),
        )
        db.hostDao().insert(Host(nickname = "db-primary", hostname = "10.0.4.12", port = 2222, username = "postgres", color = 1, group = "Production", lastConnectedAt = System.currentTimeMillis() - 86_400_000 * 2))
        db.hostDao().insert(Host(nickname = "Raspberry Pi", hostname = "pi.local", username = "pi", color = 3, group = "Home"))
        db.hostDao().insert(Host(nickname = "NAS", hostname = "192.168.1.20", username = "admin", color = 4, group = "Home", authType = AuthType.NONE))
        db.portForwardDao().upsert(PortForward(hostId = web, type = ForwardType.LOCAL, sourcePort = 5432, destHost = "db.internal", destPort = 5432))
        db.snippetDao().upsert(Snippet(name = "Disk usage", command = "df -h"))
        db.snippetDao().upsert(Snippet(name = "Follow syslog", command = "sudo journalctl -f"))
        db.snippetDao().upsert(Snippet(name = "Update packages", command = "sudo apt update && sudo apt upgrade", autoRun = false))
    }

    @Test
    fun emptyState() {
        compose.onNodeWithText("No hosts yet").assertExists()
        shot("01_hosts_empty")
    }

    @Test
    fun mainScreens() {
        seed()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("web-01").fetchSemanticsNodes().isNotEmpty() }
        shot("02_hosts")

        compose.onNodeWithText("Keys").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Pixel 9").fetchSemanticsNodes().isNotEmpty() }
        shot("03_keys")

        compose.onNodeWithText("Snippets").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Disk usage").fetchSemanticsNodes().isNotEmpty() }
        shot("04_snippets")

        compose.onNodeWithText("Settings").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Color scheme").fetchSemanticsNodes().isNotEmpty() }
        shot("05_settings")

        compose.onNodeWithText("Hosts").performClick()
        compose.onNodeWithText("New host", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Authentication").fetchSemanticsNodes().isNotEmpty() }
        shot("06_host_editor")
    }

    /**
     * Polls [condition] while advancing the Compose clock manually. Used once text fields or
     * spinners are on screen, whose infinite animations never let Compose report idle.
     */
    private fun pollUntil(what: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        compose.mainClock.autoAdvance = false
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                val s = container.sessionManager.sessions.value.firstOrNull()
                val screen = s?.let { synchronized(it.emulator) { "alt=${it.emulator.isAltScreen}\n" + it.emulator.screenText() } }
                throw AssertionError("Timed out waiting for $what. state=${s?.state?.value} prompt=${s?.prompt?.value}\nscreen=$screen")
            }
            compose.mainClock.advanceTimeBy(50)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        compose.mainClock.advanceTimeBy(50)
    }

    private fun manualShot(name: String) {
        compose.mainClock.advanceTimeBy(100)
        captureScreenRoboImage("build/screenshots/$name.png")
    }

    @Test
    fun endToEndTerminalSession() {
        val host = System.getenv("SSH_TEST_HOST")
        assumeTrue("SSH_TEST_HOST not set", host != null)
        JschAndroidConfig.apply()
        val port = System.getenv("SSH_TEST_PORT") ?: "22"
        val user = System.getenv("SSH_TEST_USER") ?: "testuser"
        val password = System.getenv("SSH_TEST_PASSWORD") ?: "testpass"

        // A blinking cursor reschedules itself forever, which Robolectric's idling never settles.
        runBlocking { container.settings.setCursorBlink(false) }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasText("user@host:port")).performTextInput("$user@$host:$port")
        compose.onNodeWithContentDescription("Connect").performClick()

        // Host key verification dialog.
        pollUntil("host key prompt") {
            container.sessionManager.sessions.value.firstOrNull()?.prompt?.value is SessionPrompt.HostKey &&
                compose.onAllNodesWithText("Trust and connect").fetchSemanticsNodes().isNotEmpty()
        }
        manualShot("07_host_key")
        compose.onNodeWithText("Trust and connect").performClick()

        // Password prompt (quick connect has no stored credentials).
        pollUntil("password prompt") {
            val p = container.sessionManager.sessions.value.first().prompt.value
            p is SessionPrompt.Password || p is SessionPrompt.KeyboardInteractive
        }
        manualShot("08_password")
        when (val prompt = container.sessionManager.sessions.value.first().prompt.value) {
            is SessionPrompt.Password -> prompt.respond(wales.tucker.terminal.ssh.PasswordResponse(password, false))
            is SessionPrompt.KeyboardInteractive -> prompt.respond(prompt.prompts.map { password })
            else -> Unit
        }
        pollUntil("prompt dismissed") { container.sessionManager.sessions.value.first().prompt.value == null }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        val session = container.sessionManager.sessions.value.first()
        pollUntil("connected") { session.state.value == SessionState.Connected }

        val script = listOf(
            "clear",
            "printf '\\033[1;34mTerminal\\033[0m on \\033[32m%s\\033[0m\\n' \"\$(hostname)\"",
            "printf '\\033[38;5;208m256 colors\\033[0m \\033[38;2;255;105;180mtruecolor\\033[0m \\033[1mbold\\033[0m \\033[3mitalic\\033[0m \\033[4munderline\\033[0m\\n'",
            "for i in 0 1 2 3 4 5 6 7; do printf \"\\033[4\${i}m  \"; done; printf '\\033[0m\\n'",
            "for i in 0 1 2 3 4 5 6 7; do printf \"\\033[10\${i}m  \"; done; printf '\\033[0m\\n'",
            "printf '┌──────────────┐\\n│ box drawing  │\\n└──────────────┘\\n'",
            "printf 'Unicode: héllo 世界 ✓ λ → ∞\\n'",
            "ls -la --color=always /etc | head -8",
            "echo \$TERM \$COLUMNS x \$LINES",
        ).joinToString("; ")
        session.writeText("$script\r")
        pollUntil("script output") {
            synchronized(session.emulator) { session.emulator.screenText().contains("xterm-256color ") }
        }
        Thread.sleep(300)
        manualShot("09_terminal")

        // Full screen app (procps top redraws in place rather than using the alternate screen).
        session.writeText("top -d 1\r")
        pollUntil("top") {
            synchronized(session.emulator) { session.emulator.screenText().let { it.contains("PID") && it.contains("load average") } }
        }
        Thread.sleep(300)
        manualShot("10_top")
        session.writeText("q")
        Thread.sleep(500)

        // A pager does use the alternate screen, and restores the shell screen on exit.
        session.writeText("less /etc/services\r")
        pollUntil("less") { synchronized(session.emulator) { session.emulator.isAltScreen } }
        session.writeText("q")
        pollUntil("less exit") { synchronized(session.emulator) { !session.emulator.isAltScreen } }

        // SFTP browser.
        compose.onNodeWithContentDescription("More").performClick()
        pollUntil("menu") { compose.onAllNodesWithText("Browse files (SFTP)").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Browse files (SFTP)").performClick()
        pollUntil("sftp listing") {
            compose.onAllNodesWithText(".bashrc").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText(".profile").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Empty folder").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Only hidden files").fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.advanceTimeBy(1000)
        manualShot("11_sftp")

        container.sessionManager.closeAll()
    }
}
