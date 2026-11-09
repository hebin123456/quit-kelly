package com.quitkelly.app.domain

import com.quitkelly.app.ai.AiMessage
import com.quitkelly.app.ai.DeepSeekClient
import com.quitkelly.app.ai.Prompts
import com.quitkelly.app.data.DigestDao
import com.quitkelly.app.data.DigestEntity
import com.quitkelly.app.data.NewsRepository
import com.quitkelly.app.data.SecretStore
import com.quitkelly.app.data.SettingsRepository
import java.time.LocalDate

class DigestGenerator(
    private val newsRepository: NewsRepository,
    private val deepSeekClient: DeepSeekClient,
    private val settingsRepository: SettingsRepository,
    private val secretStore: SecretStore,
    private val digestDao: DigestDao
) {

    suspend fun generate(date: LocalDate = LocalDate.now()): DigestEntity {
        val apiKey = secretStore.apiKey()
        if (apiKey.isBlank()) {
            throw IllegalStateException("请先在设置中配置 DeepSeek API Key")
        }

        val settings = settingsRepository.snapshot()
        val fetchResult = newsRepository.fetchAll()

        var news = newsRepository.forDate(date)
        if (news.isEmpty()) {
            news = newsRepository.recent(40)
        }
        if (news.isEmpty()) {
            val detail = fetchResult.errors.firstOrNull()?.let { "（$it）" }.orEmpty()
            throw IllegalStateException("没有抓取到可用的财经新闻$detail")
        }

        val selected = news.sortedByDescending { it.publishedAt }.take(30)
        val content = deepSeekClient.complete(
            apiKey = apiKey,
            model = settings.model,
            messages = listOf(
                AiMessage("system", Prompts.DIGEST_SYSTEM),
                AiMessage("user", Prompts.digestUser(selected, date.toString()))
            ),
            temperature = 0.4
        )

        val digest = DigestEntity(
            date = date.toString(),
            content = content,
            newsCount = selected.size,
            model = settings.model,
            createdAt = System.currentTimeMillis()
        )
        digestDao.upsert(digest)
        return digest
    }
}
