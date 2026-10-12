package com.codeeditor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.codeeditor.R
import com.codeeditor.core.search.SearchOptions
import com.codeeditor.ui.theme.EditorColors

/** Transient UI state of the find/replace bar. */
data class FindState(
    val visible: Boolean = false,
    val replaceVisible: Boolean = false,
    val query: String = "",
    val replacement: String = "",
    val options: SearchOptions = SearchOptions(),
    val matchCount: Int = 0,
    val currentMatch: Int = -1
)

/** Callbacks the find bar raises; keeps it decoupled from the ViewModel. */
data class FindCallbacks(
    val onQueryChange: (String) -> Unit,
    val onReplacementChange: (String) -> Unit,
    val onToggleCaseSensitive: () -> Unit,
    val onToggleWholeWord: () -> Unit,
    val onToggleRegex: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onReplace: () -> Unit,
    val onReplaceAll: () -> Unit,
    val onClose: () -> Unit
)

@Composable
fun FindBar(
    state: FindState,
    colors: EditorColors,
    callbacks: FindCallbacks,
    modifier: Modifier = Modifier
) {
    if (!state.visible) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.gutterBackground)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query,
                onValueChange = callbacks.onQueryChange,
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                placeholder = { Text(stringResource(R.string.find_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { callbacks.onNext() }),
                trailingIcon = { Counter(state) }
            )
            IconButton(onClick = callbacks.onPrevious) {
                Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.find_previous), tint = colors.foreground)
            }
            IconButton(onClick = callbacks.onNext) {
                Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.find_next), tint = colors.foreground)
            }
            IconButton(onClick = callbacks.onClose) {
                Icon(Icons.Filled.Close, stringResource(R.string.find_close), tint = colors.foreground)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = state.options.caseSensitive,
                onClick = callbacks.onToggleCaseSensitive,
                label = { Text("Aa", style = MaterialTheme.typography.labelMedium) }
            )
            FilterChip(
                selected = state.options.wholeWord,
                onClick = callbacks.onToggleWholeWord,
                label = { Text("ab", style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.padding(start = 6.dp)
            )
            FilterChip(
                selected = state.options.regex,
                onClick = callbacks.onToggleRegex,
                label = { Text(".*", style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.padding(start = 6.dp)
            )
        }
        if (state.replaceVisible) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.replacement,
                    onValueChange = callbacks.onReplacementChange,
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                    placeholder = { Text(stringResource(R.string.find_replace_hint)) }
                )
                TextButton(
                    onClick = callbacks.onReplace,
                    enabled = state.matchCount > 0
                ) {
                    Text(stringResource(R.string.find_replace))
                }
                TextButton(
                    onClick = callbacks.onReplaceAll,
                    enabled = state.matchCount > 0
                ) {
                    Text(stringResource(R.string.find_replace_all))
                }
            }
        }
    }
}

@Composable
private fun Counter(state: FindState) {
    if (state.query.isEmpty()) return
    val text = if (state.matchCount == 0) {
        stringResource(R.string.find_counter, 0, 0)
    } else {
        stringResource(R.string.find_counter, state.currentMatch + 1, state.matchCount)
    }
    Text(text = text, style = MaterialTheme.typography.labelSmall)
}
