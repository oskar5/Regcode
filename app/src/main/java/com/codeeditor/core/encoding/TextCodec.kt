package com.codeeditor.core.encoding

import com.codeeditor.core.editor.LineEnding
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.MalformedInputException
import java.nio.charset.UnmappableCharacterException

/** Result of decoding a byte array into text. */
data class DecodedText(
    val text: String,
    val encoding: TextEncoding,
    val hasBom: Boolean,
    val lineEnding: LineEnding,
    /**
     * True when [encoding] was guessed rather than declared (no BOM and not
     * valid UTF-8). Callers should surface this so the user can correct it.
     */
    val usedFallback: Boolean = false
)

/** Raised when bytes cannot be decoded with the requested encoding. */
class TextDecodingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Decodes and encodes file contents.
 *
 * Detection order: byte-order mark, then a NUL-pattern heuristic for UTF-16
 * without a BOM, then strict UTF-8 validation, otherwise a Windows-1251
 * (Cyrillic) fallback which is the most common legacy encoding for this
 * audience. Guesses are reported through [DecodedText.usedFallback].
 */
object TextCodec {

    fun decode(bytes: ByteArray): DecodedText {
        val bom = detectBom(bytes)
        if (bom.encoding != null) {
            val body = bytes.copyOfRange(bom.bomLength, bytes.size)
            val text = decodeStrict(body, bom.encoding)
            return DecodedText(
                text = text,
                encoding = bom.encoding,
                hasBom = true,
                lineEnding = LineEnding.detect(text)
            )
        }

        val utf16 = guessUtf16(bytes)
        if (utf16 != null) {
            val text = runCatching { decodeStrict(bytes, utf16) }.getOrNull()
            if (text != null) {
                return DecodedText(
                    text = text,
                    encoding = utf16,
                    hasBom = false,
                    lineEnding = LineEnding.detect(text),
                    usedFallback = true
                )
            }
        }

        val (text, encoding) = decodeAuto(bytes)
        return DecodedText(
            text = text,
            encoding = encoding,
            hasBom = false,
            lineEnding = LineEnding.detect(text),
            usedFallback = encoding != TextEncoding.UTF_8
        )
    }

    /**
     * Decodes [bytes] using a user-selected [encoding]. Throws
     * [TextDecodingException] when the bytes are not valid in that encoding, so
     * the caller can keep the current document untouched instead of silently
     * corrupting it.
     */
    fun decodeWith(bytes: ByteArray, encoding: TextEncoding, hasBom: Boolean): DecodedText {
        val body = if (hasBom && encoding.supportsBom && startsWithBom(bytes, encoding)) {
            bytes.copyOfRange(encoding.bom.size, bytes.size)
        } else {
            bytes
        }
        val text = try {
            decodeStrict(body, encoding)
        } catch (e: CharacterCodingException) {
            throw TextDecodingException(describe(e), e)
        }
        return DecodedText(
            text = text,
            encoding = encoding,
            hasBom = hasBom && encoding.supportsBom,
            lineEnding = LineEnding.detect(text)
        )
    }

    /** Decodes at most [maxChars] characters, for the encoding-preview dialog. */
    fun preview(
        bytes: ByteArray,
        encoding: TextEncoding,
        hasBom: Boolean,
        maxChars: Int = 400
    ): String {
        val body = if (hasBom && encoding.supportsBom && startsWithBom(bytes, encoding)) {
            bytes.copyOfRange(encoding.bom.size, bytes.size)
        } else {
            bytes
        }
        return try {
            val limit = minOf(body.size, maxChars * 4 + 8)
            val slice = if (limit < body.size) body.copyOfRange(0, limit) else body
            val decoder = encoding.charset().newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
            decoder.decode(ByteBuffer.wrap(slice)).toString().take(maxChars)
        } catch (_: CharacterCodingException) {
            ""
        }
    }

    fun encode(
        text: String,
        encoding: TextEncoding,
        lineEnding: LineEnding,
        withBom: Boolean
    ): ByteArray {
        val normalized = normalizeLineEndings(text, lineEnding)
        val body = normalized.toByteArray(encoding.charset())
        return if (withBom && encoding.bom.isNotEmpty()) encoding.bom + body else body
    }

    private fun describe(e: CharacterCodingException): String = when (e) {
        is MalformedInputException -> "пошкоджені або некоректні байти"
        is UnmappableCharacterException -> "символи відсутні у цій кодовій таблиці"
        else -> e.message ?: "помилка декодування"
    }

    private fun startsWithBom(bytes: ByteArray, encoding: TextEncoding): Boolean {
        if (bytes.size < encoding.bom.size) return false
        for (i in encoding.bom.indices) if (bytes[i] != encoding.bom[i]) return false
        return true
    }

    private fun decodeAuto(bytes: ByteArray): Pair<String, TextEncoding> {
        return try {
            decodeStrict(bytes, TextEncoding.UTF_8) to TextEncoding.UTF_8
        } catch (_: CharacterCodingException) {
            val legacy = if (canUse("windows-1251")) {
                TextEncoding.WINDOWS_1251
            } else {
                TextEncoding.ISO_8859_1
            }
            (bytes.toString(legacy.charset())) to legacy
        }
    }

    private fun decodeStrict(bytes: ByteArray, encoding: TextEncoding): String {
        val decoder = encoding.charset().newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }

    /**
     * Guesses UTF-16LE/BE for BOM-less files by looking for NUL bytes that
     * consistently fall on one byte parity, which is what ASCII-heavy UTF-16
     * text looks like. Returns null when the pattern is not convincing.
     */
    private fun guessUtf16(bytes: ByteArray): TextEncoding? {
        if (bytes.size < 4) return null
        val sample = minOf(bytes.size, 1024)
        var evenNul = 0
        var oddNul = 0
        for (i in 0 until sample) {
            if (bytes[i] == 0.toByte()) {
                if (i % 2 == 0) evenNul++ else oddNul++
            }
        }
        val threshold = maxOf(2, sample / 8)
        return when {
            oddNul >= threshold && oddNul >= evenNul * 8 -> TextEncoding.UTF_16LE
            evenNul >= threshold && evenNul >= oddNul * 8 -> TextEncoding.UTF_16BE
            else -> null
        }
    }

    private fun normalizeLineEndings(text: String, lineEnding: LineEnding): String {
        val lf = text.replace("\r\n", "\n").replace('\r', '\n')
        return when (lineEnding) {
            LineEnding.LF -> lf
            LineEnding.CRLF -> lf.replace("\n", "\r\n")
            LineEnding.CR -> lf.replace("\n", "\r")
        }
    }

    private fun canUse(name: String): Boolean = try {
        java.nio.charset.Charset.isSupported(name)
    } catch (_: Exception) {
        false
    }

    private class BomInfo(val encoding: TextEncoding?, val bomLength: Int)

    private fun detectBom(bytes: ByteArray): BomInfo {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return BomInfo(TextEncoding.UTF_8, 3)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return BomInfo(TextEncoding.UTF_16LE, 2)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return BomInfo(TextEncoding.UTF_16BE, 2)
        }
        return BomInfo(null, 0)
    }
}
