package wales.tucker.seren.auth.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import wales.tucker.seren.auth.MainActivity
import wales.tucker.seren.auth.TestApp
import wales.tucker.seren.auth.otp.OtpAlgorithm
import wales.tucker.seren.auth.otp.OtpToken
import wales.tucker.seren.auth.otp.OtpType
import wales.tucker.seren.core.ui.theme.ThemeMode

/**
 * Renders the main screens with Robolectric's native graphics and saves screenshots to
 * auth/build/screenshots, for the README.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()

    private fun shot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/screenshots/$name.png")
    }

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun seed() = runBlocking {
        // 12 seconds into a period, so rings are part way round; demo keys, not real ones.
        app.clock.time.value = 1_700_000_022_000L
        val repo = app.container.accounts
        repo.add(OtpToken("GitHub", "octocat", "JBSWY3DPEHPK3PXP"), 7)
        repo.add(OtpToken("Proton", "sam@proton.me", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"), 4)
        repo.add(OtpToken("AWS", "admin@example-prod", "MFRGGZDFMZTWQ2LKNNWG23TPOBYXE43U", algorithm = OtpAlgorithm.SHA256), 3)
        repo.add(OtpToken("Tailscale", "sam@example.com", "KRUGKIDROVUWG2ZAMJZG653OEBTG66BA"), 1)
        repo.add(OtpToken("Home VPN", "raspberry-pi", "ONSWG4TFOQQGQ33NMUQHM4DOEBQWG3LF", OtpType.HOTP, counter = 12), 0)
        repo.add(OtpToken("Fastmail", "sam@fastmail.com", "MZQXG5DNMFUWYIDDN5SGKIDTMVRXEZLU"), 2)
    }

    @Test
    fun mainScreens() {
        seed()
        waitFor("octocat")
        shot("01_accounts")

        compose.onNodeWithText("Add account", useUnmergedTree = true).performClick()
        waitFor("Scan from an image")
        shot("02_add")
        compose.onNodeWithText("Enter setup key").performClick()
        waitFor("Setup key")
        compose.activity.onBackPressedDispatcher.onBackPressed()
        waitFor("octocat")

        compose.onAllNodesWithContentDescription("More").onFirst().performClick()
        compose.onNodeWithText("Edit").performClick()
        waitFor("Current code")
        shot("03_editor")
        compose.activity.onBackPressedDispatcher.onBackPressed()
        waitFor("octocat")

        compose.onNodeWithText("Settings").performClick()
        waitFor("Block screenshots")
        shot("04_settings")
    }

    @Test
    fun emptyAndDark() {
        runBlocking { app.container.settings.setThemeMode(ThemeMode.DARK) }
        try {
            waitFor("No accounts yet")
            shot("05_empty_dark")
            seed()
            waitFor("octocat")
            shot("06_accounts_dark")
        } finally {
            // Settings outlive the test, so put them back for the others.
            runBlocking { app.container.settings.setThemeMode(ThemeMode.SYSTEM) }
        }
    }

    @Test
    fun qrCode() {
        seed()
        waitFor("octocat")
        compose.onAllNodesWithContentDescription("More")[2].performClick()
        compose.onNodeWithText("Show QR code").performClick()
        waitFor("Scan to add")
        shot("07_qr_code")
    }
}
