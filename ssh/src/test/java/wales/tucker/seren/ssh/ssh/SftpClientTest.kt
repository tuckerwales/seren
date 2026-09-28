package wales.tucker.seren.ssh.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpClientTest {
    @Test
    fun joinHandlesRootAndNestedFolders() {
        assertEquals("/a", SftpClient.join("/", "a"))
        assertEquals("/root/a", SftpClient.join("/root", "a"))
        assertEquals("/root/a", SftpClient.join("/root/", "a"))
    }

    @Test
    fun parentOfRootIsRoot() {
        assertEquals("/", SftpClient.parent("/"))
        assertEquals("/", SftpClient.parent("/root"))
        assertEquals("/root", SftpClient.parent("/root/file.txt"))
        assertEquals("/a/b", SftpClient.parent("/a/b/c"))
    }

    @Test
    fun quotePathEscapesGlobCharactersInTheFileNameOnly() {
        assertEquals("/root/notes.txt", SftpClient.quotePath("/root/notes.txt"))
        assertEquals("/root/report\\*.txt", SftpClient.quotePath("/root/report*.txt"))
        assertEquals("/tmp/a\\?b", SftpClient.quotePath("/tmp/a?b"))
        assertEquals("plain", SftpClient.quoteName("plain"))
        assertEquals("x\\\\y", SftpClient.quoteName("x\\y"))
    }

    @Test
    fun progressMonitorStopsWhenJobIsCancelledAndNeverThrowsOut() {
        val job = kotlinx.coroutines.Job()
        var last = -1L
        val monitor = safeProgressMonitor(total = 100L, job = job) { done, _ ->
            last = done
            if (done == 50L) error("ui blew up")
        }
        monitor.init(0, null, "dst", 100)
        assertEquals(0L, last)
        assertTrue(monitor.count(50))
        assertEquals(50L, last)
        job.cancel()
        assertFalse(monitor.count(10))
        assertEquals(60L, last)
        monitor.end()
    }
}
