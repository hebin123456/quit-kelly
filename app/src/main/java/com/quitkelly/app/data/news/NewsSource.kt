package com.quitkelly.app.data.news

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class RawNews(
    val title: String,
    val summary: String,
    val url: String,
    val publishedAt: Long,
    val source: String,
    val timeApproximate: Boolean = false
)

interface NewsSource {
    val id: String
    val name: String
    suspend fun fetch(): List<RawNews>
}

internal val newsJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal fun OkHttpClient.fetchText(
    url: String,
    referer: String? = null,
    headers: Map<String, String> = emptyMap()
): String {
    val builder = Request.Builder()
        .url(url)
        .header(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        )
        .header("Accept", "application/json, text/plain, */*")
        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
    referer?.let { builder.header("Referer", it) }
    headers.forEach { (key, value) -> builder.header(key, value) }
    val request = builder.build()
    newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            error("HTTP ${response.code}")
        }
        return response.body?.string() ?: error("响应为空")
    }
}

internal fun JsonObject.stringOrEmpty(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

internal fun JsonObject.longOrZero(key: String): Long =
    (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

internal fun normalizeTimestamp(value: Long): Long {
    if (value <= 0L) return System.currentTimeMillis()
    return if (value < 100_000_000_000L) value * 1000L else value
}

internal fun stripHtml(input: String): String {
    return input
        .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()
}
