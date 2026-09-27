package wales.tucker.seren.files.fs

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import wales.tucker.seren.files.TestApp
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class RevealTest {
    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val internal = File("/storage/emulated/0")
    private val sd = File("/storage/1234-ABCD")
    private val volumes = listOf(
        Volume("Internal storage", internal, primary = true, totalBytes = 0, freeBytes = 0),
        Volume("SD card", sd, primary = false, totalBytes = 0, freeBytes = 0),
    )

    @Test
    fun storageDocumentIds() {
        assertEquals(File(internal, "Download/notes.txt"), Reveal.fromStorageId("primary:Download/notes.txt", volumes))
        assertEquals(File(sd, "Photos/a.jpg"), Reveal.fromStorageId("1234-ABCD:Photos/a.jpg", volumes))
        assertEquals(File(internal, "Documents/cv.pdf"), Reveal.fromStorageId("home:cv.pdf", volumes))
        assertEquals(internal, Reveal.fromStorageId("primary:", volumes))
        assertNull(Reveal.fromStorageId("9999-0000:gone.txt", volumes))
        assertNull(Reveal.fromStorageId("nonsense", volumes))
    }

    @Test
    fun linksFromTheSystemFilePicker() {
        // What saving a download with the system picker hands back.
        val saved = DocumentsContract.buildDocumentUri(Reveal.EXTERNAL_STORAGE, "primary:Download/report 1.txt")
        assertEquals(File(internal, "Download/report 1.txt"), Reveal.locate(app, saved, volumes))

        val tree = DocumentsContract.buildDocumentUriUsingTree(
            DocumentsContract.buildTreeDocumentUri(Reveal.EXTERNAL_STORAGE, "1234-ABCD:Photos"),
            "1234-ABCD:Photos/a.jpg",
        )
        assertEquals(File(sd, "Photos/a.jpg"), Reveal.locate(app, tree, volumes))

        val raw = DocumentsContract.buildDocumentUri(Reveal.DOWNLOADS, "raw:/storage/emulated/0/Download/x.zip")
        assertEquals(File(internal, "Download/x.zip"), Reveal.locate(app, raw, volumes))
    }

    @Test
    fun serenFilesOwnLinks() {
        val own = Uri.parse("content://${app.packageName}.files/storage/Music/song.mp3")
        assertEquals(File(internal, "Music/song.mp3"), Reveal.locate(app, own, volumes))
        val card = Uri.parse("content://${app.packageName}.files/volumes/1234-ABCD/DCIM/b.jpg")
        assertEquals(File(sd, "DCIM/b.jpg"), Reveal.locate(app, card, volumes))
    }

    @Test
    fun onlyFilesOnStorageAreShown() {
        assertEquals(File(internal, "a.txt"), Reveal.locate(app, Uri.fromFile(File(internal, "a.txt")), volumes))
        assertNull(Reveal.locate(app, Uri.fromFile(File("/data/data/wales.tucker.seren.ssh/databases/terminal.db")), volumes))
        // A path that climbs out of storage is refused too.
        assertNull(Reveal.locate(app, Uri.fromFile(File(internal, "../../../data/secret")), volumes))
        val raw = DocumentsContract.buildDocumentUri(Reveal.DOWNLOADS, "raw:/proc/self/environ")
        assertNull(Reveal.locate(app, raw, volumes))
        assertNull(Reveal.locate(app, Uri.parse("content://com.example.other/document/1"), volumes))
        assertNull(Reveal.locate(app, Uri.parse("https://example.com/a.txt"), volumes))
    }
}
