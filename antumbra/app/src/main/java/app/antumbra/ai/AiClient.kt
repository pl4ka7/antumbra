package app.antumbra.ai

import app.antumbra.core.AiProvider
import app.antumbra.web.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class Model(
    val id: String,
    val name: String,
    val contextLength: Int,
    /** USD per million tokens, null when unknown. */
    val inputPrice: Double?,
    val outputPrice: Double?,
) {
    val free get() = inputPrice == 0.0 && outputPrice == 0.0
}

data class Turn(val role: String, val content: String)

data class Endpoint(val provider: AiProvider, val baseUrl: String, val apiKey: String?) {
    val root: String
        get() = when (provider) {
            AiProvider.OPENROUTER -> OPENROUTER
            AiProvider.CUSTOM -> baseUrl.trim().trimEnd('/')
        }

    fun headers(): Map<String, String> = buildMap {
        put("Accept", "application/json")
        if (!apiKey.isNullOrBlank()) put("Authorization", "Bearer $apiKey")
    }

    companion object {
        const val OPENROUTER = "https://openrouter.ai/api/v1"
    }
}

class AiException(message: String) : IOException(message)

/** OpenAI-compatible chat client. Every request goes through Gecko, therefore through Tor. */
class AiClient(private val net: Net) {

    suspend fun models(endpoint: Endpoint): List<Model> {
        val body = try {
            net.text("${endpoint.root}/models", endpoint.headers())
        } catch (e: Net.HttpException) {
            throw AiException(errorMessage(e.body) ?: "HTTP ${e.status}")
        }
        return withContext(Dispatchers.Default) {
            val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
            List(data.length()) { data.getJSONObject(it) }.map { m ->
                val pricing = m.optJSONObject("pricing")
                Model(
                    id = m.getString("id"),
                    name = m.optString("name").ifBlank { m.getString("id") },
                    contextLength = m.optInt("context_length", 0),
                    inputPrice = pricing?.optString("prompt")?.toDoubleOrNull()?.times(1_000_000),
                    outputPrice = pricing?.optString("completion")?.toDoubleOrNull()?.times(1_000_000),
                )
            }.sortedWith(compareBy({ !it.free }, { it.name.lowercase() }))
        }
    }

    /** Streams the assistant reply as text deltas. */
    fun chat(endpoint: Endpoint, model: String, turns: List<Turn>, zeroRetention: Boolean): Flow<String> = flow {
        val payload = JSONObject()
            .put("model", model)
            .put("stream", true)
            .put("messages", JSONArray(turns.map { JSONObject().put("role", it.role).put("content", it.content) }))
        if (endpoint.provider == AiProvider.OPENROUTER && zeroRetention) {
            payload.put("provider", JSONObject().put("data_collection", "deny").put("zdr", true))
        }
        val response = net.open(
            url = "${endpoint.root}/chat/completions",
            method = "POST",
            headers = endpoint.headers() + mapOf("Content-Type" to "application/json", "Accept" to "text/event-stream"),
            body = payload.toString(),
            timeoutMs = 180_000,
        )
        val stream = response.body ?: throw AiException("empty response")
        stream.bufferedReader().use { reader ->
            if (response.statusCode !in 200..299) {
                throw AiException(errorMessage(reader.readText()) ?: "HTTP ${response.statusCode}")
            }
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                val json = runCatching { JSONObject(data) }.getOrNull() ?: continue
                json.optJSONObject("error")?.let { throw AiException(it.optString("message", "provider error")) }
                val delta = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty()
                if (delta.isNotEmpty()) emit(delta)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun errorMessage(body: String): String? = runCatching {
        JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
