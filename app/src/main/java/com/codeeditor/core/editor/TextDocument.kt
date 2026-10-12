package com.codeeditor.core.editor

/**
 * Mutable in-memory text buffer with an incrementally maintained line index.
 *
 * Text is stored as a single [String] with a cached array of line start offsets.
 * Line lookups are O(log n) and editing is O(n) in the worst case, which is more
 * than fast enough for interactive editing of very large files. Rendering
 * performance is handled separately by virtualising the visible line range.
 */
class TextDocument(text: String = "") {

    private var content: String = text
    private var lineStarts: IntArray = buildLineStarts(text)

    /** Incremented on every mutation. Used by the UI layer to know when to redraw. */
    var version: Int = 0
        private set

    val length: Int get() = content.length

    val lineCount: Int get() = lineStarts.size

    /** True if the file uses CRLF line endings (detected from the first break). */
    var lineEnding: LineEnding = detectLineEnding(text)
        private set

    fun snapshot(): String = content

    fun setText(newText: String) {
        content = newText
        lineEnding = detectLineEnding(newText)
        reindex()
        version++
    }

    /** The text of [line] without its line terminator. */
    fun lineAt(line: Int): String {
        require(line in 0 until lineCount) { "line $line out of bounds 0..${lineCount - 1}" }
        val start = lineStarts[line]
        val end = lineEndOffset(line)
        return content.substring(start, end)
    }

    /** Offset where [line] begins. */
    fun lineStartOffset(line: Int): Int = lineStarts[line.coerceIn(0, lineCount - 1)]

    /** Offset of the first terminator character of [line], or the buffer end. */
    fun lineEndOffset(line: Int): Int {
        val clamped = line.coerceIn(0, lineCount - 1)
        var end = if (clamped + 1 < lineStarts.size) lineStarts[clamped + 1] - 1 else content.length
        if (end > lineStarts[clamped] && content[end - 1] == '\r') end--
        return end
    }

    /** Length of the line terminator of [line] (0, 1 or 2 code units). */
    fun lineTerminatorLength(line: Int): Int {
        val clamped = line.coerceIn(0, lineCount - 1)
        if (clamped + 1 >= lineStarts.size) return 0
        val breakOffset = lineStarts[clamped + 1] - 1
        return if (breakOffset > lineStarts[clamped] && content[breakOffset - 1] == '\r') 2 else 1
    }

    fun positionAt(offset: Int): TextPosition {
        val o = offset.coerceIn(0, content.length)
        var lo = 0
        var hi = lineStarts.size - 1
        var line = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lineStarts[mid] <= o) {
                line = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return TextPosition(line, o - lineStarts[line])
    }

    fun offsetAt(position: TextPosition): Int {
        val line = position.line.coerceIn(0, lineCount - 1)
        val maxCol = lineEndOffset(line) - lineStarts[line]
        return lineStarts[line] + position.column.coerceIn(0, maxCol)
    }

    fun offsetAt(line: Int, column: Int): Int = offsetAt(TextPosition(line, column))

    /** Inserts [text] at [offset] and returns the edit that describes it. */
    fun insert(offset: Int, text: String): TextEdit {
        if (text.isEmpty()) return TextEdit.Insert(offset, "")
        val o = offset.coerceIn(0, content.length)
        content = buildString(content.length + text.length) {
            append(content, 0, o)
            append(text)
            append(content, o, content.length)
        }
        reindex()
        version++
        return TextEdit.Insert(o, text)
    }

    /** Deletes the range [start, end) and returns the removed text. */
    fun delete(start: Int, end: Int): String {
        val s = start.coerceIn(0, content.length)
        val e = end.coerceIn(s, content.length)
        if (s == e) return ""
        val removed = content.substring(s, e)
        content = content.substring(0, s) + content.substring(e)
        reindex()
        version++
        return removed
    }

    fun substring(start: Int, end: Int): String =
        content.substring(start.coerceIn(0, content.length), end.coerceIn(0, content.length))

    private fun reindex() {
        lineStarts = buildLineStarts(content)
    }

    private fun detectLineEnding(text: String): LineEnding {
        val idx = text.indexOf('\n')
        if (idx > 0 && text[idx - 1] == '\r') return LineEnding.CRLF
        return LineEnding.LF
    }

    companion object {
        private fun buildLineStarts(s: String): IntArray {
            var breaks = 0
            for (ch in s) if (ch == '\n') breaks++
            val result = IntArray(breaks + 1)
            var cursor = 1
            for (i in s.indices) {
                if (s[i] == '\n') result[cursor++] = i + 1
            }
            return result
        }
    }
}

enum class LineEnding(val sequence: String) {
    LF("\n"),
    CRLF("\r\n")
}
