package com.codeeditor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.codeeditor.R
import com.codeeditor.ui.theme.EditorColors

/** Bottom status strip: cursor position, line endings and active language. */
@Composable
fun EditorStatusBar(state: EditorUiState, colors: EditorColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.gutterBackground)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.line_col, state.cursor.line + 1, state.cursor.column + 1),
            style = MaterialTheme.typography.labelSmall,
            color = colors.gutterActiveForeground
        )
        Box(Modifier.weight(1f))
        Text(
            text = if (state.lineEnding.sequence == "\r\n") "CRLF" else "LF",
            style = MaterialTheme.typography.labelSmall,
            color = colors.gutterForeground
        )
        Text(
            text = "  ${state.languageId}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.gutterForeground
        )
    }
}
