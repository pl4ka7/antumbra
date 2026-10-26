package app.antumbra.ai

import app.antumbra.BuildConfig
import app.antumbra.web.Net
import org.json.JSONObject

enum class ReportReason { OFFENSIVE, SEXUAL, VIOLENT, DANGEROUS, MISLEADING, OTHER }

/**
 * Sends a flagged AI response to the developer over Tor.
 * Only the flagged message, the model id and the user's note are sent; nothing identifies the user.
 */
class Reports(private val net: Net) {

    val configured get() = BuildConfig.REPORT_ENDPOINT.isNotBlank()

    suspend fun send(reason: ReportReason, note: String, model: String, content: String): Boolean = runCatching {
        val body = JSONObject()
            .put("app", "antumbra")
            .put("version", BuildConfig.VERSION_NAME)
            .put("reason", reason.name.lowercase())
            .put("note", note.take(1_000))
            .put("model", model)
            .put("content", content.take(8_000))
        val response = net.open(
            url = BuildConfig.REPORT_ENDPOINT,
            method = "POST",
            headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
            body = body.toString(),
        )
        runCatching { response.body?.close() }
        response.statusCode in 200..299
    }.getOrDefault(false)
}
