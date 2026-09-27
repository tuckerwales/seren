package wales.tucker.seren.edit.document

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** How a file ends its lines. The editor always works with "\n" and converts back on save. */
enum class LineEnding(val chars: String, val label: String) {
    LF("\n", "LF"),
    CRLF("\r\n", "CRLF"),
    CR("\r", "CR"),
}

/** A file's character encoding, and whether it starts with a byte order mark. */
data class TextEncoding(val charset: Charset, val bom: Boolean) {
    val label: String
        get() = when (charset) {
            Charsets.UTF_8 -> if (bom) "UTF-8 with BOM" else "UTF-8"
            Charsets.UTF_16LE -> "UTF-16 LE"
            Charsets.UTF_16BE -> "UTF-16 BE"
            Charsets.ISO_8859_1 -> "Latin-1"
            else -> charset.name()
        }

    companion object {
        val UTF_8 = TextEncoding(Charsets.UTF_8, bom = false)
    }
}

/** A file's text, with "\n" line breaks, and what is needed to write it back byte for byte. */
data class DecodedText(val text: String, val encoding: TextEncoding, val lineEnding: LineEnding)

class NotTextException : Exception("It doesn't look like a text file")

/**
 * Turns file bytes into editable text and back. Files are written back in the encoding and with the
 * line endings they were read with, so saving a file without edits leaves it unchanged (except that
 * a file mixing line endings is written with only its most common one).
 */
object TextCodec {
    private val UTF_8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val UTF_16LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val UTF_16BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

    /** Bytes inspected for NUL characters, which text files don't have. */
    private const val BINARY_SNIFF_BYTES = 8000

    fun decode(bytes: ByteArray): DecodedText {
        val (encoding, bomSize) = when {
            bytes.startsWith(UTF_8_BOM) -> TextEncoding(Charsets.UTF_8, bom = true) to UTF_8_BOM.size
            bytes.startsWith(UTF_16LE_BOM) -> TextEncoding(Charsets.UTF_16LE, bom = true) to UTF_16LE_BOM.size
            bytes.startsWith(UTF_16BE_BOM) -> TextEncoding(Charsets.UTF_16BE, bom = true) to UTF_16BE_BOM.size
            else -> {
                if ((0 until minOf(bytes.size, BINARY_SNIFF_BYTES)).any { bytes[it] == 0.toByte() }) throw NotTextException()
                // Anything that isn't valid UTF-8 is read as Latin-1, which maps every byte to a
                // character and back, so saving never corrupts it.
                val utf8 = if (isValidUtf8(bytes)) Charsets.UTF_8 else Charsets.ISO_8859_1
                TextEncoding(utf8, bom = false) to 0
            }
        }
        val raw = String(bytes, bomSize, bytes.size - bomSize, encoding.charset)
        val lineEnding = detectLineEnding(raw)
        val text = if (lineEnding == LineEnding.LF) raw else raw.replace("\r\n", "\n").replace('\r', '\n')
        return DecodedText(text, encoding, lineEnding)
    }

    fun encode(text: String, encoding: TextEncoding, lineEnding: LineEnding): ByteArray {
        val raw = if (lineEnding == LineEnding.LF) text else text.replace("\n", lineEnding.chars)
        val body = raw.toByteArray(encoding.charset)
        if (!encoding.bom) return body
        val bom = when (encoding.charset) {
            Charsets.UTF_16LE -> UTF_16LE_BOM
            Charsets.UTF_16BE -> UTF_16BE_BOM
            else -> UTF_8_BOM
        }
        return bom + body
    }

    /** The most common line ending in [text]; LF when it has no line breaks. */
    fun detectLineEnding(text: CharSequence): LineEnding {
        var crlf = 0
        var lf = 0
        var cr = 0
        var i = 0
        while (i < text.length) {
            when (text[i]) {
                '\r' -> if (i + 1 < text.length && text[i + 1] == '\n') { crlf++; i++ } else cr++
                '\n' -> lf++
            }
            i++
        }
        return when {
            crlf > lf && crlf >= cr -> LineEnding.CRLF
            cr > lf && cr > crlf -> LineEnding.CR
            else -> LineEnding.LF
        }
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        true
    } catch (_: CharacterCodingException) {
        false
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
