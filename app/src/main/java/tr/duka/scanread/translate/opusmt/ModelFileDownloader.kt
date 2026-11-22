package tr.duka.scanread.translate.opusmt

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * Downloads a file to disk, resuming a previous partial download when the server supports range
 * requests, and accepts it only if size and SHA-256 digest match.
 *
 * @param open opens [url] starting at byte `offset`; the response tells whether the server
 * honoured the offset (HTTP 206) or sends the whole file.
 */
class ModelFileDownloader(
    private val open: (url: String, offset: Long) -> Response,
) {
    class Response(val stream: InputStream, val resumed: Boolean)

    /**
     * @param onProgress receives the number of bytes of this file on disk so far.
     * @throws IOException on network errors or when the downloaded file does not verify.
     */
    suspend fun download(url: String, target: File, size: Long, sha256: String, onProgress: (Long) -> Unit) {
        target.parentFile?.mkdirs()
        val partial = File(target.path + PARTIAL_SUFFIX)
        if (partial.length() >= size) partial.delete()

        val offset = partial.length()
        val response = open(url, offset)
        val append = offset > 0 && response.resumed
        var written = if (append) offset else 0L
        onProgress(written)

        response.stream.use { input ->
            FileOutputStream(partial, append).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    written += read
                    onProgress(written)
                }
            }
        }

        if (partial.length() != size || sha256Of(partial) != sha256) {
            partial.delete()
            throw IOException("Downloaded ${target.name} does not match the expected size or checksum")
        }
        target.delete()
        if (!partial.renameTo(target)) throw IOException("Could not move ${target.name} into place")
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val PARTIAL_SUFFIX = ".part"
        private const val BUFFER_SIZE = 64 * 1024
    }
}
