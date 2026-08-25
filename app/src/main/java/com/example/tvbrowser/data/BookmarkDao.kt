package com.example.tvbrowser.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<Bookmark>>

    @Query("SELECT COUNT(*) FROM bookmarks")
    suspend fun count(): Int

    @Query("SELECT * FROM bookmarks WHERE origin = :origin LIMIT 1")
    suspend fun findByOrigin(origin: String): Bookmark?

    @Query("SELECT * FROM bookmarks WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): Bookmark?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookmark: Bookmark): Long

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("UPDATE bookmarks SET lastLaunchedAt = :ts WHERE id = :id")
    suspend fun touchLaunched(id: Long, ts: Long)
}
