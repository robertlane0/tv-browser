---
title: "Session and Data Persistence"
version: "1.0.0"
status: "Draft"
module: "data"
last_updated: "2026-08-22"
---

# Session and Data Persistence

## 1. Purpose

Guarantees that login sessions, watch history, and preferences survive app
restarts (plan §6), and defines the durable schema for bookmarks and settings.
Configures storage on the WebView from
[03-webview-configuration.md](./03-webview-configuration.md); consumed by the
UI in [07-ui-ux-leanback-design.md](./07-ui-ux-leanback-design.md).

## 2. Data Inventory

| Data | Store | Lifetime | Owner |
|------|-------|----------|-------|
| Login/session cookies | `CookieManager` (WebView profile) | Until site expiry or user clears | This spec §4 |
| SPA state, tokens in `localStorage` | WebView DOM storage | Until site clears or user clears | This spec §4 |
| Watch history, in-site preferences | Site-side, keyed by the above | Site-controlled | Not our data |
| Bookmarks (incl. UA mode, zoom) | Room database | Until user deletes | This spec §3 |
| Global settings | Preferences DataStore | Until cleared | This spec §5 |
| Cleartext allowlist | `network_security_config.xml` (build-time) | Per release | [02](./02-project-setup-and-dependencies.md) §5 |

## 3. Bookmark Schema (Room)

### 3.1 Entity

```kotlin
@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["origin"], unique = false)]
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val origin: String,                       // scheme + host, for match queries
    val uaMode: UaMode = UaMode.DESKTOP,      // see 04-user-agent-strategy.md
    val textZoomPercent: Int = 100,           // see 07 §7
    val bannerUri: String? = null,
    val isPreset: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLaunchedAt: Long? = null
)
```

All fields beyond the five used in
[04-user-agent-strategy.md](./04-user-agent-strategy.md) §6 tests carry
defaults, so existing construction sites remain source-compatible.

### 3.2 DDL (generated equivalent, normative shape)

```sql
CREATE TABLE IF NOT EXISTS bookmarks (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    title TEXT NOT NULL,
    url TEXT NOT NULL,
    origin TEXT NOT NULL,
    uaMode TEXT NOT NULL DEFAULT 'DESKTOP',
    textZoomPercent INTEGER NOT NULL DEFAULT 100,
    bannerUri TEXT,
    isPreset INTEGER NOT NULL DEFAULT 0,
    sortOrder INTEGER NOT NULL DEFAULT 0,
    createdAt INTEGER NOT NULL,
    lastLaunchedAt INTEGER
);
CREATE INDEX IF NOT EXISTS index_bookmarks_origin ON bookmarks(origin);
```

### 3.3 DAO

```kotlin
@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE origin = :origin LIMIT 1")
    suspend fun findByOrigin(origin: String): Bookmark?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookmark: Bookmark): Long

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("UPDATE bookmarks SET lastLaunchedAt = :ts WHERE id = :id")
    suspend fun touchLaunched(id: Long, ts: Long)
}
```

### 3.4 Migration Policy

- Schema changes MUST ship explicit `Migration` objects with default values
  (as in the v1→v2 addition of `uaMode`/`textZoomPercent` above).
  `fallbackToDestructiveMigration` is FORBIDDEN in release — bookmarks are
  user data.
- All DAO calls run on `Dispatchers.IO` ([01](./01-architecture-overview.md) §7).

## 4. Cookie and Web Storage Persistence

### 4.1 Flush Contract

```kotlin
override fun onPause() {
    webView.onPause()
    CookieManager.getInstance().flush()   // async persist of in-memory cookies
    super.onPause()
}
```

Rules:

1. `flush()` MUST be called in `BrowserActivity.onPause()` — this is the
   durability point for login cookies, watch history, and preferences.
2. `flush()` is asynchronous on all supported API levels; callers MUST NOT
   assume completion before process death. Consequently, on a clean
   background-kill the worst case is loss of cookies set in the final
   seconds — acceptable and documented.
3. Cookies MUST NOT be cleared on exit, logout-free design is intentional:
   a user logs in once per service.
4. `domStorageEnabled = true` and `databaseEnabled = true` (set in
   [03](./03-webview-configuration.md) §2) are prerequisites; without them
   SPA login state cannot persist.
5. There is exactly one WebView profile (no incognito mode). Third-party
   cookie acceptance and its privacy disclosure are specified in
   [11-security-privacy-and-drm.md](./11-security-privacy-and-drm.md) §4.

### 4.2 Process Death and Restore

- `BrowserActivity` saves the current URL and bookmark id to
  `onSaveInstanceState`; on recreation it reloads the URL rather than relying
  on WebView state restore (form fill restore is unreliable across provider
  versions).
- After restore, session cookies make re-authentication unnecessary in the
  normal case.

## 5. Global Settings (Preferences DataStore)

| Key | Type | Default | Consumed By |
|-----|------|---------|-------------|
| `global_ua_default` | string enum | `DESKTOP` | New bookmark creation ([04](./04-user-agent-strategy.md)) |
| `content_filter_enabled` | bool | `false` | [10-content-filtering-and-cleanup.md](./10-content-filtering-and-cleanup.md) |
| `webview_version_warned_major` | int | `0` | Provider gate dedup ([02](./02-project-setup-and-dependencies.md) §7) |
| `cleartext_notice_shown` | bool | `false` | HTTP indicator education ([07](./07-ui-ux-leanback-design.md) §4) |

## 6. Data Clearing and Privacy Controls

The "Clear session data" settings action ([07](./07-ui-ux-leanback-design.md) §8)
MUST, after a confirmation step:

```kotlin
suspend fun clearSessionData(context: Context) = withContext(Dispatchers.Main) {
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
    WebStorage.getInstance().deleteAllData()
    // Bookmarks and global settings are intentionally NOT touched.
}
```

This signs the user out of every service; the confirmation copy MUST say so.

## 7. Error Handling and Edge Cases

| Failure | Symptom | Handling |
|---------|---------|----------|
| `flush()` raced by process death | Last seconds of cookies lost | Documented worst case (§4.1 rule 2); no retry possible |
| Room DB corruption (power cut during write) | `SQLiteCantOpenDatabaseException` on launch | `SupportSQLiteOpenHelper` error handler: rename corrupt file, recreate empty DB, show one-time card "Service list was reset"; preset bookmarks re-seeded |
| Disk full on insert | `SQLiteFullException` | Catch in repository, surface toast "Storage full"; do not crash |
| WebView provider switch wipes nothing but changes profile internals | Rare cookie loss after provider update | Treated as site re-login; logged with old/new provider version for [12](./12-testing-and-validation-matrix.md) |
| Auto Backup restores cookies to a new device | Device-bound cookies rejected by sites | Expected; sites re-issue on login. No exclusion rules added — restoring bookmarks is the valuable part |
| Concurrent upsert from home grid and overlay | Last-write-wins | `REPLACE` conflict strategy is intentional; fields are user-scalar, no merge needed |
| User clears data while page open | Active SPA keeps in-memory state until reload | Force `webView.reload()` after clearing so the logged-out state is real |

## 8. Cross-References

- UA/zoom fields consumed by: [04-user-agent-strategy.md](./04-user-agent-strategy.md)
- Flush lifecycle hook specified by: [01-architecture-overview.md](./01-architecture-overview.md) §8
- Clearing UI: [07-ui-ux-leanback-design.md](./07-ui-ux-leanback-design.md) §8
- Privacy disclosure: [11-security-privacy-and-drm.md](./11-security-privacy-and-drm.md)
