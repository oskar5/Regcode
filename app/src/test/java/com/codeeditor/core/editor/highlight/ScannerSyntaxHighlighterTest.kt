package com.codeeditor.core.editor.highlight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerSyntaxHighlighterTest {

    private val kotlin = LanguageRegistry.highlighterForFileName("test.kt")

    @Test
    fun `detects the language from extension`() {
        assertEquals("kotlin", LanguageRegistry.forFileName("Main.kt").id)
        assertEquals("python", LanguageRegistry.forFileName("script.py").id)
        assertEquals("typescript", LanguageRegistry.forFileName("app.tsx").id)
        assertEquals("plaintext", LanguageRegistry.forFileName("LICENSE").id)
    }

    @Test
    fun `classifies keywords and numbers`() {
        val result = kotlin.highlight("val x = 42", HlState.NORMAL)
        assertTrue(result.tokens.any { it.type == TokenType.KEYWORD })
        assertTrue(result.tokens.any { it.type == TokenType.NUMBER })
    }

    @Test
    fun `highlights string literals`() {
        val result = kotlin.highlight("val s = \"hello\"", HlState.NORMAL)
        val string = result.tokens.first { it.type == TokenType.STRING }
        assertEquals("\"hello\"", "val s = \"hello\"".substring(string.start, string.end))
    }

    @Test
    fun `carries block comment state across lines`() {
        val first = kotlin.highlight("val a = 1 /* start", HlState.NORMAL)
        assertEquals(HlState.BLOCK_COMMENT, first.endState)

        val second = kotlin.highlight("still comment */ val b = 2", first.endState)
        assertEquals(HlState.NORMAL, second.endState)
        assertEquals(TokenType.COMMENT, second.tokens.first().type)
    }

    @Test
    fun `line comment consumes rest of line`() {
        val result = kotlin.highlight("val x = 1 // note", HlState.NORMAL)
        val comment = result.tokens.first { it.type == TokenType.COMMENT }
        assertEquals("// note", "val x = 1 // note".substring(comment.start, comment.end))
    }
}
