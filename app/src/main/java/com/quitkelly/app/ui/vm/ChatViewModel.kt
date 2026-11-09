package com.quitkelly.app.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.quitkelly.app.AppContainer
import com.quitkelly.app.ai.AiMessage
import com.quitkelly.app.ai.Prompts
import com.quitkelly.app.data.ChatMessageEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessageEntity> = emptyList(),
    val streamingText: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
    val hasApiKey: Boolean = true
)

class ChatViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.database.chatDao().allFlow().collect { messages ->
                _state.update { it.copy(messages = messages) }
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(hasApiKey = container.secretStore.apiKey().isNotBlank()) }
        }
    }

    fun refreshKeyState() {
        _state.update { it.copy(hasApiKey = container.secretStore.apiKey().isNotBlank()) }
    }

    fun send(input: String) {
        val text = input.trim()
        if (text.isEmpty() || _state.value.isStreaming) return

        val apiKey = container.secretStore.apiKey()
        if (apiKey.isBlank()) {
            _state.update {
                it.copy(error = "请先在设置中配置 DeepSeek API Key", hasApiKey = false)
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(isStreaming = true, streamingText = "", error = null, hasApiKey = true)
            }
            try {
                val dao = container.database.chatDao()
                dao.insert(
                    ChatMessageEntity(
                        role = "user",
                        content = text,
                        createdAt = System.currentTimeMillis()
                    )
                )
                val settings = container.settings.snapshot()
                val digest = container.database.digestDao().byDate(LocalDate.now().toString())
                val history = dao.recent(20).sortedBy { it.createdAt }
                val messages = buildList {
                    add(AiMessage("system", Prompts.chatSystem(LocalDate.now().toString(), digest?.content)))
                    history.forEach { add(AiMessage(it.role, it.content)) }
                }

                val builder = StringBuilder()
                container.deepSeekClient.stream(apiKey, settings.model, messages).collect { delta ->
                    builder.append(delta)
                    _state.update { it.copy(streamingText = builder.toString()) }
                }

                val answer = builder.toString().trim()
                if (answer.isBlank()) throw IllegalStateException("模型没有返回内容")
                dao.insert(
                    ChatMessageEntity(
                        role = "assistant",
                        content = answer,
                        createdAt = System.currentTimeMillis()
                    )
                )
                _state.update { it.copy(isStreaming = false, streamingText = "") }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isStreaming = false,
                        streamingText = "",
                        error = t.message ?: "请求失败，请稍后重试"
                    )
                }
            }
        }
    }

    fun clearChat() {
        if (_state.value.isStreaming) return
        viewModelScope.launch {
            container.database.chatDao().clear()
            _state.update { it.copy(error = null) }
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatViewModel(container) }
        }
    }
}
