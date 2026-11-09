package com.quitkelly.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val model: String = "deepseek-flash",
    val dailyHour: Int = 8,
    val dailyMinute: Int = 30,
    val enabledSources: Set<String> = setOf("wscn", "sina", "eastmoney"),
    val customRssUrls: List<String> = emptyList(),
    val notificationsEnabled: Boolean = true
)

private val Context.kellyDataStore by preferencesDataStore(name = "kelly_settings")

private val KEY_MODEL = stringPreferencesKey("model")
private val KEY_HOUR = intPreferencesKey("daily_hour")
private val KEY_MINUTE = intPreferencesKey("daily_minute")
private val KEY_SOURCES = stringPreferencesKey("enabled_sources")
private val KEY_RSS = stringPreferencesKey("custom_rss")
private val KEY_NOTIFY = booleanPreferencesKey("notifications_enabled")

private const val DEFAULT_SOURCES = "wscn,sina,eastmoney"

class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.kellyDataStore

    val flow: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            AppSettings(
                model = prefs[KEY_MODEL] ?: "deepseek-flash",
                dailyHour = (prefs[KEY_HOUR] ?: 8).coerceIn(0, 23),
                dailyMinute = (prefs[KEY_MINUTE] ?: 30).coerceIn(0, 59),
                enabledSources = (prefs[KEY_SOURCES] ?: DEFAULT_SOURCES)
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet(),
                customRssUrls = parseRssUrls(prefs[KEY_RSS] ?: ""),
                notificationsEnabled = prefs[KEY_NOTIFY] ?: true
            )
        }

    suspend fun snapshot(): AppSettings = flow.first()

    suspend fun setModel(model: String) {
        dataStore.edit { it[KEY_MODEL] = model }
    }

    suspend fun setDailyTime(hour: Int, minute: Int) {
        dataStore.edit {
            it[KEY_HOUR] = hour.coerceIn(0, 23)
            it[KEY_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setEnabledSources(ids: Set<String>) {
        dataStore.edit { it[KEY_SOURCES] = ids.joinToString(",") }
    }

    suspend fun setCustomRss(raw: String) {
        dataStore.edit { it[KEY_RSS] = raw }
    }

    suspend fun setNotifications(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIFY] = enabled }
    }

    private fun parseRssUrls(raw: String): List<String> {
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()
            .toList()
    }
}
