package com.quitkelly.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "news",
    indices = [
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["publishedAt"])
    ]
)
data class NewsItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val summary: String,
    val url: String,
    val source: String,
    val publishedAt: Long,
    val fetchedAt: Long,
    val fingerprint: String
)

@Entity(tableName = "digests")
data class DigestEntity(
    @PrimaryKey val date: String,
    val content: String,
    val newsCount: Int,
    val model: String,
    val createdAt: Long
)

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["createdAt"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val createdAt: Long
)
