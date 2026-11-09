package com.quitkelly.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<NewsItemEntity>): List<Long>

    @Query("SELECT * FROM news ORDER BY publishedAt DESC LIMIT :limit")
    fun latestFlow(limit: Int): Flow<List<NewsItemEntity>>

    @Query("SELECT * FROM news WHERE publishedAt >= :start AND publishedAt < :end ORDER BY publishedAt DESC LIMIT :limit")
    fun betweenFlow(start: Long, end: Long, limit: Int): Flow<List<NewsItemEntity>>

    @Query("SELECT * FROM news WHERE publishedAt >= :since ORDER BY publishedAt DESC LIMIT :limit")
    suspend fun since(since: Long, limit: Int): List<NewsItemEntity>

    @Query("DELETE FROM news WHERE fetchedAt < :before")
    suspend fun purgeBefore(before: Long)

    @Query("DELETE FROM news")
    suspend fun clearAll()
}

@Dao
interface DigestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(digest: DigestEntity)

    @Query("SELECT * FROM digests WHERE date = :date")
    suspend fun byDate(date: String): DigestEntity?

    @Query("SELECT * FROM digests ORDER BY date DESC")
    fun allFlow(): Flow<List<DigestEntity>>

    @Query("SELECT * FROM digests ORDER BY date DESC LIMIT 1")
    suspend fun latest(): DigestEntity?
}

@Dao
interface ChatDao {

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC, id ASC")
    fun allFlow(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}
