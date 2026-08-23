package com.example.tvbrowser.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BookmarkDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: BookmarkDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.bookmarkDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun bookmark(
        id: Long = 0,
        title: String = "Service",
        url: String = "https://example.tv/watch",
        origin: String = "https://example.tv",
        sortOrder: Int = 0,
        createdAt: Long = 1_000L,
        uaMode: UaMode = UaMode.DESKTOP
    ) = Bookmark(
        id = id,
        title = title,
        url = url,
        origin = origin,
        uaMode = uaMode,
        sortOrder = sortOrder,
        createdAt = createdAt
    )

    @Test
    fun upsertGeneratesIdAndPersistsSpecDefaults() = runBlocking {
        val id = dao.upsert(bookmark())
        assertTrue(id > 0)
        val stored = dao.findByOrigin("https://example.tv")
        assertNotNull(stored)
        assertEquals("Service", stored!!.title)
        assertEquals(UaMode.DESKTOP, stored.uaMode)
        assertEquals(100, stored.textZoomPercent)
        assertNull(stored.bannerUri)
        assertEquals(false, stored.isPreset)
        assertEquals(0, stored.sortOrder)
        assertNull(stored.lastLaunchedAt)
        assertEquals(1_000L, stored.createdAt)
    }

    @Test
    fun observeAllSortsBySortOrderThenCreatedAt() = runBlocking {
        dao.upsert(bookmark(title = "B", origin = "https://b.tv", sortOrder = 1, createdAt = 500))
        dao.upsert(bookmark(title = "A", origin = "https://a.tv", sortOrder = 0, createdAt = 900))
        dao.upsert(bookmark(title = "C", origin = "https://c.tv", sortOrder = 1, createdAt = 100))

        val all = dao.observeAll().first()
        assertEquals(listOf("A", "C", "B"), all.map { it.title })
    }

    @Test
    fun findByOriginMissReturnsNull() = runBlocking {
        dao.upsert(bookmark())
        assertNull(dao.findByOrigin("https://other.tv"))
    }

    @Test
    fun upsertWithExistingIdReplacesInPlace() = runBlocking {
        val id = dao.upsert(bookmark())
        dao.upsert(bookmark(id = id, title = "Renamed"))
        val all = dao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Renamed", all.first().title)
        assertEquals(id, all.first().id)
    }

    @Test
    fun deleteRemovesRow() = runBlocking {
        val id = dao.upsert(bookmark())
        val stored = dao.findByOrigin("https://example.tv")!!
        dao.delete(stored)
        assertNull(dao.findByOrigin("https://example.tv"))
        assertEquals(0, dao.count())
        assertTrue(id > 0)
    }

    @Test
    fun touchLaunchedSetsTimestamp() = runBlocking {
        val id = dao.upsert(bookmark())
        dao.touchLaunched(id, 42L)
        assertEquals(42L, dao.findByOrigin("https://example.tv")!!.lastLaunchedAt)
    }

    @Test
    fun uaModeRoundTripsAllEnumValues() = runBlocking {
        UaMode.values().forEachIndexed { index, mode ->
            dao.upsert(
                bookmark(title = "m$index", origin = "https://$mode.tv", uaMode = mode)
            )
        }
        val all = dao.observeAll().first()
        assertEquals(setOf(UaMode.DESKTOP, UaMode.MOBILE, UaMode.NATIVE_TV), all.map { it.uaMode }.toSet())
        assertEquals(UaMode.NATIVE_TV, dao.findByOrigin("https://NATIVE_TV.tv")!!.uaMode)
    }

    @Test
    fun unknownPersistedUaModeFallsBackToDesktop() = runBlocking {
        db.openHelper.writableDatabase.execSQL(
            """INSERT INTO bookmarks
               (title, url, origin, uaMode, textZoomPercent, bannerUri, isPreset, sortOrder, createdAt, lastLaunchedAt)
               VALUES ('Legacy', 'https://legacy.tv', 'https://legacy.tv', 'SOMETHING_ELSE',
                       100, NULL, 0, 0, 1, NULL)"""
        )
        assertEquals(UaMode.DESKTOP, dao.findByOrigin("https://legacy.tv")!!.uaMode)
    }

    @Test
    fun countReflectsRows() = runBlocking {
        assertEquals(0, dao.count())
        dao.upsert(bookmark())
        assertEquals(1, dao.count())
    }

    @Test
    fun originOfStripsPathAndDefaultPorts() {
        assertEquals("https://a.tv", Bookmark.originOf("https://a.tv/path?q=1#frag"))
        assertEquals("https://a.tv", Bookmark.originOf("https://a.tv:443/x"))
        assertEquals("http://a.tv", Bookmark.originOf("http://a.tv:80/x"))
        assertEquals("http://a.tv:8080", Bookmark.originOf("http://a.tv:8080/x"))
    }
}
