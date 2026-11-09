package com.quitkelly.app.data

import com.quitkelly.app.data.news.EastmoneySource
import com.quitkelly.app.data.news.RawNews
import com.quitkelly.app.data.news.RssNewsSource
import com.quitkelly.app.data.news.SinaSource
import com.quitkelly.app.data.news.WscnSource
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

data class FetchResult(
    val fetched: Int,
    val added: Int,
    val errors: List<String>
)

class NewsRepository(
    private val newsDao: NewsDao,
    private val settings: SettingsRepository,
    private val client: OkHttpClient
) {

    suspend fun fetchAll(): FetchResult = withContext(Dispatchers.IO) {
        val config = settings.snapshot()
        val sources = buildList {
            if ("wscn" in config.enabledSources) add(WscnSource(client))
            if ("sina" in config.enabledSources) add(SinaSource(client))
            if ("eastmoney" in config.enabledSources) add(EastmoneySource(client))
            config.customRssUrls.forEachIndexed { index, url ->
                add(RssNewsSource(client, url, "自定义源 ${index + 1}"))
            }
        }
        if (sources.isEmpty()) {
            return@withContext FetchResult(0, 0, listOf("未启用任何新闻源"))
        }

        val results = coroutineScope {
            sources.map { source ->
                async { source to runCatching { source.fetch() } }
            }.awaitAll()
        }

        val collected = mutableListOf<RawNews>()
        val errors = mutableListOf<String>()
        results.forEach { (source, outcome) ->
            outcome.onSuccess { collected += it }
                .onFailure { errors += "${source.name}：${it.message ?: "抓取失败"}" }
        }

        val now = System.currentTimeMillis()
        val entities = collected
            .asSequence()
            .filter { it.title.isNotBlank() && it.url.startsWith("http") }
            .distinctBy { fingerprint(it.title) }
            .map { item ->
                NewsItemEntity(
                    title = item.title.take(200),
                    summary = item.summary.take(800),
                    url = item.url,
                    source = item.source,
                    publishedAt = item.publishedAt,
                    fetchedAt = now,
                    fingerprint = fingerprint(item.title)
                )
            }
            .toList()

        val ids = newsDao.insertAll(entities)
        newsDao.purgeBefore(now - 7L * 24 * 60 * 60 * 1000)
        FetchResult(
            fetched = collected.size,
            added = ids.count { it != -1L },
            errors = errors
        )
    }

    fun observeForDate(date: LocalDate): Flow<List<NewsItemEntity>> {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return newsDao.betweenFlow(start, end, 40)
    }

    suspend fun forDate(date: LocalDate): List<NewsItemEntity> {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return newsDao.since(start, 60).filter { it.publishedAt < end }
    }

    suspend fun recent(limit: Int = 40): List<NewsItemEntity> {
        val since = System.currentTimeMillis() - 48L * 60 * 60 * 1000
        return newsDao.since(since, limit)
    }

    fun observeLatest(limit: Int = 40): Flow<List<NewsItemEntity>> = newsDao.latestFlow(limit)

    suspend fun clearAll() = newsDao.clearAll()

    companion object {
        fun fingerprint(title: String): String {
            val normalized = title.lowercase()
                .replace(Regex("[\\s\\p{Punct}，。；：！？、“”‘’（）【】《》〈〉—…·]"), "")
            val source = normalized.ifBlank { title }
            val digest = MessageDigest.getInstance("SHA-1").digest(source.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
