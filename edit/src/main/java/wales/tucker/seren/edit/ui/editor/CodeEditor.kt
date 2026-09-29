package wales.tucker.seren.edit.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import wales.tucker.seren.core.content.ContentColorScheme
import wales.tucker.seren.core.ui.theme.MonoFamily
import wales.tucker.seren.edit.highlight.HighlightedSpan
import wales.tucker.seren.edit.highlight.Highlighters
import wales.tucker.seren.edit.highlight.Language
import wales.tucker.seren.edit.highlight.detectLanguage
import wales.tucker.seren.edit.highlight.TokenKind
import wales.tucker.seren.edit.highlight.tokenColor

/** Space above the first line, so it doesn't touch the top bar. */
private val TopPadding = 8.dp

/** How long to wait after a keystroke before re-lexing. Keeps typing snappy on larger files. */
private const val HighlightDebounceMs = 50L

/**
 * The text canvas: JetBrains Mono in the content color scheme, with optional line numbers,
 * word wrap and Prism-style syntax colors for known languages. Pinching changes the text size
 * through [onZoom], and [onZoomEnd] when fingers lift.
 */
@OptIn(ExperimentalFoundationApi::class, FlowPreview::class, ExperimentalCoroutinesApi::class)
@Composable
fun CodeEditor(
    state: TextFieldState,
    scheme: ContentColorScheme,
    fontSize: Float,
    wordWrap: Boolean,
    lineNumbers: Boolean,
    fileName: String,
    onZoom: (Float) -> Unit,
    onZoomEnd: () -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val fg = Color(scheme.foreground)
    val cursor = Color(scheme.cursor)
    val style = TextStyle(
        fontFamily = MonoFamily,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.45f).sp,
        color = fg,
    )
    val scroll = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val selectionColors = remember(cursor) { TextSelectionColors(handleColor = cursor, backgroundColor = cursor.copy(alpha = 0.35f)) }

    // Extension first; content sniff covers shebang / XML when the name has no useful extension.
    val language = remember(fileName, state.text.take(64).toString()) {
        detectLanguage(fileName, state.text.toString())
    }
    val highlighter = remember(language) { Highlighters.forLanguage(language) }
    var spans by remember(language) { mutableStateOf<List<HighlightedSpan>>(emptyList()) }

    LaunchedEffect(highlighter, language) {
        if (language == Language.Plain) {
            spans = emptyList()
            return@LaunchedEffect
        }
        snapshotFlow { state.text.toString() }
            .distinctUntilChanged()
            .debounce(HighlightDebounceMs)
            .mapLatest { text ->
                withContext(Dispatchers.Default) { highlighter.highlight(text) }
            }
            .collect { spans = it }
    }

    val colors = remember(scheme) { TokenKindColors(scheme) }
    val outputTransformation = remember(spans, colors, language) {
        if (language == Language.Plain || spans.isEmpty()) null
        else OutputTransformation {
            val len = length
            for (span in spans) {
                if (span.start >= len) continue
                val end = span.end.coerceAtMost(len)
                if (span.start < end) {
                    addStyle(SpanStyle(color = colors[span.kind]), span.start, end)
                }
            }
        }
    }

    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        BoxWithConstraints(modifier.fillMaxSize().pinchToZoom(onZoom, onZoomEnd)) {
            val viewportWidth = maxWidth
            Row(Modifier.fillMaxSize()) {
                if (lineNumbers) LineNumbers(state, layout, scroll, style, fg.copy(alpha = 0.4f))
                val field = @Composable { fieldModifier: Modifier ->
                    BasicTextField(
                        state = state,
                        modifier = fieldModifier
                            .padding(top = TopPadding)
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent(onKeyEvent)
                            .semantics { contentDescription = "Text" },
                        textStyle = style,
                        cursorBrush = SolidColor(cursor),
                        scrollState = scroll,
                        inputTransformation = AutoIndent,
                        outputTransformation = outputTransformation,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                        ),
                        onTextLayout = { result -> layout = result() },
                    )
                }
                val start = if (lineNumbers) 6.dp else 16.dp
                if (wordWrap) {
                    field(Modifier.weight(1f).fillMaxHeight().padding(start = start, end = 12.dp))
                } else {
                    // Lines keep their length; the canvas scrolls sideways instead.
                    Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState())) {
                        field(Modifier.fillMaxHeight().widthIn(min = viewportWidth).padding(start = start, end = 12.dp))
                    }
                }
            }
        }
    }
}

/** Precomputed colors for each token kind from the active content scheme. */
private class TokenKindColors(scheme: ContentColorScheme) {
    private val map = TokenKind.entries.associateWith { tokenColor(it, scheme) }
    operator fun get(kind: TokenKind): Color = map.getValue(kind)
}

/** Keeps the indentation of the line when Enter starts a new one. */
@OptIn(ExperimentalFoundationApi::class)
private val AutoIndent = InputTransformation {
    if (changes.changeCount != 1) return@InputTransformation
    val range = changes.getRange(0)
    val original = changes.getOriginalRange(0)
    if (original.length == 0 && range.length == 1 && charAt(range.start) == '\n') {
        val indent = TextEditing.indentOf(originalText, original.start)
        if (indent.isNotEmpty()) {
            insert(range.end, indent)
            selection = TextRange(range.end + indent.length)
        }
    }
}

/**
 * Line numbers beside the text, one per line of the file (a wrapped line gets one number), drawn
 * only for the lines in view.
 */
@Composable
private fun LineNumbers(state: TextFieldState, layout: TextLayoutResult?, scroll: ScrollState, style: TextStyle, color: Color) {
    val measurer = rememberTextMeasurer()
    val text = state.text
    val lineStarts = remember(text) { lineStarts(text) }
    val digits = lineStarts.size.toString().length.coerceAtLeast(2)
    val numberStyle = style.copy(color = color)
    val digitWidth = remember(style) { measurer.measure("0", style).size.width }
    val width = with(LocalDensity.current) { (digitWidth * digits).toDp() } + 16.dp
    Canvas(
        Modifier
            .width(width)
            .fillMaxHeight()
            .padding(top = TopPadding)
            .clipToBounds(),
    ) {
        val l = layout ?: return@Canvas
        if (l.layoutInput.text.length != text.length) return@Canvas
        val top = scroll.value.toFloat()
        val firstVisibleOffset = l.getLineStart(l.getLineForVerticalPosition(top))
        var i = lineStarts.binarySearch(firstVisibleOffset).let { if (it >= 0) it else -it - 2 }.coerceAtLeast(0)
        while (i < lineStarts.size) {
            val y = l.getLineTop(l.getLineForOffset(lineStarts[i])) - top
            if (y > size.height) break
            val label = measurer.measure((i + 1).toString(), numberStyle)
            drawText(label, topLeft = Offset(size.width - 10.dp.toPx() - label.size.width, y))
            i++
        }
    }
}

private fun lineStarts(text: CharSequence): IntArray {
    var count = 1
    for (c in text) if (c == '\n') count++
    val starts = IntArray(count)
    var n = 1
    for (i in text.indices) if (text[i] == '\n') starts[n++] = i + 1
    return starts
}

/** Two finger pinch, seen before the text field so it doesn't also scroll or select. */
private fun Modifier.pinchToZoom(onZoom: (Float) -> Unit, onEnd: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var zoomed = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    zoomed = true
                    onZoom(zoom)
                }
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (zoomed) onEnd()
    }
}
