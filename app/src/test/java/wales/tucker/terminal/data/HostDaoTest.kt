package wales.tucker.terminal.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import wales.tucker.terminal.TestApp

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class HostDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun tearDown() = db.close()

    @Test
    fun hostsAreSortedByNameNotByLastConnection() = runBlocking {
        val dao = db.hostDao()
        dao.insert(Host(nickname = "web", hostname = "w.example.com", username = "u", lastConnectedAt = 1))
        dao.insert(Host(nickname = "", hostname = "b.example.com", username = "u", lastConnectedAt = 99))
        dao.insert(Host(nickname = "Alpha", hostname = "z.example.com", username = "u"))
        dao.insert(Host(nickname = "cache", hostname = "c.example.com", username = "u", lastConnectedAt = 50))
        assertEquals(listOf("Alpha", "b.example.com", "cache", "web"), dao.observeAll().first().map { it.displayName })
    }
}
