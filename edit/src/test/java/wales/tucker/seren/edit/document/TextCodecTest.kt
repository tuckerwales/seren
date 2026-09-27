package wales.tucker.seren.edit.document

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TextCodecTest {

    private fun roundTrip(bytes: ByteArray): DecodedText {
        val decoded = TextCodec.decode(bytes)
        assertArrayEquals(bytes, TextCodec.encode(decoded.text, decoded.encoding, decoded.lineEnding))
        return decoded
    }

    @Test
    fun plainUtf8() {
        val d = roundTrip("fun main() {\n    println(\"Shwmae, byd 🌟\")\n}\n".toByteArray())
        assertEquals(TextEncoding.UTF_8, d.encoding)
        assertEquals(LineEnding.LF, d.lineEnding)
    }

    @Test
    fun windowsLineEndingsAreHiddenWhileEditingAndKeptOnSave() {
        val d = roundTrip("one\r\ntwo\r\n".toByteArray())
        assertEquals("one\ntwo\n", d.text)
        assertEquals(LineEnding.CRLF, d.lineEnding)
        assertArrayEquals("one\r\ntwo\r\nthree".toByteArray(), TextCodec.encode(d.text + "three", d.encoding, d.lineEnding))
    }

    @Test
    fun classicMacLineEndings() {
        val d = roundTrip("a\rb\r".toByteArray())
        assertEquals("a\nb\n", d.text)
        assertEquals(LineEnding.CR, d.lineEnding)
    }

    @Test
    fun byteOrderMarksAreKept() {
        val utf8 = roundTrip(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "héllo".toByteArray())
        assertEquals("héllo", utf8.text)
        assertEquals("UTF-8 with BOM", utf8.encoding.label)

        val utf16 = roundTrip(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "hi\r\n".toByteArray(Charsets.UTF_16LE))
        assertEquals("hi\n", utf16.text)
        assertEquals(Charsets.UTF_16LE, utf16.encoding.charset)
        assertEquals(LineEnding.CRLF, utf16.lineEnding)
    }

    @Test
    fun invalidUtf8IsReadAsLatin1WithoutLosingBytes() {
        val bytes = byteArrayOf('c'.code.toByte(), 0xE9.toByte(), 'f'.code.toByte(), 0xFF.toByte())
        val d = roundTrip(bytes)
        assertEquals("Latin-1", d.encoding.label)
        assertEquals("cé", d.text.substring(0, 2))
    }

    @Test(expected = NotTextException::class)
    fun binaryFilesAreRefused() {
        TextCodec.decode(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 0, 0, 1))
    }

    @Test
    fun emptyFile() {
        val d = roundTrip(ByteArray(0))
        assertEquals("", d.text)
        assertEquals(LineEnding.LF, d.lineEnding)
    }

    @Test
    fun storagePaths() {
        assertEquals("Internal storage/Documents", DocumentStore.storagePath("primary:Documents/notes.txt", parentOnly = true))
        assertEquals("Internal storage", DocumentStore.storagePath("primary:notes.txt", parentOnly = true))
        assertEquals("Internal storage/Documents/Work", DocumentStore.storagePath("primary:Documents/Work", parentOnly = false))
        assertEquals("1A2B-3C4D/Books", DocumentStore.storagePath("1A2B-3C4D:Books", parentOnly = false))
    }
}
