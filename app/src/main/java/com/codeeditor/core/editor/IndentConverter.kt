package com.codeeditor.core.editor

/**
 * Converts the leading whitespace of a single line between tabs and spaces.
 * The indentation before the first non-whitespace character is expanded using
 * [tabWidth] stops, then re-emitted as either tabs or spaces. Returns the new
 * line text when it differs from the original, or `null` when nothing changes.
 */
object IndentConverter {

    fun convertLine(line: String, useTabs: Boolean, tabWidth: Int): String? {
        val width = tabWidth.coerceIn(1, 16)
        var i = 0
        var column = 0
        while (i < line.length) {
            when (line[i]) {
                ' ' -> column++
                '\t' -> column += width - (column % width)
                else -> break
            }
            i++
        }
        if (i == 0) return null
        val indent = line.substring(0, i)
        val rest = line.substring(i)
        val rebuilt = buildString {
            if (useTabs) {
                repeat(column / width) { append('\t') }
                repeat(column % width) { append(' ') }
            } else {
                repeat(column) { append(' ') }
            }
        }
        return if (rebuilt == indent) null else rebuilt + rest
    }
}
