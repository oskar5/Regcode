package com.codeeditor.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.codeeditor.R
import com.codeeditor.domain.model.EditorSettings
import com.codeeditor.domain.model.ThemeMode
import com.codeeditor.ui.theme.EditorColors
import com.codeeditor.ui.theme.EditorSchemes

/** Callbacks raised by [SettingsSheet]; keeps it decoupled from the ViewModel. */
data class SettingsCallbacks(
    val onThemeMode: (ThemeMode) -> Unit,
    val onScheme: (String) -> Unit,
    val onFontFamily: (String) -> Unit,
    val onFontSize: (Float) -> Unit,
    val onToggleLineNumbers: () -> Unit,
    val onToggleWordWrap: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: EditorSettings,
    colors: EditorColors,
    onDismiss: () -> Unit,
    callbacks: SettingsCallbacks
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.gutterBackground
    ) {
        SettingsContent(
            settings = settings,
            colors = colors,
            callbacks = callbacks
        )
    }
}

/** Plain scrollable column rendering the settings form; reused by UI tests. */
@Composable
fun SettingsContent(
    settings: EditorSettings,
    colors: EditorColors,
    callbacks: SettingsCallbacks
) {
    val dark = settings.themeMode != ThemeMode.LIGHT

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.foreground
            )

            SectionLabel(stringResource(R.string.settings_theme), colors)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { callbacks.onThemeMode(mode) },
                        label = { Text(themeLabel(mode)) }
                    )
                }
            }

            SectionLabel(stringResource(R.string.settings_scheme), colors)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                EditorSchemes.available(dark).forEach { scheme ->
                    FilterChip(
                        selected = settings.editorSchemeId == scheme.id,
                        onClick = { callbacks.onScheme(scheme.id) },
                        label = { Text(scheme.displayName) }
                    )
                }
            }

            SectionLabel(stringResource(R.string.settings_font), colors)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                FontFamilyId.entries.forEach { font ->
                    FilterChip(
                        selected = settings.fontFamilyId == font.id,
                        onClick = { callbacks.onFontFamily(font.id) },
                        label = { Text(stringResource(font.labelRes)) }
                    )
                }
            }

            SectionLabel(stringResource(R.string.settings_font_size), colors)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = settings.fontSizeSp,
                    onValueChange = callbacks.onFontSize,
                    valueRange = 10f..28f,
                    steps = 17,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = settings.fontSizeSp.toInt().toString() + " sp",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.foreground,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            SwitchRow(
                label = stringResource(R.string.settings_line_numbers),
                checked = settings.showLineNumbers,
                colors = colors,
                onCheckedChange = { callbacks.onToggleLineNumbers() }
            )
            SwitchRow(
                label = stringResource(R.string.settings_word_wrap),
                checked = settings.wordWrap,
                colors = colors,
                onCheckedChange = { callbacks.onToggleWordWrap() }
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, colors: EditorColors) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = colors.gutterForeground
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    colors: EditorColors,
    onCheckedChange: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.currentLineBackground)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.foreground
        )
        Spacer(Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { onCheckedChange() }
        )
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }
)

/** Resolved editor font options (generic system families plus a bundled ligature font). */
enum class FontFamilyId(val id: String, val labelRes: Int) {
    JETBRAINS("jetbrains", R.string.settings_font_jetbrains),
    MONO("monospace", R.string.settings_font_mono),
    SANS("sans", R.string.settings_font_sans),
    SERIF("serif", R.string.settings_font_serif)
}