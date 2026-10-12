package com.codeeditor.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeeditor.ui.theme.EditorColors

/** Horizontally scrollable tab strip with dirty markers and a "new file" action. */
@Composable
fun EditorTabs(
    tabs: List<TabInfo>,
    activeTabId: String?,
    colors: EditorColors,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewFile: () -> Unit,
    compact: Boolean = false
) {
    val scroll = rememberScrollState()
    val barHeight = if (compact) 32.dp else 40.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .background(colors.gutterBackground)
            .horizontalScroll(scroll),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            TabChip(
                tab = tab,
                active = tab.id == activeTabId,
                colors = colors,
                onSelect = { onSelect(tab.id) },
                onClose = { onClose(tab.id) }
            )
        }
        IconButton(onClick = onNewFile, modifier = Modifier.size(barHeight)) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "New file",
                tint = colors.gutterActiveForeground
            )
        }
    }
}

@Composable
private fun TabChip(
    tab: TabInfo,
    active: Boolean,
    colors: EditorColors,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .background(if (active) colors.background else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (tab.isDirty) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.caret)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = tab.title,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
            color = if (active) colors.foreground else colors.gutterForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 2.dp)
        )
        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close tab",
                tint = colors.gutterForeground,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
