package app.antumbra.ai

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.antumbra.AntumbraApp
import app.antumbra.core.AiProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class Message(val id: Long, val fromUser: Boolean, text: String = "", val context: String? = null) {
    var text by mutableStateOf(text)
    var streaming by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

sealed interface ModelsState {
    data object Idle : ModelsState
    data object Loading : ModelsState
    data class Loaded(val models: List<Model>) : ModelsState
    data class Failed(val message: String) : ModelsState
}

/** Chat with a user-chosen model. Conversations live only in memory. */
class AskViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AntumbraApp
    private val settings = app.settings
    private val client by lazy { AiClient(app.net) }
    private val reports by lazy { Reports(app.net) }

    val messages = mutableStateListOf<Message>()
    var provider by mutableStateOf(settings.aiProvider)
        private set
    var model by mutableStateOf(settings.aiModel)
        private set
    var baseUrl by mutableStateOf(settings.aiBaseUrl)
        private set
    var models by mutableStateOf<ModelsState>(ModelsState.Idle)
        private set
    var hasKey by mutableStateOf(false)
        private set

    val busy get() = job?.isActive == true
    val reportsConfigured get() = reports.configured
    private var apiKey: String? = if (settings.aiRememberKey) app.secrets.get(KEY_NAME) else null
    private var job: Job? = null
    private var nextId = 0L

    init {
        hasKey = !apiKey.isNullOrBlank()
    }

    val ready: Boolean
        get() = when (provider) {
            AiProvider.OPENROUTER -> hasKey
            AiProvider.CUSTOM -> baseUrl.startsWith("http")
        }

    private fun endpoint() = Endpoint(provider, baseUrl, apiKey)

    fun configure(provider: AiProvider, baseUrl: String) {
        this.provider = provider
        this.baseUrl = baseUrl.trim()
        settings.aiProvider = provider
        settings.aiBaseUrl = this.baseUrl
        models = ModelsState.Idle
    }

    fun setKey(key: String, remember: Boolean) {
        apiKey = key.trim().ifBlank { null }
        hasKey = apiKey != null
        settings.aiRememberKey = remember
        app.secrets.put(KEY_NAME, if (remember) apiKey else null)
        models = ModelsState.Idle
    }

    fun forgetKey() = setKey("", remember = false)

    fun selectModel(id: String) {
        model = id
        settings.aiModel = id
    }

    fun loadModels(force: Boolean = false) {
        if (!force && (models is ModelsState.Loading || models is ModelsState.Loaded)) return
        models = ModelsState.Loading
        viewModelScope.launch {
            models = try {
                ModelsState.Loaded(client.models(endpoint()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ModelsState.Failed(e.message.orEmpty())
            }
        }
    }

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || busy) return
        messages += Message(nextId++, fromUser = true, text = prompt)
        respond()
    }

    /** Sends [content] to the model while showing only [display] in the conversation. */
    fun sendWithContext(display: String, content: String) {
        if (busy) return
        messages += Message(nextId++, fromUser = true, text = display, context = content)
        respond()
    }

    fun regenerate() {
        if (busy) return
        if (messages.lastOrNull()?.fromUser == false) messages.removeAt(messages.lastIndex)
        if (messages.lastOrNull()?.fromUser == true) respond()
    }

    fun stop() {
        job?.cancel()
    }

    fun clear() {
        stop()
        messages.clear()
    }

    private fun respond() {
        val history = buildList {
            settings.aiSystemPrompt.takeIf { it.isNotBlank() }?.let { add(Turn("system", it)) }
            messages.filter { it.error == null && it.text.isNotEmpty() }
                .forEach { add(Turn(if (it.fromUser) "user" else "assistant", it.context ?: it.text)) }
        }
        val reply = Message(nextId++, fromUser = false).apply { streaming = true }
        messages += reply
        job = viewModelScope.launch {
            try {
                client.chat(endpoint(), model, history, settings.aiZeroRetention).collect { reply.text += it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reply.error = e.message.orEmpty()
            } finally {
                reply.streaming = false
            }
        }
    }

    fun report(message: Message, reason: ReportReason, note: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(reports.send(reason, note, model, message.text)) }
    }

    private companion object {
        const val KEY_NAME = "ai_api_key"
    }
}
