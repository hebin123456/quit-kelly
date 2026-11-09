package com.quitkelly.app.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.quitkelly.app.AppContainer
import com.quitkelly.app.data.DigestEntity
import com.quitkelly.app.data.NewsItemEntity
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DailyUiState(
    val loading: Boolean = true,
    val generating: Boolean = false,
    val digest: DigestEntity? = null,
    val selectedDate: String = LocalDate.now().toString(),
    val digestDates: List<String> = emptyList(),
    val news: List<NewsItemEntity> = emptyList(),
    val error: String? = null,
    val hasApiKey: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class DailyViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(DailyUiState())
    val state: StateFlow<DailyUiState> = _state.asStateFlow()

    private val selectedDate = MutableStateFlow(LocalDate.now())
    private var generating = false

    init {
        viewModelScope.launch {
            container.database.digestDao().allFlow().collect { digests ->
                _state.update { current ->
                    current.copy(digestDates = digests.map { it.date })
                }
            }
        }
        viewModelScope.launch {
            selectedDate
                .flatMapLatest { date -> container.newsRepository.observeForDate(date) }
                .collect { news ->
                    _state.update { current -> current.copy(news = news) }
                }
        }
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            val digest = container.database.digestDao().byDate(today)
            val hasKey = container.secretStore.apiKey().isNotBlank()
            _state.update {
                it.copy(
                    digest = digest,
                    selectedDate = today,
                    loading = false,
                    hasApiKey = hasKey
                )
            }
        }
    }

    fun onScreenResume() {
        viewModelScope.launch {
            val hasKey = container.secretStore.apiKey().isNotBlank()
            _state.update { it.copy(hasApiKey = hasKey) }
            val current = _state.value
            if (hasKey && current.digest == null && !generating &&
                current.selectedDate == LocalDate.now().toString()
            ) {
                generateToday()
            }
        }
    }

    fun generateToday() {
        if (generating) return
        generating = true
        viewModelScope.launch {
            _state.update { it.copy(generating = true, error = null) }
            try {
                val digest = container.digestGenerator.generate(LocalDate.now())
                selectedDate.value = LocalDate.now()
                _state.update {
                    it.copy(
                        digest = digest,
                        selectedDate = digest.date,
                        generating = false,
                        hasApiKey = true
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        generating = false,
                        error = t.message ?: "生成失败，请稍后重试"
                    )
                }
            } finally {
                generating = false
            }
        }
    }

    fun selectDate(date: String) {
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return
        selectedDate.value = parsed
        _state.update { it.copy(selectedDate = date, error = null) }
        viewModelScope.launch {
            val digest = container.database.digestDao().byDate(date)
            _state.update { it.copy(digest = digest) }
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DailyViewModel(container) }
        }
    }
}
