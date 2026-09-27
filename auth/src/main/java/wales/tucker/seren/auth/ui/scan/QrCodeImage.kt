package wales.tucker.seren.auth.ui.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import wales.tucker.seren.auth.qr.QrCodes

/**
 * A QR code for [text], always dark on white whatever the theme, since that's what scanners
 * expect.
 */
@Composable
fun QrCodeImage(text: String, description: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { QrCodes.encode(text) }
    Canvas(modifier.aspectRatio(1f).semantics { contentDescription = description }) {
        drawRect(Color.White)
        val module = size.width / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    // A hair of overlap stops seams between modules at fractional sizes.
                    drawRect(Color.Black, Offset(x * module, y * module), Size(module + 0.5f, module + 0.5f))
                }
            }
        }
    }
}
