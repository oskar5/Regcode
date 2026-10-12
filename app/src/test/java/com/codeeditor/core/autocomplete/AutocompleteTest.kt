package com.codeeditor.core.autocomplete

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WordIndexTest {

    @Test
    fun `collects repeated words and ranks by frequency`() {
        val index = WordIndex.fromText("foo bar foo baz")
        assertEquals(listOf("bar", "baz"), index.matches("b"))
        assertEquals(listOf("foo"), index.matches("fo"))
    }

    @Test
    fun `lookup is case-insensitive but keeps original spelling`() {
        val index = WordIndex.fromText("Apple apple")
        assertEquals(listOf("Apple"), index.matches("app"))
    }

    @Test
    fun `single character tokens are skipped`() {
        val index = WordIndex.fromText("a bc d ef")
        assertFalse(index.matches("ef").isEmpty())
        assertFalse(index.matches("a").contains("a"))
    }

    @Test
    fun `empty prefix yields nothing`() {
        assertEquals(emptyList(), WordIndex.fromText("anything").matches(""))
    }
}

class CompletionEngineTest {

    private fun prefix(line: String, column: Int) = CompletionEngine.prefixBeforeCaret(line, column)

    @Test
    fun `extracts the identifier fragment before the caret`() {
        assertEquals("na", prefix("val name = na", 14))
        assertEquals("foo", prefix("foo.bar", 3))
        assertEquals("", prefix("foo.bar", 4))
        assertEquals("x", prefix("xy", 1))
    }

    @Test
    fun `completes with keywords before buffer words`() {
        val index = WordIndex.fromText("foo foo bar")
        val provider = BufferCompletionProvider(index, keywords = setOf("fun", "val"))
        val context = CompletionContext(line = "f", caretColumn = 1, languageId = "kotlin", words = index)
        val items = CompletionEngine.complete("f", context, listOf(provider))
        assertEquals(listOf("fun", "foo"), items.map { it.label })
        assertEquals(listOf(CompletionKind.KEYWORD, CompletionKind.WORD), items.map { it.kind })
    }

    @Test
    fun `deduplicates a keyword and a buffer word`() {
        val index = WordIndex.fromText("val x val")
        val provider = BufferCompletionProvider(index, keywords = setOf("val"))
        val context = CompletionContext("", 0, "kotlin", index)
        val items = CompletionEngine.complete("va", context, listOf(provider))
        assertEquals(1, items.size)
        assertEquals("val", items.first().label)
        assertEquals(CompletionKind.KEYWORD, items.first().kind)
    }

    @Test
    fun `no providers means no results`() {
        val index = WordIndex.fromText("foo")
        val context = CompletionContext("f", 1, "kotlin", index)
        assertEquals(emptyList(), CompletionEngine.complete("f", context, emptyList()))
    }

    @Test
    fun `empty prefix produces no results`() {
        val index = WordIndex.fromText("foo")
        val context = CompletionContext("", 0, "kotlin", index)
        assertEquals(emptyList(), CompletionEngine.complete("", context, emptyList()))
    }
}