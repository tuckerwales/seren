package wales.tucker.seren.ssh.ui.terminal

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Draws box drawing and block element characters geometrically so they join seamlessly across
 * cells regardless of the font's line spacing.
 */
object BoxDrawing {
    private const val NONE = 0
    private const val LIGHT = 1
    private const val HEAVY = 2

    // Segments packed as up | right << 4 | down << 8 | left << 12.
    private fun seg(up: Int, right: Int, down: Int, left: Int) = up or (right shl 4) or (down shl 8) or (left shl 12)

    private val LINES: Map<Int, Int> = buildMap {
        put(0x2500, seg(NONE, LIGHT, NONE, LIGHT))
        put(0x2501, seg(NONE, HEAVY, NONE, HEAVY))
        put(0x2502, seg(LIGHT, NONE, LIGHT, NONE))
        put(0x2503, seg(HEAVY, NONE, HEAVY, NONE))
        put(0x250C, seg(NONE, LIGHT, LIGHT, NONE))
        put(0x250D, seg(NONE, HEAVY, LIGHT, NONE))
        put(0x250E, seg(NONE, LIGHT, HEAVY, NONE))
        put(0x250F, seg(NONE, HEAVY, HEAVY, NONE))
        put(0x2510, seg(NONE, NONE, LIGHT, LIGHT))
        put(0x2511, seg(NONE, NONE, LIGHT, HEAVY))
        put(0x2512, seg(NONE, NONE, HEAVY, LIGHT))
        put(0x2513, seg(NONE, NONE, HEAVY, HEAVY))
        put(0x2514, seg(LIGHT, LIGHT, NONE, NONE))
        put(0x2515, seg(LIGHT, HEAVY, NONE, NONE))
        put(0x2516, seg(HEAVY, LIGHT, NONE, NONE))
        put(0x2517, seg(HEAVY, HEAVY, NONE, NONE))
        put(0x2518, seg(LIGHT, NONE, NONE, LIGHT))
        put(0x2519, seg(LIGHT, NONE, NONE, HEAVY))
        put(0x251A, seg(HEAVY, NONE, NONE, LIGHT))
        put(0x251B, seg(HEAVY, NONE, NONE, HEAVY))
        put(0x251C, seg(LIGHT, LIGHT, LIGHT, NONE))
        put(0x251D, seg(LIGHT, HEAVY, LIGHT, NONE))
        put(0x2520, seg(HEAVY, LIGHT, HEAVY, NONE))
        put(0x2523, seg(HEAVY, HEAVY, HEAVY, NONE))
        put(0x2524, seg(LIGHT, NONE, LIGHT, LIGHT))
        put(0x2525, seg(LIGHT, NONE, LIGHT, HEAVY))
        put(0x2528, seg(HEAVY, NONE, HEAVY, LIGHT))
        put(0x252B, seg(HEAVY, NONE, HEAVY, HEAVY))
        put(0x252C, seg(NONE, LIGHT, LIGHT, LIGHT))
        put(0x252F, seg(NONE, HEAVY, LIGHT, HEAVY))
        put(0x2530, seg(NONE, LIGHT, HEAVY, LIGHT))
        put(0x2533, seg(NONE, HEAVY, HEAVY, HEAVY))
        put(0x2534, seg(LIGHT, LIGHT, NONE, LIGHT))
        put(0x2537, seg(LIGHT, HEAVY, NONE, HEAVY))
        put(0x2538, seg(HEAVY, LIGHT, NONE, LIGHT))
        put(0x253B, seg(HEAVY, HEAVY, NONE, HEAVY))
        put(0x253C, seg(LIGHT, LIGHT, LIGHT, LIGHT))
        put(0x253F, seg(LIGHT, HEAVY, LIGHT, HEAVY))
        put(0x2542, seg(HEAVY, LIGHT, HEAVY, LIGHT))
        put(0x254B, seg(HEAVY, HEAVY, HEAVY, HEAVY))
        put(0x2574, seg(NONE, NONE, NONE, LIGHT))
        put(0x2575, seg(LIGHT, NONE, NONE, NONE))
        put(0x2576, seg(NONE, LIGHT, NONE, NONE))
        put(0x2577, seg(NONE, NONE, LIGHT, NONE))
        put(0x2578, seg(NONE, NONE, NONE, HEAVY))
        put(0x2579, seg(HEAVY, NONE, NONE, NONE))
        put(0x257A, seg(NONE, HEAVY, NONE, NONE))
        put(0x257B, seg(NONE, NONE, HEAVY, NONE))
    }

    private val path = Path()
    private val rect = RectF()

    /** Returns true if [cp] was drawn. */
    fun draw(canvas: Canvas, cp: Int, left: Float, top: Float, w: Float, h: Float, color: Int, paint: Paint): Boolean {
        if (cp < 0x2500 || cp > 0x259F) return false
        paint.color = color
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = false
        val result = when {
            cp in 0x256D..0x2570 -> {
                drawRounded(canvas, cp, left, top, w, h, paint); true
            }
            cp >= 0x2580 -> drawBlock(canvas, cp, left, top, w, h, color, paint)
            cp in 0x2550..0x256C -> false // double lines: use the font's glyphs
            else -> LINES[cp]?.let { drawLines(canvas, it, left, top, w, h, paint); true } ?: false
        }
        paint.isAntiAlias = true
        paint.alpha = 255
        return result
    }

    private fun thickness(w: Float, weight: Int): Float {
        val light = maxOf(1f, (w / 8f)).let { kotlin.math.round(it) }
        return if (weight == HEAVY) light * 2 else light
    }

    private fun drawLines(canvas: Canvas, s: Int, left: Float, top: Float, w: Float, h: Float, paint: Paint) {
        val up = s and 0xF
        val right = (s shr 4) and 0xF
        val down = (s shr 8) and 0xF
        val leftW = (s shr 12) and 0xF
        val cx = kotlin.math.round(left + w / 2f)
        val cy = kotlin.math.round(top + h / 2f)
        // Extend strokes into the joint by half the perpendicular stroke width so corners are square.
        val vThick = maxOf(if (up != NONE) thickness(w, up) else 0f, if (down != NONE) thickness(w, down) else 0f)
        val hThick = maxOf(if (leftW != NONE) thickness(w, leftW) else 0f, if (right != NONE) thickness(w, right) else 0f)
        if (leftW != NONE) {
            val t = thickness(w, leftW)
            canvas.drawRect(left, cy - t / 2, cx + vThick / 2, cy + t / 2, paint)
        }
        if (right != NONE) {
            val t = thickness(w, right)
            canvas.drawRect(cx - vThick / 2, cy - t / 2, left + w, cy + t / 2, paint)
        }
        if (up != NONE) {
            val t = thickness(w, up)
            canvas.drawRect(cx - t / 2, top, cx + t / 2, cy + hThick / 2, paint)
        }
        if (down != NONE) {
            val t = thickness(w, down)
            canvas.drawRect(cx - t / 2, cy - hThick / 2, cx + t / 2, top + h, paint)
        }
    }

    private fun drawRounded(canvas: Canvas, cp: Int, left: Float, top: Float, w: Float, h: Float, paint: Paint) {
        val t = thickness(w, LIGHT)
        val cx = kotlin.math.round(left + w / 2f)
        val cy = kotlin.math.round(top + h / 2f)
        val r = minOf(w, h) / 2f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = t
        paint.isAntiAlias = true
        path.reset()
        when (cp) {
            0x256D -> { // down and right
                path.moveTo(cx, top + h); path.lineTo(cx, cy + r)
                path.quadTo(cx, cy, cx + r, cy); path.lineTo(left + w, cy)
            }
            0x256E -> { // down and left
                path.moveTo(cx, top + h); path.lineTo(cx, cy + r)
                path.quadTo(cx, cy, cx - r, cy); path.lineTo(left, cy)
            }
            0x256F -> { // up and left
                path.moveTo(cx, top); path.lineTo(cx, cy - r)
                path.quadTo(cx, cy, cx - r, cy); path.lineTo(left, cy)
            }
            else -> { // up and right
                path.moveTo(cx, top); path.lineTo(cx, cy - r)
                path.quadTo(cx, cy, cx + r, cy); path.lineTo(left + w, cy)
            }
        }
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawBlock(canvas: Canvas, cp: Int, left: Float, top: Float, w: Float, h: Float, color: Int, paint: Paint): Boolean {
        val right = left + w
        val bottom = top + h
        fun fill(l: Float, t: Float, r: Float, b: Float) = canvas.drawRect(l, t, r, b, paint)
        val mx = left + w / 2
        val my = top + h / 2
        when (cp) {
            0x2580 -> fill(left, top, right, my)
            in 0x2581..0x2588 -> fill(left, bottom - h * (cp - 0x2580) / 8f, right, bottom)
            in 0x2589..0x258F -> fill(left, top, left + w * (0x2590 - cp) / 8f, bottom)
            0x2590 -> fill(mx, top, right, bottom)
            0x2591, 0x2592, 0x2593 -> {
                paint.color = color
                paint.alpha = when (cp) {
                    0x2591 -> 64
                    0x2592 -> 128
                    else -> 192
                }
                fill(left, top, right, bottom)
            }
            0x2594 -> fill(left, top, right, top + h / 8f)
            0x2595 -> fill(right - w / 8f, top, right, bottom)
            0x2596 -> fill(left, my, mx, bottom)
            0x2597 -> fill(mx, my, right, bottom)
            0x2598 -> fill(left, top, mx, my)
            0x2599 -> {
                fill(left, top, mx, bottom); fill(mx, my, right, bottom)
            }
            0x259A -> {
                fill(left, top, mx, my); fill(mx, my, right, bottom)
            }
            0x259B -> {
                fill(left, top, right, my); fill(left, my, mx, bottom)
            }
            0x259C -> {
                fill(left, top, right, my); fill(mx, my, right, bottom)
            }
            0x259D -> fill(mx, top, right, my)
            0x259E -> {
                fill(mx, top, right, my); fill(left, my, mx, bottom)
            }
            0x259F -> {
                fill(mx, top, right, bottom); fill(left, my, mx, bottom)
            }
            else -> return false
        }
        rect.setEmpty()
        return true
    }
}
