package wales.tucker.seren.auth.qr

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import wales.tucker.seren.auth.TestApp

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QrCodesTest {
    private val link = "otpauth://totp/GitHub:octocat?secret=JBSWY3DPEHPK3PXP&issuer=GitHub"

    /** Draws [text]'s QR code at [scale] pixels a module, dark on light or the other way round. */
    private fun render(text: String, scale: Int, inverted: Boolean = false): Pair<IntArray, Int> {
        val m = QrCodes.encode(text)
        val size = m.width * scale + 40
        val pixels = IntArray(size * size) { if (inverted) Color.BLACK else Color.WHITE }
        for (y in 0 until m.height) for (x in 0 until m.width) {
            if (m[x, y]) for (dy in 0 until scale) for (dx in 0 until scale) {
                pixels[(20 + y * scale + dy) * size + 20 + x * scale + dx] = if (inverted) Color.WHITE else Color.BLACK
            }
        }
        return pixels to size
    }

    @Test
    fun decodesAnImage() {
        val (pixels, size) = render(link, 6)
        val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        assertEquals(link, QrCodes.decodeBitmap(bitmap))
    }

    @Test
    fun decodesLightOnDarkCodes() {
        val (pixels, size) = render(link, 5, inverted = true)
        assertEquals(link, QrCodes.decodeBitmap(Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)))
    }

    @Test
    fun decodesACameraFrameWithPaddedRows() {
        val (pixels, size) = render(link, 4)
        val stride = size + 16
        val y = ByteArray(stride * size)
        for (row in 0 until size) for (col in 0 until size) {
            y[row * stride + col] = (if (pixels[row * size + col] == Color.BLACK) 0 else 255).toByte()
        }
        assertEquals(link, QrCodes.decodeLuminance(y, stride, size, size))
    }

    @Test
    fun blankImagesHaveNoCode() {
        assertNull(QrCodes.decodeBitmap(Bitmap.createBitmap(IntArray(200 * 200) { Color.WHITE }, 200, 200, Bitmap.Config.ARGB_8888)))
    }
}
