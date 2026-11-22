package tr.duka.scanread.ui.about

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tr.duka.scanread.BuildConfig
import tr.duka.scanread.R
import tr.duka.scanread.ui.components.ScreenScaffold

/**
 * A third-party component and its license. License text is shown from [assetPath] when the
 * license requires shipping it, otherwise [url] is opened.
 */
private class Notice(
    @StringRes val name: Int,
    @StringRes val license: Int,
    val url: String,
    val assetPath: String? = null,
)

private val NOTICES = listOf(
    Notice(R.string.notice_opus_mt, R.string.license_cc_by_4, "https://github.com/Helsinki-NLP/Opus-MT"),
    Notice(
        R.string.notice_comic_relief,
        R.string.license_ofl,
        "https://github.com/loudifier/Comic-Relief",
        assetPath = "licenses/ComicRelief-OFL.txt",
    ),
    Notice(R.string.notice_onnx_runtime, R.string.license_mit, "https://github.com/microsoft/onnxruntime"),
    Notice(R.string.notice_ml_kit, R.string.license_ml_kit, "https://developers.google.com/ml-kit/terms"),
    Notice(R.string.notice_androidx, R.string.license_apache_2, "https://www.apache.org/licenses/LICENSE-2.0"),
    Notice(R.string.notice_kotlin, R.string.license_apache_2, "https://www.apache.org/licenses/LICENSE-2.0"),
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var shownLicense by remember { mutableStateOf<Notice?>(null) }
    val openUrl: (String) -> Unit = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    ScreenScaffold(title = stringResource(R.string.about_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge) },
                supportingContent = { Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME)) },
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Header(stringResource(R.string.about_privacy_title))
            Text(
                text = stringResource(R.string.about_privacy_text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_contact)) },
                supportingContent = { Text(stringResource(R.string.support_email)) },
                modifier = Modifier.clickable {
                    val email = context.getString(R.string.support_email)
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                    runCatching { context.startActivity(intent) }
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_source_code)) },
                supportingContent = { Text(stringResource(R.string.about_source_code_url)) },
                modifier = Modifier.clickable { openUrl(context.getString(R.string.about_source_code_url)) },
            )
            HorizontalDivider()

            Header(stringResource(R.string.about_licenses))
            NOTICES.forEach { notice ->
                ListItem(
                    headlineContent = { Text(stringResource(notice.name)) },
                    supportingContent = { Text(stringResource(notice.license)) },
                    modifier = Modifier.clickable {
                        if (notice.assetPath != null) shownLicense = notice else openUrl(notice.url)
                    },
                )
            }
        }
    }

    shownLicense?.let { notice ->
        val text = remember(notice) {
            context.assets.open(checkNotNull(notice.assetPath)).bufferedReader().use { it.readText() }
        }
        AlertDialog(
            onDismissRequest = { shownLicense = null },
            title = { Text(stringResource(notice.name)) },
            text = {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { shownLicense = null }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}
