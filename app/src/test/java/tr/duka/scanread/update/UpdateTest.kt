package tr.duka.scanread.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `parses tags and version names`() {
        assertEquals(AppVersion(listOf(0, 2, 1)), AppVersion.parse("v0.2.1"))
        assertEquals(AppVersion(listOf(0, 1, 0)), AppVersion.parse("0.1.0-debug"))
        assertNull(AppVersion.parse("model-opus-mt-en-tr-v1"))
    }

    @Test
    fun `compares numerically, missing parts are zero`() {
        assertTrue(AppVersion.parse("0.10.0")!! > AppVersion.parse("0.9.9")!!)
        assertEquals(0, AppVersion.parse("1.2")!!.compareTo(AppVersion.parse("1.2.0")!!))
        assertTrue(AppVersion.parse("1.0.1")!! > AppVersion.parse("1.0")!!)
    }
}

class ReleaseParserTest {

    private fun release(tag: String, vararg apks: String, draft: Boolean = false, prerelease: Boolean = false) = """
        {"tag_name": "$tag", "draft": $draft, "prerelease": $prerelease,
         "html_url": "https://example.com/releases/$tag",
         "assets": [${apks.joinToString(",") { """{"name": "$it", "browser_download_url": "https://example.com/$it"}""" }}]}
    """

    @Test
    fun `picks the newest app release and the APK for this device`() {
        val json = "[" + listOf(
            release("model-opus-mt-en-tr-v1", "encoder.onnx"),
            release("v0.2.0", "ScanRead-0.2.0-arm64-v8a.apk", "ScanRead-0.2.0-armeabi-v7a.apk"),
            release("v0.1.0", "ScanRead-0.1.0-arm64-v8a.apk"),
        ).joinToString(",") + "]"

        val newest = ReleaseParser.newest(json, listOf("arm64-v8a", "armeabi-v7a"))!!

        assertEquals("0.2.0", newest.version.toString())
        assertEquals("https://example.com/ScanRead-0.2.0-arm64-v8a.apk", newest.apkUrl)
        assertEquals("https://example.com/releases/v0.2.0", newest.pageUrl)
    }

    @Test
    fun `falls back to the next supported ABI`() {
        val json = "[" + release("v0.2.0", "ScanRead-0.2.0-armeabi-v7a.apk") + "]"

        val newest = ReleaseParser.newest(json, listOf("arm64-v8a", "armeabi-v7a"))!!

        assertEquals("https://example.com/ScanRead-0.2.0-armeabi-v7a.apk", newest.apkUrl)
    }

    @Test
    fun `skips drafts and pre-releases`() {
        val json = "[" + listOf(
            release("v0.3.0", "a-arm64-v8a.apk", draft = true),
            release("v0.2.5", "b-arm64-v8a.apk", prerelease = true),
            release("v0.2.0", "c-arm64-v8a.apk"),
        ).joinToString(",") + "]"

        assertEquals("0.2.0", ReleaseParser.newest(json, listOf("arm64-v8a"))!!.version.toString())
    }
}
