package wales.tucker.terminal.emulator

/**
 * A cell style packed into a [Long].
 *
 * Layout: bits 0..24 foreground color, bits 25..49 background color, bits 50..63 attributes.
 * A color is either a palette index (0..255), one of the special indices below, or a 24-bit
 * RGB value tagged with [RGB_FLAG].
 */
object TextStyle {
    const val COLOR_DEFAULT_FG = 256
    const val COLOR_DEFAULT_BG = 257
    const val COLOR_CURSOR = 258
    const val PALETTE_SIZE = 259

    const val RGB_FLAG = 0x1000000

    const val BOLD = 1
    const val ITALIC = 1 shl 1
    const val UNDERLINE = 1 shl 2
    const val BLINK = 1 shl 3
    const val INVERSE = 1 shl 4
    const val INVISIBLE = 1 shl 5
    const val STRIKETHROUGH = 1 shl 6
    const val FAINT = 1 shl 7
    const val DOUBLE_UNDERLINE = 1 shl 8
    const val OVERLINE = 1 shl 9

    private const val COLOR_MASK = 0x1FFFFFFL

    val DEFAULT: Long = encode(COLOR_DEFAULT_FG, COLOR_DEFAULT_BG, 0)

    fun encode(fg: Int, bg: Int, attrs: Int): Long =
        (fg.toLong() and COLOR_MASK) or
            ((bg.toLong() and COLOR_MASK) shl 25) or
            (attrs.toLong() shl 50)

    fun fg(style: Long): Int = (style and COLOR_MASK).toInt()

    fun bg(style: Long): Int = ((style ushr 25) and COLOR_MASK).toInt()

    fun attrs(style: Long): Int = (style ushr 50).toInt()

    fun rgb(r: Int, g: Int, b: Int): Int =
        RGB_FLAG or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    fun isRgb(color: Int): Boolean = color and RGB_FLAG != 0
}
