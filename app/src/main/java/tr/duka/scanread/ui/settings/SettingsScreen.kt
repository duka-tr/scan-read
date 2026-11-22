package tr.duka.scanread.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tr.duka.scanread.R
import tr.duka.scanread.core.settings.AppSettings
import tr.duka.scanread.ui.MainViewModel
import tr.duka.scanread.ui.components.ScreenScaffold
import kotlin.math.roundToInt

private val ComicFont = FontFamily(Font(R.font.comic_relief))
private const val PREVIEW_TEXT_SIZE_SP = 16f

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSupport: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings ?: return
    val context = LocalContext.current

    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            SectionHeader(stringResource(R.string.settings_section_button))
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_button_size), style = MaterialTheme.typography.bodyLarge)
                ButtonSizeSelector(current.buttonSize, viewModel::setButtonSize)
                Spacer(modifier = Modifier.height(8.dp))
                PercentSlider(
                    label = stringResource(R.string.settings_button_opacity),
                    value = current.buttonOpacity,
                    range = AppSettings.MIN_BUTTON_OPACITY..1f,
                    onChangeFinished = viewModel::setButtonOpacity,
                )
            }

            SectionHeader(stringResource(R.string.settings_section_translation))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                PercentSlider(
                    label = stringResource(R.string.settings_text_size),
                    value = current.textScale,
                    range = AppSettings.MIN_TEXT_SCALE..AppSettings.MAX_TEXT_SCALE,
                    onChangeFinished = viewModel::setTextScale,
                    preview = { scale ->
                        Text(
                            text = stringResource(R.string.settings_text_preview),
                            fontFamily = ComicFont,
                            fontSize = (PREVIEW_TEXT_SIZE_SP * scale).sp,
                        )
                    },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_skip_sound_effects)) },
                supportingContent = { Text(stringResource(R.string.settings_skip_sound_effects_summary)) },
                trailingContent = {
                    Switch(checked = current.skipSoundEffects, onCheckedChange = viewModel::setSkipSoundEffects)
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_section_app))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                NavigationItem(stringResource(R.string.settings_app_language)) {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                }
            }
            NavigationItem(stringResource(R.string.home_support), onOpenSupport)
            NavigationItem(stringResource(R.string.about_title), onOpenAbout)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ButtonSizeSelector(selected: AppSettings.ButtonSize, onSelect: (AppSettings.ButtonSize) -> Unit) {
    val labels = mapOf(
        AppSettings.ButtonSize.SMALL to R.string.settings_size_small,
        AppSettings.ButtonSize.MEDIUM to R.string.settings_size_medium,
        AppSettings.ButtonSize.LARGE to R.string.settings_size_large,
    )
    val sizes = AppSettings.ButtonSize.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        sizes.forEachIndexed { index, size ->
            SegmentedButton(
                selected = size == selected,
                onClick = { onSelect(size) },
                shape = SegmentedButtonDefaults.itemShape(index, sizes.size),
            ) {
                Text(stringResource(labels.getValue(size)))
            }
        }
    }
}

/** A slider showing its value in percent; the setting is saved when the user lets go. */
@Composable
private fun PercentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChangeFinished: (Float) -> Unit,
    preview: (@Composable (Float) -> Unit)? = null,
) {
    var position by remember(value) { mutableFloatStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.settings_percent, (position * 100).roundToInt()), style = MaterialTheme.typography.bodyMedium)
    }
    Slider(
        value = position,
        onValueChange = { position = it },
        onValueChangeFinished = { onChangeFinished(position) },
        valueRange = range,
    )
    preview?.invoke(position)
}

@Composable
private fun NavigationItem(text: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
