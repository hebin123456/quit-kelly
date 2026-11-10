package com.quitkelly.app.data.news

import java.security.MessageDigest
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient

class ClsSource(private val client: OkHttpClient) : NewsSource {

    override val id: String = "cls"
    override val name: String = "财联社"

    override suspend fun fetch(): List<RawNews> {
        val params = linkedMapOf(
            "app" to "CailianpressWeb",
            "os" to "web",
            "rn" to "40",
            "sv" to "8.4.6"
        )
        val query = params.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
        val sign = md5Hex(sha1Hex(query))
        val text = client.fetchText(
            url = "https://www.cls.cn/v1/roll/get_roll_list?$query&sign=$sign",
            referer = "https://www.cls.cn/telegraph"
        )
        val items = newsJson.parseToJsonElement(text)
            .jsonObject["data"]?.jsonObject?.get("roll_data")?.jsonArray ?: return emptyList()

        return items.mapNotNull { element ->
            val obj = element.jsonObject
            val content = stripHtml(obj.stringOrEmpty("content")).trim()
                .ifBlank { stripHtml(obj.stringOrEmpty("brief")).trim() }
            if (content.isBlank()) return@mapNotNull null
            val rawTitle = stripHtml(obj.stringOrEmpty("title")).trim().ifBlank {
                content.lineSequence().firstOrNull()?.take(80).orEmpty()
            }
            if (rawTitle.isBlank()) return@mapNotNull null
            val level = obj.stringOrEmpty("level")
            val prefix = if (level == "A" || level == "B") "【重要】" else ""
            val id = obj.stringOrEmpty("id")
            RawNews(
                title = (prefix + rawTitle).take(120),
                summary = content.take(500),
                url = if (id.isNotBlank()) {
                    "https://www.cls.cn/detail/$id"
                } else {
                    "https://www.cls.cn/telegraph"
                },
                publishedAt = normalizeTimestamp(obj.longOrZero("ctime")),
                source = name
            )
        }
    }

    private fun sha1Hex(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun md5Hex(value: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

class JiemianSource(private val client: OkHttpClient) : NewsSource {

    override val id: String = "jiemian"
    override val name: String = "界面新闻"

    private val pattern = Regex(
        """<a[^>]*href="(https://www\.jiemian\.com/article/\d+\.html)"[^>]*>\s*<h3[^>]*class="card-list__title"[^>]*>([^<]+)</h3>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    override suspend fun fetch(): List<RawNews> {
        val html = client.fetchText("https://www.jiemian.com/lists/6.html")
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

class ThePaperSource(private val client: OkHttpClient) : NewsSource {

    override val id: String = "thepaper"
    override val name: String = "澎湃财经"

    private val scriptPattern = Regex(
        """<script type="application/ld\+json">([\s\S]*?)</script>""",
        RegexOption.IGNORE_CASE
    )

    override suspend fun fetch(): List<RawNews> {
        val html = client.fetchText("https://www.thepaper.cn/channel_25951")
        val jsonText = scriptPattern.findAll(html)
            .map { it.groupValues[1] }
            .firstOrNull { it.contains("itemListElement") }
            ?: return emptyList()
        val items = runCatching {
            newsJson.parseToJsonElement(jsonText)
                .jsonObject["mainEntity"]?.jsonObject
                ?.get("itemListElement")?.jsonArray
        }.getOrNull() ?: return emptyList()

        return items.mapNotNull { element ->
            val obj = element.jsonObject
            val url = obj.stringOrEmpty("url")
            val title = stripHtml(obj.stringOrEmpty("name")).trim()
            if (!url.contains("newsDetail_forward_") || title.length < 4) return@mapNotNull null
            RawNews(
                title = title.take(120),
                summary = "",
                url = url,
                publishedAt = parseTime(obj.stringOrEmpty("datePublished")),
                source = name
            )
        }.distinctBy { it.url }.take(40).toList()
    }

    private fun parseTime(raw: String): Long {
        return runCatching {
            OffsetDateTime.parse(raw.trim()).toInstant().toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
    }
}

class CaixinSource(private val client: OkHttpClient) : NewsSource {

    override val id: String = "caixin"
    override val name: String = "财新网"

    private val zone = ZoneId.of("Asia/Shanghai")
    private val pattern = Regex(
        """<li>\s*<a href="(https?://[^"]*caixin\.com/\d{4}-\d{2}-\d{2}/\d+\.html)">([^<]+)</a><span>(\d{2}-\d{2} \d{2}:\d{2})</span></li>""",
        RegexOption.IGNORE_CASE
    )

    override suspend fun fetch(): List<RawNews> {
        val html = client.fetchText("https://www.caixin.com/")
        return pattern.findAll(html).mapNotNull { match ->
            val url = match.groupValues[1]
            val title = stripHtml(match.groupValues[2]).trim()
            if (title.length < 6) return@mapNotNull null
            RawNews(
                title = title.take(120),
                summary = "",
                url = url,
                publishedAt = parseTime(match.groupValues[3]),
                source = name
            )
        }.distinctBy { it.url }.take(40).toList()
    }

    private fun parseTime(raw: String): Long {
        return runCatching {
            val now = LocalDate.now()
            val month = raw.substring(0, 2).toInt()
            val day = raw.substring(3, 5).toInt()
            val hour = raw.substring(6, 8).toInt()
            val minute = raw.substring(9, 11).toInt()
            var date = LocalDate.of(now.year, month, day)
            if (date.isAfter(now)) date = date.minusYears(1)
            date.atTime(hour, minute)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
    }
}
