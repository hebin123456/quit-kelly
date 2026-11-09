package com.quitkelly.app.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.quitkelly.app.AppContainer
import com.quitkelly.app.ai.AiMessage
import com.quitkelly.app.data.AppSettings
import com.quitkelly.app.work.WorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKey: String = "",
    val settings: AppSettings = AppSettings(),
    val rssText: String = "",
    val testing: Boolean = false,
    val testResult: String? = null,
    val message: String? = null
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private var rssInitialized = false

    init {
        _state.update { it.copy(apiKey = container.secretStore.apiKey()) }
        viewModelScope.launch {
            container.settings.flow.collect { settings ->
                val rss = if (rssInitialized) {
                    _state.value.rssText
                } else {
                    settings.customRssUrls.joinToString("\n")
                }
                rssInitialized = true
                _state.update { it.copy(settings = settings, rssText = rss) }
            }
        }
    }

    fun saveApiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) {
            _state.update { it.copy(message = "API Key 不能为空") }
            return
        }
        container.secretStore.saveApiKey(trimmed)
        _state.update { it.copy(apiKey = trimmed, message = "API Key 已保存") }
    }

    fun testConnection() {
        val key = _state.value.apiKey.trim()
        if (key.isBlank()) {
            _state.update { it.copy(testResult = "请先填写并保存 API Key") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(testing = true, testResult = null) }
            val result = runCatching {
                container.deepSeekClient.complete(
                    apiKey = key,
                    model = _state.value.settings.model,
                    messages = listOf(AiMessage("user", "只回复四个字：连接成功")),
                    temperature = 0.0,
                    maxTokens = 20
                )
            }
            _state.update {
                it.copy(
                    testing = false,
                    testResult = result.fold(
                        onSuccess = { reply -> "连接成功（模型回复：${reply.take(20)}）" },
                        onFailure = { error -> "连接失败：${error.message ?: "未知错误"}" }
                    )
                )
            }
        }
    }

    fun setModel(model: String) {
        viewModelScope.launch {
            container.settings.setModel(model)
            _state.update { it.copy(message = "模型已切换") }
        }
    }

    fun setHour(hour: Int) {
        updateSchedule { it.copy(dailyHour = hour) }
    }

    fun setMinute(minute: Int) {
        updateSchedule { it.copy(dailyMinute = minute) }
    }

    private fun updateSchedule(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val updated = transform(container.settings.snapshot())
            container.settings.setDailyTime(updated.dailyHour, updated.dailyMinute)
            WorkScheduler.schedule(container.appContext, updated)
            _state.update { it.copy(message = "每日推送时间已更新") }
        }
    }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = container.settings.snapshot()
            val updated = if (enabled) {
                current.enabledSources + id
            } else {
                current.enabledSources - id
            }
            container.settings.setEnabledSources(updated)
        }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.setNotifications(enabled)
        }
    }

    fun updateRssText(text: String) {
        _state.update { it.copy(rssText = text) }
    }

    fun saveRss() {
        viewModelScope.launch {
            container.settings.setCustomRss(_state.value.rssText)
            _state.update { it.copy(message = "自定义 RSS 已保存") }
        }
    }

    fun clearNews() {
        viewModelScope.launch {
            container.newsRepository.clearAll()
            _state.update { it.copy(message = "新闻缓存已清空") }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
