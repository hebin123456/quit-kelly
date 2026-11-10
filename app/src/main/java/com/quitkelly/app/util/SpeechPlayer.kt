package com.quitkelly.app.util

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SpeechState(
    val ready: Boolean = false,
    val speaking: Boolean = false,
    val paused: Boolean = false,
    val activeKey: String? = null,
    val error: String? = null
)

class SpeechPlayer(context: Context) : TextToSpeech.OnInitListener {

    private val _state = MutableStateFlow(SpeechState())
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    private var pending: Pair<String, String>? = null
    private var generation = 0
    private var chunks: List<String> = emptyList()
    private var chunkIndex = 0

    private val tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            _state.value = SpeechState(ready = false, error = "语音引擎初始化失败")
            pending = null
            return
        }

        val engine = tts ?: run {
            _state.value = SpeechState(ready = false, error = "语音引擎不可用")
            return
        }
        val languageResult = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            val fallback = engine.setLanguage(Locale.CHINESE)
            if (fallback == TextToSpeech.LANG_MISSING_DATA ||
                fallback == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                _state.value = SpeechState(ready = false, error = "当前设备未安装中文语音包")
                pending = null
                return
            }
        }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                val parsed = parseUtteranceId(utteranceId) ?: return
                if (parsed.first != generation) return
                if (parsed.second != chunkIndex) return
                chunkIndex++
                if (chunkIndex >= chunks.size) {
                    finish()
                } else {
                    speakCurrentChunk(generation)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val parsed = parseUtteranceId(utteranceId) ?: return
                if (parsed.first != generation) return
                generation++
                finish("朗读失败，请检查语音引擎")
            }
        })

        _state.value = SpeechState(ready = true)
        pending?.let { (key, text) ->
            pending = null
            speak(key, text)
        }
    }

    fun toggle(key: String, text: String) {
        val current = _state.value
        if (current.speaking && current.activeKey == key) {
            stop()
        } else {
            speak(key, text)
        }
    }

    fun pause() {
        val current = _state.value
        if (!current.speaking || current.paused) return
        generation++
        tts?.stop()
        _state.value = current.copy(paused = true)
    }

    fun resume() {
        val current = _state.value
        if (!current.speaking || !current.paused) return
        generation++
        _state.value = current.copy(paused = false)
        speakCurrentChunk(generation)
    }

    fun stop() {
        generation++
        tts?.stop()
        pending = null
        finish()
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun speak(key: String, text: String) {
        if (!_state.value.ready) {
            if (_state.value.error == null) {
                pending = key to text
            }
            return
        }
        if (_state.value.error != null) {
            _state.value = _state.value.copy(error = null)
        }

        val newChunks = chunk(cleanForSpeech(text))
        if (newChunks.isEmpty()) {
            _state.value = _state.value.copy(error = "没有可朗读的内容")
            return
        }

        tts?.stop()
        generation++
        val currentGeneration = generation
        chunks = newChunks
        chunkIndex = 0
        _state.value = SpeechState(
            ready = true,
            speaking = true,
            paused = false,
            activeKey = key
        )
        speakCurrentChunk(currentGeneration)
    }

    private fun speakCurrentChunk(currentGeneration: Int) {
        if (currentGeneration != generation) return
        val engine = tts ?: run {
            generation++
            finish("语音引擎不可用")
            return
        }
        if (chunkIndex >= chunks.size) {
            finish()
            return
        }

        val utteranceId = "kelly_${currentGeneration}_$chunkIndex"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val result = engine.speak(
            chunks[chunkIndex],
            TextToSpeech.QUEUE_FLUSH,
            params,
            utteranceId
        )
        if (result == TextToSpeech.ERROR) {
            generation++
            finish("朗读启动失败")
        }
    }

    private fun finish(error: String? = null) {
        chunks = emptyList()
        chunkIndex = 0
        _state.value = SpeechState(
            ready = _state.value.ready,
            speaking = false,
            paused = false,
            activeKey = null,
            error = error
        )
    }

    private fun parseUtteranceId(utteranceId: String?): Pair<Int, Int>? {
        val parts = utteranceId?.split('_') ?: return null
        val gen = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val index = parts.getOrNull(2)?.toIntOrNull() ?: return null
        return gen to index
    }

    private fun cleanForSpeech(text: String): String {
        return text
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), " ")
            .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
            .replace(Regex("[#>*`_~]"), "")
            .replace("|", "，")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun chunk(text: String, maxLength: Int = 150): List<String> {
        if (text.length <= maxLength) {
            return listOf(text).filter { it.isNotBlank() }
        }
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = minOf(start + maxLength, text.length)
            var split = end
            if (end < text.length) {
                val searchEnd = minOf(end, text.length - 1)
                val lastBreak = text.lastIndexOfAny(
                    charArrayOf('。', '！', '？', '；', '，', '\n'),
                    searchEnd
                )
                if (lastBreak > start + maxLength / 3) {
                    split = lastBreak + 1
                }
            }
            result += text.substring(start, split).trim()
            start = split
        }
        return result.filter { it.isNotBlank() }
    }
}
