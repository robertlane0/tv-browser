package com.example.tvbrowser.data

import android.net.Uri
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["origin"], unique = false)]
)
@Parcelize
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val origin: String,
    @ColumnInfo(defaultValue = "DESKTOP") val uaMode: UaMode = UaMode.DESKTOP,
    @ColumnInfo(defaultValue = "100") val textZoomPercent: Int = 100,
    val bannerUri: String? = null,
    @ColumnInfo(defaultValue = "0") val isPreset: Boolean = false,
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLaunchedAt: Long? = null
) : Parcelable {

    fun withLaunchedAt(ts: Long): Bookmark = copy(lastLaunchedAt = ts)

    companion object {
        fun originOf(url: String): String {
            val uri = Uri.parse(url)
            val scheme = uri.scheme ?: "https"
            val host = uri.host.orEmpty()
            val port = uri.port.takeIf { it > 0 }
            val defaultPort = (scheme == "https" && port == 443) || (scheme == "http" && port == 80)
            return if (port != null && !defaultPort) "$scheme://$host:$port" else "$scheme://$host"
        }
    }
}
