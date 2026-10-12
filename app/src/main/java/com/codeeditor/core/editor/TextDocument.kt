package com.codeeditor.core.editor

import com.codeeditor.core.editor.edit.TextEdit

/**
 * Mutable in-memory text buffer backed by a piece tree.
 *
 * The buffer is kept as a balanced tree of immutable "pieces" that reference
 * regions of the originally loaded text or of appended insertion buffers. Edits
 * are O(log n) and insertions never rewrite the stored text, so repeated typing
 * and delete-all operations stay fast on very large files. Line starts are not
 * stored as a materialised array: the number of newlines before any offset is
 * derived from per-node line counters, keeping line queries logarithmic too.
 *
 * The [version] counter increments on every mutation and lets the UI decide
 * when to recompute syntax highlighting and redraw.
 */
class TextDocument(text: String = "") {

    private var seed: Int = 0x2F6E2B1
    private var original: String = text
    private val added: StringBuilder = StringBuilder()
    private var root: Node? = makeNode(Piece(SRC_ORIGINAL, 0, text.length, text.breaks()))
    private var cachedSnapshot: String? = null

    /** Incremented on every mutation. Used by the UI layer to know when to redraw. */
    var version: Int = 0
        private set

    val length: Int get() = root?.size ?: 0

    val lineCount: Int get() = (root?.lines ?: 0) + 1

    /** True if the file appeared to use CRLF line endings when last loaded/set. */
    var lineEnding: LineEnding = LineEnding.detect(text)
        private set

    fun snapshot(): String {
        cachedSnapshot?.let { return it }
        return extract(0, length).also { cachedSnapshot = it }
    }

    /** Replaces the whole buffer with [newText], discarding edit history pieces. */
    fun setText(newText: String) {
        original = newText
        added.clear()
        root = makeNode(Piece(SRC_ORIGINAL, 0, newText.length, newText.breaks()))
        lineEnding = LineEnding.detect(newText)
        cachedSnapshot = null
        version++
    }

    /** The text of [line] without its line terminator. */
    fun lineAt(line: Int): String {
        require(line in 0 until lineCount) { "line $line out of bounds 0..${lineCount - 1}" }
        return extract(lineStartOffset(line), lineEndOffset(line))
    }

    /** Offset where [line] begins. */
    fun lineStartOffset(line: Int): Int {
        val clamped = line.coerceIn(0, lineCount - 1)
        return if (clamped == 0) 0 else nthNewlineOffset(clamped) + 1
    }

    /** Offset of the first terminator character of [line], or the buffer end. */
    fun lineEndOffset(line: Int): Int {
        val clamped = line.coerceIn(0, lineCount - 1)
        var end = if (clamped + 1 < lineCount) lineStartOffset(clamped + 1) - 1 else length
        if (end > lineStartOffset(clamped) && end - 1 >= 0 && charAt(end - 1) == '\r') end--
        return end
    }

    /** Length of the line terminator of [line] (0, 1 or 2 code units). */
    fun lineTerminatorLength(line: Int): Int {
        val clamped = line.coerceIn(0, lineCount - 1)
        if (clamped + 1 >= lineCount) return 0
        val breakOffset = lineStartOffset(clamped + 1) - 1
        return if (breakOffset > lineStartOffset(clamped) && charAt(breakOffset - 1) == '\r') 2 else 1
    }

    fun positionAt(offset: Int): TextPosition {
        val o = offset.coerceIn(0, length)
        val line = newlinesBefore(o)
        val lineStart = if (line == 0) 0 else nthNewlineOffset(line) + 1
        return TextPosition(line, o - lineStart)
    }

    fun offsetAt(position: TextPosition): Int {
        val line = position.line.coerceIn(0, lineCount - 1)
        val start = lineStartOffset(line)
        val maxCol = lineEndOffset(line) - start
        return start + position.column.coerceIn(0, maxCol)
    }

    fun offsetAt(line: Int, column: Int): Int = offsetAt(TextPosition(line, column))

    /** Inserts [text] at [offset] and returns the edit that describes it. */
    fun insert(offset: Int, text: String): TextEdit {
        if (text.isEmpty()) return TextEdit.Insert(offset.coerceIn(0, length), "")
        val o = offset.coerceIn(0, length)
        val addStart = added.length
        added.append(text)
        val (left, right) = split(root, o)
        root = merge(merge(left, makeNode(Piece(SRC_ADDED, addStart, text.length, text.breaks()))), right)
        cachedSnapshot = null
        version++
        return TextEdit.Insert(o, text)
    }

    /** Deletes the range [start, end) and returns the removed text. */
    fun delete(start: Int, end: Int): String {
        val s = start.coerceIn(0, length)
        val e = end.coerceIn(s, length)
        if (s == e) return ""
        val removed = extract(s, e)
        val (left, middleAndRight) = split(root, s)
        val (_, right) = split(middleAndRight, e - s)
        root = merge(left, right)
        cachedSnapshot = null
        version++
        return removed
    }

    fun substring(start: Int, end: Int): String {
        val s = start.coerceIn(0, length)
        val e = end.coerceIn(s, length)
        return extract(s, e)
    }

    private fun charAt(offset: Int): Char {
        var cur = root
        var remaining = offset.coerceIn(0, length)
        while (cur != null) {
            val leftSize = nodeSize(cur.left)
            if (remaining < leftSize) {
                cur = cur.left
            } else if (remaining < leftSize + cur.piece.length) {
                val src = sourceOf(cur.piece)
                return src[cur.piece.start + (remaining - leftSize)]
            } else {
                remaining -= leftSize + cur.piece.length
                cur = cur.right
            }
        }
        return '\u0000'
    }

    private fun extract(start: Int, end: Int): String {
        val s = start.coerceIn(0, length)
        val e = end.coerceIn(s, length)
        if (e <= s) return ""
        val sb = StringBuilder(e - s)
        collect(root, s, e, sb)
        return sb.toString()
    }

    /**
     * Appends the characters in [start, end) of [node]'s subtree. Ranges are
     * relative to the start of the subtree, so subtrees fully outside the range
     * are skipped entirely (this keeps [lineAt] logarithmic for long buffers).
     */
    private fun collect(node: Node?, start: Int, end: Int, sb: StringBuilder) {
        if (node == null || end <= start) return
        val leftSize = nodeSize(node.left)
        val nodeStart = leftSize
        val nodeEnd = leftSize + node.piece.length
        if (start < nodeStart) {
            collect(node.left, start, if (end < nodeStart) end else nodeStart, sb)
        }
        if (end > nodeStart && start < nodeEnd) {
            val from = if (start > nodeStart) start - nodeStart else 0
            val to = if (end < nodeEnd) end - nodeStart else node.piece.length
            val src = sourceOf(node.piece)
            sb.append(src, node.piece.start + from, node.piece.start + to)
        }
        if (end > nodeEnd) {
            collect(node.right, if (start > nodeEnd) start - nodeEnd else 0, end - nodeEnd, sb)
        }
    }

    /** Number of '\n' characters at offsets strictly before [offset]. */
    private fun newlinesBefore(offset: Int): Int {
        var count = 0
        var remaining = offset.coerceIn(0, length)
        var cur = root
        while (cur != null && remaining > 0) {
            val leftSize = nodeSize(cur.left)
            if (remaining <= leftSize) {
                cur = cur.left
            } else {
                count += nodeLines(cur.left)
                remaining -= leftSize
                val p = cur.piece
                val take = minOf(remaining, p.length)
                if (take > 0) {
                    val src = sourceOf(p)
                    for (i in 0 until take) if (src[p.start + i] == '\n') count++
                }
                remaining -= take
                if (remaining <= 0) break
                cur = cur.right
            }
        }
        return count
    }

    /** Offset of the [n]-th '\n' character (1-based), or -1 if there are fewer. */
    private fun nthNewlineOffset(n: Int): Int {
        var remaining = n
        var offset = 0
        var cur = root
        while (cur != null) {
            val leftLines = nodeLines(cur.left)
            if (remaining <= leftLines) {
                cur = cur.left
            } else {
                offset += nodeSize(cur.left)
                remaining -= leftLines
                val p = cur.piece
                val src = sourceOf(p)
                for (i in 0 until p.length) {
                    if (src[p.start + i] == '\n') {
                        remaining--
                        if (remaining == 0) return offset + i
                    }
                }
                offset += p.length
                cur = cur.right
            }
        }
        return -1
    }

    private fun sourceOf(piece: Piece): CharSequence =
        if (piece.source == SRC_ORIGINAL) original else added

    private fun pieceBreaks(source: Int, start: Int, length: Int): Int {
        val src = if (source == SRC_ORIGINAL) original else added
        var count = 0
        for (i in start until start + length) if (src[i] == '\n') count++
        return count
    }

    /** Splits [root] into two trees at logical offset [k]. */
    private fun split(root: Node?, k: Int): Pair<Node?, Node?> {
        if (root == null) return null to null
        val leftSize = nodeSize(root.left)
        if (k <= leftSize) {
            val (a, b) = split(root.left, k)
            root.left = b
            update(root)
            return a to root
        } else if (k >= leftSize + root.piece.length) {
            val (a, b) = split(root.right, k - leftSize - root.piece.length)
            root.right = a
            update(root)
            return root to b
        } else {
            val pos = k - leftSize
            val p = root.piece
            val leftChild = root.left
            val rightChild = root.right
            val prefixNode = Node(
                Piece(p.source, p.start, pos, pieceBreaks(p.source, p.start, pos)),
                root.priority
            )
            val suffixNode = makeNode(
                Piece(p.source, p.start + pos, p.length - pos, pieceBreaks(p.source, p.start + pos, p.length - pos))
            )
            val left = merge(leftChild, prefixNode)
            val right = merge(suffixNode, rightChild)
            return left to right
        }
    }

    private fun merge(a: Node?, b: Node?): Node? {
        if (a == null) return b
        if (b == null) return a
        return if (a.priority > b.priority) {
            a.right = merge(a.right, b)
            update(a)
            a
        } else {
            b.left = merge(a, b.left)
            update(b)
            b
        }
    }

    private fun makeNode(piece: Piece): Node {
        val node = Node(piece, nextPriority())
        update(node)
        return node
    }

    private fun update(node: Node) {
        node.size = nodeSize(node.left) + node.piece.length + nodeSize(node.right)
        node.lines = nodeLines(node.left) + node.piece.ownLines + nodeLines(node.right)
    }

    private fun nodeSize(node: Node?): Int = node?.size ?: 0

    private fun nodeLines(node: Node?): Int = node?.lines ?: 0

    private fun nextPriority(): Int {
        seed = seed xor (seed shl 13)
        seed = seed xor (seed ushr 17)
        seed = seed xor (seed shl 5)
        return seed and 0x7FFFFFFF
    }

    private fun String.breaks(): Int {
        var count = 0
        for (i in indices) if (this[i] == '\n') count++
        return count
    }

    private class Piece(val source: Int, val start: Int, val length: Int, val ownLines: Int)

    private class Node(val piece: Piece, val priority: Int) {
        var left: Node? = null
        var right: Node? = null
        var size: Int = piece.length
        var lines: Int = piece.ownLines
    }

    companion object {
        private const val SRC_ORIGINAL = 0
        private const val SRC_ADDED = 1
    }
}

enum class LineEnding(val sequence: String) {
    LF("\n"),
    CRLF("\r\n");

    companion object {
        /**
         * Detects the dominant line-ending convention. Any '\r' character means
         * the file is CRLF-oriented (covers the traditional check that missed
         * '\r\n' appearing right at the very start of the buffer). Files with no
         * carriage returns at all are treated as LF.
         */
        fun detect(text: String): LineEnding =
            if (text.indexOf('\r') >= 0) CRLF else LF
    }
}