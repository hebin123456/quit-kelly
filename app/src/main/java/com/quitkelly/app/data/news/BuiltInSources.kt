package com.quitkelly.app.data.news

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient

class WscnSource(private val client: OkHttpClient) : NewsSource {

    override val id: String = "wscn"
    override val name: String = "华尔街见闻"

    override suspend fun fetch(): List<RawNews> {
        val text = client.fetchText(
            "https://api-one-wscn.awtmt.com/apiv1/content/lives?channel=global-channel&limit=40"
        )
        val items = newsJson.parseToJsonElement(text)
            .jsonObject["data"]?.jsonObject?.get("items")?.jsonArray ?: return emptyList()

        return items.mapNotNull { element ->
            val obj = element.jsonObject
            val content = obj.stringOrEmpty("content_text")
                .ifBlank { stripHtml(obj.stringOrEmpty("content")) }
            val title = obj.stringOrEmpty("title").ifBlank {
                content.lineSequence().firstOrNull()?.take(70).orEmpty()
            }
            val url = obj.stringOrEmpty("uri")
            if (title.isBlank() || !url.startsWith("http")) return@mapNotNull null
            RawNews(
                title = title.trim(),
                summary = content.take(500),
                url = url,
                publishedAt = normalizeTimestamp(obj.longOrZero("display_time")),
                source = name
            )
        }
    }
}

class SinaSource(private val client: OkHttpClient) : NewsSource {

    override val id: String = "sina"
    override val name: String = "新浪财经"

    override suspend fun fetch(): List<RawNews> {
        val text = client.fetchText(
            "https://feed.mix.sina.com.cn/api/roll/get?pageid=153&lid=2516&k=&num=40&page=1"
        )
        val data = newsJson.parseToJsonElement(text)
            .jsonObject["result"]?.jsonObject?.get("data")?.jsonArray ?: return emptyList()

        return data.mapNotNull { element ->
            val obj = element.jsonObject
            val title = obj.stringOrEmpty("title").trim()
            val url = obj.stringOrEmpty("url")
            if (title.isBlank() || !url.startsWith("http")) return@mapNotNull null
            RawNews(
                title = title,
                summary = stripHtml(obj.stringOrEmpty("intro")).take(500),
                url = url,
                publishedAt = normalizeTimestamp(obj.longOrZero("ctime")),
                source = name
            )
        }
    }
}

class EastmoneySource(private val client: OkHttpClient) : NewsSource {

    override val id: String = "eastmoney"
    override val name: String = "东方财富"

    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val zone = ZoneId.of("Asia/Shanghai")

    override suspend fun fetch(): List<RawNews> {
        val text = client.fetchText(
            "https://np-listapi.eastmoney.com/comm/web/getNewsByColumns?client=web&biz=web_news_col&column=350&order=1&needInteractData=0&page_index=1&page_size=40&req_trace=1"
        )
        val data = newsJson.parseToJsonElement(text)
            .jsonObject["data"]?.jsonObject?.get("list")?.jsonArray ?: return emptyList()

        return data.mapNotNull { element ->
            val obj = element.jsonObject
            val title = obj.stringOrEmpty("title").trim()
            val url = obj.stringOrEmpty("url")
            if (title.isBlank() || !url.startsWith("http")) return@mapNotNull null
            RawNews(
                title = title,
                summary = stripHtml(obj.stringOrEmpty("summary")).take(500),
                url = url,
                publishedAt = parseTime(obj.stringOrEmpty("showTime")),
                source = name
            )
        }
    }

    private fun parseTime(raw: String): Long {
        return runCatching {
            LocalDateTime.parse(raw.trim(), timeFormat)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
    }
}
