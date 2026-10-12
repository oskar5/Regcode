package com.codeeditor.core.editor.highlight

/**
 * Central catalogue of supported languages. The UI asks for a highlighter by
 * file name; adding a language is a matter of appending one [LanguageDefinition]
 * here (or registering a TextMate / Tree-sitter backed [SyntaxHighlighter]).
 */
object LanguageRegistry {

    private val plainText = LanguageDefinition(
        id = "plaintext",
        displayName = "Plain Text",
        extensions = emptySet(),
        lineComment = null,
        blockCommentStart = null,
        blockCommentEnd = null,
        strings = emptyList()
    )

    private val definitions: List<LanguageDefinition> = listOf(
        kotlin(),
        java(),
        cLike("c", "C", setOf("c", "h")),
        cLike("cpp", "C++", setOf("cpp", "cc", "cxx", "hpp", "hh", "hxx")),
        python(),
        javascript(),
        typescript(),
        html(),
        css(),
        json(),
        markdown(),
        bash()
    )

    private val byId: Map<String, LanguageDefinition> =
        (definitions + plainText).associateBy { it.id }

    private val byExtension: Map<String, LanguageDefinition> = buildMap {
        for (def in definitions) {
            for (ext in def.extensions) put(ext.lowercase(), def)
        }
    }

    val all: List<LanguageDefinition> = definitions

    fun plainText(): LanguageDefinition = plainText

    fun byId(id: String): LanguageDefinition = byId[id] ?: plainText

    fun forFileName(fileName: String?): LanguageDefinition {
        if (fileName.isNullOrBlank()) return plainText
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return byExtension[ext] ?: plainText
    }

    fun highlighterFor(language: LanguageDefinition): SyntaxHighlighter =
        ScannerSyntaxHighlighter(language)

    fun highlighterForFileName(fileName: String?): SyntaxHighlighter =
        highlighterFor(forFileName(fileName))

    // ----------------------------------------------------------------- definitions

    private fun kotlin() = LanguageDefinition(
        id = "kotlin",
        displayName = "Kotlin",
        extensions = setOf("kt", "kts"),
        keywords = setOf(
            "package", "import", "class", "interface", "object", "fun", "val", "var",
            "typealias", "constructor", "init", "this", "super", "if", "else", "when",
            "for", "while", "do", "return", "break", "continue", "throw", "try", "catch",
            "finally", "as", "is", "in", "out", "by", "where", "suspend", "inline",
            "noinline", "crossinline", "reified", "operator", "infix", "external",
            "open", "final", "abstract", "sealed", "data", "enum", "annotation",
            "override", "private", "protected", "public", "internal", "lateinit",
            "const", "companion", "get", "set", "field", "expect", "actual", "vararg",
            "tailrec", "suspend", "dynamic", "value"
        ),
        types = setOf(
            "Int", "Long", "Short", "Byte", "Float", "Double", "Boolean", "Char",
            "String", "Unit", "Any", "Nothing", "Array", "List", "MutableList", "Map",
            "MutableMap", "Set", "MutableSet", "Pair", "Triple", "Sequence", "IntArray",
            "LongArray", "BooleanArray", "Comparable", "Iterable", "Collection"
        ),
        constants = setOf("true", "false", "null"),
        lineComment = "//",
        blockCommentStart = "/*",
        blockCommentEnd = "*/",
        strings = listOf(StringRule("\"\"\"", "\"\"\""), StringRule("\""), StringRule("'"))
    )

    private fun java() = LanguageDefinition(
        id = "java",
        displayName = "Java",
        extensions = setOf("java"),
        keywords = setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new", "package",
            "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient",
            "try", "void", "volatile", "while", "var", "record", "sealed", "permits",
            "yield"
        ),
        types = setOf(
            "String", "Integer", "Long", "Double", "Float", "Boolean", "Character",
            "Object", "List", "ArrayList", "Map", "HashMap", "Set", "HashSet", "Optional"
        ),
        constants = setOf("true", "false", "null"),
        lineComment = "//",
        blockCommentStart = "/*",
        blockCommentEnd = "*/",
        strings = listOf(StringRule("\"\"\"", "\"\"\""), StringRule("\""), StringRule("'"))
    )

    private fun cLike(id: String, name: String, exts: Set<String>) = LanguageDefinition(
        id = id,
        displayName = name,
        extensions = exts,
        keywords = setOf(
            "auto", "break", "case", "const", "continue", "default", "do", "else", "enum",
            "extern", "for", "goto", "if", "register", "return", "sizeof", "static",
            "struct", "switch", "typedef", "union", "volatile", "while", "class",
            "namespace", "template", "typename", "using", "new", "delete", "public",
            "private", "protected", "virtual", "override", "final", "constexpr",
            "noexcept", "nullptr", "this", "operator", "friend", "inline", "throw",
            "try", "catch", "dynamic_cast", "static_cast", "reinterpret_cast",
            "const_cast", "explicit", "mutable", "thread_local"
        ),
        types = setOf(
            "bool", "char", "int", "long", "short", "float", "double", "void", "signed",
            "unsigned", "size_t", "uint8_t", "uint16_t", "uint32_t", "uint64_t", "int8_t",
            "int16_t", "int32_t", "int64_t", "string", "vector", "map", "set", "size_t"
        ),
        constants = setOf("true", "false", "NULL", "nullptr"),
        lineComment = "//",
        blockCommentStart = "/*",
        blockCommentEnd = "*/",
        strings = listOf(StringRule("\""), StringRule("'"))
    )

    private fun python() = LanguageDefinition(
        id = "python",
        displayName = "Python",
        extensions = setOf("py", "pyw"),
        keywords = setOf(
            "and", "as", "assert", "async", "await", "break", "class", "continue", "def",
            "del", "elif", "else", "except", "finally", "for", "from", "global", "if",
            "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise",
            "return", "try", "while", "with", "yield", "match", "case"
        ),
        types = setOf(
            "int", "float", "str", "bool", "list", "dict", "tuple", "set", "bytes",
            "object", "NoneType", "Exception", "self"
        ),
        constants = setOf("True", "False", "None", "self", "cls"),
        lineComment = "#",
        blockCommentStart = null,
        blockCommentEnd = null,
        strings = listOf(
            StringRule("\"\"\"", "\"\"\""),
            StringRule("'''", "'''"),
            StringRule("\""),
            StringRule("'")
        )
    )

    private fun javascript() = LanguageDefinition(
        id = "javascript",
        displayName = "JavaScript",
        extensions = setOf("js", "jsx", "mjs", "cjs"),
        keywords = setOf(
            "var", "let", "const", "function", "return", "if", "else", "for", "while",
            "do", "switch", "case", "default", "break", "continue", "new", "delete",
            "typeof", "instanceof", "in", "of", "this", "super", "class", "extends",
            "static", "get", "set", "async", "await", "yield", "try", "catch", "finally",
            "throw", "import", "export", "from", "as", "void", "with", "debugger"
        ),
        types = setOf(
            "Object", "Array", "String", "Number", "Boolean", "Function", "Date",
            "RegExp", "Map", "Set", "Promise", "Symbol", "Error", "JSON"
        ),
        constants = setOf("true", "false", "null", "undefined", "NaN", "Infinity", "this"),
        lineComment = "//",
        blockCommentStart = "/*",
        blockCommentEnd = "*/",
        strings = listOf(StringRule("`"), StringRule("\""), StringRule("'"))
    )

    private fun typescript() = javascript().copy(
        id = "typescript",
        displayName = "TypeScript",
        extensions = setOf("ts", "tsx", "mts", "cts"),
        keywords = javascript().keywords + setOf(
            "interface", "type", "enum", "namespace", "declare", "abstract", "implements",
            "readonly", "public", "private", "protected", "override", "satisfies",
            "keyof", "infer", "is", "asserts"
        ),
        types = javascript().types + setOf(
            "any", "unknown", "never", "void", "number", "string", "boolean", "symbol",
            "bigint", "Record", "Partial", "Readonly", "Pick", "Omit"
        )
    )

    private fun html() = LanguageDefinition(
        id = "html",
        displayName = "HTML",
        extensions = setOf("html", "htm", "xhtml"),
        keywords = emptySet(),
        lineComment = null,
        blockCommentStart = "<!--",
        blockCommentEnd = "-->",
        strings = listOf(StringRule("\""), StringRule("'"))
    )

    private fun css() = LanguageDefinition(
        id = "css",
        displayName = "CSS",
        extensions = setOf("css", "scss", "less"),
        keywords = setOf(
            "important", "media", "import", "keyframes", "font-face", "supports",
            "from", "to", "and", "not", "only"
        ),
        lineComment = "//",
        blockCommentStart = "/*",
        blockCommentEnd = "*/",
        strings = listOf(StringRule("\""), StringRule("'"))
    )

    private fun json() = LanguageDefinition(
        id = "json",
        displayName = "JSON",
        extensions = setOf("json", "jsonc"),
        keywords = emptySet(),
        constants = setOf("true", "false", "null"),
        lineComment = null,
        blockCommentStart = "/*",
        blockCommentEnd = "*/",
        strings = listOf(StringRule("\""))
    )

    private fun markdown() = LanguageDefinition(
        id = "markdown",
        displayName = "Markdown",
        extensions = setOf("md", "markdown", "mdown"),
        keywords = emptySet(),
        lineComment = null,
        blockCommentStart = "<!--",
        blockCommentEnd = "-->",
        strings = listOf(StringRule("`", "`"), StringRule("\"", "\""))
    )

    private fun bash() = LanguageDefinition(
        id = "bash",
        displayName = "Shell",
        extensions = setOf("sh", "bash", "zsh", "bashrc", "profile"),
        keywords = setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done",
            "case", "esac", "in", "function", "select", "time", "return", "break",
            "continue", "local", "export", "readonly", "declare", "unset", "shift",
            "source", "alias", "echo", "exit", "eval", "exec", "trap", "set"
        ),
        types = setOf("int", "string", "array"),
        constants = setOf("true", "false"),
        lineComment = "#",
        blockCommentStart = null,
        blockCommentEnd = null,
        strings = listOf(StringRule("\""), StringRule("'"), StringRule("`"))
    )
}
