package wales.tucker.seren.ssh.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import wales.tucker.seren.ssh.FakeSecretBox
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

    private val box = FakeSecretBox()

    private fun secretKey(db: AppDatabase, name: String, privateKey: String, fingerprint: String) = runBlocking {
        db.keyDao().insert(
            SshKey(
                name = name,
                type = KeyType.ED25519,
                bits = 256,
                encryptedPrivateKey = box.encryptString(privateKey),
                publicKey = "ssh-ed25519 AAAA $name",
                fingerprint = fingerprint,
            ),
        )
    }

    @Test
    fun passwordProtectedBackupsCarryKeysAndPasswords() = runBlocking {
        val keyId = secretKey(source, "Laptop", "-----BEGIN OPENSSH PRIVATE KEY-----\nabc\n", "SHA256:laptop")
        source.hostDao().insert(Host(nickname = "web", hostname = "web.example.com", username = "deploy", authType = AuthType.KEY, keyId = keyId))
        source.hostDao().insert(Host(nickname = "db", hostname = "db.example.com", username = "admin", encryptedPassword = box.encryptString("hunter22")))

        val json = Backup(source, box).export("correct horse".toCharArray())
        val root = JSONObject(json)
        assertEquals(Backup.FORMAT, root.getString("format"))
        assertEquals(2, root.getInt("version"))
        assertTrue(root.getBoolean("encrypted"))
        for (secret in listOf("hunter22", "OPENSSH", "web.example.com", "Laptop")) {
            assertFalse("$secret must not appear in the file", json.contains(secret))
        }
        assertTrue(Backup(target, box).isEncrypted(json))

        val needsPassword = runCatching { Backup(target, box).import(json) }.exceptionOrNull()
        assertTrue(needsPassword is Backup.PasswordRequiredException)
        val wrong = runCatching { Backup(target, box).import(json, "wrong".toCharArray()) }.exceptionOrNull()
        assertTrue(wrong is Backup.WrongPasswordException)
        assertTrue(target.hostDao().all().isEmpty())

        val result = Backup(target, box).import(json, "correct horse".toCharArray())
        assertEquals(Backup.ImportResult(hosts = 2, snippets = 0, skipped = 0, keys = 1), result)
        val key = target.keyDao().all().single()
        assertEquals("Laptop", key.name)
        assertEquals("-----BEGIN OPENSSH PRIVATE KEY-----\nabc\n", box.decryptString(key.encryptedPrivateKey))
        val hosts = target.hostDao().all().associateBy { it.nickname }
        assertEquals(AuthType.KEY, hosts.getValue("web").authType)
        assertEquals(key.id, hosts.getValue("web").keyId)
        assertEquals("hunter22", box.decryptString(hosts.getValue("db").encryptedPassword!!))

        // Importing again adds nothing: the key is known by its fingerprint.
        assertEquals(
            Backup.ImportResult(hosts = 0, snippets = 0, skipped = 3, keys = 0),
            Backup(target, box).import(json, "correct horse".toCharArray()),
        )
    }

    @Test
    fun importedKeysNeverReplaceADifferentKeyWithTheSameName() = runBlocking {
        val keyId = secretKey(source, "Laptop", "new key", "SHA256:new")
        source.hostDao().insert(Host(nickname = "web", hostname = "web.example.com", username = "deploy", authType = AuthType.KEY, keyId = keyId))
        val mine = secretKey(target, "Laptop", "old key", "SHA256:old")

        Backup(target, box).import(Backup(source, box).export("password".toCharArray()), "password".toCharArray())

        val keys = target.keyDao().all().associateBy { it.name }
        assertEquals("old key", box.decryptString(keys.getValue("Laptop").encryptedPrivateKey))
        assertEquals(mine, keys.getValue("Laptop").id)
        val imported = keys.getValue("Laptop (2)")
        assertEquals("new key", box.decryptString(imported.encryptedPrivateKey))
        // The host uses the key it was exported with, not the one that shares its name.
        assertEquals(imported.id, target.hostDao().all().single().keyId)
    }

    @Test
    fun plainBackupsStayReadableByOlderVersions() = runBlocking {
        secretKey(source, "Laptop", "private", "SHA256:laptop")
        val json = Backup(source, box).export()
        val root = JSONObject(json)
        assertEquals(1, root.getInt("version"))
        assertFalse(root.has("keys"))
        assertFalse(json.contains("private"))
        assertFalse(Backup(target).isEncrypted(json))
    }

    @Test
    fun damagedEncryptedBackupsAreRejected() = runBlocking {
        val root = JSONObject(Backup(source, box).export("password".toCharArray())).put("data", "%%%")
        val error = runCatching { Backup(target, box).import(root.toString(), "password".toCharArray()) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals("This backup is damaged", error?.message)
    }
}
