package wales.tucker.terminal.ui.terminal

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ActionMode
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.OverScroller
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withClip
import wales.tucker.terminal.R
import wales.tucker.terminal.emulator.CursorShape
import wales.tucker.terminal.emulator.KeyEncoder
import wales.tucker.terminal.emulator.MouseMode
import wales.tucker.terminal.emulator.TerminalEmulator
import wales.tucker.terminal.emulator.TerminalKey
import wales.tucker.terminal.emulator.TerminalRow
import wales.tucker.terminal.emulator.TextStyle
import wales.tucker.terminal.emulator.WcWidth
import wales.tucker.terminal.session.TerminalSession
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders a [TerminalSession]'s emulator and turns touch, IME and hardware keyboard input into
 * bytes for the remote shell.
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        /** Sticky modifiers from the extra keys bar (KeyEncoder.MOD_*). */
        fun currentModifiers(): Int = 0

        /** Called after a key consumed the sticky modifiers. */
        fun onModifiersConsumed() {}

        fun onFontSizeChanged(sizeSp: Float) {}

        fun onTap() {}

        /** Called when the view scrolls into the history or back to the bottom. */
        fun onScrolledBackChanged(scrolledBack: Boolean) {}
    }

    var listener: Listener? = null

    var session: TerminalSession? = null
        set(value) {
            if (field === value) return
            field?.screenListener = null
            field = value
            value?.screenListener = screenListener
            scrollOffset = 0
            clearSelection()
            lastScrolledCount = value?.emulator?.mainBuffer?.linesScrolledIntoHistory ?: 0
            updateGrid(force = true)
            invalidate()
        }

    var fontSizeSp: Float = 13f
        set(value) {
            if (field == value) return
            field = value
            updateFontMetrics()
            updateGrid(force = true)
            invalidate()
        }

    var cursorShapeOverride: CursorShape? = null
    var cursorBlinkEnabled: Boolean = true
        set(value) {
            field = value
            restartBlink()
        }
    var volumeKeysAsModifiers: Boolean = false

    private val screenListener: () -> Unit = { postInvalidateOnAnimation() }

    // Fonts and metrics.
    private val regular: Typeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono_regular) ?: Typeface.MONOSPACE
    private val bold: Typeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono_bold) ?: Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    private val italic: Typeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono_italic) ?: Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC)
    private val boldItalic: Typeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono_bold_italic) ?: Typeface.create(Typeface.MONOSPACE, Typeface.BOLD_ITALIC)

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { typeface = regular }
    private val fillPaint = Paint()
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cellWidth = 1f
    private var cellHeight = 1
    private var baseline = 0
    private val density = resources.displayMetrics.density

    private var cols = 0
    private var rows = 0

    // Scrollback.
    private var scrollOffset = 0
        set(value) {
            val wasBack = field > 0
            field = value
            if (wasBack != value > 0) listener?.onScrolledBackChanged(value > 0)
        }
    private var lastScrolledCount = 0L
    private var scrollRemainder = 0f
    private val scroller = OverScroller(context)
    private var flingLastY = 0

    // Selection, in absolute line coordinates (line index + lines scrolled into history).
    private var selecting = false
    private var selStartRow = 0L
    private var selStartCol = 0
    private var selEndRow = 0L
    private var selEndCol = 0
    private var draggingHandle = 0 // 0 none, 1 start, 2 end
    private var actionMode: ActionMode? = null
    private val handleRadius = 11f * density

    // Cursor blink.
    private var cursorBlinkOn = true
    private val blinkRunnable = object : Runnable {
        override fun run() {
            cursorBlinkOn = !cursorBlinkOn
            invalidate()
            postDelayed(this, BLINK_INTERVAL)
        }
    }

    // Held hardware modifiers from volume keys.
    private var volumeCtrl = false
    private var volumeAlt = false

    private val charBuf = CharArray(512)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        updateFontMetrics()
        contentDescription = context.getString(R.string.terminal_content_description)
    }

    // region Metrics and size

    private fun updateFontMetrics() {
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSizeSp, resources.displayMetrics)
        textPaint.textSize = px
        val fm = textPaint.fontMetricsInt
        cellHeight = max(1, fm.descent - fm.ascent + fm.leading)
        baseline = -fm.ascent
        cellWidth = max(1f, textPaint.measureText("M"))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGrid(force = false)
    }

    private fun updateGrid(force: Boolean) {
        val w = width - paddingLeft - paddingRight
        val h = height - paddingTop - paddingBottom
        if (w <= 0 || h <= 0) return
        val newCols = max(4, floor(w / cellWidth).toInt())
        val newRows = max(2, h / cellHeight)
        if (force || newCols != cols || newRows != rows) {
            cols = newCols
            rows = newRows
            session?.resize(newCols, newRows, w, h)
        }
    }

    // endregion

    // region Drawing

    override fun onDraw(canvas: Canvas) {
        val s = session
        // Never paint outside our bounds: when hosted in Compose the canvas may not be clipped.
        canvas.withClip(0, 0, width, height) {
            if (s == null) {
                fillPaint.color = 0xFF000000.toInt()
                drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
            } else {
                drawTerminal(this, s)
            }
        }
    }

    private fun drawTerminal(canvas: Canvas, s: TerminalSession) {
        val emu = s.emulator
        synchronized(emu) {
            val palette = emu.palette
            val reverse = emu.reverseVideo
            val defaultBg = palette[if (reverse) TextStyle.COLOR_DEFAULT_FG else TextStyle.COLOR_DEFAULT_BG]
            fillPaint.color = defaultBg
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)

            val buffer = emu.buffer
            val scrolled = emu.mainBuffer.linesScrolledIntoHistory
            if (emu.isAltScreen) {
                scrollOffset = 0
            } else if (scrollOffset > 0) {
                scrollOffset += (scrolled - lastScrolledCount).toInt()
            }
            lastScrolledCount = scrolled
            scrollOffset = scrollOffset.coerceIn(0, buffer.historySize)

            val visibleRows = min(rows, emu.rows)
            val top = -scrollOffset
            val selRange = selectionRange(scrolled)
            for (vy in 0 until visibleRows) {
                val lineIndex = top + vy
                if (lineIndex < -buffer.historySize || lineIndex >= emu.rows) continue
                val row = buffer.lineAt(lineIndex)
                val y = paddingTop + vy * cellHeight
                drawRow(canvas, row, y, palette, reverse, defaultBg)
                if (selRange != null) {
                    val abs = lineIndex + scrolled
                    drawSelection(canvas, abs, y, row.cols, selRange)
                }
            }

            if (scrollOffset == 0 && emu.cursorVisible && emu.cursorY < visibleRows) {
                drawCursor(canvas, emu, palette, reverse)
            }
            if (selRange != null) drawHandles(canvas, scrolled, palette[TextStyle.COLOR_CURSOR])
            if (scrollOffset > 0) drawScrollIndicator(canvas, buffer.historySize, palette[TextStyle.COLOR_DEFAULT_FG])
        }
    }

    private fun resolveColors(style: Long, palette: IntArray, reverse: Boolean): Long {
        var fg = TextStyle.fg(style)
        val bg = TextStyle.bg(style)
        val attrs = TextStyle.attrs(style)
        if (attrs and TextStyle.BOLD != 0 && fg in 0..7) fg += 8
        var fgColor = colorOf(fg, palette, reverse)
        var bgColor = colorOf(bg, palette, reverse)
        if (attrs and TextStyle.INVERSE != 0) {
            val t = fgColor; fgColor = bgColor; bgColor = t
        }
        if (attrs and TextStyle.FAINT != 0) fgColor = blend(fgColor, bgColor, 0.55f)
        if (attrs and TextStyle.INVISIBLE != 0) fgColor = bgColor
        return (fgColor.toLong() shl 32) or (bgColor.toLong() and 0xFFFFFFFFL)
    }

    private fun colorOf(c: Int, palette: IntArray, reverse: Boolean): Int = when {
        TextStyle.isRgb(c) -> 0xFF000000.toInt() or (c and 0xFFFFFF)
        reverse && c == TextStyle.COLOR_DEFAULT_FG -> palette[TextStyle.COLOR_DEFAULT_BG]
        reverse && c == TextStyle.COLOR_DEFAULT_BG -> palette[TextStyle.COLOR_DEFAULT_FG]
        c in palette.indices -> palette[c]
        else -> palette[TextStyle.COLOR_DEFAULT_FG]
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val r = ((a shr 16 and 0xFF) * t + (b shr 16 and 0xFF) * (1 - t)).toInt()
        val g = ((a shr 8 and 0xFF) * t + (b shr 8 and 0xFF) * (1 - t)).toInt()
        val bl = ((a and 0xFF) * t + (b and 0xFF) * (1 - t)).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private fun applyTextStyle(attrs: Int) {
        val isBold = attrs and TextStyle.BOLD != 0
        val isItalic = attrs and TextStyle.ITALIC != 0
        textPaint.typeface = when {
            isBold && isItalic -> boldItalic
            isBold -> bold
            isItalic -> italic
            else -> regular
        }
        textPaint.isUnderlineText = attrs and (TextStyle.UNDERLINE or TextStyle.DOUBLE_UNDERLINE) != 0
        textPaint.isStrikeThruText = attrs and TextStyle.STRIKETHROUGH != 0
    }

    private fun drawRow(canvas: Canvas, row: TerminalRow, y: Int, palette: IntArray, reverse: Boolean, defaultBg: Int) {
        val n = min(row.cols, cols)
        val text = row.text
        val styles = row.styles
        var x = 0
        while (x < n) {
            val style = styles[x]
            val cp = text[x]
            if (cp == TerminalRow.WIDE_TAIL) {
                x++; continue
            }
            val wide = row.isWideAt(x)
            val combining = row.getCombining(x)
            val simple = !wide && combining == null && cp in 0x20..0x7E
            if (simple) {
                // Gather a run of printable ASCII cells with the same style.
                var end = x + 1
                while (end < n && styles[end] == style && text[end] in 0x20..0x7E && row.getCombining(end) == null) end++
                drawRun(canvas, row, x, end, y, style, palette, reverse, defaultBg)
                x = end
            } else {
                drawCell(canvas, cp, combining, x, if (wide) 2 else 1, y, style, palette, reverse, defaultBg)
                x += if (wide) 2 else 1
            }
        }
    }

    private fun drawBackground(canvas: Canvas, x0: Int, x1: Int, y: Int, bg: Int, defaultBg: Int) {
        if (bg == defaultBg) return
        fillPaint.color = bg
        val left = paddingLeft + x0 * cellWidth
        val right = if (x1 >= cols) width.toFloat() else paddingLeft + x1 * cellWidth
        canvas.drawRect(left, y.toFloat(), right, (y + cellHeight).toFloat(), fillPaint)
    }

    private fun drawRun(canvas: Canvas, row: TerminalRow, start: Int, end: Int, y: Int, style: Long, palette: IntArray, reverse: Boolean, defaultBg: Int) {
        val colors = resolveColors(style, palette, reverse)
        val fg = (colors ushr 32).toInt()
        val bg = colors.toInt()
        drawBackground(canvas, start, end, y, bg, defaultBg)
        var allSpaces = true
        val len = end - start
        var i = 0
        while (i < len) {
            val chunk = min(len - i, charBuf.size)
            for (k in 0 until chunk) {
                val c = row.text[start + i + k]
                charBuf[k] = c.toChar()
                if (c != ' '.code) allSpaces = false
            }
            val attrs = TextStyle.attrs(style)
            if (!allSpaces || attrs and (TextStyle.UNDERLINE or TextStyle.DOUBLE_UNDERLINE or TextStyle.STRIKETHROUGH) != 0) {
                applyTextStyle(attrs)
                textPaint.color = fg
                val left = paddingLeft + (start + i) * cellWidth
                val measured = textPaint.measureText(charBuf, 0, chunk)
                val expected = chunk * cellWidth
                if (abs(measured - expected) > 0.5f) {
                    textPaint.textScaleX = expected / measured
                    canvas.drawText(charBuf, 0, chunk, left, (y + baseline).toFloat(), textPaint)
                    textPaint.textScaleX = 1f
                } else {
                    canvas.drawText(charBuf, 0, chunk, left, (y + baseline).toFloat(), textPaint)
                }
                if (attrs and TextStyle.OVERLINE != 0) {
                    fillPaint.color = fg
                    canvas.drawRect(left, y.toFloat(), left + expected, y + density, fillPaint)
                }
            }
            i += chunk
        }
    }

    private fun drawCell(canvas: Canvas, cp: Int, combining: String?, x: Int, width: Int, y: Int, style: Long, palette: IntArray, reverse: Boolean, defaultBg: Int) {
        val colors = resolveColors(style, palette, reverse)
        val fg = (colors ushr 32).toInt()
        val bg = colors.toInt()
        drawBackground(canvas, x, x + width, y, bg, defaultBg)
        if (cp == ' '.code && combining == null) return
        val left = paddingLeft + x * cellWidth
        if (BoxDrawing.draw(canvas, cp, left, y.toFloat(), cellWidth * width, cellHeight.toFloat(), fg, fillPaint)) return
        applyTextStyle(TextStyle.attrs(style))
        textPaint.color = fg
        val str = if (combining == null) String(Character.toChars(cp)) else String(Character.toChars(cp)) + combining
        val measured = textPaint.measureText(str)
        val expected = width * cellWidth
        if (measured > expected + 0.5f && measured > 0) {
            textPaint.textScaleX = expected / measured
            canvas.drawText(str, left, (y + baseline).toFloat(), textPaint)
            textPaint.textScaleX = 1f
        } else {
            // Center narrower glyphs (e.g. emoji rendered by a fallback font) in their cells.
            val offset = if (measured < expected) (expected - measured) / 2f else 0f
            canvas.drawText(str, left + offset, (y + baseline).toFloat(), textPaint)
        }
    }

    private fun drawCursor(canvas: Canvas, emu: TerminalEmulator, palette: IntArray, reverse: Boolean) {
        val cx = min(emu.cursorX, cols - 1)
        val cy = emu.cursorY
        val blinking = cursorBlinkEnabled && emu.cursorBlink
        if (blinking && !cursorBlinkOn && hasFocus()) return
        val row = emu.buffer.row(cy)
        val wide = row.isWideAt(cx)
        val w = if (wide) 2 else 1
        val left = paddingLeft + cx * cellWidth
        val top = (paddingTop + cy * cellHeight).toFloat()
        val right = left + cellWidth * w
        val bottom = top + cellHeight
        val cursorColor = palette[TextStyle.COLOR_CURSOR]
        fillPaint.color = cursorColor
        val shape = cursorShapeOverride?.takeIf { emu.cursorShape == CursorShape.BLOCK } ?: emu.cursorShape
        if (!hasFocus()) {
            fillPaint.style = Paint.Style.STROKE
            fillPaint.strokeWidth = max(1f, density)
            canvas.drawRect(left + 0.5f, top + 0.5f, right - 0.5f, bottom - 0.5f, fillPaint)
            fillPaint.style = Paint.Style.FILL
            return
        }
        when (shape) {
            CursorShape.BLOCK -> {
                canvas.drawRect(left, top, right, bottom, fillPaint)
                val cp = row.text[cx]
                if (cp != ' '.code && cp != TerminalRow.WIDE_TAIL) {
                    val colors = resolveColors(row.styles[cx], palette, reverse)
                    val bg = colors.toInt()
                    applyTextStyle(TextStyle.attrs(row.styles[cx]))
                    textPaint.isUnderlineText = false
                    textPaint.color = if (bg == cursorColor) palette[TextStyle.COLOR_DEFAULT_BG] else bg
                    val str = String(Character.toChars(cp)) + (row.getCombining(cx) ?: "")
                    if (!BoxDrawing.draw(canvas, cp, left, top, cellWidth * w, cellHeight.toFloat(), textPaint.color, Paint())) {
                        canvas.drawText(str, left, top + baseline, textPaint)
                    }
                }
            }
            CursorShape.UNDERLINE -> canvas.drawRect(left, bottom - max(2f, 2 * density), right, bottom, fillPaint)
            CursorShape.BAR -> canvas.drawRect(left, top, left + max(2f, 2 * density), bottom, fillPaint)
        }
    }

    private fun drawScrollIndicator(canvas: Canvas, historySize: Int, color: Int) {
        val total = historySize + rows
        val trackHeight = height.toFloat()
        val thumbHeight = max(24 * density, trackHeight * rows / total)
        val topFraction = (historySize - scrollOffset).toFloat() / max(1, historySize)
        val thumbTop = (trackHeight - thumbHeight) * topFraction
        fillPaint.color = (color and 0x00FFFFFF) or 0x66000000
        val w = 4 * density
        canvas.drawRoundRect(RectF(width - w - 2 * density, thumbTop, width - 2 * density, thumbTop + thumbHeight), w, w, fillPaint)
    }

    // endregion

    // region Selection

    /** Returns normalized selection (startRow, startCol, endRow, endCol) in absolute rows, or null. */
    private fun selectionRange(@Suppress("UNUSED_PARAMETER") scrolled: Long): LongArray? {
        if (!selecting) return null
        val forward = selStartRow < selEndRow || (selStartRow == selEndRow && selStartCol <= selEndCol)
        return if (forward) longArrayOf(selStartRow, selStartCol.toLong(), selEndRow, selEndCol.toLong())
        else longArrayOf(selEndRow, selEndCol.toLong(), selStartRow, selStartCol.toLong())
    }

    private fun drawSelection(canvas: Canvas, absRow: Long, y: Int, rowCols: Int, sel: LongArray) {
        if (absRow < sel[0] || absRow > sel[2]) return
        val from = if (absRow == sel[0]) sel[1].toInt() else 0
        val to = if (absRow == sel[2]) sel[3].toInt() else rowCols - 1
        if (to < from) return
        fillPaint.color = 0x5580A8FF
        canvas.drawRect(paddingLeft + from * cellWidth, y.toFloat(), paddingLeft + (to + 1) * cellWidth, (y + cellHeight).toFloat(), fillPaint)
    }

    private fun handlePosition(absRow: Long, col: Int, scrolled: Long, isEnd: Boolean): Pair<Float, Float>? {
        val lineIndex = absRow - scrolled
        val vy = lineIndex + scrollOffset
        if (vy < 0 || vy >= rows) return null
        val x = paddingLeft + (if (isEnd) col + 1 else col) * cellWidth
        val y = (paddingTop + (vy + 1) * cellHeight).toFloat()
        return x to y
    }

    private fun drawHandles(canvas: Canvas, scrolled: Long, color: Int) {
        val sel = selectionRange(scrolled) ?: return
        handlePaint.color = color
        handlePosition(sel[0], sel[1].toInt(), scrolled, false)?.let { (x, y) ->
            canvas.drawCircle(x - handleRadius * 0.7f, y + handleRadius * 0.7f, handleRadius, handlePaint)
            canvas.drawRect(x - handleRadius * 0.7f, y, x, y + handleRadius * 0.7f, handlePaint)
        }
        handlePosition(sel[2], sel[3].toInt(), scrolled, true)?.let { (x, y) ->
            canvas.drawCircle(x + handleRadius * 0.7f, y + handleRadius * 0.7f, handleRadius, handlePaint)
            canvas.drawRect(x, y, x + handleRadius * 0.7f, y + handleRadius * 0.7f, handlePaint)
        }
    }

    private fun cellAt(x: Float, y: Float): Pair<Int, Int> {
        val col = floor((x - paddingLeft) / cellWidth).toInt().coerceIn(0, max(0, cols - 1))
        val row = floor((y - paddingTop) / cellHeight).toInt().coerceIn(0, max(0, rows - 1))
        return col to row
    }

    private fun isWordChar(cp: Int): Boolean =
        Character.isLetterOrDigit(cp) || cp in WORD_EXTRA || cp > 0x7F && cp != 0x3000

    private fun startSelection(x: Float, y: Float) {
        val s = session ?: return
        val emu = s.emulator
        val (col, vrow) = cellAt(x, y)
        synchronized(emu) {
            val scrolled = emu.mainBuffer.linesScrolledIntoHistory
            val lineIndex = vrow - scrollOffset
            if (lineIndex >= emu.rows || lineIndex < -emu.buffer.historySize) return
            val row = emu.buffer.lineAt(lineIndex)
            var start = col
            var end = col
            val c = if (col < row.cols) row.text[col] else ' '.code
            if (isWordChar(c)) {
                while (start > 0 && (row.text[start - 1] == TerminalRow.WIDE_TAIL || isWordChar(row.text[start - 1]))) start--
                while (end < row.cols - 1 && (row.text[end + 1] == TerminalRow.WIDE_TAIL || isWordChar(row.text[end + 1]))) end++
            }
            selStartRow = lineIndex + scrolled
            selEndRow = selStartRow
            selStartCol = start
            selEndCol = end
        }
        selecting = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        showActionMode()
        invalidate()
    }

    fun clearSelection() {
        if (!selecting && actionMode == null) return
        selecting = false
        draggingHandle = 0
        actionMode?.finish()
        actionMode = null
        invalidate()
    }

    private fun selectedText(): String {
        val s = session ?: return ""
        val emu = s.emulator
        synchronized(emu) {
            val scrolled = emu.mainBuffer.linesScrolledIntoHistory
            val sel = selectionRange(scrolled) ?: return ""
            return emu.getText((sel[0] - scrolled).toInt(), sel[1].toInt(), (sel[2] - scrolled).toInt(), sel[3].toInt())
        }
    }

    private fun selectAll() {
        val s = session ?: return
        val emu = s.emulator
        synchronized(emu) {
            val scrolled = emu.mainBuffer.linesScrolledIntoHistory
            selStartRow = -emu.buffer.historySize + scrolled
            selStartCol = 0
            selEndRow = emu.rows - 1 + scrolled
            selEndCol = emu.cols - 1
        }
        selecting = true
        invalidate()
    }

    private fun handleSelectionTouch(e: MotionEvent): Boolean {
        val s = session ?: return false
        val emu = s.emulator
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val scrolled = synchronized(emu) { emu.mainBuffer.linesScrolledIntoHistory }
                val sel = selectionRange(scrolled) ?: return false
                val start = handlePosition(sel[0], sel[1].toInt(), scrolled, false)
                val end = handlePosition(sel[2], sel[3].toInt(), scrolled, true)
                val slop = handleRadius * 2.2f
                draggingHandle = when {
                    end != null && hypot(e.x - (end.first + handleRadius * 0.7f), e.y - (end.second + handleRadius * 0.7f)) < slop -> 2
                    start != null && hypot(e.x - (start.first - handleRadius * 0.7f), e.y - (start.second + handleRadius * 0.7f)) < slop -> 1
                    else -> 0
                }
                if (draggingHandle != 0) {
                    // Normalize so that the dragged end is the one we move.
                    if (selStartRow > selEndRow || (selStartRow == selEndRow && selStartCol > selEndCol)) {
                        val r = selStartRow; selStartRow = selEndRow; selEndRow = r
                        val c = selStartCol; selStartCol = selEndCol; selEndCol = c
                    }
                    actionMode?.hide(Long.MAX_VALUE)
                }
                return draggingHandle != 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingHandle == 0) return false
                val y = e.y - handleRadius * 1.5f
                val (col, vrow) = cellAt(e.x, y)
                val scrolled = synchronized(emu) { emu.mainBuffer.linesScrolledIntoHistory }
                val abs = vrow - scrollOffset + scrolled
                if (draggingHandle == 1) {
                    selStartRow = abs; selStartCol = col
                } else {
                    selEndRow = abs; selEndCol = col
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggingHandle == 0) return false
                draggingHandle = 0
                actionMode?.hide(0)
                actionMode?.invalidateContentRect()
                return true
            }
        }
        return false
    }

    private fun hypot(dx: Float, dy: Float) = kotlin.math.sqrt(dx * dx + dy * dy)

    private fun showActionMode() {
        if (actionMode != null) {
            actionMode?.invalidateContentRect()
            return
        }
        actionMode = startActionMode(object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(Menu.NONE, MENU_COPY, 0, android.R.string.copy).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                menu.add(Menu.NONE, MENU_PASTE, 1, android.R.string.paste).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                menu.add(Menu.NONE, MENU_SELECT_ALL, 2, android.R.string.selectAll).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
                    MENU_COPY -> {
                        copyToClipboard(selectedText())
                        clearSelection()
                    }
                    MENU_PASTE -> {
                        clearSelection()
                        pasteFromClipboard()
                    }
                    MENU_SELECT_ALL -> {
                        selectAll()
                        mode.invalidateContentRect()
                    }
                }
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                actionMode = null
                if (selecting) {
                    selecting = false
                    invalidate()
                }
            }

            override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                val s = session
                if (s == null) {
                    outRect.set(0, 0, width, height); return
                }
                val scrolled = synchronized(s.emulator) { s.emulator.mainBuffer.linesScrolledIntoHistory }
                val sel = selectionRange(scrolled)
                if (sel == null) {
                    outRect.set(0, 0, width, height); return
                }
                val top = ((sel[0] - scrolled + scrollOffset).coerceIn(0, rows.toLong()) * cellHeight + paddingTop).toInt()
                val bottom = ((sel[2] - scrolled + scrollOffset + 1).coerceIn(0, rows.toLong()) * cellHeight + paddingTop).toInt()
                val left = if (sel[0] == sel[2]) (paddingLeft + sel[1] * cellWidth).toInt() else 0
                val right = if (sel[0] == sel[2]) (paddingLeft + (sel[3] + 1) * cellWidth).toInt() else width
                outRect.set(left, top, right, max(bottom, top + cellHeight))
            }
        }, ActionMode.TYPE_FLOATING)
    }

    fun copyToClipboard(text: String) {
        if (text.isEmpty()) return
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("Terminal", text))
    }

    fun pasteFromClipboard() {
        val cm = context.getSystemService(ClipboardManager::class.java)
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        if (!text.isNullOrEmpty()) {
            scrollToBottom()
            session?.paste(text)
        }
    }

    // endregion

    // region Touch

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            scroller.forceFinished(true)
            scrollRemainder = 0f
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (selecting) {
                clearSelection()
                return true
            }
            val s = session
            if (s != null) {
                val (col, row) = cellAt(e.x, e.y)
                val mouse = synchronized(s.emulator) {
                    if (s.emulator.mouseMode != MouseMode.NONE && scrollOffset == 0) {
                        (s.emulator.encodeMouse(0, col, row, true) ?: byteArrayOf()) +
                            (s.emulator.encodeMouse(0, col, row, false) ?: byteArrayOf())
                    } else {
                        null
                    }
                }
                if (mouse != null && mouse.isNotEmpty()) s.write(mouse)
            }
            showKeyboard()
            listener?.onTap()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (scaleDetector.isInProgress) return
            startSelection(e.x, e.y)
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (selecting && draggingHandle != 0) return true
            scrollRemainder += distanceY
            val lines = (scrollRemainder / cellHeight).toInt()
            if (lines != 0) {
                scrollRemainder -= lines * cellHeight
                scrollLines(-lines)
            }
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val s = session ?: return false
            if (synchronized(s.emulator) { s.emulator.isAltScreen }) return false
            flingLastY = 0
            scroller.fling(0, 0, 0, velocityY.roundToInt(), 0, 0, -1_000_000, 1_000_000)
            postInvalidateOnAnimation()
            return true
        }
    })

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var accumulated = 1f

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            accumulated = 1f
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            accumulated *= detector.scaleFactor
            if (abs(accumulated - 1f) > 0.06f) {
                val newSize = (fontSizeSp * accumulated).coerceIn(MIN_FONT_SP, MAX_FONT_SP)
                accumulated = 1f
                if (abs(newSize - fontSizeSp) >= 0.25f) {
                    fontSizeSp = (newSize * 2).roundToInt() / 2f
                    listener?.onFontSizeChanged(fontSizeSp)
                }
            }
            return true
        }
    })

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            val y = scroller.currY
            val delta = y - flingLastY
            val lines = delta / cellHeight
            if (lines != 0) {
                flingLastY += lines * cellHeight
                scrollLines(lines)
            }
            if (!scroller.isFinished) postInvalidateOnAnimation()
        }
    }

    /** Positive [lines] scrolls back into history (content moves down). */
    private fun scrollLines(lines: Int) {
        val s = session ?: return
        val emu = s.emulator
        val bytes = synchronized(emu) {
            if (emu.isAltScreen) {
                val (col, row) = cols / 2 to rows / 2
                if (emu.mouseMode != MouseMode.NONE) {
                    val button = if (lines > 0) 64 else 65
                    val one = emu.encodeMouse(button, col, row, true) ?: byteArrayOf()
                    var out = byteArrayOf()
                    repeat(min(abs(lines), 10)) { out += one }
                    out
                } else {
                    val key = if (lines > 0) TerminalKey.UP else TerminalKey.DOWN
                    val one = KeyEncoder.encode(key, 0, emu.applicationCursorKeys)
                    var out = byteArrayOf()
                    repeat(min(abs(lines), 10)) { out += one }
                    out
                }
            } else {
                scrollOffset = (scrollOffset + lines).coerceIn(0, emu.buffer.historySize)
                null
            }
        }
        if (bytes != null && bytes.isNotEmpty()) s.write(bytes)
        invalidate()
    }

    fun scrollToBottom() {
        if (scrollOffset != 0) {
            scrollOffset = 0
            invalidate()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (selecting && handleSelectionTouch(event)) return true
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL) {
            val v = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (v != 0f) {
                scrollLines((v * 3).roundToInt())
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    fun showKeyboard() {
        requestFocus()
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    // endregion

    // region Focus and blink

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        session?.let { s ->
            synchronized(s.emulator) { s.emulator.encodeFocus(gainFocus) }?.let { s.write(it) }
        }
        restartBlink()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        session?.screenListener = screenListener
        restartBlink()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(blinkRunnable)
        actionMode?.finish()
    }

    private fun restartBlink() {
        removeCallbacks(blinkRunnable)
        cursorBlinkOn = true
        if (cursorBlinkEnabled && isAttachedToWindow) postDelayed(blinkRunnable, BLINK_INTERVAL)
    }

    private fun resetBlinkPhase() {
        if (!cursorBlinkEnabled) return
        removeCallbacks(blinkRunnable)
        cursorBlinkOn = true
        postDelayed(blinkRunnable, BLINK_INTERVAL)
    }

    // endregion

    // region Keyboard

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_ACTION_NONE or
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        return TerminalInputConnection()
    }

    private inner class TerminalInputConnection : BaseInputConnection(this@TerminalView, true) {
        private val editable = Editable.Factory.getInstance().newEditable("")

        override fun getEditable(): Editable = editable

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            super.commitText(text, newCursorPosition)
            flushEditable()
            return true
        }

        override fun finishComposingText(): Boolean {
            super.finishComposingText()
            flushEditable()
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (editable.isEmpty()) {
                repeat(max(1, beforeLength)) { sendKey(TerminalKey.BACKSPACE, 0) }
                repeat(afterLength) { sendKey(TerminalKey.DELETE, 0) }
                return true
            }
            return super.deleteSurroundingText(beforeLength, afterLength)
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean = dispatchKeyEvent(event)

        private fun flushEditable() {
            val text = editable.toString()
            if (text.isNotEmpty()) {
                editable.clear()
                sendText(text)
            }
        }
    }

    private fun effectiveModifiers(event: KeyEvent?): Int {
        var mods = listener?.currentModifiers() ?: 0
        if (event != null) {
            if (event.isShiftPressed) mods = mods or KeyEncoder.MOD_SHIFT
            if (event.isAltPressed) mods = mods or KeyEncoder.MOD_ALT
            if (event.isCtrlPressed) mods = mods or KeyEncoder.MOD_CTRL
        }
        if (volumeCtrl) mods = mods or KeyEncoder.MOD_CTRL
        if (volumeAlt) mods = mods or KeyEncoder.MOD_ALT
        return mods
    }

    private fun consumeStickyModifiers() {
        listener?.onModifiersConsumed()
    }

    /** Sends typed text (from the IME or extra keys), applying sticky modifiers to the first character. */
    fun sendText(text: String) {
        val s = session ?: return
        clearSelectionForTyping()
        var mods = effectiveModifiers(null)
        var i = 0
        val out = java.io.ByteArrayOutputStream()
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (cp == '\n'.code) {
                out.write(KeyEncoder.encode(TerminalKey.ENTER, mods and KeyEncoder.MOD_ALT, false, s.emulator.newLineMode))
            } else {
                out.write(KeyEncoder.encodeChar(cp, mods and (KeyEncoder.MOD_CTRL or KeyEncoder.MOD_ALT)))
            }
            if (mods != 0) {
                consumeStickyModifiers()
                mods = if (volumeCtrl || volumeAlt) effectiveModifiers(null) else 0
            }
        }
        s.write(out.toByteArray())
    }

    fun sendKey(key: TerminalKey, extraModifiers: Int) {
        val s = session ?: return
        clearSelectionForTyping()
        val mods = extraModifiers or effectiveModifiers(null)
        s.sendKey(key, mods)
        if (mods != 0) consumeStickyModifiers()
    }

    private fun clearSelectionForTyping() {
        if (selecting) clearSelection()
        scrollToBottom()
        resetBlinkPhase()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (volumeKeysAsModifiers) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                volumeCtrl = true; return true
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                volumeAlt = true; return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
        ) return super.onKeyDown(keyCode, event)

        val s = session ?: return super.onKeyDown(keyCode, event)
        val special = specialKey(keyCode)
        if (special != null) {
            val eventMods = effectiveModifiers(event)
            clearSelectionForTyping()
            s.sendKey(special, eventMods)
            if (eventMods and (listener?.currentModifiers() ?: 0) != 0) consumeStickyModifiers()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_V && event.isCtrlPressed && event.isShiftPressed) {
            pasteFromClipboard(); return true
        }
        if (keyCode == KeyEvent.KEYCODE_C && event.isCtrlPressed && event.isShiftPressed) {
            if (selecting) copyToClipboard(selectedText())
            clearSelection()
            return true
        }
        val mods = effectiveModifiers(event)
        // Unicode char without Ctrl/Alt meta so we can encode those ourselves.
        val meta = event.metaState and (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK).inv()
        var cp = event.getUnicodeChar(meta)
        if (cp == 0) return super.onKeyDown(keyCode, event)
        if (cp and KeyCharacterMap.COMBINING_ACCENT != 0) return true
        clearSelectionForTyping()
        s.write(KeyEncoder.encodeChar(cp, mods and (KeyEncoder.MOD_CTRL or KeyEncoder.MOD_ALT)))
        if (listener?.currentModifiers() ?: 0 != 0) consumeStickyModifiers()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (volumeKeysAsModifiers) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                volumeCtrl = false; return true
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                volumeAlt = false; return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun specialKey(keyCode: Int): TerminalKey? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> TerminalKey.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> TerminalKey.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> TerminalKey.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> TerminalKey.RIGHT
        KeyEvent.KEYCODE_MOVE_HOME -> TerminalKey.HOME
        KeyEvent.KEYCODE_MOVE_END -> TerminalKey.END
        KeyEvent.KEYCODE_PAGE_UP -> TerminalKey.PAGE_UP
        KeyEvent.KEYCODE_PAGE_DOWN -> TerminalKey.PAGE_DOWN
        KeyEvent.KEYCODE_INSERT -> TerminalKey.INSERT
        KeyEvent.KEYCODE_FORWARD_DEL -> TerminalKey.DELETE
        KeyEvent.KEYCODE_DEL -> TerminalKey.BACKSPACE
        KeyEvent.KEYCODE_TAB -> TerminalKey.TAB
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> TerminalKey.ENTER
        KeyEvent.KEYCODE_ESCAPE -> TerminalKey.ESCAPE
        KeyEvent.KEYCODE_F1 -> TerminalKey.F1
        KeyEvent.KEYCODE_F2 -> TerminalKey.F2
        KeyEvent.KEYCODE_F3 -> TerminalKey.F3
        KeyEvent.KEYCODE_F4 -> TerminalKey.F4
        KeyEvent.KEYCODE_F5 -> TerminalKey.F5
        KeyEvent.KEYCODE_F6 -> TerminalKey.F6
        KeyEvent.KEYCODE_F7 -> TerminalKey.F7
        KeyEvent.KEYCODE_F8 -> TerminalKey.F8
        KeyEvent.KEYCODE_F9 -> TerminalKey.F9
        KeyEvent.KEYCODE_F10 -> TerminalKey.F10
        KeyEvent.KEYCODE_F11 -> TerminalKey.F11
        KeyEvent.KEYCODE_F12 -> TerminalKey.F12
        else -> null
    }

    // endregion

    companion object {
        private const val BLINK_INTERVAL = 530L
        const val MIN_FONT_SP = 6f
        const val MAX_FONT_SP = 32f
        private const val MENU_COPY = 1
        private const val MENU_PASTE = 2
        private const val MENU_SELECT_ALL = 3
        private val WORD_EXTRA = setOf('-'.code, '_'.code, '.'.code, '/'.code, '~'.code, ':'.code, '@'.code, '%'.code, '+'.code, '='.code, '?'.code, '&'.code, '#'.code)
    }
}
