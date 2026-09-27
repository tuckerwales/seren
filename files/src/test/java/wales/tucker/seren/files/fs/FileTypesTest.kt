package wales.tucker.seren.files.fs

import org.junit.Assert.assertEquals
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
}
