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
 * Characters inside a single piece are not scanned linearly any more: each
 * backing buffer (the original text and the append-only insertion buffer) keeps
 * a [NewlineIndex] of cumulative newline counts at fixed-size chunk boundaries.
 * That turns the worst case — a 200 MB file that is still a single piece — from
 * an O(n) scan per cursor move into an O(chunk) lookup plus a binary descent.
 *
 * The [version] counter increments on every mutation and lets the UI decide
 * when to recompute syntax highlighting and redraw.
 */
class TextDocument(text: String = "") {

    private var seed: Int = 0x2F6E2B1
    private var original: String = text
    private val added: StringBuilder = StringBuilder()
    private val originalIndex = NewlineIndex(CHUNK_SIZE)
    private val addedIndex = NewlineIndex(CHUNK_SIZE)
    private var root: Node? = null
    private var cachedSnapshot: String? = null

    /** Incremented on every mutation. Used by the UI layer to know when to redraw. */
    var version: Int = 0
        private set

    val length: Int get() = root?.size ?: 0

    val lineCount: Int get() = (root?.lines ?: 0) + 1

    /** True if the file appeared to use CRLF line endings when last loaded/set. */
    var lineEnding: LineEnding = LineEnding.detect(text)
        private set

    init {
        resetRoot(text)
    }

    fun snapshot(): String {
        cachedSnapshot?.let { return it }
        return extract(0, length).also { cachedSnapshot = it }
    }

    /** Replaces the whole buffer with [newText], discarding edit history pieces. */
    fun setText(newText: String) {
        original = newText
        added.clear()
        originalIndex.reset()
        addedIndex.reset()
        resetRoot(newText)
        lineEnding = LineEnding.detect(newText)
        version++
    }

    private fun resetRoot(newText: String) {
        originalIndex.ensure(original)
        root = makeNode(Piece(SRC_ORIGINAL, 0, newText.length, originalIndex.countIn(original, 0, newText.length)))
        cachedSnapshot = null
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
        addedIndex.ensure(added)
        val (left, right) = split(root, o)
        val node = makeNode(Piece(SRC_ADDED, addStart, text.length, addedIndex.countIn(added, addStart, addStart + text.length)))
        root = merge(merge(left, node), right)
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
                continue
            }
            count += nodeLines(cur.left)
            remaining -= leftSize
            val p = cur.piece
            val take = minOf(remaining, p.length)
            if (take > 0) {
                val src = sourceOf(p)
                count += indexFor(p.source).countIn(src, p.start, p.start + take)
            }
            remaining -= take
            if (remaining <= 0) break
            cur = cur.right
        }
        return count
    }

    /** Offset of the [n]-th '\n' character (1-based), or -1 if there are fewer. */
    private fun nthNewlineOffset(n: Int): Int {
        if (n <= 0) return -1
        var remaining = n
        var offset = 0
        var cur = root
        while (cur != null) {
            val leftLines = nodeLines(cur.left)
            if (remaining <= leftLines) {
                cur = cur.left
                continue
            }
            offset += nodeSize(cur.left)
            remaining -= leftLines
            val p = cur.piece
            if (remaining > p.ownLines) {
                offset += p.length
                remaining -= p.ownLines
                cur = cur.right
                continue
            }
            val src = sourceOf(p)
            val local = indexFor(p.source)
                .offsetOfNthNewline(src, p.start, p.start + p.length, remaining)
            return if (local < 0) -1 else offset + (local - p.start)
        }
        return -1
    }

    private fun sourceOf(piece: Piece): CharSequence =
        if (piece.source == SRC_ORIGINAL) original else added

    private fun indexFor(source: Int): NewlineIndex =
        if (source == SRC_ORIGINAL) originalIndex else addedIndex

    private fun pieceBreaks(source: Int, start: Int, length: Int): Int =
        indexFor(source).countIn(
            if (source == SRC_ORIGINAL) original else added,
            start,
            start + length
        )

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

    private class Piece(val source: Int, val start: Int, val length: Int, val ownLines: Int)

    private class Node(val piece: Piece, val priority: Int) {
        var left: Node? = null
        var right: Node? = null
        var size: Int = piece.length
        var lines: Int = piece.ownLines
    }

    /**
     * Cumulative newline counter over a chunked backing buffer.
     *
     * The backing buffers are never mutated in place (the original text is
     * immutable and the insertion buffer only grows by appending), so the index
     * only ever extends forward. `cumulative[k]` holds the number of newlines
     * before offset `k * chunkSize`; the trailing, not-yet-full chunk is scanned
     * on demand.
     */
    private class NewlineIndex(private val chunkSize: Int) {
        private val cumulative = ArrayList<Int>().apply { add(0) }

        fun reset() {
            cumulative.clear()
            cumulative.add(0)
        }

        fun ensure(source: CharSequence) {
            var chunk = cumulative.size - 1
            while ((chunk + 1) * chunkSize <= source.length) {
                var count = cumulative[chunk]
                val from = chunk * chunkSize
                val to = from + chunkSize
                for (i in from until to) if (source[i] == '\n') count++
                cumulative.add(count)
                chunk++
            }
        }

        /** Newlines in `[0, offset)`. */
        fun countBefore(source: CharSequence, offset: Int): Int {
            if (offset <= 0) return 0
            ensure(source)
            var chunk = offset / chunkSize
            if (chunk > cumulative.size - 1) chunk = cumulative.size - 1
            var count = cumulative[chunk]
            var i = chunk * chunkSize
            while (i < offset && i < source.length) {
                if (source[i] == '\n') count++
                i++
            }
            return count
        }

        fun countIn(source: CharSequence, start: Int, end: Int): Int {
            if (end <= start) return 0
            return countBefore(source, end) - countBefore(source, start)
        }

        /**
         * Offset of the [nth] (1-based) newline inside `[start, end)`, or -1.
         * Binary-searches the prefix-count function, so only a handful of
         * chunk-sized scans are needed even for a multi-hundred-MB piece.
         */
        fun offsetOfNthNewline(source: CharSequence, start: Int, end: Int, nth: Int): Int {
            if (nth <= 0 || end <= start) return -1
            ensure(source)
            if (end > source.length) {
                val fallbackEnd = source.length
                if (fallbackEnd <= start) return -1
                return offsetOfNthNewline(source, start, fallbackEnd, nth)
            }
            val base = countBefore(source, start)
            val target = base + nth
            var lo = start
            var hi = end - 1
            var result = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (countBefore(source, mid + 1) >= target) {
                    result = mid
                    hi = mid - 1
                } else {
                    lo = mid + 1
                }
            }
            return if (result >= start && source[result] == '\n') result else -1
        }
    }

    companion object {
        private const val SRC_ORIGINAL = 0
        private const val SRC_ADDED = 1

        /** Characters per index chunk. Small enough to scan cheaply, large enough to stay compact. */
        const val CHUNK_SIZE = 4096
    }
}

enum class LineEnding(val sequence: String) {
    LF("\n"),
    CRLF("\r\n"),
    CR("\r");

    companion object {
        /**
         * Detects the dominant line-ending convention by counting actual line
         * breaks, so a lone CRLF pair no longer forces a whole LF file to be
         * treated as CRLF and CR-only (classic Mac) files are recognised too.
         */
        fun detect(text: String): LineEnding {
            var crlf = 0
            var lf = 0
            var cr = 0
            var i = 0
            while (i < text.length) {
                when (text[i]) {
                    '\r' -> {
                        if (i + 1 < text.length && text[i + 1] == '\n') {
                            crlf++
                            i++
                        } else {
                            cr++
                        }
                    }
                    '\n' -> lf++
                }
                i++
            }
            return when {
                crlf == 0 && cr > 0 && lf == 0 -> CR
                crlf >= cr && crlf >= lf && crlf > 0 -> CRLF
                cr > lf -> CR
                else -> LF
            }
        }
    }
}
