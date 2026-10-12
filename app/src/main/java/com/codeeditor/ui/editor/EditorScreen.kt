package com.codeeditor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeeditor.core.editor.Selection
import com.codeeditor.core.editor.highlight.SyntaxToken
import com.codeeditor.ui.theme.EditorColors
import com.codeeditor.ui.theme.LocalEditorColors
import kotlin.math.roundToInt

/**
 * The editable code surface: a virtualised line renderer plus an invisible text
 * field that captures IME and hardware-keyboard input. Host-agnostic (no
 * Scaffold), so it can be embedded under a tab bar in the workspace.
 */
@Composable
fun EditorSurface(
    state: EditorUiState,
    style: EditorStyle,
    lineText: (Int) -> String,
    tokensForLine: (Int) -> List<SyntaxToken>,
    onAction: (EditorAction) -> Unit,
    modifier: Modifier = Modifier,
    completionVisible: Boolean = false,
    onFontSizeChange: (Float) -> Unit = {}
) {
    val colors = LocalEditorColors.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    val monoStyle = remember(style) {
        TextStyle(
            fontFamily = style.fontFamily,
            fontSize = style.fontSizeSp.sp,
            lineHeight = style.lineHeightSp.sp,
            fontFeatureSettings = if (style.useLigatures) "calt" else null
        )
    }
    val charWidth = remember(monoStyle, measurer) {
        measurer.measure(AnnotatedString("MMMMMMMMMM"), monoStyle).size.width / 10f
    }
    val lineHeight = remember(monoStyle, measurer) {
        measurer.measure(AnnotatedString("Mg"), monoStyle).size.height.toFloat()
    }
    val textPadding = with(density) { 10.dp.toPx() }
    val gutterDigits = maxOf(3, state.lineCount.toString().length)
    val gutterWidth = with(density) { (gutterDigits * charWidth + 20.dp.toPx()).toDp() }
    val lineHeightDp = with(density) { lineHeight.toDp() }
    // Width of the widest line (plus padding). The whole code column is given this
    // fixed width and lives inside a single horizontal scroller, so horizontal
    // scrolling works regardless of which line happens to be on screen.
    val codeWidth = with(density) {
        (state.maxLineLength * charWidth + textPadding + 10.dp.toPx()).toDp()
    }

    val listState = rememberLazyListState()
    val hScroll = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    var inputValue by remember { mutableStateOf("") }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(style.fontSizeSp) { zoomScale = 1f }

    LaunchedEffect(state.cursor.line, state.contentVersion) {
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isNotEmpty()) {
            val first = visible.first().index
            val last = visible.last().index
            if (state.cursor.line < first || state.cursor.line > last) {
                listState.animateScrollToItem(state.cursor.line)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var pressed: Boolean
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        if (kotlin.math.abs(zoom - 1f) > 0.005f) zoomScale *= zoom
                        pressed = event.changes.any { it.pressed }
                    } while (pressed)
                    val target = (style.fontSizeSp * zoomScale).coerceIn(
                        MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP
                    )
                    val rounded = kotlin.math.round(target * 10f) / 10f
                    if (kotlin.math.abs(rounded - style.fontSizeSp) > 0.05f) {
                        onFontSizeChange(rounded)
                    }
                }
            }
    ) {
        // The whole code column is fixed to the widest line and wrapped in one
        // shared horizontal scroller, so horizontal scrolling is not tied to which
        // row is currently composed (the cause of the previous no-op scrolling).
        val viewportWidth = if (maxWidth.isFinite) maxWidth else 0.dp
        val listWidth = maxOf(gutterWidth + codeWidth, viewportWidth)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(hScroll)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .width(listWidth)
                    .fillMaxHeight()
            ) {
                items(count = state.lineCount, key = { it }) { index ->
                    EditorLineRow(
                        index = index,
                        state = state,
                        style = style,
                        monoStyle = monoStyle,
                        charWidth = charWidth,
                        lineHeight = lineHeight,
                        lineHeightDp = lineHeightDp,
                        gutterWidth = gutterWidth,
                        textPadding = textPadding,
                        hScroll = hScroll,
                        colors = colors,
                        lineText = lineText,
                        tokensForLine = tokensForLine,
                        onTapLine = { line, x ->
                            focusRequester.requestFocus()
                            val col = ((x - textPadding) / charWidth).roundToInt().coerceAtLeast(0)
                            onAction(EditorAction.MoveTo(line, col))
                        },
                        onDoubleTapLine = { line, x ->
                            focusRequester.requestFocus()
                            val col = ((x - textPadding) / charWidth).roundToInt().coerceAtLeast(0)
                            onAction(EditorAction.MoveTo(line, col))
                            onAction(EditorAction.SelectWord)
                        }
                    )
                }
            }
        }

        BasicTextField(
            value = inputValue,
            onValueChange = { newValue ->
                if (newValue.isNotEmpty()) {
                    onAction(EditorAction.InsertText(newValue))
                    inputValue = ""
                }
            },
            modifier = Modifier
                .size(1.dp)
                .align(Alignment.TopStart)
                .alpha(0f)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event -> handleHardwareKey(event, onAction, completionVisible) },
            textStyle = monoStyle,
            cursorBrush = SolidColor(Color.Transparent)
        )
    }
}

@Composable
private fun EditorLineRow(
    index: Int,
    state: EditorUiState,
    style: EditorStyle,
    monoStyle: TextStyle,
    charWidth: Float,
    lineHeight: Float,
    lineHeightDp: Dp,
    gutterWidth: Dp,
    textPadding: Float,
    hScroll: ScrollState,
    colors: EditorColors,
    lineText: (Int) -> String,
    tokensForLine: (Int) -> List<SyntaxToken>,
    onTapLine: (Int, Float) -> Unit,
    onDoubleTapLine: (Int, Float) -> Unit
) {
    val version = state.contentVersion
    val text = remember(index, version) { lineText(index) }
    val annotated = remember(index, version) { buildHighlightedText(text, tokensForLine(index), colors) }
    val selections = state.effectiveSelections

    val isCurrent = selections.any {
        it.caret.line == index || (!it.isCollapsed && index in it.start.line..it.end.line)
    }
    val selectionSpans = selections.mapNotNull { sel ->
        if (sel.isCollapsed) null else selectedColumns(sel, index, text.length)
    }
    val caretColumns = selections
        .filter { it.isCollapsed && it.caret.line == index }
        .map { it.caret.column }
    val density = LocalDensity.current
    val rowBackground = if (isCurrent) colors.currentLineBackground else Color.Transparent
    val gutterBackground = if (isCurrent) colors.currentLineBackground else colors.background

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(lineHeightDp)
            .background(rowBackground)
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) {
            if (style.showLineNumbers) {
                Spacer(Modifier.width(gutterWidth))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(index, version) {
                        detectTapGestures(
                            onTap = { offset -> onTapLine(index, offset.x) },
                            onDoubleTap = { offset -> onDoubleTapLine(index, offset.x) }
                        )
                    }
            ) {
                selectionSpans.forEach { span ->
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((span.first * charWidth + textPadding).roundToInt(), 0) }
                            .width(with(density) { ((span.last - span.first + 1) * charWidth).toDp() })
                            .height(lineHeightDp)
                            .background(colors.selection)
                    )
                }
                Text(
                    text = annotated,
                    style = monoStyle,
                    color = colors.foreground,
                    softWrap = false,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 10.dp)
                )
                caretColumns.forEach { column ->
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((column * charWidth + textPadding).roundToInt(), 0) }
                            .width(2.dp)
                            .height(with(density) { lineHeight.toDp() })
                            .background(colors.caret)
                    )
                }
            }
        }
        // Line numbers stay pinned to the left: they are drawn on top and shifted
        // back by the current horizontal scroll offset.
        if (style.showLineNumbers) {
            Text(
                text = (index + 1).toString(),
                modifier = Modifier
                    .width(gutterWidth)
                    .fillMaxHeight()
                    .offset { IntOffset(hScroll.value, 0) }
                    .background(gutterBackground)
                    .padding(end = 8.dp),
                style = monoStyle,
                color = if (isCurrent) colors.gutterActiveForeground else colors.gutterForeground,
                textAlign = TextAlign.End,
                maxLines = 1
            )
        }
    }
}

/** Columns [from, to] selected on [line] by [selection], or null if none. */
private fun selectedColumns(selection: Selection, line: Int, lineLength: Int): IntRange? {
    val startLine = selection.start.line
    val endLine = selection.end.line
    if (line < startLine || line > endLine) return null
    val from: Int
    val to: Int
    when {
        startLine == endLine -> {
            from = selection.start.column
            to = selection.end.column
        }
        line == startLine -> {
            from = selection.start.column
            to = lineLength
        }
        line == endLine -> {
            from = 0
            to = selection.end.column
        }
        else -> {
            from = 0
            to = lineLength
        }
    }
    return if (to > from) from until to else null
}

private fun handleHardwareKey(
    event: KeyEvent,
    onAction: (EditorAction) -> Unit,
    completionVisible: Boolean
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (completionVisible) {
        return when (event.key) {
            Key.DirectionDown -> { onAction(EditorAction.CompletionNext); true }
            Key.DirectionUp -> { onAction(EditorAction.CompletionPrevious); true }
            Key.Enter -> { onAction(EditorAction.AcceptCompletion); true }
            Key.Tab -> { onAction(EditorAction.AcceptCompletion); true }
            Key.Escape -> { onAction(EditorAction.DismissCompletion); true }
            else -> false
        }
    }
    val ctrl = event.isCtrlPressed || event.isMetaPressed
    val alt = event.isAltPressed
    val shift = event.isShiftPressed
    return when {
        ctrl && shift && event.key == Key.Z -> { onAction(EditorAction.Redo); true }
        ctrl && event.key == Key.Z -> { onAction(EditorAction.Undo); true }
        ctrl && event.key == Key.Y -> { onAction(EditorAction.Redo); true }
        ctrl && event.key == Key.Slash -> { onAction(EditorAction.ToggleComment); true }
        ctrl && shift && event.key == Key.D -> { onAction(EditorAction.DuplicateLine); true }
        ctrl && event.key == Key.D -> { onAction(EditorAction.AddNextOccurrence); true }
        alt && event.key == Key.DirectionUp -> { onAction(EditorAction.MoveLineUp); true }
        alt && event.key == Key.DirectionDown -> { onAction(EditorAction.MoveLineDown); true }
        ctrl && event.key == Key.F -> { onAction(EditorAction.OpenFind); true }
        ctrl && event.key == Key.H -> { onAction(EditorAction.OpenReplace); true }
        ctrl && event.key == Key.Spacebar -> { onAction(EditorAction.ShowCompletions); true }
        shift && event.key == Key.F3 -> { onAction(EditorAction.FindPrevious); true }
        event.key == Key.F3 -> { onAction(EditorAction.FindNext); true }
        event.key == Key.Escape -> { onAction(EditorAction.CloseFind); true }
        event.key == Key.Enter -> { onAction(EditorAction.Newline); true }
        event.key == Key.Backspace -> { onAction(EditorAction.Backspace); true }
        event.key == Key.Delete -> { onAction(EditorAction.DeleteForward); true }
        event.key == Key.Tab -> { onAction(EditorAction.InsertTab); true }
        event.key == Key.DirectionLeft -> { onAction(EditorAction.MoveLeft); true }
        event.key == Key.DirectionRight -> { onAction(EditorAction.MoveRight); true }
        event.key == Key.DirectionUp -> { onAction(EditorAction.MoveUp); true }
        event.key == Key.DirectionDown -> { onAction(EditorAction.MoveDown); true }
        event.key == Key.MoveHome -> { onAction(EditorAction.MoveLineStart); true }
        event.key == Key.MoveEnd -> { onAction(EditorAction.MoveLineEnd); true }
        else -> false
    }
}

private const val MIN_FONT_SIZE_SP = 10f
private const val MAX_FONT_SIZE_SP = 28f
