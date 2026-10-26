package app.antumbra.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebResponse
import java.io.IOException
import java.nio.ByteBuffer

/**
 * HTTP through Gecko's network stack, so every request inherits the Tor proxy,
 * DNS-over-Tor and first-party isolation of the browser.
 */
class Net(runtime: GeckoRuntime) {

    private val executor = GeckoWebExecutor(runtime)

    class HttpException(val status: Int, val body: String) : IOException("HTTP $status")

    suspend fun open(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Long = 60_000,
    ): WebResponse {
        val request = WebRequest.Builder(url)
            .method(method)
            .cacheMode(WebRequest.CACHE_MODE_NO_STORE)
            .apply {
                headers.forEach { (k, v) -> header(k, v) }
                if (body != null) {
                    val bytes = body.toByteArray()
                    body(ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() })
                }
            }
            .build()
        val response = withContext(Dispatchers.Main) {
            executor.fetch(request, GeckoWebExecutor.FETCH_FLAGS_ANONYMOUS).await()
        } ?: throw IOException("network error")
        response.setReadTimeoutMillis(timeoutMs)
        return response
    }

    suspend fun text(url: String, headers: Map<String, String> = emptyMap()): String {
        val response = open(url, headers = headers)
        val text = withContext(Dispatchers.IO) { response.body?.bufferedReader()?.use { it.readText() }.orEmpty() }
        if (response.statusCode !in 200..299) throw HttpException(response.statusCode, text)
        return text
    }
}
