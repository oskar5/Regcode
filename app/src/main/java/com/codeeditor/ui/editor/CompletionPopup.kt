package com.codeeditor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.codeeditor.core.autocomplete.CompletionItem
import com.codeeditor.ui.theme.EditorColors

/** Popup state for the completion list. */
data class CompletionState(
    val visible: Boolean = false,
    val items: List<CompletionItem> = emptyList(),
    val filterText: String = "",
    val selectedIndex: Int = 0,
    val caretOffset: Int = -1
)

/** Floating suggestion list anchored to the bottom of the editor pane. */
@Composable
fun CompletionPopup(
    state: CompletionState,
    colors: EditorColors,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.visible || state.items.isEmpty()) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = colors.gutterBackground,
        border = BorderStroke(1.dp, colors.currentLineBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .width(320.dp)
                .heightIn(max = 200.dp)
        ) {
            items(count = state.items.size) { index ->
                val item = state.items[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (index == state.selectedIndex) colors.selection else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.foreground
                    )
                    item.detail?.let { detail ->
                        Text(
                            text = "  $detail",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.gutterForeground
                        )
                    }
                }
            }
        }
    }
}