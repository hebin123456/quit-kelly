package com.quitkelly.app

import android.content.Context
import com.quitkelly.app.ai.DeepSeekClient
import com.quitkelly.app.data.AppDatabase
import com.quitkelly.app.data.AttachmentStore
import com.quitkelly.app.data.NewsRepository
import com.quitkelly.app.data.SecretStore
import com.quitkelly.app.data.SettingsRepository
import com.quitkelly.app.domain.DigestGenerator
import com.quitkelly.app.util.SpeechPlayer
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val database: AppDatabase = AppDatabase.get(appContext)

    val settings = SettingsRepository(appContext)

    val secretStore = SecretStore(appContext)

    val attachmentStore = AttachmentStore(appContext)

    val speechPlayer = SpeechPlayer(appContext)

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val newsRepository = NewsRepository(database.newsDao(), settings, httpClient)

    val deepSeekClient = DeepSeekClient(httpClient)

    val digestGenerator = DigestGenerator(
        newsRepository = newsRepository,
        deepSeekClient = deepSeekClient,
        settingsRepository = settings,
        secretStore = secretStore,
        digestDao = database.digestDao()
    )
}
