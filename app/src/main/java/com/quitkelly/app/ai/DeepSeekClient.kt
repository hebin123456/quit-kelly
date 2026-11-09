package com.quitkelly.app.ai

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class AiMessage(
    val role: String,
    val content: String,
    val images: List<String> = emptyList()
)

class ApiException(message: String) : Exception(message)

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

class DeepSeekClient(baseClient: OkHttpClient) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = baseClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun complete(
        apiKey: String,
        model: String,
        messages: List<AiMessage>,
        temperature: Double? = 0.7,
        maxTokens: Int? = null
    ): String = withContext(Dispatchers.IO) {
        val request = buildRequest(
            apiKey = apiKey,
            payload = buildPayload(model, messages, stream = false, temperature = temperature, maxTokens = maxTokens)
        )
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(friendlyError(response.code, body))
            }
            val content = runCatching {
                json.parseToJsonElement(body)
                    .jsonObject["choices"]?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("message")?.jsonObject
                    ?.get("content")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            val result = content?.trim()
            if (result.isNullOrBlank()) throw ApiException("模型返回了空内容")
            result
        }
    }

    fun stream(
        apiKey: String,
        model: String,
        messages: List<AiMessage>,
        temperature: Double = 0.7
    ): Flow<String> = flow {
        val request = buildRequest(
            apiKey = apiKey,
            payload = buildPayload(model, messages, stream = true, temperature = temperature, maxTokens = null)
        )
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException(friendlyError(response.code, response.body?.string().orEmpty()))
            }
            val source = response.body?.source() ?: throw ApiException("响应为空")
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty()) continue
                if (data == "[DONE]") break
                val delta = runCatching {
                    json.parseToJsonElement(data)
                        .jsonObject["choices"]?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.get("delta")?.jsonObject
                        ?.get("content")?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                if (!delta.isNullOrEmpty()) emit(delta)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequest(apiKey: String, payload: String): Request {
        return Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun buildPayload(
        model: String,
        messages: List<AiMessage>,
        stream: Boolean,
        temperature: Double?,
        maxTokens: Int?
    ): String {
        return buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray {
                messages.forEach { message ->
                    add(buildJsonObject {
                        put("role", message.role)
                        if (message.images.isEmpty()) {
                            put("content", message.content)
                        } else {
                            put("content", buildJsonArray {
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", message.content)
                                })
                                message.images.forEach { dataUrl ->
                                    add(buildJsonObject {
                                        put("type", "image_url")
                                        put("image_url", buildJsonObject {
                                            put("url", dataUrl)
                                            put("detail", "auto")
                                        })
                                    })
                                }
                            })
                        }
                    })
                }
            })
            put("stream", stream)
            temperature?.let { put("temperature", it) }
            maxTokens?.let { put("max_tokens", it) }
        }.toString()
    }

    private fun friendlyError(code: Int, body: String): String {
        val detail = runCatching {
            json.parseToJsonElement(body)
                .jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return when (code) {
            401 -> "API Key 无效或已过期，请到设置中检查"
            402 -> "DeepSeek 账户余额不足，请先充值"
            429 -> "请求过于频繁，请稍后再试"
            500, 502, 503 -> "DeepSeek 服务暂时不可用（HTTP $code）"
            else -> "请求失败（HTTP $code）" + (detail?.let { "：$it" } ?: "")
        }
    }
}
