package com.scanread.ui.languages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanread.R
import com.scanread.ui.MainViewModel
import com.scanread.ui.components.LanguageModels
import com.scanread.ui.components.LanguagePairPicker
import com.scanread.ui.components.ScreenScaffold

/** Language pair selection and the language files for that pair. */
@Composable
fun LanguagesScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val pair by viewModel.languagePair.collectAsStateWithLifecycle()
    val basic by viewModel.basicModel.collectAsStateWithLifecycle()
    val highQuality by viewModel.highQualityModel.collectAsStateWithLifecycle()

    ScreenScaffold(title = stringResource(R.string.languages_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LanguagePairPicker(
                pair = pair,
                sources = viewModel.sourceLanguages,
                targets = viewModel.targetLanguages,
                onChange = viewModel::setLanguages,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.languages_models),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                LanguageModels(
                    highQuality = highQuality,
                    basic = basic,
                    onDownloadHighQuality = viewModel::downloadHighQualityModel,
                    onDeleteHighQuality = viewModel::deleteHighQualityModel,
                    onDownloadBasic = viewModel::downloadBasicModel,
                    onDeleteBasic = viewModel::deleteBasicModel,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Text(
                text = stringResource(R.string.languages_source_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
