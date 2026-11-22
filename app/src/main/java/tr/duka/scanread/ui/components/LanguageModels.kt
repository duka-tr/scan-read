package tr.duka.scanread.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tr.duka.scanread.R
import tr.duka.scanread.translate.opusmt.HighQualityModels
import tr.duka.scanread.ui.ModelState

private const val BYTES_PER_MB = 1_000_000L

/**
 * Language files for the selected pair: the high-quality model when one exists for the pair,
 * and the basic ML Kit files as a fallback (hidden once the high-quality model is ready).
 */
@Composable
fun LanguageModels(
    highQuality: HighQualityModels.State,
    basic: ModelState,
    onDownloadHighQuality: () -> Unit,
    onDeleteHighQuality: () -> Unit,
    onDownloadBasic: () -> Unit,
    onDeleteBasic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (highQuality.status != HighQualityModels.Status.UNSUPPORTED) {
            HighQualitySection(highQuality, onDownloadHighQuality, onDeleteHighQuality)
            if (highQuality.status == HighQualityModels.Status.READY) return@Column
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        }
        BasicSection(basic, onDownloadBasic, onDeleteBasic)
    }
}

@Composable
private fun HighQualitySection(state: HighQualityModels.State, onDownload: () -> Unit, onDelete: () -> Unit) {
    Text(text = stringResource(R.string.translation_high_quality_title), style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(4.dp))
    val status = when (state.status) {
        HighQualityModels.Status.READY -> stringResource(R.string.translation_model_ready)
        HighQualityModels.Status.DOWNLOADING ->
            stringResource(R.string.translation_high_quality_downloading, (state.progress * 100).toInt())
        HighQualityModels.Status.ERROR -> stringResource(R.string.translation_high_quality_error)
        HighQualityModels.Status.CHECKING -> stringResource(R.string.translation_model_checking)
        else -> stringResource(R.string.translation_high_quality_missing, (state.downloadSize / BYTES_PER_MB).toInt())
    }
    StatusText(status)
    when (state.status) {
        HighQualityModels.Status.MISSING, HighQualityModels.Status.ERROR -> {
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onDownload) { Text(stringResource(R.string.translation_action_download)) }
        }
        HighQualityModels.Status.DOWNLOADING -> {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
        }
        HighQualityModels.Status.READY -> {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.translation_action_delete)) }
        }
        else -> Unit
    }
}

@Composable
private fun BasicSection(state: ModelState, onDownload: () -> Unit, onDelete: () -> Unit) {
    Text(text = stringResource(R.string.translation_basic_title), style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(4.dp))
    val status = when (state) {
        ModelState.CHECKING -> R.string.translation_model_checking
        ModelState.MISSING -> R.string.translation_model_missing
        ModelState.DOWNLOADING -> R.string.translation_model_downloading
        ModelState.READY -> R.string.translation_model_ready
        ModelState.ERROR -> R.string.translation_model_error
    }
    StatusText(stringResource(status))
    when (state) {
        ModelState.MISSING, ModelState.ERROR -> {
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(onClick = onDownload) { Text(stringResource(R.string.translation_action_download)) }
        }
        ModelState.DOWNLOADING -> {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        ModelState.READY -> {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.translation_action_delete)) }
        }
        ModelState.CHECKING -> Unit
    }
}

@Composable
private fun StatusText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
