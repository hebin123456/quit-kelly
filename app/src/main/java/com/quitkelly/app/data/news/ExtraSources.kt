package com.quitkelly.app.data.news

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient

class ThsSource(private val client: OkHttpClient) : NewsSource {

    override val id: String = "ths"
    override val name: String = "同花顺"

    override suspend fun fetch(): List<RawNews> {
        val text = client.fetchText(
            "https://news.10jqka.com.cn/tapp/news/push/stock/?page=1&tag=&track=website&pagesize=40"
        )
        val items = newsJson.parseToJsonElement(text)
            .jsonObject["data"]?.jsonObject?.get("list")?.jsonArray ?: return emptyList()

        return items.mapNotNull { element ->
            val obj = element.jsonObject
            val title = obj.stringOrEmpty("title").trim()
            val url = obj.stringOrEmpty("url")
            if (title.isBlank() || !url.startsWith("http")) return@mapNotNull null
            RawNews(
                title = title,
                summary = stripHtml(obj.stringOrEmpty("digest"))
                    .ifBlank { stripHtml(obj.stringOrEmpty("short")) }
                    .take(500),
                url = url,
                publishedAt = normalizeTimestamp(obj.longOrZero("ctime")),
                source = name
            )
        }
    }
}

class Jin10Source(private val client: OkHttpClient) : NewsSource {

    override val id: String = "jin10"
    override val name: String = "金十数据"

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val zone = ZoneId.of("Asia/Shanghai")

    override suspend fun fetch(): List<RawNews> {
        val text = client.fetchText(
            url = "https://flash-api.jin10.com/get_flash_list?channel=-8200&vip=1&max_time=",
            headers = mapOf(
                "x-app-id" to "bVBF4FyRTn5NJF5n",
                "x-version" to "1.0.0"
            )
        )
        val items = newsJson.parseToJsonElement(text)
            .jsonObject["data"]?.jsonArray ?: return emptyList()

        return items.mapNotNull { element ->
            val obj = element.jsonObject
            val data = obj["data"]?.jsonObject
            val content = data?.stringOrEmpty("content").orEmpty().trim()
            if (content.isBlank()) return@mapNotNull null
            val rawTitle = data?.stringOrEmpty("title").orEmpty().ifBlank {
                content.lineSequence().firstOrNull()?.take(70).orEmpty()
            }
            if (rawTitle.isBlank()) return@mapNotNull null
            val important = obj.longOrZero("important") == 1L
            val link = data?.stringOrEmpty("source_link").orEmpty()
            RawNews(
                title = if (important) "【重要】$rawTitle" else rawTitle,
                summary = content.take(500),
                url = if (link.startsWith("http")) link else "https://www.jin10.com/",
                publishedAt = parseTime(obj.stringOrEmpty("time")),
                source = name
            )
        }
    }

    private fun parseTime(raw: String): Long {
        return runCatching {
            LocalDateTime.parse(raw.trim(), formatter)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
    }
}

class NbdSource(private val client: OkHttpClient) : NewsSource {

    override val id: String = "nbd"
    override val name: String = "每日经济新闻"

    private val zone = ZoneId.of("Asia/Shanghai")
    private val pattern = Regex(
        """<a[^>]*href="(https://www\.nbd\.com\.cn/articles/(\d{4}-\d{2}-\d{2})/\d+\.html)"[^>]*?(?:title="([^"]*)")?[^>]*>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    override suspend fun fetch(): List<RawNews> {
        val html = client.fetchText("https://www.nbd.com.cn/")
        return pattern.findAll(html).mapNotNull { match ->
            val url = match.groupValues[1]
            val date = match.groupValues[2]
            val titleAttr = stripHtml(match.groupValues[4])
            val inner = stripHtml(match.groupValues[5])
            val title = titleAttr.ifBlank { inner }.trim()
            if (title.length < 6) return@mapNotNull null
            val published = runCatching {
                LocalDate.parse(date)
                    .atTime(12, 0)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            }.getOrElse { System.currentTimeMillis() }
            RawNews(
                title = title.take(120),
                summary = "",
                url = url,
                publishedAt = published,
                source = name
            )
        }.distinctBy { it.url }.take(40).toList()
    }
}
