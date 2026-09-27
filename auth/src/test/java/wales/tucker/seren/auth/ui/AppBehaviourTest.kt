package wales.tucker.seren.auth.ui

import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import wales.tucker.seren.auth.MainActivity
import wales.tucker.seren.auth.TestApp
import wales.tucker.seren.auth.backup.BackupEntry
import wales.tucker.seren.auth.backup.SerenBackup
import wales.tucker.seren.auth.otp.OtpAuthUri
import wales.tucker.seren.auth.otp.OtpToken
import wales.tucker.seren.auth.otp.OtpType
import wales.tucker.seren.auth.otp.formatCode
import java.io.File
import java.net.URLEncoder
import java.util.Base64

/** Drives the real UI: adding, copying, editing, deleting, importing and exporting accounts. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppBehaviourTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val repo get() = app.container.accounts
    private val github = OtpToken("GitHub", "octocat", "JBSWY3DPEHPK3PXP")

    private fun exists(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = compose.waitUntil(5_000) { exists(text) }

    private fun code(token: OtpToken) = formatCode(token.code(app.clock.now()))

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    @Test
    fun addsAnAccountFromItsSetupKey() {
        waitFor("No accounts yet")
        compose.onNodeWithText("Add account").performClick()
        compose.onNodeWithText("Enter setup key").performClick()
        waitFor("Setup key")

        compose.onNodeWithText("Save").performClick()
        waitFor("Enter the setup key")
        compose.onNodeWithText("Enter a service or an account name").assertExists()

        field("Service").performTextInput("GitHub")
        field("Account").performTextInput("octocat")
        field("Setup key").performTextInput("jbsw y3dp ehpk 3pxp")
        // The preview lets people check the code against the site before saving.
        waitFor("Current code")
        compose.onNodeWithText(code(github)).assertExists()

        compose.onNodeWithText("Save").performClick()
        waitFor("Added GitHub")
        compose.onNodeWithText("octocat").assertExists()
        compose.onNodeWithText(code(github)).assertExists()
        assertEquals(github, runBlocking { repo.all() }.single().token)
    }

    @Test
    fun badSetupKeysAndDuplicatesAreExplained() {
        runBlocking { repo.add(github) }
        waitFor("GitHub")
        compose.onNodeWithText("Add account", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Enter setup key").performClick()
        field("Service").performTextInput("Other")
        field("Setup key").performTextInput("NOT-A-KEY-1")
        waitFor("Setup keys use only the letters A to Z and the digits 2 to 7")

        field("Setup key").performTextReplacement("JBSWY3DPEHPK3PXP")
        compose.onNodeWithText("Save").performClick()
        waitFor("This setup key is already in Seren Auth as GitHub")
    }

    @Test
    fun tappingAnAccountCopiesItsCode() {
        runBlocking { repo.add(github) }
        waitFor("octocat")
        compose.onNodeWithText("octocat").performClick()
        waitFor("Copied the GitHub code")
        val clip = app.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertEquals(github.code(app.clock.now()), clip.getItemAt(0).text.toString())
    }

    @Test
    fun codesChangeWithTimeAndShowTheNextOneNearTheEnd() {
        runBlocking { repo.add(github) }
        app.clock.time.value = 1_700_000_010_000L // the start of a 30 second period
        waitFor(code(github))
        compose.onNodeWithContentDescription("30 seconds left").assertExists()
        assertTrue(!exists("Next "))

        app.clock.time.value = 1_700_000_035_000L // 5 seconds left in the next period
        waitFor(code(github))
        compose.onNodeWithContentDescription("5 seconds left").assertExists()
        val next = formatCode(github.code(app.clock.now() + 30_000))
        waitFor("Next $next")
    }

    @Test
    fun counterBasedAccountsMoveOnWhenAsked() {
        val vpn = OtpToken("VPN", "me", "GEZDGNBVGY3TQOJQ", OtpType.HOTP)
        runBlocking { repo.add(vpn) }
        waitFor(code(vpn))
        compose.onNodeWithContentDescription("Next code").performClick()
        waitFor(code(vpn.copy(counter = 1)))
        assertEquals(1, runBlocking { repo.all() }.single().token.counter)
    }

    @Test
    fun hiddenCodesShowWhenTapped() {
        runBlocking {
            repo.add(github)
            app.container.settings.setHideCodes(true)
        }
        try {
            waitFor("••• •••")
            assertTrue(!exists(code(github)))
            compose.onNodeWithText("••• •••").performClick()
            waitFor(code(github))
        } finally {
            runBlocking { app.container.settings.setHideCodes(false) }
        }
    }

    @Test
    fun searchFiltersAccounts() {
        runBlocking {
            repo.add(github)
            repo.add(OtpToken("AWS", "admin", "GEZDGNBVGY3TQOJQ"))
        }
        waitFor("AWS")
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("git")
        compose.waitUntil(5_000) { !exists("AWS") }
        compose.onNodeWithText("octocat").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("zzz")
        waitFor("No matches")
    }

    @Test
    fun editsAnAccount() {
        runBlocking { repo.add(github) }
        waitFor("octocat")
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Edit").performClick()
        waitFor("Edit account")
        field("Account").performTextReplacement("monalisa")

        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("Discard changes to GitHub?")
        compose.onNodeWithText("Keep editing").performClick()

        compose.onNodeWithText("Save").performClick()
        waitFor("Saved GitHub")
        compose.onNodeWithText("monalisa").assertExists()
        assertEquals("monalisa", runBlocking { repo.all() }.single().token.name)
    }

    @Test
    fun deletingAsksFirstAndCanBeUndone() {
        runBlocking { repo.add(github) }
        waitFor("octocat")
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Delete").performClick()
        waitFor("Delete GitHub?")
        compose.onNodeWithText("Delete").performClick()
        waitFor("No accounts yet")
        compose.onNodeWithText("Undo").performClick()
        waitFor("octocat")
        assertEquals(1, runBlocking { repo.all() }.size)
    }

    @Test
    fun showsAnAccountsQrCode() {
        runBlocking { repo.add(github) }
        waitFor("octocat")
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Show QR code").performClick()
        waitFor("Scan to add GitHub")
        compose.onNodeWithContentDescription("QR code for GitHub").assertExists()
        compose.onNodeWithText("Done").performClick()
    }

    @Test
    fun otpauthLinksOpenInTheEditorToCheckFirst() {
        waitFor("No accounts yet")
        compose.activity.openLinks.trySend(OtpAuthUri.format(github.copy(digits = 8)))
        waitFor("Add account")
        compose.onNodeWithText("octocat").assertExists()
        compose.onNodeWithText("Save").performClick()
        waitFor("Added GitHub")
        assertEquals(8, runBlocking { repo.all() }.single().token.digits)
    }

    @Test
    fun googleAuthenticatorTransferCodesImportEveryAccount() {
        fun field(n: Int, b: ByteArray) = byteArrayOf(((n shl 3) or 2).toByte(), b.size.toByte()) + b
        val secret = "12345678901234567890".toByteArray()
        val a = field(1, secret) + field(2, "alice".toByteArray()) + field(3, "Example".toByteArray())
        val b = field(1, "abcdefghijabcdefghij".toByteArray()) + field(2, "bob".toByteArray())
        val data = URLEncoder.encode(Base64.getEncoder().encodeToString(field(1, a) + field(1, b)), "UTF-8")
        waitFor("No accounts yet")
        compose.activity.openLinks.trySend("otpauth-migration://offline?data=$data")
        waitFor("Import 2 accounts from Google Authenticator?")
        compose.onNodeWithText("Import").performClick()
        waitFor("Added 2 accounts")
        compose.onNodeWithText("alice").assertExists()
    }

    @Test
    fun exportsOtpauthLinksAfterAWarning() {
        runBlocking {
            repo.add(github)
            repo.add(OtpToken("AWS", "admin", "GEZDGNBVGY3TQOJQ"))
        }
        waitFor("octocat")
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Export as otpauth links").performScrollTo().performClick()
        waitFor("Export without encryption?")
        compose.onNodeWithText("Export").performClick()

        val out = File(app.cacheDir, "links.txt")
        answerFilePicker(Uri.fromFile(out))
        waitThroughSpinner("Exported 2 accounts")
        val lines = out.readLines().filter { it.isNotBlank() }
        assertEquals(setOf("GitHub", "AWS"), lines.map { OtpAuthUri.parse(it).issuer }.toSet())
    }

    @Test
    fun importsABackupFileSkippingAccountsAlreadyHere() {
        runBlocking { repo.add(github) }
        val file = File(app.cacheDir, "import.json").apply {
            writeText(SerenBackup.export(listOf(BackupEntry(github), BackupEntry(OtpToken("AWS", "admin", "GEZDGNBVGY3TQOJQ"), 4)), password = null))
        }
        waitFor("octocat")
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Import").performScrollTo().performClick()
        answerFilePicker(Uri.fromFile(file))

        waitFor("Import 1 account from a Seren Auth backup?")
        compose.onNodeWithText("1 account is already in Seren Auth and will be skipped.").assertExists()
        compose.onNode(hasText("Import") and hasAnyAncestor(isDialog())).performClick()
        waitFor("Added 1 account, skipped 1 already here")
        assertEquals(listOf("AWS", "GitHub"), runBlocking { repo.all() }.map { it.token.issuer })
    }

    @Test
    fun unreadableFilesAreExplained() {
        val file = File(app.cacheDir, "notes.txt").apply { writeText("shopping list") }
        waitFor("No accounts yet")
        compose.onNodeWithText("Import from a file").performClick()
        answerFilePicker(Uri.fromFile(file))
        waitFor("Couldn't import")
        compose.onNodeWithText("This isn't a file Seren Auth can import").assertExists()
        compose.onNodeWithText("Close").performClick()
    }

    @Test
    fun withoutCameraAccessTheScannerOffersOtherWays() {
        waitFor("No accounts yet")
        compose.onNodeWithText("Add account").performClick()
        compose.onNodeWithText("Scan QR code").performClick()
        waitFor("Camera access is off")
        compose.onNodeWithText("Allow camera").assertExists()
        compose.onNodeWithText("Enter setup key instead").performClick()
        waitFor("Setup key")
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("No accounts yet")
    }

    @Test
    fun lockScreenNamesTheApp() {
        compose.activity.locked = true
        waitFor("Seren Auth is locked")
        compose.activity.locked = false
        waitFor("No accounts yet")
    }

    /**
     * Waits for [text] while a progress spinner may be showing. A spinner animates forever, so
     * Compose never reports idle; step the clock by hand instead.
     */
    private fun waitThroughSpinner(text: String) {
        compose.mainClock.autoAdvance = false
        try {
            val deadline = System.currentTimeMillis() + 10_000
            while (!exists(text)) {
                check(System.currentTimeMillis() < deadline) { "Timed out waiting for \"$text\"" }
                compose.mainClock.advanceTimeByFrame()
                Thread.sleep(10)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    /** Plays the system file picker: answers the activity the app just started with [uri]. */
    private fun answerFilePicker(uri: Uri) {
        compose.waitForIdle()
        val shadow = shadowOf(compose.activity)
        val started = shadow.nextStartedActivityForResult ?: error("No file picker was opened")
        shadow.receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(uri))
    }
}
