package com.codeeditor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.codeeditor.ui.theme.EditorColors

/**
 * A compact horizontal row of common code symbols for on-screen (non-physical)
 * keyboards. Scrollable when the row is wider than the screen. Visibility is
 * controlled by the user through the editor settings.
 */
@Composable
fun KeyRow(
    colors: EditorColors,
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.gutterBackground)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        SYMBOLS.forEach { symbol ->
            Text(
                text = symbol,
                style = MaterialTheme.typography.titleMedium,
                color = colors.foreground,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onKey(symbol) }
                    .padding(horizontal = 9.dp, vertical = 6.dp)
            )
        }
    }
}

private val SYMBOLS = listOf(
    "{", "}", "(", ")", "[", "]", "<", ">",
    ";", ":", "=", ",", ".", "'", "\"",
    "+", "-", "*", "/", "&", "|", "!", "?",
    "%", "@", "#", "^", "~", "_", "\\", "$", "`"
)