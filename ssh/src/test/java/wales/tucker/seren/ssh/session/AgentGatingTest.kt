package wales.tucker.seren.ssh.session

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import wales.tucker.seren.ssh.AppContainer
import wales.tucker.seren.ssh.SerenApp
import wales.tucker.seren.ssh.TestApp
import wales.tucker.seren.ssh.data.AuthType
import wales.tucker.seren.ssh.data.Host
import wales.tucker.seren.ssh.data.KeyType
import wales.tucker.seren.ssh.data.SshKey
import wales.tucker.seren.ssh.ssh.AgentLockedException
import wales.tucker.seren.ssh.ssh.ForwardAgentRequiresAppLockException
import wales.tucker.seren.ssh.ssh.SshKeys

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class AgentGatingTest {

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = ApplicationProvider.getApplicationContext<SerenApp>().container
        container.agent.wipe()
        runBlocking { container.settings.setAppLock(false) }
    }

    @Test
    fun unlockAgentRequiresAppLock() {
        assertThrows(ForwardAgentRequiresAppLockException::class.java) {
            runBlocking { container.sessionManager.unlockAgent() }
        }
        assertFalse(container.agent.isUnlocked)
    }

    @Test
    fun unlockAgentLoadsKeysWhenAppLockOn() = runBlocking {
        container.settings.setAppLock(true)
        val material = SshKeys.generate(KeyType.ED25519, 256, "gate")
        container.database.keyDao().insert(
            SshKey(
                name = "gate",
                type = KeyType.ED25519,
                bits = 256,
                encryptedPrivateKey = container.secretBox.encryptString(material.privateKey),
                publicKey = material.publicKey,
                fingerprint = material.fingerprint,
            ),
        )
        container.sessionManager.unlockAgent()
        assertTrue(container.agent.isUnlocked)
        assertTrue(container.agent.keyCount >= 1)
        container.sessionManager.wipeAgent()
        assertFalse(container.agent.isUnlocked)
    }

    @Test
    fun openWithForwardAgentRefusesWhenAppLockOff() {
        val host = Host(
            nickname = "box",
            hostname = "127.0.0.1",
            username = "u",
            authType = AuthType.NONE,
            forwardAgent = true,
        )
        assertThrows(ForwardAgentRequiresAppLockException::class.java) {
            runBlocking { container.sessionManager.open(host) }
        }
    }

    @Test
    fun openWithForwardAgentRefusesWhenAgentLocked() {
        runBlocking { container.settings.setAppLock(true) }
        val host = Host(
            nickname = "box",
            hostname = "127.0.0.1",
            username = "u",
            authType = AuthType.NONE,
            forwardAgent = true,
        )
        assertThrows(AgentLockedException::class.java) {
            runBlocking { container.sessionManager.open(host) }
        }
    }

    @Test
    fun settingsReportAppLockOffByDefault() = runBlocking {
        assertFalse(container.settings.settings.first().appLock)
    }
}
