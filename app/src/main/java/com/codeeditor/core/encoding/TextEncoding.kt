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
    WINDOWS_1252("Windows-1252", "windows-1252", byteArrayOf()),
    KOI8_U("KOI8-U", "KOI8-U", byteArrayOf()),
    ISO_8859_1("ISO-8859-1", "ISO-8859-1", byteArrayOf());

    fun charset(): Charset = try {
        Charset.forName(charsetName)
    } catch (_: Exception) {
        Charsets.ISO_8859_1
    }

    /** True when this encoding has a defined byte-order mark. */
    val supportsBom: Boolean get() = bom.isNotEmpty()

    companion object {
        /** Encodings offered in the "reopen with encoding" picker, in menu order. */
        val selectable: List<TextEncoding> = listOf(
            UTF_8, UTF_16LE, UTF_16BE, WINDOWS_1251, WINDOWS_1252, KOI8_U, ISO_8859_1
        )

        fun byNameOrNull(name: String?): TextEncoding? =
            name?.let { value -> entries.firstOrNull { it.name == value } }
    }
}
