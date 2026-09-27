package wales.tucker.terminal.ui.sftp

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import wales.tucker.terminal.TerminalApp
import wales.tucker.terminal.TestApp
import wales.tucker.terminal.session.SessionPrompt
import wales.tucker.terminal.session.SessionState
import wales.tucker.terminal.session.TerminalSession
import wales.tucker.terminal.ssh.JschAndroidConfig
import wales.tucker.terminal.ssh.PasswordResponse
import wales.tucker.terminal.ssh.SftpClient
import java.io.File

/** Exercises the SFTP browser's view model against a real sshd (see SshIntegrationTest). */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class SftpViewModelTest {
    private val host = System.getenv("SSH_TEST_HOST")
    private val port = System.getenv("SSH_TEST_PORT")?.toInt() ?: 22
    private val user = System.getenv("SSH_TEST_USER") ?: "testuser"
    private val password = System.getenv("SSH_TEST_PASSWORD") ?: "testpass"

    private val app get() = ApplicationProvider.getApplicationContext<TerminalApp>()
    private val container get() = app.container

    private lateinit var session: TerminalSession
    private lateinit var remote: SftpClient
    private lateinit var dir: String

    private fun pollUntil(what: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for $what")
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
    }

    @Before
    fun setUp() = runBlocking {
        assumeTrue("SSH_TEST_HOST not set", host != null)
        JschAndroidConfig.apply()
        session = container.sessionManager.openQuick(user, host, port)
        pollUntil("connected") {
            when (val p = session.prompt.value) {
                is SessionPrompt.HostKey -> p.respond(true)
                is SessionPrompt.Password -> p.respond(PasswordResponse(password, false))
                is SessionPrompt.KeyboardInteractive -> p.respond(p.prompts.map { password })
                null -> Unit
            }
            session.state.value == SessionState.Connected
        }
        remote = session.openSftpChannel()
        dir = SftpClient.join(remote.home(), "vm-test-${System.nanoTime()}")
        remote.mkdir(dir)
    }

    @After
    fun tearDown() = runBlocking {
        if (!::session.isInitialized) return@runBlocking
        runCatching { remote.delete(remote.stat(dir)) }
        remote.close()
        container.sessionManager.closeAll()
    }

    private fun viewModel(): SftpViewModel {
        val vm = SftpViewModel(container, session.id)
        pollUntil("home listing") { vm.path.value != null }
        vm.navigate(dir)
        pollUntil("test folder") { vm.path.value == dir && !vm.loading.value }
        return vm
    }

    private fun localFile(name: String, text: String): Uri {
        val folder = File(app.cacheDir, "up-${System.nanoTime()}").apply { mkdirs() }
        return Uri.fromFile(File(folder, name).apply { writeText(text) })
    }

    private fun remoteText(name: String) = runBlocking { remote.readText(SftpClient.join(dir, name)) }

    @Test
    fun uploadAsksBeforeReplacingAFile() {
        runBlocking { remote.writeText(SftpClient.join(dir, "notes.txt"), "original") }
        val vm = viewModel()

        vm.requestUpload(localFile("notes.txt", "replacement"), app)
        pollUntil("replace prompt") { vm.pendingReplace.value != null }
        assertEquals("notes.txt", vm.pendingReplace.value!!.name)
        vm.dismissReplace()
        assertEquals("original", remoteText("notes.txt"))

        vm.requestUpload(localFile("notes.txt", "replacement"), app)
        pollUntil("replace prompt") { vm.pendingReplace.value != null }
        vm.confirmReplace(app)
        pollUntil("upload") { vm.transfer.value == null && runCatching { remoteText("notes.txt") }.getOrNull() == "replacement" }
    }

    @Test
    fun uploadOfANewFileDoesNotAsk() {
        val vm = viewModel()
        vm.requestUpload(localFile("new.txt", "fresh"), app)
        pollUntil("upload") { runCatching { remoteText("new.txt") }.getOrNull() == "fresh" }
        assertEquals(null, vm.pendingReplace.value)
        assertNotNull(vm.files.value)
    }

    @Test
    fun onlyOneTransferRunsAtATime() {
        val vm = viewModel()
        val folder = File(app.cacheDir, "big-${System.nanoTime()}").apply { mkdirs() }
        val big = Uri.fromFile(File(folder, "big.bin").apply { writeBytes(ByteArray(16 * 1024 * 1024)) })
        vm.requestUpload(big, app)
        pollUntil("transfer started") { vm.transfer.value != null }
        vm.requestUpload(localFile("other.txt", "second"), app)
        pollUntil("transfer finished") { vm.transfer.value == null }
        ShadowLooper.idleMainLooper()
        assertEquals(null, runCatching { remoteText("other.txt") }.getOrNull())
    }
}
