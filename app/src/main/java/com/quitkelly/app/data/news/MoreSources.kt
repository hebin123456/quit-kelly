package com.quitkelly.app.data.news

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient

class YicaiSource(private val client: OkHttpClient) : NewsSource {

    override val id: String = "yicai"
    override val name: String = "第一财经"

    private val zone = ZoneId.of("Asia/Shanghai")
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override suspend fun fetch(): List<RawNews> {
        val html = client.fetchText("https://www.yicai.com/news/")
        val arrayText = extractJsonArray(html, "var firstlist = [") ?: return emptyList()
        val items = runCatching {
            newsJson.parseToJsonElement(arrayText).jsonArray
        }.getOrNull() ?: return emptyList()

        return items.mapNotNull { element ->
            val obj = element.jsonObject
            val url = obj.stringOrEmpty("url")
            if (!url.startsWith("/news/")) return@mapNotNull null
            val title = stripHtml(obj.stringOrEmpty("NewsTitle")).trim()
            if (title.isBlank()) return@mapNotNull null
            RawNews(
                title = title.take(120),
                summary = stripHtml(obj.stringOrEmpty("NewsNotes")).take(300),
                url = "https://www.yicai.com$url",
                publishedAt = parseTime(obj.stringOrEmpty("CreateDate")),
                source = name
            )
        }.distinctBy { it.url }.take(40).toList()
    }

    private fun parseTime(raw: String): Long {
        return runCatching {
            LocalDateTime.parse(raw.trim(), isoFormatter)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
    }
}

class TwentyOneSource(private val client: OkHttpClient) : NewsSource {

    override val id: String = "21jingji"
    override val name: String = "21世纪经济报道"

    private val zone = ZoneId.of("Asia/Shanghai")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    private val hrefFirst = Regex(
        """<a[^>]*href="(https?://(?:m|www)\.21jingji\.com/article/(\d{8})/[^"]+\.html)"[^>]*title="([^"]+)"[^>]*>""",
        RegexOption.IGNORE_CASE
    )
    private val titleFirst = Regex(
        """<a[^>]*title="([^"]+)"[^>]*href="(https?://(?:m|www)\.21jingji\.com/article/(\d{8})/[^"]+\.html)"[^>]*>""",
        RegexOption.IGNORE_CASE
    )

    override suspend fun fetch(): List<RawNews> {
        val html = client.fetchText("https://www.21jingji.com/")
        val result = mutableListOf<RawNews>()

        hrefFirst.findAll(html).forEach { match ->
            result += toNews(
                url = match.groupValues[1],
                date = match.groupValues[2],
                title = match.groupValues[3]
            )
        }
        titleFirst.findAll(html).forEach { match ->
            result += toNews(
                url = match.groupValues[2],
                date = match.groupValues[3],
                title = match.groupValues[1]
            )
        }

        return result
            .filter { it.title.isNotBlank() && it.title.length >= 6 }
            .distinctBy { it.url }
            .take(40)
    }

    private fun toNews(url: String, date: String, title: String): RawNews {
        val published = runCatching {
            LocalDate.parse(date, dateFormatter)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
        return RawNews(
            title = stripHtml(title).trim().take(120),
            summary = "",
            url = url,
            publishedAt = published,
            source = name,
            timeApproximate = true
        )
    }
}

class NeteaseSource(private val client: OkHttpClient) : NewsSource {

    override val id: String = "netease"
    override val name: String = "网易财经"

    private val pattern = Regex(
        """<a[^>]*href="(https://www\.163\.com/money/article/[A-Za-z0-9]+\.html)"[^>]*title="([^"]+)"[^>]*>""",
        RegexOption.IGNORE_CASE
    )

    override suspend fun fetch(): List<RawNews> {
        val html = client.fetchText("https://money.163.com/")
        val now = System.currentTimeMillis()
        return pattern.findAll(html)
            .mapIndexedNotNull { index, match ->
                val url = match.groupValues[1]
                val title = stripHtml(match.groupValues[2]).trim()
                if (title.length < 6) return@mapIndexedNotNull null
                RawNews(
                    title = title.take(120),
                    summary = "",
                    url = url,
                    publishedAt = now - index * 60_000L,
                    source = name,
                    timeApproximate = true
                )
            }
            .distinctBy { it.url }
            .take(40)
            .toList()
    }
}

private fun extractJsonArray(html: String, marker: String): String? {
    val markerIndex = html.indexOf(marker)
    if (markerIndex < 0) return null
    val start = html.indexOf('[', markerIndex)
    if (start < 0) return null

    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until html.length) {
        val c = html[index]
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return html.substring(start, index + 1)
                }
            }
        }
    }
    return null
}
