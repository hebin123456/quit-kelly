package com.quitkelly.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val dateFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)

fun formatTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(timeFormatter)

fun formatDateLabel(date: String): String = runCatching {
    LocalDate.parse(date).format(dateFormatter)
}.getOrDefault(date)

fun shortDate(date: String): String = date.takeLast(5).replace("-", "/")

fun greeting(): String {
    val hour = LocalTime.now().hour
    return when {
        hour < 6 -> "夜深了"
        hour < 11 -> "早上好"
        hour < 14 -> "中午好"
        hour < 18 -> "下午好"
        else -> "晚上好"
    }
}
