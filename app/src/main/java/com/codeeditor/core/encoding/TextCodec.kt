package com.codeeditor.core.encoding

import com.codeeditor.core.editor.LineEnding
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Result of decoding a byte array into text. */
data class DecodedText(
    val text: String,
    val encoding: TextEncoding,
    val hasBom: Boolean,
    val lineEnding: LineEnding
)

/**
 * Decodes and encodes file contents.
 *
 * Detection order: byte-order mark, then strict UTF-8 validation, otherwise a
 * Windows-1251 (Cyrillic) fallback which is the most common legacy encoding for
 * this audience.
 */
object TextCodec {

    fun decode(bytes: ByteArray): DecodedText {
        val bom = detectBom(bytes)
        val bodyStart = bom.bomLength
        val body = if (bodyStart > 0) bytes.copyOfRange(bodyStart, bytes.size) else bytes

        val decoded: Pair<String, TextEncoding> = when {
            bom.encoding != null -> decodeStrict(body, bom.encoding) to bom.encoding
            else -> decodeAuto(body)
        }
        return DecodedText(
            text = decoded.first,
            encoding = decoded.second,
            hasBom = bom.encoding != null,
            lineEnding = detectLineEnding(decoded.first)
        )
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

    private fun detectLineEnding(text: String): LineEnding {
        val idx = text.indexOf('\n')
        return if (idx > 0 && text[idx - 1] == '\r') LineEnding.CRLF else LineEnding.LF
    }

    private fun normalizeLineEndings(text: String, lineEnding: LineEnding): String {
        val lf = text.replace("\r\n", "\n").replace('\r', '\n')
        return if (lineEnding == LineEnding.CRLF) lf.replace("\n", "\r\n") else lf
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
