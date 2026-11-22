package tr.duka.scanread.update

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Looks up the newest release on GitHub. Only used in builds distributed outside Google Play
 * (Play updates apps itself and does not allow apps to update from elsewhere).
 */
class UpdateChecker(
    private val releasesApiUrl: String,
    private val currentVersion: AppVersion,
) {
    sealed interface Result {
        data class Available(val release: AppRelease) : Result
        data object UpToDate : Result
        data object Failed : Result
    }

    suspend fun check(): Result = withContext(Dispatchers.IO) {
        try {
            val release = ReleaseParser.newest(download(), Build.SUPPORTED_ABIS.toList())
            if (release != null && release.version > currentVersion) Result.Available(release) else Result.UpToDate
        } catch (e: IOException) {
            Result.Failed
        } catch (e: org.json.JSONException) {
            Result.Failed
        }
    }

    private fun download(): String {
        val connection = URL(releasesApiUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            // A generic client name; no device or user information is sent.
            connection.setRequestProperty("User-Agent", "ScanRead-update-check")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
    }
}
