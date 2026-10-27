import com.codeeditor.core.editor.TextDocument
import com.codeeditor.core.editor.TextPosition

fun main() {
    val doc = TextDocument("a\rb")
    println(doc.lineCount)
    println(doc.positionAt(2))
}
