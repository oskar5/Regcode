package com.codeeditor.core.encoding

import java.nio.charset.Charset

/** Text encodings the editor can read and write. */
enum class TextEncoding(
    val displayName: String,
    val charsetName: String,
    val bom: ByteArray
) {
    UTF_8("UTF-8", "UTF-8", byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())),
    UTF_16LE("UTF-16 LE", "UTF-16LE", byteArrayOf(0xFF.toByte(), 0xFE.toByte())),
    UTF_16BE("UTF-16 BE", "UTF-16BE", byteArrayOf(0xFE.toByte(), 0xFF.toByte())),
    WINDOWS_1251("Windows-1251", "windows-1251", byteArrayOf()),
    ISO_8859_1("ISO-8859-1", "ISO-8859-1", byteArrayOf());

    fun charset(): Charset = try {
        Charset.forName(charsetName)
    } catch (_: Exception) {
        Charsets.ISO_8859_1
    }
}
