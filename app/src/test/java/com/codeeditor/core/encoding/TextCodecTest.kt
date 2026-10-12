package com.codeeditor.core.encoding

import com.codeeditor.core.editor.LineEnding
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCodecTest {

    @Test
    fun `decodes plain utf8`() {
        val decoded = TextCodec.decode("héllo".toByteArray(Charsets.UTF_8))
        assertEquals("héllo", decoded.text)
        assertEquals(TextEncoding.UTF_8, decoded.encoding)
        assertFalse(decoded.hasBom)
    }

    @Test
    fun `detects utf8 bom`() {
        val bytes = TextEncoding.UTF_8.bom + "hi".toByteArray(Charsets.UTF_8)
        val decoded = TextCodec.decode(bytes)
        assertEquals("hi", decoded.text)
        assertEquals(TextEncoding.UTF_8, decoded.encoding)
        assertTrue(decoded.hasBom)
    }

    @Test
    fun `detects utf16le bom`() {
        val bytes = TextEncoding.UTF_16LE.bom + "Привіт".toByteArray(Charsets.UTF_16LE)
        val decoded = TextCodec.decode(bytes)
        assertEquals("Привіт", decoded.text)
        assertEquals(TextEncoding.UTF_16LE, decoded.encoding)
    }

    @Test
    fun `falls back to windows-1251 for legacy cyrillic`() {
        val bytes = "Привіт".toByteArray(Charset.forName("windows-1251"))
        val decoded = TextCodec.decode(bytes)
        assertEquals("Привіт", decoded.text)
        assertEquals(TextEncoding.WINDOWS_1251, decoded.encoding)
    }

    @Test
    fun `detects crlf line endings`() {
        val decoded = TextCodec.decode("a\r\nb".toByteArray(Charsets.UTF_8))
        assertEquals(LineEnding.CRLF, decoded.lineEnding)
    }

    @Test
    fun `encodes with crlf conversion`() {
        val bytes = TextCodec.encode("a\nb\n", TextEncoding.UTF_8, LineEnding.CRLF, withBom = false)
        assertEquals("a\r\nb\r\n", String(bytes, Charsets.UTF_8))
    }

    @Test
    fun `round trips windows-1251`() {
        val bytes = TextCodec.encode("Тест", TextEncoding.WINDOWS_1251, LineEnding.LF, withBom = false)
        assertEquals("Тест", TextCodec.decode(bytes).text)
    }
}
