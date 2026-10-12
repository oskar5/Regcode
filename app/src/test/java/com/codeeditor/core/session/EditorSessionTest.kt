package com.codeeditor.core.session

import com.codeeditor.core.editor.TextPosition
import com.codeeditor.core.editor.highlight.LanguageRegistry
import com.codeeditor.core.search.SearchOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSessionTest {

    private fun session(text: String = "") = EditorSession(
        id = "test",
        uri = null,
        title = "Test.kt",
        initialText = text,
        language = LanguageRegistry.byId("kotlin")
    )

    @Test
    fun `typing inserts text and marks dirty`() {
        val s = session()
        s.insertText("val x")
        assertEquals("val x", s.content)
        assertTrue(s.dirty)
        assertTrue(s.canUndo)
    }

    @Test
    fun `newline auto-indents after an opening brace`() {
        val s = session("fun a() {")
        s.setCaret(0, 9)
        s.newline()
        assertEquals("fun a() {\n    ", s.content)
    }

    @Test
    fun `newline keeps existing leading whitespace`() {
        val s = session("    if (x)")
        s.setCaret(0, s.lineLength(0))
        s.newline()
        assertEquals("    if (x)\n    ", s.content)
    }

    @Test
    fun `backspace at line start merges with previous line`() {
        val s = session("ab\ncd")
        s.setCaret(1, 0)
        s.backspace()
        assertEquals("abcd", s.content)
    }

    @Test
    fun `undo and redo restore content`() {
        val s = session()
        s.insertText("hi")
        s.undo()
        assertEquals("", s.content)
        s.redo()
        assertEquals("hi", s.content)
    }

    @Test
    fun `vertical caret movement keeps column`() {
        val s = session("abcdef\nxy\nabcdef")
        s.setCaret(0, 4)
        s.moveDown()
        assertEquals(TextPosition(1, 2), s.caret)
        s.moveUp()
        assertEquals(TextPosition(0, 4), s.caret)
    }

    @Test
    fun `markSaved clears dirty but keeps undo history`() {
        val s = session("hello")
        s.setCaret(0, 5)
        s.insertText(" world")
        s.markSaved()
        assertFalse(s.dirty)
        assertTrue(s.canUndo)
        s.undo()
        assertEquals("hello", s.content)
        s.redo()
        assertEquals("hello world", s.content)
    }

    @Test
    fun `tokens are produced for the correct language`() {
        val s = session("val x = 1")
        assertTrue(s.tokensForLine(0).isNotEmpty())
        assertEquals("kotlin", s.language.id)
    }

    @Test
    fun `typing an open bracket auto-closes and is one undo step`() {
        val s = session()
        s.insertText("(")
        assertEquals("()", s.content)
        assertEquals(TextPosition(0, 1), s.caret)
        s.undo()
        assertEquals("", s.content)
    }

    @Test
    fun `typing a closing bracket skips over the auto-inserted one`() {
        val s = session()
        s.insertText("(")
        s.insertText(")")
        assertEquals("()", s.content)
        assertEquals(TextPosition(0, 2), s.caret)
    }

    @Test
    fun `typing a quote wraps the selected text`() {
        val s = session("ab")
        s.setSelection(TextPosition(0, 0), TextPosition(0, 2))
        s.insertText("\"")
        assertEquals("\"ab\"", s.content)
    }

    @Test
    fun `toggle line comment adds then removes the prefix`() {
        val s = session("val x = 1")
        s.setCaret(0, 0)
        s.toggleLineComment()
        assertEquals("// val x = 1", s.content)
        s.toggleLineComment()
        assertEquals("val x = 1", s.content)
    }

    @Test
    fun `duplicate line copies the current line below`() {
        val s = session("abc\ndef")
        s.setCaret(0, 1)
        s.duplicateLine()
        assertEquals("abc\nabc\ndef", s.content)
    }

    @Test
    fun `move line down swaps with the line below`() {
        val s = session("abc\ndef")
        s.setCaret(0, 1)
        s.moveLineDown()
        assertEquals("def\nabc", s.content)
        assertEquals(TextPosition(1, 1), s.caret)
    }

    @Test
    fun `add next occurrence creates a second selection`() {
        val s = session("foo bar foo")
        s.setCaret(0, 1)
        s.addNextOccurrence()
        assertEquals(2, s.allSelections.size)
    }

    @Test
    fun `inserting with multiple selections edits every caret`() {
        val s = session("foo\nfoo")
        s.setCaret(0, 1)
        s.addNextOccurrence()
        s.insertText("X")
        assertEquals("X\nX", s.content)
    }

    @Test
    fun `find all returns offset matches`() {
        val s = session("foo bar foo")
        val matches = s.findAll("foo", SearchOptions())
        assertEquals(listOf(0 to 3, 8 to 11), matches.map { it.start to it.end })
    }

    @Test
    fun `select match highlights the result`() {
        val s = session("hello world")
        val match = s.findAll("world", SearchOptions()).first()
        s.selectMatch(match)
        assertEquals(TextPosition(0, 6), s.selection.start)
        assertEquals(TextPosition(0, 11), s.selection.end)
    }

    @Test
    fun `replace match swaps one occurrence`() {
        val s = session("foo bar foo")
        val match = s.findAll("foo", SearchOptions()).first()
        s.replaceMatch(match, "baz")
        assertEquals("baz bar foo", s.content)
        assertTrue(s.canUndo)
    }

    @Test
    fun `replace all swaps every occurrence and is one undo step`() {
        val s = session("foo foo foo")
        assertEquals(3, s.replaceAll("foo", SearchOptions(), "x"))
        assertEquals("x x x", s.content)
        s.undo()
        assertEquals("foo foo foo", s.content)
        s.redo()
        assertEquals("x x x", s.content)
    }

    @Test
    fun `replace all with regex matches patterns`() {
        val s = session("a1 b2 c3")
        assertEquals(3, s.replaceAll("""\d""", SearchOptions(regex = true), "#"))
        assertEquals("a# b# c#", s.content)
        s.undo()
        assertEquals("a1 b2 c3", s.content)
    }

    @Test
    fun `replace all with whole word skips partial matches`() {
        val s = session("foo foobar foo")
        assertEquals(2, s.replaceAll("foo", SearchOptions(wholeWord = true), "x"))
        assertEquals("x foobar x", s.content)
    }

    @Test
    fun `replace all returns zero when nothing matches`() {
        val s = session("abc")
        assertEquals(0, s.replaceAll("zzz", SearchOptions(), "x"))
        assertEquals("abc", s.content)
    }

    @Test
    fun `word index reflects the buffer contents`() {
        val s = session("foo bar foo")
        assertTrue(s.wordIndex().matches("fo").contains("foo"))
        s.insertText(" baz")
        assertTrue(s.wordIndex().matches("ba").contains("baz"))
    }
}
