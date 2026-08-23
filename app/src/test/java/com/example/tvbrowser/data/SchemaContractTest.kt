package com.example.tvbrowser.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SchemaContractTest {

    private fun schemaJson(): JSONObject {
        val relative = "schemas/com.example.tvbrowser.data.AppDatabase/1.json"
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Exported Room schema not found: tried $candidates")
        return JSONObject(file.readText())
    }

    @Test
    fun databaseIsVersionOne() {
        assertEquals(1, schemaJson().getJSONObject("database").getInt("version"))
    }

    @Test
    fun bookmarksTableMatchesNormativeDdlShape() {
        val entity = schemaJson().getJSONObject("database")
            .getJSONArray("entities")
            .let { arr -> (0 until arr.length()).map(arr::getJSONObject) }
            .first { it.getString("tableName") == "bookmarks" }

        val fields = entity.getJSONArray("fields")
            .let { arr -> (0 until arr.length()).associate { f ->
                val o = arr.getJSONObject(f)
                o.getString("columnName") to o
            } }

        val expected = mapOf(
            "id" to Triple("INTEGER", true, null as String?),
            "title" to Triple("TEXT", true, null),
            "url" to Triple("TEXT", true, null),
            "origin" to Triple("TEXT", true, null),
            "uaMode" to Triple("TEXT", true, "'DESKTOP'"),
            "textZoomPercent" to Triple("INTEGER", true, "100"),
            "bannerUri" to Triple("TEXT", false, null),
            "isPreset" to Triple("INTEGER", true, "0"),
            "sortOrder" to Triple("INTEGER", true, "0"),
            "createdAt" to Triple("INTEGER", true, null),
            "lastLaunchedAt" to Triple("INTEGER", false, null)
        )

        assertEquals(expected.keys, fields.keys)
        expected.forEach { (column, spec) ->
            val (affinity, notNull, defaultValue) = spec
            val field = fields.getValue(column)
            assertEquals("$column affinity", affinity, field.getString("affinity"))
            assertEquals("$column notNull", notNull, field.optBoolean("notNull", false))
            if (defaultValue == null) {
                assertFalse("$column must have no default", field.has("defaultValue"))
            } else {
                assertEquals("$column default", defaultValue, field.getString("defaultValue"))
            }
        }

        val primaryKey = entity.getJSONObject("primaryKey")
        assertTrue(primaryKey.getBoolean("autoGenerate"))
        assertEquals(listOf("id"), primaryKey.getJSONArray("columnNames").toList<String>())
    }

    @Test
    fun originIndexExistsAndIsNonUnique() {
        val entity = schemaJson().getJSONObject("database")
            .getJSONArray("entities")
            .let { arr -> (0 until arr.length()).map(arr::getJSONObject) }
            .first { it.getString("tableName") == "bookmarks" }

        val index = entity.getJSONArray("indices").getJSONObject(0)
        assertEquals("index_bookmarks_origin", index.getString("name"))
        assertFalse(index.getBoolean("unique"))
        assertTrue(index.getString("createSql").contains("`origin`"))
    }

    private fun <T> org.json.JSONArray.toList(): List<T> =
        (0 until length()).map { get(it) as T }
}
