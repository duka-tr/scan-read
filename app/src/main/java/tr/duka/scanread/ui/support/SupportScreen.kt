package tr.duka.scanread.ui.support

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tr.duka.scanread.BuildConfig
import tr.duka.scanread.R
import tr.duka.scanread.ui.components.ScreenScaffold

/**
 * Ways to support the project. With [BuildConfig.TIPS_ENABLED], optional Google Play tips (nothing
 * is unlocked by tipping); otherwise free ways to help: starring the project, visiting the website.
 */
@Composable
fun SupportRoute(onBack: () -> Unit) {
    if (!BuildConfig.TIPS_ENABLED) {
        ProjectLinksScreen(onBack)
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tipJar = remember { TipJar(context, scope) }
    DisposableEffect(tipJar) {
        tipJar.connect()
        onDispose { tipJar.close() }
    }
    val state by tipJar.state.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(tipJar) {
        tipJar.events.collect { event ->
            message = when (event) {
                TipJar.Event.THANKS -> R.string.support_thanks
                TipJar.Event.FAILED -> R.string.support_failed
            }
        }
    }

    SupportScreen(
        state = state,
        message = message,
        onTip = { tip -> context.findActivity()?.let { tipJar.tip(it, tip) } },
        onBack = onBack,
    )
}

@Composable
private fun SupportScreen(
    state: TipJar.State,
    message: Int?,
    onTip: (TipJar.Tip) -> Unit,
    onBack: () -> Unit,
) {
    val tipLabels = listOf(R.string.support_tip_small, R.string.support_tip_medium, R.string.support_tip_large)

    ScreenScaffold(title = stringResource(R.string.home_support), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = stringResource(R.string.support_text),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            when (state) {
                TipJar.State.Loading -> CircularProgressIndicator()
                TipJar.State.Unavailable -> Text(
                    text = stringResource(R.string.support_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                is TipJar.State.Ready -> state.tips.forEachIndexed { index, tip ->
                    FilledTonalButton(onClick = { onTip(tip) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.support_tip_button, stringResource(tipLabels.getOrElse(index) { tipLabels.last() }), tip.price))
                    }
                }
            }
            message?.let {
                Text(text = stringResource(it), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ProjectLinksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val open: (Int) -> Unit = { url ->
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(url))))
    }
    ScreenScaffold(title = stringResource(R.string.home_support), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = stringResource(R.string.support_free_text),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            FilledTonalButton(onClick = { open(R.string.about_source_code_url) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.support_star_github))
            }
            FilledTonalButton(onClick = { open(R.string.developer_website_url) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.support_visit_website))
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
