package com.quitkelly.app.ui.vm

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.quitkelly.app.AppContainer
import com.quitkelly.app.ai.AiMessage
import com.quitkelly.app.ai.Prompts
import com.quitkelly.app.data.AttachmentStore
import com.quitkelly.app.data.ChatMessageEntity
import com.quitkelly.app.data.ChatSessionEntity
import com.quitkelly.app.data.PendingAttachment
import com.quitkelly.app.util.formatTime
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessageEntity> = emptyList(),
    val sessions: List<ChatSessionEntity> = emptyList(),
    val currentSessionId: String? = null,
    val streamingText: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
    val hasApiKey: Boolean = true,
    val pendingAttachments: List<PendingAttachment> = emptyList(),
    val importingAttachment: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val currentSessionId = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            container.database.chatSessionDao().allFlow().collect { sessions ->
                _state.update { it.copy(sessions = sessions) }
            }
        }
        viewModelScope.launch {
            val latest = container.database.chatSessionDao().latest()
            val id = latest?.id ?: createSessionInternal()
            currentSessionId.value = id
            _state.update { it.copy(currentSessionId = id) }
        }
        viewModelScope.launch {
            currentSessionId
                .filterNotNull()
                .flatMapLatest { id -> container.database.chatDao().messagesFlow(id) }
                .collect { messages ->
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

    fun newSession() {
        if (_state.value.isStreaming) return
        viewModelScope.launch {
            val id = createSessionInternal()
            currentSessionId.value = id
            _state.update {
                it.copy(
                    currentSessionId = id,
                    messages = emptyList(),
                    pendingAttachments = emptyList(),
                    error = null
                )
            }
        }
    }

    fun selectSession(id: String) {
        if (_state.value.isStreaming || id == _state.value.currentSessionId) return
        currentSessionId.value = id
        _state.update {
            it.copy(
                currentSessionId = id,
                messages = emptyList(),
                pendingAttachments = emptyList(),
                error = null
            )
        }
    }

    fun deleteSession(id: String) {
        if (_state.value.isStreaming) return
        viewModelScope.launch {
            val wasCurrent = _state.value.currentSessionId == id
            container.database.chatDao().clearSession(id)
            container.database.chatSessionDao().delete(id)
            if (wasCurrent) {
                val nextId = container.database.chatSessionDao().latest()?.id
                    ?: createSessionInternal()
                currentSessionId.value = nextId
                _state.update {
                    it.copy(
                        currentSessionId = nextId,
                        messages = emptyList(),
                        pendingAttachments = emptyList()
                    )
                }
            }
        }
    }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty() || _state.value.importingAttachment) return
        val remaining = AttachmentStore.MAX_ATTACHMENTS - _state.value.pendingAttachments.size
        if (remaining <= 0) {
            _state.update {
                it.copy(error = "每条消息最多添加 ${AttachmentStore.MAX_ATTACHMENTS} 个附件")
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(importingAttachment = true, error = null) }
            val results = container.attachmentStore.importImages(uris.take(remaining))
            val imported = results.mapNotNull { it.getOrNull() }
            val failure = results.firstNotNullOfOrNull { it.exceptionOrNull()?.message }
            _state.update {
                it.copy(
                    pendingAttachments = it.pendingAttachments + imported,
                    importingAttachment = false,
                    error = failure ?: if (uris.size > remaining) {
                        "最多添加 ${AttachmentStore.MAX_ATTACHMENTS} 个附件，多余的已忽略"
                    } else {
                        null
                    }
                )
            }
        }
    }

    fun addFile(uri: Uri) {
        if (_state.value.importingAttachment) return
        if (_state.value.pendingAttachments.size >= AttachmentStore.MAX_ATTACHMENTS) {
            _state.update {
                it.copy(error = "每条消息最多添加 ${AttachmentStore.MAX_ATTACHMENTS} 个附件")
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(importingAttachment = true, error = null) }
            val result = container.attachmentStore.importFile(uri)
            val imported = result.getOrNull()
            val failure = result.exceptionOrNull()
            _state.update { current ->
                current.copy(
                    pendingAttachments = if (imported != null) {
                        current.pendingAttachments + imported
                    } else {
                        current.pendingAttachments
                    },
                    importingAttachment = false,
                    error = failure?.message
                )
            }
        }
    }

    fun removeAttachment(id: String) {
        _state.update { current ->
            current.copy(pendingAttachments = current.pendingAttachments.filterNot { it.id == id })
        }
    }

    fun send(input: String) {
        val text = input.trim()
        val pending = _state.value.pendingAttachments
        if ((text.isEmpty() && pending.isEmpty()) || _state.value.isStreaming) return
        if (_state.value.importingAttachment) return

        val sessionId = _state.value.currentSessionId
        if (sessionId == null) {
            _state.update { it.copy(error = "会话初始化中，请稍后再试") }
            return
        }

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
                val history = dao.recent(sessionId, 20).sortedBy { it.createdAt }
                val attachmentsJson = container.attachmentStore.toJson(pending)
                dao.insert(
                    ChatMessageEntity(
                        role = "user",
                        content = text,
                        createdAt = System.currentTimeMillis(),
                        attachmentsJson = attachmentsJson,
                        sessionId = sessionId
                    )
                )
                touchSession(sessionId, text, pending)
                _state.update { it.copy(pendingAttachments = emptyList()) }

                val settings = container.settings.snapshot()
                val today = LocalDate.now()
                val digest = container.database.digestDao().byDate(today.toString())
                    ?: container.database.digestDao().latest()
                val news = container.newsRepository.forDate(today)
                    .ifEmpty { container.newsRepository.recent(15) }
                val headlines = news.take(15).joinToString("\n") { item ->
                    "- ${formatTime(item.publishedAt)}【${item.source}】${item.title.take(90)}"
                }
                val imageUrls = container.attachmentStore.buildImageDataUrls(pending)
                val attachmentText = container.attachmentStore.buildTextContext(pending)

                val promptText = buildString {
                    if (text.isNotBlank()) append(text)
                    if (attachmentText.isNotBlank()) {
                        if (isNotEmpty()) appendLine().appendLine()
                        append(attachmentText.trim())
                    }
                    if (isEmpty()) append("请分析我发送的附件。")
                }

                val messages = buildList {
                    add(
                        AiMessage(
                            "system",
                            Prompts.chatSystem(
                                date = today.toString(),
                                digestDate = digest?.date,
                                digest = digest?.content,
                                headlines = headlines
                            )
                        )
                    )
                    history.forEach { message ->
                        val attachments = container.attachmentStore.fromJson(message.attachmentsJson)
                        val content = if (attachments.isEmpty()) {
                            message.content
                        } else {
                            buildString {
                                if (message.content.isNotBlank()) {
                                    append(message.content).appendLine().appendLine()
                                }
                                append("【已发送附件：")
                                append(attachments.joinToString("、") { it.name })
                                append("】")
                            }
                        }
                        add(AiMessage(message.role, content))
                    }
                    add(AiMessage("user", promptText, imageUrls))
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
                        createdAt = System.currentTimeMillis(),
                        sessionId = sessionId
                    )
                )
                container.database.chatSessionDao().byId(sessionId)?.let { session ->
                    container.database.chatSessionDao().upsert(
                        session.copy(updatedAt = System.currentTimeMillis())
                    )
                }
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
        val sessionId = _state.value.currentSessionId ?: return
        viewModelScope.launch {
            container.database.chatDao().clearSession(sessionId)
            container.database.chatSessionDao().byId(sessionId)?.let { session ->
                container.database.chatSessionDao().upsert(
                    session.copy(title = DEFAULT_TITLE, updatedAt = System.currentTimeMillis())
                )
            }
            _state.update { it.copy(error = null, pendingAttachments = emptyList()) }
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun createSessionInternal(): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        container.database.chatSessionDao().upsert(
            ChatSessionEntity(
                id = id,
                title = DEFAULT_TITLE,
                createdAt = now,
                updatedAt = now
            )
        )
        return id
    }

    private suspend fun touchSession(
        sessionId: String,
        text: String,
        attachments: List<PendingAttachment>
    ) {
        val dao = container.database.chatSessionDao()
        val session = dao.byId(sessionId) ?: return
        val title = if (session.title == DEFAULT_TITLE) {
            val fromText = text.trim().replace(Regex("\\s+"), " ").take(20)
            val fromAttachment = attachments.firstOrNull()?.name
                ?.substringBeforeLast('.')
                ?.take(20)
                .orEmpty()
            fromText.ifBlank { fromAttachment }.ifBlank { DEFAULT_TITLE }
        } else {
            session.title
        }
        dao.upsert(session.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    companion object {
        private const val DEFAULT_TITLE = "新对话"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatViewModel(container) }
        }
    }
}
