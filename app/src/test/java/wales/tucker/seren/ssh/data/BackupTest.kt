package wales.tucker.seren.ssh.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import wales.tucker.seren.ssh.TestApp

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class BackupTest {
    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val source = database()
    private val target = database()

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    private fun key(db: AppDatabase, name: String) = runBlocking {
        db.keyDao().insert(SshKey(name = name, type = KeyType.ED25519, bits = 256, encryptedPrivateKey = "secret", publicKey = "pub", fingerprint = "fp"))
    }

    @Test
    fun roundTripsHostsForwardsJumpHostsAndSnippetsWithoutSecrets() = runBlocking {
        val keyId = key(source, "Laptop")
        val bastion = source.hostDao().insert(Host(nickname = "bastion", hostname = "b.example.com", username = "me", encryptedPassword = "pw"))
        val web = source.hostDao().insert(
            Host(nickname = "web", hostname = "10.0.0.5", port = 2222, username = "deploy", authType = AuthType.KEY, keyId = keyId, group = "Prod", jumpHostId = bastion, startupCommand = "tmux"),
        )
        source.hostDao().insert(Host(nickname = "nas", hostname = "nas.local", username = "admin", authType = AuthType.KEY, keyId = key(source, "Other")))
        source.portForwardDao().upsert(PortForward(hostId = web, type = ForwardType.LOCAL, sourcePort = 5432, destHost = "db", destPort = 5432))
        source.snippetDao().upsert(Snippet(name = "Disk", command = "df -h", autoRun = false))

        val json = Backup(source).export()
        assertFalse("no passwords", json.contains("\"pw\""))
        assertFalse("no private keys", json.contains("secret"))

        key(target, "Laptop")
        val result = Backup(target).import(json)
        assertEquals(Backup.ImportResult(hosts = 3, snippets = 1, skipped = 0), result)

        val hosts = target.hostDao().all().associateBy { it.nickname }
        val importedWeb = hosts.getValue("web")
        assertEquals(hosts.getValue("bastion").id, importedWeb.jumpHostId)
        assertEquals(AuthType.KEY, importedWeb.authType)
        assertEquals(target.keyDao().all().single { it.name == "Laptop" }.id, importedWeb.keyId)
        assertEquals(listOf(2222, "Prod", "tmux"), listOf(importedWeb.port, importedWeb.group, importedWeb.startupCommand))
        assertNull(hosts.getValue("bastion").encryptedPassword)
        // Its key is not on this device, so it asks for credentials instead.
        assertEquals(AuthType.NONE, hosts.getValue("nas").authType)
        assertEquals(listOf(5432), target.portForwardDao().forHost(importedWeb.id).map { it.destPort })
        assertEquals(listOf("df -h"), target.snippetDao().all().map { it.command })

        // Importing again adds nothing.
        assertEquals(Backup.ImportResult(hosts = 0, snippets = 0, skipped = 4), Backup(target).import(json))
    }

    @Test
    fun importsBackupsMadeBeforeTheRename() = runBlocking {
        source.snippetDao().upsert(Snippet(name = "Disk", command = "df -h"))
        val json = Backup(source).export().replace(Backup.FORMAT, "wales.tucker.terminal.backup")

        assertEquals(Backup.ImportResult(hosts = 0, snippets = 1, skipped = 0), Backup(target).import(json))
    }

    @Test
    fun rejectsFilesThatAreNotBackups() = runBlocking {
        for (bad in listOf("", "not json", "{\"hosts\": []}")) {
            val error = runCatching { Backup(target).import(bad) }.exceptionOrNull()
            assertTrue(bad, error is IllegalArgumentException)
        }
    }
}
