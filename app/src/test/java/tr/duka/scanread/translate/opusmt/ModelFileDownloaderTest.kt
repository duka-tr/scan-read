package tr.duka.scanread.translate.opusmt

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class ModelFileDownloaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val content = ByteArray(200_000) { (it % 251).toByte() }
    private val sha256 = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }

    /** Serves [content]; honours ranges only if [supportsRange]. Records requested offsets. */
    private class FakeServer(private val content: ByteArray, private val supportsRange: Boolean) {
        val offsets = mutableListOf<Long>()
        fun open(url: String, offset: Long): ModelFileDownloader.Response {
            offsets += offset
            val start = if (supportsRange) offset.toInt() else 0
            return ModelFileDownloader.Response(
                ByteArrayInputStream(content, start, content.size - start),
                resumed = supportsRange && offset > 0,
            )
        }
    }

    private fun target() = File(folder.root, "model/encoder.onnx")
    private fun partial() = File(target().path + ModelFileDownloader.PARTIAL_SUFFIX)

    @Test
    fun `downloads, verifies and moves the file into place`() = runBlocking {
        val server = FakeServer(content, supportsRange = true)
        var lastProgress = 0L

        ModelFileDownloader(server::open).download("u", target(), content.size.toLong(), sha256) { lastProgress = it }

        assertArrayEquals(content, target().readBytes())
        assertFalse(partial().exists())
        assertEquals(content.size.toLong(), lastProgress)
    }

    @Test
    fun `resumes a partial download when the server supports ranges`() = runBlocking {
        partial().parentFile.mkdirs()
        partial().writeBytes(content.copyOf(80_000))
        val server = FakeServer(content, supportsRange = true)

        ModelFileDownloader(server::open).download("u", target(), content.size.toLong(), sha256) {}

        assertEquals(listOf(80_000L), server.offsets)
        assertArrayEquals(content, target().readBytes())
    }

    @Test
    fun `starts over when the server ignores the range`() = runBlocking {
        partial().parentFile.mkdirs()
        partial().writeBytes(content.copyOf(80_000))
        val server = FakeServer(content, supportsRange = false)

        ModelFileDownloader(server::open).download("u", target(), content.size.toLong(), sha256) {}

        assertArrayEquals(content, target().readBytes())
    }

    @Test
    fun `rejects a file that does not match the checksum`() = runBlocking {
        val corrupted = content.copyOf().also { it[1000] = 0x7f }
        val server = FakeServer(corrupted, supportsRange = true)

        try {
            ModelFileDownloader(server::open).download("u", target(), content.size.toLong(), sha256) {}
            fail("expected IOException")
        } catch (expected: IOException) {
            // The corrupted download is discarded entirely.
        }
        assertFalse(target().exists())
        assertFalse(partial().exists())
        assertTrue(folder.root.exists())
    }
}
