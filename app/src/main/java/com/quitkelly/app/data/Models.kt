package com.quitkelly.app.data

import androidx.room.ColumnInfo
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
    val fingerprint: String,
    @ColumnInfo(defaultValue = "0") val timeApproximate: Boolean = false
)

@Entity(tableName = "digests")
data class DigestEntity(
    @PrimaryKey val date: String,
    val content: String,
    val newsCount: Int,
    val model: String,
    val createdAt: Long
)

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "'kelly'") val agentId: String = "kelly"
)

@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val systemPrompt: String,
    val isBuiltIn: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["createdAt"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "''") val attachmentsJson: String = "",
    @ColumnInfo(defaultValue = "''") val sessionId: String = ""
)
