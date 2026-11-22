package tr.duka.scanread.update

import org.json.JSONArray

/**
 * An app release published on GitHub.
 *
 * @property apkUrl download link of the APK for this device's CPU architecture, `null` if none.
 */
data class AppRelease(
    val version: AppVersion,
    val pageUrl: String,
    val apkUrl: String?,
)

/** Picks the newest app release from the GitHub releases API response. */
object ReleaseParser {

    /**
     * @param json body of `GET /repos/{owner}/{repo}/releases`.
     * @param supportedAbis this device's ABIs in order of preference (`Build.SUPPORTED_ABIS`).
     * @return the highest stable app release; tags that are not versions (model releases), drafts
     * and pre-releases are skipped.
     */
    fun newest(json: String, supportedAbis: List<String>): AppRelease? {
        val releases = JSONArray(json)
        var best: AppRelease? = null
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
            val version = AppVersion.parse(release.optString("tag_name")) ?: continue
            if (!release.optString("tag_name").startsWith("v")) continue
            if (best != null && version <= best.version) continue

            val assets = release.optJSONArray("assets") ?: JSONArray()
            val apkNames = (0 until assets.length()).map { assets.getJSONObject(it) }
                .filter { it.optString("name").endsWith(".apk") }
            val apk = supportedAbis.firstNotNullOfOrNull { abi ->
                apkNames.firstOrNull { it.optString("name").contains(abi) }
            }
            best = AppRelease(
                version = version,
                pageUrl = release.optString("html_url"),
                apkUrl = apk?.optString("browser_download_url"),
            )
        }
        return best
    }
}
