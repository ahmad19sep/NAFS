package com.ahmad.nafs.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "record_cache", primaryKeys = ["userId", "cacheKey"])
data class RecordCacheEntity(val userId: String, val cacheKey: String, val ciphertext: String, val iv: String)

@Dao
interface RecordCacheDao {
    @Query("SELECT * FROM record_cache WHERE userId = :userId AND cacheKey = :key")
    suspend fun get(userId: String, key: String): RecordCacheEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: RecordCacheEntity)
    @Query("DELETE FROM record_cache WHERE userId = :userId")
    suspend fun clear(userId: String)
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS record_cache (userId TEXT NOT NULL, cacheKey TEXT NOT NULL, ciphertext TEXT NOT NULL, iv TEXT NOT NULL, PRIMARY KEY(userId, cacheKey))")
    }
}
