package wales.tucker.seren.auth.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import wales.tucker.seren.auth.TestApp
import wales.tucker.seren.auth.backup.BackupEntry
import wales.tucker.seren.auth.otp.OtpToken
import wales.tucker.seren.auth.otp.OtpType

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class AccountRepositoryTest {
    private val container get() = ApplicationProvider.getApplicationContext<TestApp>().container
    private val repo get() = container.accounts

    private val github = OtpToken("GitHub", "octocat", "JBSWY3DPEHPK3PXP")
    private val aws = OtpToken("AWS", "admin", "GEZDGNBVGY3TQOJQ")

    @Test
    fun setupKeysAreStoredEncrypted() = runBlocking {
        repo.add(github)
        val stored = container.database.accounts().all().single()
        assertFalse(stored.secretEnc.contains(github.secret))
        assertTrue(stored.secretEnc.startsWith("enc:"))
        assertEquals(github, repo.all().single().token)
    }

    @Test
    fun theSameKeyIsNotAddedTwice() = runBlocking {
        assertTrue(repo.add(github) is AccountRepository.AddResult.Added)
        val again = repo.add(github.copy(issuer = "Other"))
        assertEquals("GitHub", (again as AccountRepository.AddResult.Duplicate).existing.token.issuer)
    }

    @Test
    fun importSkipsKeysAlreadyHereAndRepeatsInTheFile() = runBlocking {
        repo.add(github)
        val entries = listOf(BackupEntry(github), BackupEntry(aws, 3), BackupEntry(aws))
        assertEquals(2, repo.countExisting(entries))
        assertEquals(AccountRepository.ImportSummary(added = 1, duplicates = 2), repo.import(entries))
        val all = repo.accounts.first()
        assertEquals(listOf("AWS", "GitHub"), all.map { it.token.issuer })
        assertEquals(3, all.first().color)
    }

    @Test
    fun counterBasedAccountsMoveOnAndDeletedOnesComeBack() = runBlocking {
        val id = (repo.add(OtpToken("VPN", "me", "JBSWY3DPEHPK3PXP", OtpType.HOTP)) as AccountRepository.AddResult.Added).id
        repo.nextCounter(id)
        val account = repo.get(id)!!
        assertEquals(1, account.token.counter)
        repo.delete(id)
        assertTrue(repo.all().isEmpty())
        repo.restore(account)
        assertEquals(account, repo.get(id))
    }
}
