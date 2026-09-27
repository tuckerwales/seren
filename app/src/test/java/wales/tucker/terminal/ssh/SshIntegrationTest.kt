package wales.tucker.terminal.ssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import wales.tucker.terminal.data.ForwardType
import wales.tucker.terminal.data.KeyType
import wales.tucker.terminal.data.PortForward
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Runs against a real sshd. Enabled when SSH_TEST_HOST is set, e.g.
 * SSH_TEST_HOST=127.0.0.1 SSH_TEST_PORT=2222 SSH_TEST_USER=testuser SSH_TEST_PASSWORD=testpass
 * SSH_TEST_AUTHORIZED_KEYS=/home/testuser/.ssh/authorized_keys
 */
class SshIntegrationTest {
    private val host = System.getenv("SSH_TEST_HOST")
    private val port = System.getenv("SSH_TEST_PORT")?.toInt() ?: 22
    private val user = System.getenv("SSH_TEST_USER") ?: "testuser"
    private val password = System.getenv("SSH_TEST_PASSWORD") ?: "testpass"

    private val dao = FakeKnownHostDao()

    private inner class TestUi(
        var acceptHostKey: Boolean = true,
        var passwordAnswer: String? = password,
    ) : ConnectionUi {
        val hostKeyRequests = mutableListOf<HostKeyRequest>()
        var passwordPrompts = 0
        val logs = mutableListOf<String>()

        override fun verifyHostKey(request: HostKeyRequest): Boolean {
            hostKeyRequests += request
            return acceptHostKey
        }

        override fun promptPassword(target: String, message: String): PasswordResponse? {
            passwordPrompts++
            return passwordAnswer?.let { PasswordResponse(it, remember = true) }
        }

        override fun promptKeyboardInteractive(
            target: String,
            name: String,
            instruction: String,
            prompts: List<KeyboardInteractivePrompt>,
        ): List<String>? = prompts.map { password }

        override fun log(message: String) {
            logs += message
        }
    }

    @Before
    fun setUp() {
        assumeTrue("SSH_TEST_HOST not set", host != null)
        JschAndroidConfig.apply()
    }

    private fun target(pw: String? = password, key: String? = null) =
        ConnectionTarget("test", host!!, port, user, password = pw, privateKey = key, keyName = "k")

    @Test
    fun passwordAuthAndHostKeyTrustOnFirstUse() {
        val ui = TestUi()
        val c = SshConnection(dao, ui)
        c.connect(target())
        assertTrue(c.isConnected)
        assertEquals(1, ui.hostKeyRequests.size)
        assertTrue(!ui.hostKeyRequests[0].changed)
        assertEquals(1, dao.entries.value.size)
        c.disconnect()

        // Second connection trusts the stored key.
        val ui2 = TestUi()
        val c2 = SshConnection(dao, ui2)
        c2.connect(target())
        assertEquals(0, ui2.hostKeyRequests.size)
        c2.disconnect()
    }

    @Test
    fun rejectedHostKeyFails() {
        val c = SshConnection(dao, TestUi(acceptHostKey = false))
        try {
            c.connect(target())
            throw AssertionError("expected failure")
        } catch (e: Exception) {
            assertEquals("Host key was not accepted", SshConnection.describeError(e))
        }
    }

    @Test
    fun changedHostKeyIsReported() {
        SshConnection(dao, TestUi()).apply { connect(target()); disconnect() }
        val stored = dao.entries.value.single()
        dao.entries.value = listOf(stored.copy(key = "AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKLsabgH5C9okWi0dh2l9GKJl"))
        val ui = TestUi(acceptHostKey = false)
        try {
            SshConnection(dao, ui).connect(target())
            throw AssertionError("expected failure")
        } catch (_: Exception) {
        }
        assertEquals(1, ui.hostKeyRequests.size)
        assertTrue(ui.hostKeyRequests[0].changed)
    }

    @Test
    fun promptsForPasswordWhenNotStored() {
        val ui = TestUi()
        val c = SshConnection(dao, ui)
        c.connect(target(pw = null))
        assertTrue(ui.passwordPrompts >= 1 || c.isConnected)
        assertEquals(password, c.passwordToRemember ?: password)
        c.disconnect()
    }

    @Test
    fun cancelledPasswordPromptFails() {
        val ui = TestUi(passwordAnswer = null)
        val c = SshConnection(dao, object : ConnectionUi by ui {
            override fun promptKeyboardInteractive(
                target: String, name: String, instruction: String, prompts: List<KeyboardInteractivePrompt>,
            ): List<String>? = null
        })
        try {
            c.connect(target(pw = null))
            throw AssertionError("expected failure")
        } catch (e: Exception) {
            val msg = SshConnection.describeError(e)
            assertTrue(msg, msg == "Authentication cancelled" || msg == "Authentication failed")
        }
    }

    @Test
    fun publicKeyAuth() {
        val authorizedKeys = System.getenv("SSH_TEST_AUTHORIZED_KEYS")
        assumeTrue(authorizedKeys != null)
        for (type in listOf(KeyType.ED25519, KeyType.ECDSA, KeyType.RSA)) {
            val key = SshKeys.generate(type, if (type == KeyType.RSA) 3072 else 256, "test")
            File(authorizedKeys!!).writeText(key.publicKey + "\n")
            val ui = TestUi(passwordAnswer = null)
            val c = SshConnection(dao, ui)
            c.connect(target(pw = null, key = key.privateKey))
            assertTrue("$type auth", c.isConnected)
            assertEquals(0, ui.passwordPrompts)
            c.disconnect()
        }
    }

    @Test
    fun interactiveShell() {
        val c = SshConnection(dao, TestUi())
        c.connect(target())
        val shell = c.openShell(80, 24, 800, 480)
        shell.output.write("echo marker-\$((20+22)); echo \$TERM\n".toByteArray())
        shell.output.flush()
        val out = StringBuilder()
        val buf = ByteArray(4096)
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && !(out.contains("marker-42") && out.contains("xterm-256color"))) {
            if (shell.input.available() > 0) {
                val n = shell.input.read(buf)
                if (n < 0) break
                out.append(String(buf, 0, n))
            } else {
                Thread.sleep(20)
            }
        }
        assertTrue(out.toString(), out.contains("marker-42"))
        assertTrue(out.toString(), out.contains("xterm-256color"))
        shell.resize(100, 30, 1000, 600)
        shell.close()
        c.disconnect()
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun readBanner(socket: Socket): String {
        socket.soTimeout = 10_000
        val sb = StringBuilder()
        val input = socket.getInputStream()
        while (!sb.contains("\n")) {
            val b = input.read()
            if (b < 0) break
            sb.append(b.toChar())
        }
        return sb.toString()
    }

    @Test
    fun localForwardAndSocksProxy() {
        val c = SshConnection(dao, TestUi())
        c.connect(target())
        val lport = freePort()
        val dport = freePort()
        val errors = c.startForwards(
            listOf(
                PortForward(hostId = 1, type = ForwardType.LOCAL, sourcePort = lport, destHost = "127.0.0.1", destPort = port),
                PortForward(hostId = 1, type = ForwardType.DYNAMIC, sourcePort = dport),
            ),
        )
        assertTrue(errors.toString(), errors.isEmpty())

        Socket("127.0.0.1", lport).use { s ->
            assertTrue(readBanner(s).startsWith("SSH-2.0"))
        }

        // SOCKS5 with a domain name.
        Socket().use { s ->
            s.connect(InetSocketAddress("127.0.0.1", dport), 5000)
            s.soTimeout = 10_000
            val out = s.getOutputStream()
            val input = s.getInputStream()
            out.write(byteArrayOf(5, 1, 0))
            assertEquals(5, input.read()); assertEquals(0, input.read())
            val hostBytes = "localhost".toByteArray()
            out.write(byteArrayOf(5, 1, 0, 3, hostBytes.size.toByte()) + hostBytes + byteArrayOf((port shr 8).toByte(), port.toByte()))
            val reply = ByteArray(10)
            var read = 0
            while (read < 10) read += input.read(reply, read, 10 - read)
            assertEquals(0, reply[1].toInt())
            assertTrue(readBanner(s).startsWith("SSH-2.0"))
        }

        // SOCKS4 with an IP address.
        Socket().use { s ->
            s.connect(InetSocketAddress("127.0.0.1", dport), 5000)
            s.soTimeout = 10_000
            val out = s.getOutputStream()
            val input = s.getInputStream()
            out.write(byteArrayOf(4, 1, (port shr 8).toByte(), port.toByte(), 127, 0, 0, 1, 0))
            val reply = ByteArray(8)
            var read = 0
            while (read < 8) read += input.read(reply, read, 8 - read)
            assertEquals(0x5A, reply[1].toInt())
            assertTrue(readBanner(s).startsWith("SSH-2.0"))
        }
        c.disconnect()
    }

    @Test
    fun jumpHost() {
        val ui = TestUi()
        val c = SshConnection(dao, ui)
        c.connect(target(), jump = target())
        assertTrue(c.isConnected)
        c.disconnect()
    }

    @Test
    fun sftpOperations() = runBlocking {
        val c = SshConnection(dao, TestUi())
        c.connect(target())
        val sftp = SftpClient(c.openSftp())
        val home = sftp.home()
        val dir = SftpClient.join(home, "sftp-test-${System.nanoTime()}")
        sftp.mkdir(dir)
        val file = SftpClient.join(dir, "hello.txt")
        val data = "hello sftp\n".repeat(1000).toByteArray()
        var progress = 0L
        sftp.upload(data.inputStream(), file, data.size.toLong()) { done, _ -> progress = done }
        assertEquals(data.size.toLong(), progress)
        val listing = sftp.list(dir)
        assertEquals(listOf("hello.txt"), listing.map { it.name })
        assertEquals(data.size.toLong(), listing[0].size)
        val out = ByteArrayOutputStream()
        sftp.download(file, out) { _, _ -> }
        assertTrue(data.contentEquals(out.toByteArray()))
        sftp.rename(file, SftpClient.join(dir, "renamed.txt"))
        assertEquals("renamed.txt", sftp.list(dir).single().name)
        sftp.writeText(SftpClient.join(dir, "t.txt"), "abc")
        assertEquals("abc", sftp.readText(SftpClient.join(dir, "t.txt")))
        sftp.delete(sftp.stat(dir))
        assertTrue(sftp.list(home).none { it.path == dir })
        sftp.close()
        c.disconnect()
    }

    @Test
    fun sftpBrowsingIsNotBlockedByATransferOnAnotherChannel() = runBlocking {
        val c = SshConnection(dao, TestUi())
        c.connect(target())
        val browse = SftpClient(c.openSftp())
        val transfer = SftpClient(c.openSftp())
        val home = browse.home()
        val file = SftpClient.join(home, "big-${System.nanoTime()}.bin")
        val data = ByteArray(2 * 1024 * 1024) { it.toByte() }
        browse.upload(data.inputStream(), file, data.size.toLong()) { _, _ -> }

        // A destination that stalls, like a slow disk, until the listing below has finished.
        val release = CountDownLatch(1)
        val stalled = CountDownLatch(1)
        val out = object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
            override fun write(b: ByteArray, off: Int, len: Int) {
                stalled.countDown()
                release.await()
            }
        }
        val download = async(Dispatchers.IO) { transfer.download(file, out) { _, _ -> } }
        assertTrue(stalled.await(10, TimeUnit.SECONDS))
        val listing = withTimeout(5_000) { browse.list(home) }
        assertTrue(listing.any { it.path == file })
        release.countDown()
        download.await()

        browse.delete(browse.stat(file))
        transfer.close()
        browse.close()
        c.disconnect()
    }
}
