package com.codeeditor.core.session

import com.codeeditor.core.editor.IndentConverter
import com.codeeditor.core.editor.LineEnding
import com.codeeditor.core.editor.Selection
import com.codeeditor.core.editor.TextDocument
import com.codeeditor.core.editor.TextPosition
import com.codeeditor.core.editor.edit.TextEdit
import com.codeeditor.core.editor.edit.UndoRedoManager
import com.codeeditor.core.editor.highlight.HlState
import com.codeeditor.core.editor.highlight.LanguageDefinition
import com.codeeditor.core.editor.highlight.LanguageRegistry
import com.codeeditor.core.editor.highlight.SyntaxHighlighter
import com.codeeditor.core.autocomplete.WordIndex
import com.codeeditor.core.editor.highlight.SyntaxToken
import com.codeeditor.core.encoding.TextEncoding
import com.codeeditor.core.search.SearchMatch
import com.codeeditor.core.search.SearchOptions
import com.codeeditor.core.search.TextSearch

/**
 * Editing state for a single open document: buffer, undo history, one or more
 * carets/selections and the incremental syntax-highlight cache.
 *
 * Free of Android and Compose dependencies so it can be unit-tested and reused.
 */
class EditorSession(
    val id: String,
    uri: String?,
    title: String,
    initialText: String,
    language: LanguageDefinition,
    var encoding: TextEncoding = TextEncoding.UTF_8,
    var hasBom: Boolean = false
) {

    val document: TextDocument = TextDocument(initialText)
    private val undoRedo = UndoRedoManager()
    private var highlighter: SyntaxHighlighter = LanguageRegistry.highlighterFor(language)
    private val lineStates = ArrayList<Int>().apply { add(HlState.NORMAL) }

    private val selections = mutableListOf(Selection.collapsed(document.positionAt(document.length)))

    private var wordIndexCache: WordIndex? = null
    private var wordIndexVersion: Int = -1

    private var maxLineLengthVersion: Int = -1
    private var cachedMaxLineLength: Int = 0

    /**
     * Line whose length produced [cachedMaxLineLength], plus the dirty range of
     * the last edit. This lets [maxLineLength] update in O(1) for the common
     * "typing inside a line" case instead of rescanning a multi-hundred-MB
     * buffer on every keystroke; a full rescan happens only when the line that
     * currently holds the maximum is touched or when an edit changes the line
     * count (which is when the cached maximum can no longer be trusted).
     */
    private var maxLineLine: Int = -1
    private var lengthDirtyFrom: Int = -1
    private var lengthDirtyTo: Int = -1
    private var lengthDirtyAll: Boolean = false

    var title: String = title
    var uri: String? = uri
        private set
    var dirty: Boolean = false
        private set
    var indentUnit: String = "    "
    var autoCloseBrackets: Boolean = true

    /** Document version at the point the file was last saved, used to derive [dirty]. */
    var savedVersion: Int = document.version
        private set

    /** Whether the next recorded simple edit may merge into the previous one. */
    private var coalesceNext: Boolean = false
    private var desiredColumn: Int = 0

    val language: LanguageDefinition get() = highlighter.language
    val canUndo: Boolean get() = undoRedo.canUndo
    val canRedo: Boolean get() = undoRedo.canRedo
    val lineEnding: LineEnding get() = document.lineEnding
    val content: String get() = document.snapshot()

    /** The active (last) selection; drives the status bar and navigation. */
    val selection: Selection get() = selections.last()
    val caret: TextPosition get() = selection.caret
    val allSelections: List<Selection> get() = selections.toList()

    fun lineText(line: Int): String = document.lineAt(line)
    fun lineLength(line: Int): Int = document.lineEndOffset(line) - document.lineStartOffset(line)
    fun lineCount(): Int = document.lineCount

    /**
     * Length of the longest line, cached per document version. Drives the shared
     * horizontal scroll width so every row reports the same scroll range.
     */
    fun maxLineLength(): Int {
        if (maxLineLengthVersion == document.version) return cachedMaxLineLength
        val mustRescan = lengthDirtyAll ||
            lengthDirtyFrom < 0 ||
            document.length < FULL_SCAN_LIMIT ||
            (maxLineLine in lengthDirtyFrom..lengthDirtyTo)
        if (mustRescan) {
            rescanMaxLineLength()
        } else {
            val end = minOf(lengthDirtyTo, document.lineCount - 1)
            var line = lengthDirtyFrom
            while (line <= end) {
                val len = lineLength(line)
                if (len > cachedMaxLineLength) {
                    cachedMaxLineLength = len
                    maxLineLine = line
                }
                line++
            }
        }
        lengthDirtyFrom = -1
        lengthDirtyTo = -1
        lengthDirtyAll = false
        maxLineLengthVersion = document.version
        return cachedMaxLineLength
    }

    private fun rescanMaxLineLength() {
        val text = document.snapshot()
        var max = 0
        var maxLine = -1
        var line = 0
        var current = 0
        for (i in text.indices) {
            when (text[i]) {
                '\n' -> {
                    if (current > max) { max = current; maxLine = line }
                    line++
                    current = 0
                }
                '\r' -> Unit
                else -> current++
            }
        }
        if (current > max) { max = current; maxLine = line }
        cachedMaxLineLength = max
        maxLineLine = maxLine
    }

    /** Records that lines [fromLine]..[toLine] were rewritten by an edit. */
    private fun touchLines(fromLine: Int, toLine: Int = fromLine) {
        if (lengthDirtyAll) return
        if (lengthDirtyFrom < 0) {
            lengthDirtyFrom = fromLine
            lengthDirtyTo = toLine
        } else {
            lengthDirtyFrom = minOf(lengthDirtyFrom, fromLine)
            lengthDirtyTo = maxOf(lengthDirtyTo, toLine)
        }
    }

    /** Forces a full rescan on the next [maxLineLength] call. */
    private fun touchAllLines() {
        lengthDirtyAll = true
    }

    fun tokensForLine(line: Int): List<SyntaxToken> =
        highlighter.highlight(document.lineAt(line), stateAt(line)).tokens

    /** Frequency-indexed words of the buffer, rebuilt only when the text changes. */
    fun wordIndex(): WordIndex {
        if (wordIndexVersion == document.version && wordIndexCache != null) {
            return wordIndexCache!!
        }
        // Rebuilding the word index is a full O(n) pass; on very large buffers we
        // skip it (and complete only from the active line) to protect typing
        // latency instead of freezing the UI on every keystroke.
        val index = if (document.length <= WORD_INDEX_LIMIT) {
            WordIndex.fromText(document.snapshot())
        } else {
            WordIndex.EMPTY
        }
        wordIndexCache = index
        wordIndexVersion = document.version
        return index
    }

    fun markSaved() {
        dirty = false
        savedVersion = document.version
        coalesceNext = false
    }

    fun markDirty() {
        dirty = true
    }

    /**
     * Points this session at a different file after "Save As": updates the URI,
     * title and language (highlighting), discarding the per-line highlight cache
     * since the syntax rules may have changed. The buffer, undo history, carets
     * and encoding are left untouched.
     */
    fun retarget(newUri: String?, newTitle: String, newLanguage: LanguageDefinition) {
        uri = newUri
        title = newTitle
        highlighter = LanguageRegistry.highlighterFor(newLanguage)
        lineStates.clear()
        lineStates.add(HlState.NORMAL)
    }

    // ---------------------------------------------------------------- insertion

    fun insertText(text: String) {
        if (text == "\n" || text == "\r\n") {
            newline()
            return
        }
        if (selections.size > 1) {
            insertAtAllSelections(text)
            coalesceNext = false
            return
        }
        if (autoCloseBrackets && text.length == 1 && handleAutoClose(text[0])) {
            coalesceNext = false
            return
        }
        val edit = replaceSelection(text) ?: return
        undoRedo.record(edit, coalesce = coalesceNext && text.length == 1)
        dirty = true
        coalesceNext = text.length == 1
        invalidateFrom(
            document.positionAt(edit.offset).line,
            full = text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0
        )
    }

    fun insertTab() = insertText(indentUnit)

    fun newline() {
        coalesceNext = false
        if (selections.size > 1) {
            insertAtAllSelections("\n")
            return
        }
        val line = document.lineAt(caret.line)
        val leading = line.takeWhile { it == ' ' || it == '\t' }
        val beforeCaret = line.substring(0, caret.column.coerceAtMost(line.length))
        val extra = if (beforeCaret.trimEnd().lastOrNull()?.let { it in language.indentsAfter } == true) {
            indentUnit
        } else {
            ""
        }
        val edit = replaceSelection("\n" + leading + extra) ?: return
        undoRedo.record(edit)
        dirty = true
        invalidateFrom(document.positionAt(edit.offset).line)
    }

    fun backspace() {
        if (selections.size == 1 && selection.isCollapsed) {
            singleBackspace()
            return
        }
        coalesceNext = false
        val ordered = selections.sortedByDescending { document.offsetAt(it.start) }
        val edits = ArrayList<TextEdit>()
        val newSels = ArrayList<Selection>()
        for (sel in ordered) {
            if (!sel.isCollapsed) {
                val s = document.offsetAt(sel.start)
                val e = document.offsetAt(sel.end)
                val removed = document.delete(s, e)
                edits.add(TextEdit.Delete(s, removed))
                newSels.add(Selection.collapsed(document.positionAt(s)))
            } else {
                val offset = document.offsetAt(sel.caret)
                if (offset == 0) {
                    newSels.add(sel)
                    continue
                }
                val start = if (sel.caret.column == 0 && sel.caret.line > 0) {
                    offset - document.lineTerminatorLength(sel.caret.line - 1)
                } else {
                    offset - 1 - (if (Character.isLowSurrogate(content[offset - 1])) 1 else 0)
                }
                val removed = document.delete(start, offset)
                edits.add(TextEdit.Delete(start, removed))
                newSels.add(Selection.collapsed(document.positionAt(start)))
            }
        }
        if (edits.isEmpty()) return
        undoRedo.record(TextEdit.Group(edits))
        dirty = true
        setSelections(newSels)
        invalidateFrom(newSels.minOf { it.caret.line })
    }

    fun deleteForward() {
        if (selections.size > 1) {
            deleteForwardAll()
            return
        }
        if (!selection.isCollapsed) {
            backspace()
            return
        }
        deleteForwardSingle()
    }

    private fun singleBackspace() {
        val offset = document.offsetAt(caret)
        if (offset == 0) return
        val joinsLines = caret.column == 0 && caret.line > 0
        val start = if (joinsLines) {
            offset - document.lineTerminatorLength(caret.line - 1)
        } else {
            offset - 1 - (if (Character.isLowSurrogate(content[offset - 1])) 1 else 0)
        }
        val removed = document.delete(start, offset)
        undoRedo.record(TextEdit.Delete(start, removed), coalesce = coalesceNext)
        dirty = true
        coalesceNext = true
        collapseTo(document.positionAt(start))
        invalidateFrom(document.positionAt(start).line, full = joinsLines)
    }

    private fun deleteForwardSingle() {
        val offset = document.offsetAt(caret)
        if (offset >= document.length) return
        val lineLen = lineLength(caret.line)
        val joinsLines = caret.column >= lineLen
        val end = if (joinsLines) {
            offset + document.lineTerminatorLength(caret.line)
        } else {
            offset + 1 + (if (Character.isHighSurrogate(content[offset])) 1 else 0)
        }
        val removed = document.delete(offset, end)
        undoRedo.record(TextEdit.Delete(offset, removed))
        dirty = true
        coalesceNext = false
        collapseTo(document.positionAt(offset))
        invalidateFrom(caret.line, full = joinsLines)
    }

    private fun deleteForwardAll() {
        coalesceNext = false
        val ordered = selections.sortedBy { document.offsetAt(it.start) }
        val edits = ArrayList<TextEdit>()
        val newSels = ArrayList<Selection>()
        for (sel in ordered) {
            if (!sel.isCollapsed) {
                val s = document.offsetAt(sel.start)
                val e = document.offsetAt(sel.end)
                val removed = document.delete(s, e)
                edits.add(TextEdit.Delete(s, removed))
                newSels.add(Selection.collapsed(document.positionAt(s)))
            } else {
                val offset = document.offsetAt(sel.caret)
                if (offset >= document.length) {
                    newSels.add(sel)
                    continue
                }
                val lineLen = lineLength(sel.caret.line)
                val end = if (sel.caret.column >= lineLen) {
                    offset + document.lineTerminatorLength(sel.caret.line)
                } else {
                    offset + 1 + (if (Character.isHighSurrogate(content[offset])) 1 else 0)
                }
                val removed = document.delete(offset, end)
                edits.add(TextEdit.Delete(offset, removed))
                newSels.add(Selection.collapsed(document.positionAt(offset)))
            }
        }
        if (edits.isEmpty()) return
        undoRedo.record(TextEdit.Group(edits))
        dirty = true
        setSelections(newSels)
        invalidateFrom(newSels.minOf { it.caret.line })
    }

    private fun replaceSelection(text: String): TextEdit? {
        val sel = selection
        if (!sel.isCollapsed) {
            val s = document.offsetAt(sel.start)
            val e = document.offsetAt(sel.end)
            val removed = document.delete(s, e)
            val insert = document.insert(s, text)
            collapseTo(document.positionAt(s + text.length))
            return TextEdit.Group(
                listOf(TextEdit.Delete(s, removed), insert)
            )
        }
        val offset = document.offsetAt(sel.caret)
        val edit = document.insert(offset, text)
        collapseTo(document.positionAt(offset + text.length))
        return edit
    }

    private fun insertAtAllSelections(text: String) {
        val ordered = selections.sortedByDescending { document.offsetAt(it.start) }
        val edits = ArrayList<TextEdit>()
        val newSels = ArrayList<Selection>()
        for (sel in ordered) {
            val s = document.offsetAt(sel.start)
            val e = document.offsetAt(sel.end)
            if (e > s) {
                val removed = document.delete(s, e)
                edits.add(TextEdit.Delete(s, removed))
            }
            val insert = document.insert(s, text)
            edits.add(insert)
            newSels.add(Selection.collapsed(document.positionAt(s + text.length)))
        }
        if (edits.isEmpty()) return
        undoRedo.record(TextEdit.Group(edits))
        dirty = true
        setSelections(newSels)
        invalidateFrom(newSels.minOf { it.caret.line })
    }

    // ---------------------------------------------------------------- auto-close

    private fun handleAutoClose(ch: Char): Boolean {
        val open = OPEN_TO_CLOSE[ch]
        if (open != null) {
            if (selection.isCollapsed) {
                val offset = document.offsetAt(caret)
                val first = document.insert(offset, ch.toString())
                val second = document.insert(offset + 1, open.toString())
                undoRedo.record(TextEdit.Group(listOf(first, second)))
                dirty = true
                collapseTo(document.positionAt(offset + 1))
                invalidateFrom(document.positionAt(offset).line)
            } else {
                wrapSelection(ch.toString(), open.toString())
            }
            return true
        }
        if (ch in QUOTES) {
            if (!selection.isCollapsed) {
                wrapSelection(ch.toString(), ch.toString())
                return true
            }
            val offset = document.offsetAt(caret)
            val next = if (offset < document.length) content[offset] else '\u0000'
            if (next == ch) {
                collapseTo(document.positionAt(offset + 1))
                return true
            }
            val first = document.insert(offset, ch.toString())
            val second = document.insert(offset + 1, ch.toString())
            undoRedo.record(TextEdit.Group(listOf(first, second)))
            dirty = true
            collapseTo(document.positionAt(offset + 1))
            invalidateFrom(document.positionAt(offset).line)
            return true
        }
        if (ch in CLOSE_CHARS && selection.isCollapsed) {
            val offset = document.offsetAt(caret)
            val next = if (offset < document.length) content[offset] else '\u0000'
            if (next == ch) {
                collapseTo(document.positionAt(offset + 1))
                return true
            }
        }
        return false
    }

    private fun wrapSelection(open: String, close: String) {
        val sel = selection
        val s = document.offsetAt(sel.start)
        val e = document.offsetAt(sel.end)
        val closeEdit = document.insert(e, close)
        val openEdit = document.insert(s, open)
        undoRedo.record(TextEdit.Group(listOf(closeEdit, openEdit)))
        dirty = true
        val newStart = document.positionAt(s + open.length)
        val newEnd = document.positionAt(s + open.length + (e - s))
        setSingle(Selection(newStart, newEnd))
        invalidateFrom(document.positionAt(s).line)
    }

    // ---------------------------------------------------------------- line commands

    /** Toggles line comments (or wraps the selection in a block comment). */
    fun toggleLineComment() {
        coalesceNext = false
        val prefix = language.lineComment
        if (prefix == null) {
            toggleBlockComment()
            return
        }
        val first = selection.start.line
        val last = selection.end.line
        val lines = (first..last).toList()
        val allCommented = lines.all { document.lineAt(it).trimStart().startsWith(prefix) }
        val edits = ArrayList<TextEdit>()
        for (line in lines.sortedDescending()) {
            val lineStart = document.lineStartOffset(line)
            val text = document.lineAt(line)
            if (allCommented) {
                val idx = text.indexOf(prefix)
                if (idx < 0) continue
                var len = prefix.length
                if (text.getOrNull(idx + len) == ' ') len++
                val removed = document.delete(lineStart + idx, lineStart + idx + len)
                edits.add(TextEdit.Delete(lineStart + idx, removed))
            } else {
                val indent = text.takeWhile { it == ' ' || it == '\t' }.length
                edits.add(document.insert(lineStart + indent, "$prefix "))
            }
        }
        if (edits.isEmpty()) return
        undoRedo.record(TextEdit.Group(edits))
        dirty = true
        collapseTo(TextPosition(first, 0))
        invalidateFrom(first)
    }

    private fun toggleBlockComment() {
        val open = language.blockCommentStart ?: return
        val close = language.blockCommentEnd ?: return
        val sel = selection
        if (!sel.isCollapsed) {
            wrapSelection(open, close)
        } else {
            val offset = document.offsetAt(caret)
            val insert = document.insert(offset, open + close)
            undoRedo.record(insert)
            dirty = true
            collapseTo(document.positionAt(offset + open.length))
            invalidateFrom(document.positionAt(offset).line)
        }
    }

    /** Duplicates the current line (or all lines of the selection) below. */
    fun duplicateLine() {
        coalesceNext = false
        val first = selection.start.line
        val last = selection.end.line
        val start = document.lineStartOffset(first)
        val end = lineStartOffsetOrEnd(last)
        val block = document.substring(start, end)
        val needsBreak = last == document.lineCount - 1
        val payload = if (needsBreak) "\n" + block else block
        val edit = document.insert(end, payload)
        undoRedo.record(edit)
        dirty = true
        collapseTo(TextPosition(last + 1, caret.column))
        invalidateFrom(first)
    }

    fun moveLineUp() = moveLine(-1)

    fun moveLineDown() = moveLine(1)

    private fun moveLine(direction: Int) {
        coalesceNext = false
        val line = caret.line
        val target = line + direction
        if (target < 0 || target >= document.lineCount) return
        val upper = minOf(line, target)
        val lower = maxOf(line, target)
        val start = document.lineStartOffset(upper)
        val end = lineStartOffsetOrEnd(lower)
        val upperText = document.lineAt(upper)
        val lowerText = document.lineAt(lower)
        val upperTerm = terminatorOf(upper)
        val lowerTerm = terminatorOf(lower)
        val block = upperText + upperTerm + lowerText + lowerTerm
        val swapped = lowerText + upperTerm + upperText + lowerTerm
        if (block == swapped) return
        val removed = document.delete(start, end)
        val insert = document.insert(start, swapped)
        undoRedo.record(TextEdit.Group(listOf(TextEdit.Delete(start, removed), insert)))
        dirty = true
        collapseTo(TextPosition(target, caret.column.coerceAtMost(document.lineAt(target).length)))
        invalidateFrom(upper)
    }

    // ---------------------------------------------------------------- multi-cursor

    /** Adds a caret/selection at the next occurrence of the selected text. */
    fun addNextOccurrence() {
        coalesceNext = false
        var base = selection
        if (base.isCollapsed) {
            base = wordAt(base.caret) ?: return
            selections[selections.size - 1] = base
        }
        val baseStart = document.offsetAt(base.start)
        val baseEnd = document.offsetAt(base.end)
        val needle = document.substring(baseStart, baseEnd)
        if (needle.isEmpty()) return
        val text = content
        // Search forward from the end of the current base, then wrap once to the
        // start so the last occurrence links back to the first. Advance by one
        // character (not by needle length) so overlapping matches are reachable.
        var index = text.indexOf(needle, baseEnd)
        if (index < 0) index = text.indexOf(needle, 0)
        while (index >= 0) {
            val end = index + needle.length
            val overlapsExisting = selections.any { sel ->
                val s = document.offsetAt(sel.start)
                val e = document.offsetAt(sel.end)
                index < e && end > s
            }
            if (!overlapsExisting) {
                val occurrence = Selection(
                    document.positionAt(index),
                    document.positionAt(end)
                )
                selections.add(occurrence)
                setSelections(selections)
                return
            }
            index = text.indexOf(needle, index + 1)
        }
    }

    /** Selects the word under the caret (used by double-tap). */
    fun selectWordAtCaret() {
        val word = wordAt(caret) ?: return
        setSingle(word)
    }

    /** Starts a new selection anchored at [line]/[column] (used by long-press). */
    fun beginSelection(line: Int, column: Int) {
        val clampedLine = line.coerceIn(0, document.lineCount - 1)
        val pos = TextPosition(clampedLine, column.coerceIn(0, lineLength(clampedLine)))
        setSingle(Selection(pos, pos))
    }

    /** Extends the current selection's caret to [line]/[column], keeping the anchor. */
    fun extendSelectionTo(line: Int, column: Int) {
        val clampedLine = line.coerceIn(0, document.lineCount - 1)
        val pos = TextPosition(clampedLine, column.coerceIn(0, lineLength(clampedLine)))
        setSingle(Selection(selection.anchor, pos))
    }

    private fun wordAt(position: TextPosition): Selection? {
        val line = document.lineAt(position.line)
        if (line.isEmpty()) return null
        var column = position.column.coerceIn(0, line.length)
        if (column == line.length) column--
        if (!isWordChar(line[column])) return null
        var start = column
        while (start > 0 && isWordChar(line[start - 1])) start--
        var end = column
        while (end < line.length && isWordChar(line[end])) end++
        return Selection(
            TextPosition(position.line, start),
            TextPosition(position.line, end)
        )
    }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

    // ---------------------------------------------------------------- search & replace

    fun findAll(query: String, options: SearchOptions): List<SearchMatch> =
        TextSearch.findAll(document.snapshot(), query, options)

    fun selectMatch(match: SearchMatch) {
        setSingle(Selection(document.positionAt(match.start), document.positionAt(match.end)))
    }

    /** Replaces one match, recording a single undo step. */
    fun replaceMatch(match: SearchMatch, replacement: String): Boolean {
        coalesceNext = false
        if (match.end <= match.start) return false
        val removed = document.delete(match.start, match.end)
        val edits = ArrayList<TextEdit>()
        edits.add(TextEdit.Delete(match.start, removed))
        if (replacement.isNotEmpty()) edits.add(document.insert(match.start, replacement))
        undoRedo.record(TextEdit.Group(edits))
        dirty = true
        collapseTo(document.positionAt(match.start + replacement.length))
        invalidateFrom(document.positionAt(match.start).line)
        return true
    }

    /** Replaces every match as one undo step; returns how many were replaced. */
    fun replaceAll(query: String, options: SearchOptions, replacement: String): Int {
        coalesceNext = false
        val found = findAll(query, options)
        if (found.isEmpty()) return 0
        val edits = ArrayList<TextEdit>()
        for (match in found.sortedByDescending { it.start }) {
            val removed = document.delete(match.start, match.end)
            edits.add(TextEdit.Delete(match.start, removed))
            if (replacement.isNotEmpty()) edits.add(document.insert(match.start, replacement))
        }
        undoRedo.record(TextEdit.Group(edits))
        dirty = true
        invalidateFrom(0)
        return found.size
    }

    // ---------------------------------------------------------------- undo / redo

    fun undo() {
        val edit = undoRedo.undo() ?: return
        val caret = applyInverse(edit)
        coalesceNext = false
        collapseTo(caret)
        invalidateFrom(caret.line)
        dirty = document.version != savedVersion
    }

    fun redo() {
        val edit = undoRedo.redo() ?: return
        val caret = apply(edit)
        coalesceNext = false
        collapseTo(caret)
        invalidateFrom(caret.line)
        dirty = document.version != savedVersion
    }

    private fun apply(edit: TextEdit): TextPosition = when (edit) {
        is TextEdit.Insert -> {
            document.insert(edit.offset, edit.text)
            document.positionAt(edit.offset + edit.text.length)
        }
        is TextEdit.Delete -> {
            document.delete(edit.offset, edit.offset + edit.text.length)
            document.positionAt(edit.offset)
        }
        is TextEdit.Group -> {
            var last = document.positionAt(edit.offset)
            edit.edits.forEach { last = apply(it) }
            last
        }
    }

    private fun applyInverse(edit: TextEdit): TextPosition = when (edit) {
        is TextEdit.Insert -> {
            document.delete(edit.offset, edit.offset + edit.text.length)
            document.positionAt(edit.offset)
        }
        is TextEdit.Delete -> {
            document.insert(edit.offset, edit.text)
            document.positionAt(edit.offset + edit.text.length)
        }
        is TextEdit.Group -> {
            var last = document.positionAt(edit.offset)
            edit.edits.reversed().forEach { last = applyInverse(it) }
            last
        }
    }

    // ---------------------------------------------------------------- navigation

    fun moveLeft() {
        if (!selection.isCollapsed) {
            collapseTo(selection.start)
            return
        }
        val next = when {
            caret.column > 0 -> caret.copy(column = caret.column - 1)
            caret.line > 0 -> TextPosition(caret.line - 1, lineLength(caret.line - 1))
            else -> return
        }
        setSingle(Selection.collapsed(next))
        desiredColumn = next.column
    }

    fun moveRight() {
        if (!selection.isCollapsed) {
            collapseTo(selection.end)
            return
        }
        val len = lineLength(caret.line)
        val next = when {
            caret.column < len -> caret.copy(column = caret.column + 1)
            caret.line < document.lineCount - 1 -> TextPosition(caret.line + 1, 0)
            else -> return
        }
        setSingle(Selection.collapsed(next))
        desiredColumn = next.column
    }

    fun moveUp() {
        if (caret.line == 0) return
        val line = caret.line - 1
        val col = desiredColumn.coerceAtMost(lineLength(line))
        val d = desiredColumn
        setSingle(Selection.collapsed(TextPosition(line, col)))
        desiredColumn = d
    }

    fun moveDown() {
        if (caret.line >= document.lineCount - 1) return
        val line = caret.line + 1
        val col = desiredColumn.coerceAtMost(lineLength(line))
        val d = desiredColumn
        setSingle(Selection.collapsed(TextPosition(line, col)))
        desiredColumn = d
    }

    fun moveToColumn(column: Int) {
        collapseTo(TextPosition(caret.line, column.coerceAtLeast(0)))
    }

    fun setCaret(line: Int, column: Int) {
        val clampedLine = line.coerceIn(0, document.lineCount - 1)
        collapseTo(TextPosition(clampedLine, column.coerceIn(0, lineLength(clampedLine))))
    }

    fun setSelection(anchor: TextPosition, caret: TextPosition) {
        setSingle(Selection(anchor, caret))
    }

    fun selectAll() {
        setSingle(Selection(TextPosition.Zero, document.positionAt(document.length)))
    }

    fun moveToDocumentStart() = collapseTo(TextPosition.Zero)

    fun moveToDocumentEnd() = collapseTo(document.positionAt(document.length))

    /** Moves the caret by [pages] screenfuls, clamped to the document. */
    fun movePage(pages: Int, pageLines: Int) {
        val target = (caret.line + pages * pageLines.coerceAtLeast(1))
            .coerceIn(0, document.lineCount - 1)
        collapseTo(TextPosition(target, caret.column.coerceAtMost(lineLength(target))))
    }

    // ---------------------------------------------------------------- clipboard

    /** The text covered by the primary selection ("" when collapsed). */
    fun selectedText(): String {
        val sel = selection
        if (sel.isCollapsed) return ""
        return document.substring(document.offsetAt(sel.start), document.offsetAt(sel.end))
    }

    /**
     * Replaces the primary selection with [text] (used for paste). Records one
     * undo step and leaves the caret after the inserted text.
     */
    fun paste(text: String) {
        if (text.isEmpty()) return
        coalesceNext = false
        val edit = replaceSelection(text) ?: return
        undoRedo.record(edit)
        dirty = true
        invalidateFrom(
            document.positionAt(edit.offset).line,
            full = text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0
        )
    }

    /** Deletes every selection's range in one undo step (used for clipboard cut). */
    fun deleteSelection() {
        val nonCollapsed = selections.filter { !it.isCollapsed }
        if (nonCollapsed.isEmpty()) return
        coalesceNext = false
        val ordered = nonCollapsed.sortedByDescending { document.offsetAt(it.start) }
        val edits = ArrayList<TextEdit>()
        val newSels = ArrayList<Selection>()
        for (sel in ordered) {
            val s = document.offsetAt(sel.start)
            val e = document.offsetAt(sel.end)
            val removed = document.delete(s, e)
            edits.add(TextEdit.Delete(s, removed))
            newSels.add(Selection.collapsed(document.positionAt(s)))
        }
        if (edits.isEmpty()) return
        undoRedo.record(TextEdit.Group(edits))
        dirty = true
        setSelections(newSels)
        invalidateFrom(newSels.minOf { it.caret.line })
    }

    // ---------------------------------------------------------------- snippets / indent

    /**
     * Inserts [body] at the caret, placing the caret [caretOffset] code units
     * into the inserted text (the snippet placeholder).
     */
    fun insertSnippet(body: String, caretOffset: Int) {
        coalesceNext = false
        val edit = replaceSelection(body) ?: return
        undoRedo.record(edit)
        dirty = true
        val safeOffset = caretOffset.coerceIn(0, body.length)
        collapseTo(document.positionAt(edit.offset + safeOffset))
        invalidateFrom(document.positionAt(edit.offset).line, full = body.indexOf('\n') >= 0)
    }

    /**
     * Converts indentation of the whole document (or the selection) as one undo
     * step. Returns the number of lines changed.
     */
    fun convertIndents(useTabs: Boolean, tabWidth: Int, selectionOnly: Boolean): Int {
        coalesceNext = false
        val first = if (selectionOnly) selection.start.line else 0
        val last = if (selectionOnly) selection.end.line else document.lineCount - 1
        if (first > last) return 0
        val edits = ArrayList<TextEdit>()
        var changed = 0
        for (line in last downTo first) {
            val text = document.lineAt(line)
            val converted = IndentConverter.convertLine(text, useTabs, tabWidth)
            if (converted != null) {
                val lineStart = document.lineStartOffset(line)
                val removed = document.delete(lineStart, lineStart + text.length)
                edits.add(TextEdit.Delete(lineStart, removed))
                edits.add(document.insert(lineStart, converted))
                changed++
            }
        }
        if (edits.isEmpty()) return 0
        undoRedo.record(TextEdit.Group(edits))
        dirty = true
        invalidateFrom(first)
        return changed
    }

    /** Replaces the whole buffer (used by "reopen with encoding") without history. */
    fun reloadText(text: String) {
        document.setText(text)
        undoRedo.clear()
        coalesceNext = false
        selections.clear()
        selections.add(Selection.Zero)
        lineStates.clear()
        lineStates.add(HlState.NORMAL)
        wordIndexCache = null
        wordIndexVersion = -1
        maxLineLengthVersion = -1
        maxLineLine = -1
        lengthDirtyAll = true
        dirty = false
        savedVersion = document.version
    }

    // ---------------------------------------------------------------- internals

    private fun setSingle(selection: Selection) {
        selections.clear()
        selections.add(selection)
    }

    private fun setSelections(list: List<Selection>) {
        val sorted = list.sortedBy { document.offsetAt(it.start) }
        selections.clear()
        selections.addAll(sorted.ifEmpty { listOf(Selection.Zero) })
    }

    private fun collapseTo(position: TextPosition) {
        setSingle(Selection.collapsed(position))
        desiredColumn = position.column
    }

    private fun lineStartOffsetOrEnd(line: Int): Int {
        val start = document.lineStartOffset(line)
        return start + lineLength(line) + document.lineTerminatorLength(line)
    }

    private fun terminatorOf(line: Int): String {
        val breakOffset = document.lineStartOffset(line) + lineLength(line)
        val len = document.lineTerminatorLength(line)
        return if (len == 0) "" else document.substring(breakOffset, breakOffset + len)
    }

    private fun stateAt(line: Int): Int {
        while (lineStates.size <= line) {
            val src = lineStates.size - 1
            val result = highlighter.highlight(document.lineAt(src), lineStates[src])
            lineStates.add(result.endState)
        }
        return lineStates[line]
    }

    private fun invalidateFrom(line: Int, full: Boolean = true) {
        if (full) touchAllLines() else touchLines(line)
        val keep = line + 1
        while (lineStates.size > keep) lineStates.removeAt(lineStates.size - 1)
    }

    companion object {
        private val OPEN_TO_CLOSE = mapOf('(' to ')', '[' to ']', '{' to '}')
        private val QUOTES = setOf('"', '\'', '`')
        private val CLOSE_CHARS = setOf(')', ']', '}')

        /** Below this size a max-line scan is cheap enough to always run fully. */
        const val FULL_SCAN_LIMIT = 1 shl 20

        /** Documents larger than this skip the O(n) word index to keep typing snappy. */
        const val WORD_INDEX_LIMIT = 1 shl 22
    }
}
