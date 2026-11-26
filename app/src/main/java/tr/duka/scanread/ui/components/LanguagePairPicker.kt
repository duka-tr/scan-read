package tr.duka.scanread.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tr.duka.scanread.R
import tr.duka.scanread.core.locale.LanguageNames
import tr.duka.scanread.translate.LanguagePair
import java.text.Collator
import java.util.Locale

/** A selectable language with its name in the screen's language. */
private data class LanguageOption(val code: String, val name: String)

/**
 * Source and target language selection. Names are shown and sorted in the screen's language, which
 * follows the app language setting (e.g. Turkish "Ç" right after "C").
 */
@Composable
fun LanguagePairPicker(
    pair: LanguagePair,
    sources: List<String>,
    targets: List<String>,
    onChange: (source: String, target: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val sourceOptions = remember(sources, locale) { optionsOf(sources, locale) }
    val targetOptions = remember(targets, locale) { optionsOf(targets, locale) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LanguageDropdown(
            label = stringResource(R.string.languages_source),
            options = sourceOptions,
            selected = pair.source,
            onSelect = { onChange(it, pair.target) },
        )
        LanguageDropdown(
            label = stringResource(R.string.languages_target),
            options = targetOptions.filter { it.code != pair.source },
            selected = pair.target,
            onSelect = { onChange(pair.source, it) },
        )
    }
}

private fun optionsOf(codes: List<String>, locale: Locale): List<LanguageOption> {
    val collator = Collator.getInstance(locale)
    return codes.map { LanguageOption(it, LanguageNames.displayName(it, locale)) }.sortedWith(compareBy(collator) { it.name })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    label: String,
    options: List<LanguageOption>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = options.firstOrNull { it.code == selected }?.name ?: selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        onSelect(option.code)
                        expanded = false
                    },
                )
            }
        }
    }
}
