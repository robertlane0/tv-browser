package com.example.tvbrowser.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BookmarkRepository(
    private val dao: BookmarkDao,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {

    fun observeAll(): Flow<List<Bookmark>> = dao.observeAll()

    suspend fun findByOrigin(origin: String): Bookmark? =
        withContext(io) { dao.findByOrigin(origin) }

    suspend fun findById(id: Long): Bookmark? =
        withContext(io) { dao.findById(id) }

    suspend fun upsert(bookmark: Bookmark): Long =
        withContext(io) { dao.upsert(bookmark) }

    suspend fun delete(bookmark: Bookmark): Unit =
        withContext(io) { dao.delete(bookmark) }

    suspend fun touchLaunched(id: Long, ts: Long = System.currentTimeMillis()): Unit =
        withContext(io) { dao.touchLaunched(id, ts) }

    suspend fun seedPresetsIfEmpty(now: Long = System.currentTimeMillis()): Boolean =
        withContext(io) {
            if (dao.count() == 0) {
                PresetServices.all(now).forEach { dao.upsert(it) }
                true
            } else {
                false
            }
        }
}
