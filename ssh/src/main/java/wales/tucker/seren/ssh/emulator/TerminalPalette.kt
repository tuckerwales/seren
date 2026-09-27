package wales.tucker.seren.ssh.emulator

import wales.tucker.seren.core.content.ContentColorScheme

/** Full terminal palette: 256 indexed colors followed by default fg, default bg and cursor. */
fun ContentColorScheme.palette(): IntArray {
    val p = IntArray(TextStyle.PALETTE_SIZE)
    for (i in 0 until 16) p[i] = ansi[i]
    val steps = intArrayOf(0, 95, 135, 175, 215, 255)
    var i = 16
    for (r in 0 until 6) for (g in 0 until 6) for (b in 0 until 6) {
        p[i++] = argb(steps[r], steps[g], steps[b])
    }
    for (k in 0 until 24) {
        val v = 8 + k * 10
        p[i++] = argb(v, v, v)
    }
    p[TextStyle.COLOR_DEFAULT_FG] = foreground
    p[TextStyle.COLOR_DEFAULT_BG] = background
    p[TextStyle.COLOR_CURSOR] = cursor
    return p
}

private fun argb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
