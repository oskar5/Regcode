package com.codeeditor.core.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class TextDocumentTest {

    @Test
    fun `empty document has a single line`() {
        val doc = TextDocument("")
        assertEquals(1, doc.lineCount)
        assertEquals("", doc.lineAt(0))
    }

    @Test
    fun `counts lines and reads them back`() {
        val doc = TextDocument("alpha\nbeta\ngamma")
        assertEquals(3, doc.lineCount)
        assertEquals("alpha", doc.lineAt(0))
        assertEquals("beta", doc.lineAt(1))
        assertEquals("gamma", doc.lineAt(2))
    }

    @Test
    fun `trailing newline produces empty last line`() {
        val doc = TextDocument("a\n")
        assertEquals(2, doc.lineCount)
        assertEquals("", doc.lineAt(1))
    }

    @Test
    fun `trim carriage return on windows line endings`() {
        val doc = TextDocument("a\r\nb")
        assertEquals(LineEnding.CRLF, doc.lineEnding)
        assertEquals("a", doc.lineAt(0))
        assertEquals("b", doc.lineAt(1))
        assertEquals(2, doc.lineTerminatorLength(0))
    }

    @Test
    fun `maps offsets to positions and back`() {
        val doc = TextDocument("abc\ndefg")
        val pos = doc.positionAt(5)
        assertEquals(TextPosition(1, 1), pos)
        assertEquals(5, doc.offsetAt(pos))
    }

    @Test
    fun `insert shifts line index`() {
        val doc = TextDocument("ab")
        doc.insert(1, "\nXY\n")
        assertEquals("a\nXY\nb", doc.snapshot())
        assertEquals(3, doc.lineCount)
        assertEquals("XY", doc.lineAt(1))
    }

    @Test
    fun `delete removes range and returns removed text`() {
        val doc = TextDocument("hello world")
        val removed = doc.delete(5, 11)
        assertEquals(" world", removed)
        assertEquals("hello", doc.snapshot())
    }

    @Test
    fun `version increments on mutation`() {
        val doc = TextDocument("x")
        val v = doc.version
        doc.insert(0, "a")
        assertEquals(v + 1, doc.version)
    }
}
