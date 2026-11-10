package com.quitkelly.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NewsItemEntity::class,
        DigestEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun newsDao(): NewsDao

    abstract fun digestDao(): DigestDao

    abstract fun chatSessionDao(): ChatSessionDao

    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE chat_messages ADD COLUMN attachmentsJson TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "ALTER TABLE chat_messages ADD COLUMN sessionId TEXT NOT NULL DEFAULT ''"
                )
                database.execSQL(
                    "UPDATE chat_messages SET sessionId = 'default' WHERE sessionId = ''"
                )
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO chat_sessions (id, title, createdAt, updatedAt)
                    SELECT 'default', '历史对话', MIN(createdAt), MAX(createdAt)
                    FROM chat_messages
                    HAVING COUNT(*) > 0
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE news ADD COLUMN timeApproximate INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    """
                    UPDATE news
                    SET timeApproximate = 1,
                        publishedAt = MIN(
                            publishedAt,
                            CAST(strftime('%s', 'now') AS INTEGER) * 1000
                        )
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kelly.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
