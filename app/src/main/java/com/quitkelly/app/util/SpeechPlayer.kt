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
    val activeKey: String? = null,
    val error: String? = null
)

class SpeechPlayer(context: Context) : TextToSpeech.OnInitListener {

    private val _state = MutableStateFlow(SpeechState())
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    private var pending: Pair<String, String>? = null
    private var generation = 0
    private var activeChunks = 0
    private var completedChunks = 0

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
                val chunkGeneration = generationOf(utteranceId) ?: return
                if (chunkGeneration != generation) return
                completedChunks++
                if (completedChunks >= activeChunks) {
                    finish()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val chunkGeneration = generationOf(utteranceId) ?: return
                if (chunkGeneration != generation) return
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
        if (_state.value.speaking && _state.value.activeKey == key) {
            stop()
        } else {
            speak(key, text)
        }
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

        val chunks = chunk(cleanForSpeech(text))
        if (chunks.isEmpty()) {
            _state.value = _state.value.copy(error = "没有可朗读的内容")
            return
        }

        val engine = tts ?: run {
            _state.value = _state.value.copy(error = "语音引擎不可用")
            return
        }

        engine.stop()
        generation++
        val currentGeneration = generation
        activeChunks = chunks.size
        completedChunks = 0
        _state.value = SpeechState(ready = true, speaking = true, activeKey = key)

        chunks.forEachIndexed { index, chunk ->
            val utteranceId = "kelly_${currentGeneration}_$index"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            val result = engine.speak(
                chunk,
                if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                params,
                utteranceId
            )
            if (result == TextToSpeech.ERROR) {
                generation++
                finish("朗读启动失败")
                return
            }
        }
    }

    private fun finish(error: String? = null) {
        _state.value = SpeechState(
            ready = _state.value.ready,
            speaking = false,
            activeKey = null,
            error = error
        )
    }

    private fun generationOf(utteranceId: String?): Int? {
        return utteranceId?.split('_')?.getOrNull(1)?.toIntOrNull()
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

    private fun chunk(text: String, maxLength: Int = 2800): List<String> {
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
                if (lastBreak > start + maxLength / 2) {
                    split = lastBreak + 1
                }
            }
            result += text.substring(start, split).trim()
            start = split
        }
        return result.filter { it.isNotBlank() }
    }
}
