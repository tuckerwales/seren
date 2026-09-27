package wales.tucker.seren.files.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTypesTest {
    @Test
    fun extensions() {
        assertEquals("jpg", FileTypes.extension("IMG_0001.JPG"))
        assertEquals("gz", FileTypes.extension("backup.tar.gz"))
        assertEquals("", FileTypes.extension(".bashrc"))
        assertEquals("", FileTypes.extension("README"))
        assertEquals("", FileTypes.extension("trailing."))
    }

    @Test
    fun kinds() {
        assertEquals(FileKind.IMAGE, FileTypes.kind("photo.HEIC", isDirectory = false))
        assertEquals(FileKind.VIDEO, FileTypes.kind("clip.mp4", isDirectory = false))
        assertEquals(FileKind.AUDIO, FileTypes.kind("song.flac", isDirectory = false))
        assertEquals(FileKind.PDF, FileTypes.kind("paper.pdf", isDirectory = false))
        assertEquals(FileKind.DOCUMENT, FileTypes.kind("budget.xlsx", isDirectory = false))
        assertEquals(FileKind.TEXT, FileTypes.kind("notes.md", isDirectory = false))
        assertEquals(FileKind.ARCHIVE, FileTypes.kind("photos.zip", isDirectory = false))
        assertEquals(FileKind.APP, FileTypes.kind("app.apk", isDirectory = false))
        assertEquals(FileKind.OTHER, FileTypes.kind("data.bin", isDirectory = false))
        assertEquals(FileKind.FOLDER, FileTypes.kind("photos.zip", isDirectory = true))
    }

    @Test
    fun mimeTypesFallBackWhenAndroidDoesNotKnow() {
        assertEquals("text/markdown", FileTypes.mimeType("notes.md"))
        assertEquals("application/pdf", FileTypes.mimeType("paper.pdf"))
        assertEquals("image/*", FileTypes.mimeType("scan.dng"))
        assertEquals("application/octet-stream", FileTypes.mimeType("data.bin"))
    }

    @Test
    fun textWorthOfferingToAnEditor() {
        for (name in listOf("notes.txt", "build.gradle.kts", ".bashrc", "Makefile", "config", "id_ed25519.pub", "server.pem")) {
            assertTrue(name, FileTypes.looksLikeText(name))
        }
        for (name in listOf("photo.jpg", "backup.zip", "paper.pdf", "app.apk", "song.mp3")) {
            assertFalse(name, FileTypes.looksLikeText(name))
        }
    }

    @Test
    fun editorTypesAreAlwaysText() {
        assertEquals("text/markdown", FileTypes.textMimeType("notes.md"))
        assertEquals("text/yaml", FileTypes.textMimeType("compose.yml"))
        // Seren Edit only accepts text, so anything else goes as plain text.
        assertEquals("text/plain", FileTypes.textMimeType(".bashrc"))
        assertEquals("text/plain", FileTypes.textMimeType("id_ed25519.pub"))
    }

    @Test
    fun privateKeysByNameAndSize() {
        assertTrue(FileTypes.looksLikePrivateKey("id_ed25519", 411))
        assertTrue(FileTypes.looksLikePrivateKey("id_rsa", 2_602))
        assertTrue(FileTypes.looksLikePrivateKey("aws-server.pem", 1_700))
        assertTrue(FileTypes.looksLikePrivateKey("work.PPK", 1_400))
        // Public keys, empty files and anything too big to be a key are not offered.
        assertFalse(FileTypes.looksLikePrivateKey("id_ed25519.pub", 100))
        assertFalse(FileTypes.looksLikePrivateKey("id_rsa", 0))
        assertFalse(FileTypes.looksLikePrivateKey("bundle.pem", FileTypes.MAX_KEY_SIZE + 1))
        assertFalse(FileTypes.looksLikePrivateKey("notes.txt", 100))
    }
}
