package tr.duka.scanread.translate.opusmt

import tr.duka.scanread.translate.LanguagePair
import tr.duka.scanread.translate.TranslationModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads, verifies and deletes Opus-MT bundles listed in [OpusMtCatalog].
 *
 * Files are fetched from [releasesBaseUrl] (`…/releases/download`); interrupted downloads resume.
 */
class OpusMtModelStore(
    private val filesDir: File,
    private val releasesBaseUrl: String,
    private val downloader: ModelFileDownloader = ModelFileDownloader(::openHttp),
) : TranslationModelStore {

    override suspend fun isDownloaded(pair: LanguagePair): Boolean =
        OpusMtBundle.installed(filesDir, pair) != null

    override suspend fun download(pair: LanguagePair, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val entry = OpusMtCatalog.entry(pair) ?: throw IOException("No high-quality model for $pair")
        val bundle = checkNotNull(OpusMtBundle.locate(filesDir, pair))
        val total = entry.totalSize.toFloat()
        var completed = 0L

        for (file in entry.files) {
            val target = File(bundle.directory, file.name)
            // Files are only moved into place after verification, so a present file of the right size is done.
            if (target.length() != file.size) {
                downloader.download(OpusMtCatalog.url(releasesBaseUrl, entry, file), target, file.size, file.sha256) { bytes ->
                    onProgress((completed + bytes) / total)
                }
            }
            completed += file.size
            onProgress(completed / total)
        }
    }

    override suspend fun delete(pair: LanguagePair) = withContext(Dispatchers.IO) {
        OpusMtBundle.locate(filesDir, pair)?.directory?.deleteRecursively()
        Unit
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000

        fun openHttp(url: String, offset: Long): ModelFileDownloader.Response {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                connection.disconnect()
                throw IOException("HTTP $code for $url")
            }
            return ModelFileDownloader.Response(
                stream = connection.inputStream,
                resumed = code == HttpURLConnection.HTTP_PARTIAL,
            )
        }
    }
}
