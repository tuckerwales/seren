package wales.tucker.seren.ssh.ui.sftp

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.launch
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
import wales.tucker.seren.ssh.SerenApp
import wales.tucker.seren.ssh.TestApp
import wales.tucker.seren.ssh.session.SessionPrompt
import wales.tucker.seren.ssh.session.SessionState
import wales.tucker.seren.ssh.session.TerminalSession
import wales.tucker.seren.ssh.ssh.JschAndroidConfig
import wales.tucker.seren.ssh.ssh.PasswordResponse
import wales.tucker.seren.ssh.ssh.SftpClient
import java.io.File

/** Exercises the SFTP browser's view model against a real sshd (see SshIntegrationTest). */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class SftpViewModelTest {
    private val host = System.getenv("SSH_TEST_HOST")
    private val port = System.getenv("SSH_TEST_PORT")?.toInt() ?: 22
    private val user = System.getenv("SSH_TEST_USER") ?: "testuser"
    private val password = System.getenv("SSH_TEST_PASSWORD") ?: "testpass"

    private val app get() = ApplicationProvider.getApplicationContext<SerenApp>()
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

    @Test
    fun backRetracesVisitedFolders() {
        val sub = SftpClient.join(dir, "sub")
        runBlocking { remote.mkdir(sub) }
        val vm = viewModel()
        val home = runBlocking { remote.home() }
        vm.navigate(sub)
        pollUntil("sub folder") { vm.path.value == sub && !vm.loading.value }
        vm.navigate("/")
        pollUntil("root") { vm.path.value == "/" && !vm.loading.value }

        vm.back()
        pollUntil("back to sub") { vm.path.value == sub && !vm.loading.value }
        vm.back()
        pollUntil("back to test folder") { vm.path.value == dir && !vm.loading.value }
        vm.back()
        pollUntil("back home") { vm.path.value == home && !vm.loading.value }
        assertEquals(false, vm.canGoBack.value)
    }

    @Test
    fun severalFilesUploadInTurnAskingOnlyAboutClashes() {
        runBlocking { remote.writeText(SftpClient.join(dir, "b.txt"), "original") }
        val vm = viewModel()
        vm.uploadAll(listOf(localFile("a.txt", "first"), localFile("b.txt", "second"), localFile("c.txt", "third")), app)

        pollUntil("replace prompt for b.txt") { vm.pendingReplace.value?.name == "b.txt" }
        assertEquals("first", remoteText("a.txt"))
        // Skipping it carries on with the rest.
        vm.dismissReplace()
        pollUntil("c.txt uploaded") { runCatching { remoteText("c.txt") }.getOrNull() == "third" }
        assertEquals("original", remoteText("b.txt"))
        pollUntil("idle") { vm.transfer.value == null }
    }

    @Test
    fun cancellingATransferDropsTheRestOfTheQueue() {
        val vm = viewModel()
        val folder = File(app.cacheDir, "big-${System.nanoTime()}").apply { mkdirs() }
        val big = Uri.fromFile(File(folder, "big.bin").apply { writeBytes(ByteArray(16 * 1024 * 1024)) })
        vm.uploadAll(listOf(big, localFile("after.txt", "never")), app)
        pollUntil("transfer started") { vm.transfer.value != null }
        vm.cancelTransfer()
        pollUntil("transfer stopped") { vm.transfer.value == null }
        Thread.sleep(500)
        ShadowLooper.idleMainLooper()
        assertEquals(null, runCatching { remoteText("after.txt") }.getOrNull())
    }

    @Test
    fun downloadsSayWhereTheyWent() {
        runBlocking { remote.writeText(SftpClient.join(dir, "report.txt"), "numbers") }
        val vm = viewModel()
        val downloads = mutableListOf<Download>()
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).launch { vm.downloads.collect { downloads += it } }
        val file = vm.files.value.single { it.name == "report.txt" }
        val saved = File(app.cacheDir, "report-${System.nanoTime()}.txt")
        vm.download(file, Uri.fromFile(saved), app)
        pollUntil("download") { downloads.isNotEmpty() }
        job.cancel()
        assertEquals(Download(Uri.fromFile(saved), "report.txt"), downloads.single())
        assertEquals("numbers", saved.readText())
    }

    @Test
    fun browsingCarriesOnWhenItsChannelCloses() {
        val vm = viewModel()
        // The view model browses on the session's shared channel: close it under it.
        runBlocking { session.sftp().close() }
        runBlocking { remote.writeText(SftpClient.join(dir, "later.txt"), "hello") }
        val messages = mutableListOf<String>()
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).launch { vm.messages.collect { messages += it } }
        vm.refresh()
        pollUntil("listing") { vm.files.value.any { it.name == "later.txt" } }
        job.cancel()
        assertEquals(emptyList<String>(), messages.filter { it.startsWith("Cannot open") })
    }

    @Test
    fun browsingComesBackAfterAReconnect() {
        val vm = viewModel()
        session.reconnect()
        pollUntil("reconnected") {
            when (val p = session.prompt.value) {
                is SessionPrompt.Password -> p.respond(PasswordResponse(password, false))
                is SessionPrompt.KeyboardInteractive -> p.respond(p.prompts.map { password })
                else -> Unit
            }
            session.state.value == SessionState.Connected
        }
        runBlocking {
            remote = session.openSftpChannel()
            remote.writeText(SftpClient.join(dir, "after.txt"), "back")
        }
        vm.refresh()
        pollUntil("listing after reconnect") { vm.files.value.any { it.name == "after.txt" } }
        assertEquals(dir, vm.path.value)
    }
}
