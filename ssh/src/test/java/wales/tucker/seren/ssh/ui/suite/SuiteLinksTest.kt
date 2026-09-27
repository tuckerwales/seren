package wales.tucker.seren.ssh.ui.suite

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import wales.tucker.seren.core.suite.SuiteApp
import wales.tucker.seren.core.suite.Suite
import wales.tucker.seren.ssh.MainActivity
import wales.tucker.seren.ssh.SerenApp
import wales.tucker.seren.ssh.TestApp
import wales.tucker.seren.ssh.data.AuthType
import wales.tucker.seren.ssh.data.Host
import wales.tucker.seren.ssh.data.KeyType
import wales.tucker.seren.ssh.session.SessionPrompt
import wales.tucker.seren.ssh.session.SessionState
import wales.tucker.seren.ssh.ssh.JschAndroidConfig
import wales.tucker.seren.ssh.ssh.PasswordResponse
import wales.tucker.seren.ssh.ssh.SftpClient
import wales.tucker.seren.ssh.ssh.SshKeys
import java.io.File

/** How Seren SSH takes files and keys from other apps, and hands downloads to Seren Files. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SuiteLinksTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<SerenApp>()
    private val container get() = app.container

    @Before
    fun setUp() {
        Robolectric.setupContentProvider(TestFilesProvider::class.java, TestFilesProvider.AUTHORITY)
    }

    @After
    fun tearDown() {
        container.sessionManager.closeAll()
    }

    private fun exists(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    /** Advances the Compose clock by hand, since spinners never let Compose report idle. */
    private fun pollUntil(what: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        compose.mainClock.autoAdvance = false
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for $what")
            compose.mainClock.advanceTimeBy(50)
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        compose.mainClock.advanceTimeBy(50)
    }

    private fun share(vararg uris: Uri) = compose.runOnUiThread {
        compose.activity.handleIntent(Suite.sendIntent(SuiteApp.SSH, uris.toList(), "text/plain"))
    }

    @Test
    fun sharesOnlyAcceptContentLinks() {
        val link = Uri.parse("content://files/notes.txt")
        val one = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, link)
        assertEquals(listOf(link), MainActivity.sharedUris(one))

        // Some apps only fill in the clip.
        val clipOnly = Intent(Intent.ACTION_SEND).apply { clipData = ClipData.newRawUri(null, link) }
        assertEquals(listOf(link), MainActivity.sharedUris(clipOnly))

        val private = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, Uri.fromFile(File("/data/data/wales.tucker.seren.ssh/databases/terminal.db")))
        assertEquals(emptyList<Uri>(), MainActivity.sharedUris(private))

        val other = Uri.parse("content://files/photo.jpg")
        val many = Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(link, other, link))
        assertEquals(listOf(link, other), MainActivity.sharedUris(many))
    }

    @Test
    fun sharedFilesAskWhichServerToUploadTo() {
        runBlocking { container.database.hostDao().insert(Host(nickname = "web", hostname = "web.example.com", username = "deploy")) }
        share(TestFilesProvider.share(app, "notes.txt", "hello"))
        pollUntil("upload screen") { exists("Upload notes.txt") }
        assertTrue(exists("deploy@web.example.com"))

        compose.onNodeWithContentDescription("Cancel").performClick()
        pollUntil("hosts screen") { exists("Quick connect") }
        assertTrue(container.sessionManager.sessions.value.isEmpty())
    }

    @Test
    fun sharingSeveralFilesNamesHowMany() {
        share(TestFilesProvider.share(app, "a.txt", "a"), TestFilesProvider.share(app, "b.txt", "b"))
        pollUntil("upload screen") { exists("Upload 2 files") }
        // No hosts or sessions yet.
        assertTrue(exists("No servers yet"))
    }

    @Test
    fun keysFromSerenFilesOpenImportKeyFilledIn() {
        val key = SshKeys.generate(KeyType.ED25519, 256, "test")
        val uri = TestFilesProvider.share(app, "id_test", key.privateKey)
        compose.runOnUiThread { compose.activity.handleIntent(Suite.importKeyIntent(uri, "id_test")) }
        pollUntil("import screen") { exists("OPENSSH PRIVATE KEY") }
        assertTrue(exists("id_test"))
        // Nothing is imported until people say so.
        assertTrue(runBlocking { container.database.keyDao().all() }.isEmpty())

        compose.onNode(hasText("Import key") and hasClickAction()).performScrollTo().performClick()
        pollUntil("key imported") { runBlocking { container.database.keyDao().all() }.isNotEmpty() }
        val saved = runBlocking { container.database.keyDao().all() }.single()
        assertEquals("id_test", saved.name)
        assertEquals(key.fingerprint, saved.fingerprint)
    }

    @Test
    fun importKeyIgnoresLinksThatAreNotContent() {
        compose.runOnUiThread { compose.activity.handleIntent(Suite.importKeyIntent(Uri.fromFile(File(app.filesDir, "secret")))) }
        compose.waitForIdle()
        assertTrue(exists("Quick connect"))
        assertTrue(!exists("Import key"))
    }

    private val sshHost: String? = System.getenv("SSH_TEST_HOST")
    private val sshPort get() = System.getenv("SSH_TEST_PORT")?.toInt() ?: 22
    private val sshUser get() = System.getenv("SSH_TEST_USER") ?: "testuser"
    private val sshPassword get() = System.getenv("SSH_TEST_PASSWORD") ?: "testpass"

    @Test
    fun sharedFilesUploadToTheFolderPeopleOpenAndDownloadsShowInSerenFiles() {
        assumeTrue("SSH_TEST_HOST not set", sshHost != null)
        JschAndroidConfig.apply()
        shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = SuiteApp.FILES.packageName })
        runBlocking {
            container.database.hostDao().insert(Host(nickname = "test server", hostname = sshHost!!, port = sshPort, username = sshUser, authType = AuthType.PASSWORD))
        }
        val name = "shared-${System.nanoTime()}.txt"
        share(TestFilesProvider.share(app, name, "from Seren Files"))
        // Once the screen has slid in, so the hosts list behind it is gone.
        pollUntil("upload screen") { exists("Upload $name") && compose.onAllNodesWithText("test server").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("test server").performClick()

        // The connection's questions are asked right on the SFTP screen. (They are answered in
        // code: the password field's focus keeps Robolectric's Compose from going idle.)
        pollUntil("host key prompt") { exists("Trust and connect") }
        compose.onNodeWithText("Trust and connect").performClick()
        val session = container.sessionManager.sessions.value.single()
        pollUntil("password prompt") { session.prompt.value is SessionPrompt.Password }
        (session.prompt.value as SessionPrompt.Password).respond(PasswordResponse(sshPassword, false))
        pollUntil("connected") { session.state.value == SessionState.Connected && session.prompt.value == null }

        pollUntil("upload card") { exists("Upload here") }
        val remote = runBlocking { session.openSftpChannel() }
        val target = SftpClient.join(runBlocking { remote.home() }, name)
        try {
            compose.onNodeWithText("Upload here").performClick()
            pollUntil("uploaded") { runCatching { runBlocking { remote.readText(target) } }.getOrNull() == "from Seren Files" }
            // Listed in the folder (not just named in the "Uploaded" message).
            pollUntil("listed") { compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() && !exists("Upload here") }

            // Download it back, as if saved with the system file picker.
            compose.onNodeWithText(name).performClick()
            pollUntil("save picker") { shadowOf(compose.activity).peekNextStartedActivityForResult() != null }
            val picker = shadowOf(compose.activity).nextStartedActivityForResult
            val saved = File(app.cacheDir, "downloaded-$name")
            compose.runOnUiThread { shadowOf(compose.activity).receiveResult(picker.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(saved))) }
            pollUntil("downloaded") { exists("Show in Seren Files") }
            assertEquals("from Seren Files", saved.readText())

            compose.onNodeWithText("Show in Seren Files").performClick()
            compose.waitForIdle()
            val reveal = shadowOf(app).nextStartedActivity
            assertEquals(Suite.ACTION_REVEAL, reveal.action)
            assertEquals(SuiteApp.FILES.packageName, reveal.`package`)
            assertEquals(Uri.fromFile(saved), reveal.data)
            assertEquals(name, reveal.getStringExtra(Suite.EXTRA_DISPLAY_NAME))
        } finally {
            runBlocking { runCatching { remote.delete(remote.stat(target)) } }
            remote.close()
        }
    }
}
