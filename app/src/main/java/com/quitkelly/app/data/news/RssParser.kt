package com.quitkelly.app.data.news

import java.io.StringReader
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import okhttp3.OkHttpClient
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class RssNewsSource(
    private val client: OkHttpClient,
    private val feedUrl: String,
    override val name: String
) : NewsSource {

    override val id: String = "rss:$feedUrl"

    override suspend fun fetch(): List<RawNews> {
        val text = client.fetchText(feedUrl)
        return RssParser.parse(text, name)
    }
}

object RssParser {

    private val rfc1123 = DateTimeFormatter.RFC_1123_DATE_TIME
    private val isoOffset = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    private val isoLocal = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val simple = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    private val alternative = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)

    fun parse(xml: String, sourceName: String): List<RawNews> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        val result = mutableListOf<RawNews>()
        var inItem = false
        var title = ""
        var link = ""
        var description = ""
        var date = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase(Locale.ROOT)) {
                        "item", "entry" -> {
                            inItem = true
                            title = ""
                            link = ""
                            description = ""
                            date = ""
                        }

                        "link" -> if (inItem) {
                            val href = parser.getAttributeValue(null, "href")
                            if (!href.isNullOrBlank()) {
                                link = href.trim()
                            } else {
                                val text = parser.readTextSafely()
                                if (text.startsWith("http")) link = text
                            }
                        }

                        "title" -> if (inItem) {
                            title = parser.readTextSafely()
                        }

                        "description", "summary", "content" -> if (inItem && description.isBlank()) {
                            description = parser.readTextSafely()
                        }

                        "pubdate", "published", "updated" -> if (inItem && date.isBlank()) {
                            date = parser.readTextSafely()
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val tag = parser.name.lowercase(Locale.ROOT)
                    if (tag == "item" || tag == "entry") {
                        if (inItem) {
                            val cleanTitle = stripHtml(title).trim()
                            val cleanLink = link.trim()
                            if (cleanTitle.isNotBlank() && cleanLink.startsWith("http")) {
                                result += RawNews(
                                    title = cleanTitle,
                                    summary = stripHtml(description).take(500),
                                    url = cleanLink,
                                    publishedAt = parseDate(date),
                                    source = sourceName
                                )
                            }
                        }
                        inItem = false
                    }
                }
            }
            event = parser.next()
        }
        return result.distinctBy { it.url }.take(40)
    }

    private fun XmlPullParser.readTextSafely(): String {
        return try {
            nextText().trim()
        } catch (t: Throwable) {
            ""
        }
    }

    private fun parseDate(raw: String): Long {
        val text = raw.trim()
        if (text.isEmpty()) return System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        return runCatching {
            ZonedDateTime.parse(text, rfc1123).toInstant().toEpochMilli()
        }.recoverCatching {
            ZonedDateTime.parse(text, alternative).toInstant().toEpochMilli()
        }.recoverCatching {
            OffsetDateTime.parse(text, isoOffset).toInstant().toEpochMilli()
        }.recoverCatching {
            LocalDateTime.parse(text, isoLocal).atZone(zone).toInstant().toEpochMilli()
        }.recoverCatching {
            LocalDateTime.parse(text, simple).atZone(zone).toInstant().toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
    }
}
