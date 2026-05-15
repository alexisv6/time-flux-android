# Time Flux — Data Storage, Query Performance & Schema Research

*Research date: May 2026 | Scope: SQLite/SQLDelight best practices for a life timeline app*

---

## Quick Reference: Key Decisions

| Decision | Recommendation |
|---|---|
| Schema pattern | Base table + JSON payload + stored generated columns (Option C) |
| Tags storage | Normalized junction table (`entry_tags`) |
| Timestamp format | `INTEGER` Unix milliseconds (UTC) |
| "On This Day" | Stored `month_day` generated column + index |
| Full-text search | FTS5 virtual table with trigger sync |
| Android SQLite | `BundledSQLiteDriver` (SQLite 3.46 — FTS5 + window functions guaranteed) |
| Pagination | Cursor-based on `created_at` |
| Paging library | AndroidX Paging 3.4+ (native KMP targets) |
| Zoom aggregations | Pre-aggregated summary tables for year/decade; on-the-fly for week |
| WAL mode | Always enabled + `synchronous = NORMAL` |
| Batch writes | Single `database.transaction {}` per batch |
| Reactivity | `asFlow().mapToList(Dispatchers.IO)` + `distinctUntilChanged()` |
| SQLite page cache | `PRAGMA cache_size = -8000` (8 MB) |
| Index count | ~10–12 targeted indexes; verify with `EXPLAIN QUERY PLAN` |
| Streak calculation | Gaps-and-islands with `ROW_NUMBER()` window function |

---

## 1. Schema Design

### Why Option C: Base Table + JSON Payload

Four patterns were evaluated for storing heterogeneous entry types (mood, sleep, journal, habit, milestone, health) in one timeline store:

- **Option A (single wide table with nullable columns):** Simple but sparse rows waste pages; enforcing NOT NULL per type is impossible; adding a module requires `ALTER TABLE`.
- **Option B (base table + per-type child tables):** Correct and clean but requires up to 6 LEFT JOINs on every timeline scroll query.
- **Option C (base table + JSON blob payload):** Single table = zero joins on the hot scroll path. Schema extension = add a new `module_type` string and a new payload shape, no migration. **Recommended.**
- **Option D (separate tables per module + UNION ALL):** Query planner must run each sub-query separately; cross-module filtering requires repeating conditions in every branch. Avoid as primary pattern.

### Recommended Base Table

```sql
CREATE TABLE timeline_entries (
  id            INTEGER PRIMARY KEY,
  created_at    INTEGER NOT NULL,             -- Unix epoch milliseconds (UTC)
  ends_at       INTEGER,                      -- NULL = point-in-time; set = duration span (sleep, travel)
  module_type   TEXT NOT NULL,                -- 'mood' | 'journal' | 'sleep' | 'habit' | 'milestone' | 'health'
  title         TEXT,
  note          TEXT,                         -- short summary always shown on timeline
  media_uri     TEXT,
  location_lat  REAL,
  location_lng  REAL,
  location_name TEXT,                         -- "Austin, TX" — for cross-module search
  is_pinned     INTEGER NOT NULL DEFAULT 0,   -- milestone / pinned flag
  payload       TEXT NOT NULL DEFAULT '{}',  -- JSON blob with module-specific fields

  -- Stored generated columns for index-sargable filtering:
  month_day     TEXT GENERATED ALWAYS AS (
                  strftime('%m-%d', created_at / 1000, 'unixepoch')
                ) STORED,
  year_month    TEXT GENERATED ALWAYS AS (
                  strftime('%Y-%m', created_at / 1000, 'unixepoch')
                ) STORED,
  year_week     TEXT GENERATED ALWAYS AS (
                  strftime('%Y-%W', created_at / 1000, 'unixepoch')
                ) STORED
);
```

**Why INTEGER milliseconds?** INTEGER comparison is 10–100x faster than TEXT ISO-8601 for range scans. Store UTC, format for display in the UI layer.

**Why STORED generated columns?** Querying `WHERE strftime('%m-%d', created_at/1000, 'unixepoch') = '03-14'` is NOT index-sargable — SQLite can't use an index on a function over a column. A stored generated column is written at insert time and indexed normally. This is the key trick for "On This Day" and aggregation queries.

### Tags — Normalized Junction Table

**Do not store tags as a JSON array in the payload.** JSON array membership checks require either a full table scan or a complex expression index. A normalized junction table stays fast at any scale.

```sql
CREATE TABLE tags (
  id   INTEGER PRIMARY KEY,
  name TEXT NOT NULL UNIQUE COLLATE NOCASE
);

CREATE TABLE entry_tags (
  entry_id INTEGER NOT NULL REFERENCES timeline_entries(id) ON DELETE CASCADE,
  tag_id   INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
  PRIMARY KEY (entry_id, tag_id)
);

CREATE INDEX idx_entry_tags_tag ON entry_tags(tag_id, entry_id);
```

Benchmark: JSON array membership on unindexed columns = full table scan (~976 ms at 100K rows). Indexed junction table lookup = <1 ms.

### Duration vs Point-in-Time Entries

- `ends_at IS NULL` → point-in-time (mood, milestone, journal)
- `ends_at IS NOT NULL` → duration span (sleep, workout, travel)

For entries spanning multiple days (a week-long trip), maintain a pre-computed fan-out table:

```sql
CREATE TABLE entry_spans (
  entry_id   INTEGER NOT NULL REFERENCES timeline_entries(id) ON DELETE CASCADE,
  span_date  TEXT NOT NULL  -- 'YYYY-MM-DD' for each day covered
);
CREATE INDEX idx_spans_date ON entry_spans(span_date);
```

Only populate for entries where `ends_at - created_at > 86400000` (>24 hours). Keeps the table small.

---

## 2. Indexing Strategy

### Essential Indexes (~10 total)

```sql
-- Primary timeline scroll
CREATE INDEX idx_created_at ON timeline_entries(created_at DESC);

-- Most common combined filter: module type + date range
CREATE INDEX idx_module_created ON timeline_entries(module_type, created_at DESC);

-- "On This Day" (stored generated column)
CREATE INDEX idx_month_day ON timeline_entries(month_day);

-- Aggregation views (year grid, weekly averages)
CREATE INDEX idx_year_month ON timeline_entries(year_month);
CREATE INDEX idx_year_week  ON timeline_entries(year_week);

-- Partial indexes (small, cheap)
CREATE INDEX idx_media     ON timeline_entries(media_uri) WHERE media_uri IS NOT NULL;
CREATE INDEX idx_location  ON timeline_entries(location_name) WHERE location_name IS NOT NULL;
CREATE INDEX idx_ends_at   ON timeline_entries(ends_at) WHERE ends_at IS NOT NULL;

-- Tags (defined above in junction table)
```

**Composite index column order rule:** equality-filtered columns first, range column last. E.g., `(module_type, created_at DESC)` supports `WHERE module_type = 'mood' AND created_at BETWEEN ? AND ?`.

**Write cost:** At 10–70 inserts/day, index maintenance overhead is unmeasurable. Only concerns for bulk imports — use transactions.

---

## 3. Pagination & Infinite Scroll

### Cursor-Based Pagination (not offset)

Offset pagination (`LIMIT 50 OFFSET 1000`) forces SQLite to scan and discard 1000 rows before returning 50. At scale this is 17x slower than cursor pagination. Cursor pagination jumps directly to the anchor row via an index.

```sql
-- Scroll backward (load older entries)
selectBeforeCursor:
SELECT id, created_at, ends_at, module_type, title, note, media_uri, payload
FROM timeline_entries
WHERE (created_at, id) < (:cursor_ts, :cursor_id)   -- compound cursor breaks ties
ORDER BY created_at DESC, id DESC
LIMIT :page_size;

-- Scroll forward (load newer entries)
selectAfterCursor:
SELECT id, created_at, ends_at, module_type, title, note, media_uri, payload
FROM timeline_entries
WHERE (created_at, id) > (:cursor_ts, :cursor_id)
ORDER BY created_at ASC, id ASC
LIMIT :page_size;
```

### Recommended Page Sizes by Zoom Level

| Zoom Level | Data Source | Page Size |
|---|---|---|
| Hour / Day | Raw `timeline_entries` | 50–100 entries |
| Week | Grouped by day | 7–30 day buckets |
| Month | `daily_summaries` pre-agg | 30–90 rows |
| Year | `monthly_summaries` pre-agg | 12 rows |
| Decade | `yearly_summaries` pre-agg | 10 rows |

Load 2–3 pages ahead of the visible window. On zoom-level change, switch data source entirely — don't try to reuse the same query.

### SQLDelight + Flow

```kotlin
fun observePageBefore(beforeTs: Long, beforeId: Long, limit: Long): Flow<List<TimelineEntry>> =
    queries.selectBeforeCursor(beforeTs, beforeId, limit)
        .asFlow()
        .mapToList(Dispatchers.IO)
        .distinctUntilChanged()
```

`asFlow()` re-emits on any write to `timeline_entries`. `distinctUntilChanged()` suppresses re-emission when the visible window's data hasn't changed.

---

## 4. Multi-Zoom-Level Queries

### Pre-Aggregated Summary Tables

SQLite has no native materialized views. Use trigger-maintained or app-layer-maintained summary tables:

```sql
CREATE TABLE daily_summaries (
  date          TEXT PRIMARY KEY,   -- 'YYYY-MM-DD'
  entry_count   INTEGER NOT NULL DEFAULT 0,
  mood_avg      REAL,
  sleep_hours   REAL,
  has_journal   INTEGER NOT NULL DEFAULT 0,
  has_milestone INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE monthly_summaries (
  year_month    TEXT PRIMARY KEY,   -- 'YYYY-MM'
  entry_count   INTEGER NOT NULL DEFAULT 0,
  mood_avg      REAL,
  sleep_hours   REAL,
  habit_rate    REAL
);
```

Update these in the same transaction as the entry insert (app-layer upsert, not SQL triggers — easier to maintain).

**Rule of thumb:** Pre-aggregate when >500 rows involved OR query runs on the scroll hot path.

### Zoom-Level Query Examples

```sql
-- Week view: summary per day
selectWeekSummary:
SELECT
  date(created_at / 1000, 'unixepoch') AS entry_date,
  COUNT(*) AS entry_count,
  AVG(CASE WHEN module_type = 'mood'
      THEN CAST(json_extract(payload, '$.score') AS REAL) END) AS mood_avg,
  SUM(CASE WHEN module_type = 'sleep'
      THEN (ends_at - created_at) / 3600000.0 ELSE 0 END) AS sleep_hours
FROM timeline_entries
WHERE created_at >= :week_start AND created_at < :week_end
GROUP BY entry_date;

-- Year grid: entry count per module per month
selectYearGrid:
SELECT year_month, module_type, COUNT(*) AS count
FROM timeline_entries
WHERE year_month LIKE :year || '%'
GROUP BY year_month, module_type
ORDER BY year_month, module_type;

-- Decade view: milestones only
selectDecadeHighlights:
SELECT id, created_at, title, module_type
FROM timeline_entries
WHERE created_at >= :decade_start AND created_at < :decade_end
  AND (module_type = 'milestone' OR is_pinned = 1)
ORDER BY created_at DESC;
```

---

## 5. Full-Text Search (FTS5)

### What It Is
FTS5 maintains an inverted index — mapping every token to rows containing it. Searching `"moved to Austin"` intersects token lists for `moved`, `to`, `Austin` instead of scanning all text. Orders of magnitude faster than `LIKE '%Austin%'` at scale.

### Android Note: Use BundledSQLiteDriver
System SQLite on Android does not reliably ship with FTS5 enabled. Use `BundledSQLiteDriver` (androidx.sqlite 2.5.0+, SQLite 3.46.0) — adds ~1 MB to APK, guarantees FTS5 and window functions on all Android versions. Use the same on iOS for consistency.

### Schema

```sql
CREATE VIRTUAL TABLE entry_fts USING fts5(
  entry_id      UNINDEXED,
  title,
  note,
  body,                       -- journal body (empty for non-journal entries)
  tokenize = 'unicode61 remove_diacritics 1'
);
```

### FTS5 Sync Triggers

```sql
CREATE TRIGGER fts_after_insert AFTER INSERT ON timeline_entries BEGIN
  INSERT INTO entry_fts(entry_id, title, note, body)
  VALUES (NEW.id, NEW.title, NEW.note, json_extract(NEW.payload, '$.body'));
END;

CREATE TRIGGER fts_after_update AFTER UPDATE ON timeline_entries BEGIN
  INSERT INTO entry_fts(entry_fts, entry_id) VALUES ('delete', OLD.id);
  INSERT INTO entry_fts(entry_id, title, note, body)
  VALUES (NEW.id, NEW.title, NEW.note, json_extract(NEW.payload, '$.body'));
END;

CREATE TRIGGER fts_after_delete AFTER DELETE ON timeline_entries BEGIN
  INSERT INTO entry_fts(entry_fts, entry_id) VALUES ('delete', OLD.id);
END;
```

### Search Query with Date + Module Filter

```sql
searchWithFilter:
SELECT e.id, e.created_at, e.module_type, e.title, e.note
FROM timeline_entries e
INNER JOIN entry_fts f ON f.entry_id = e.id
WHERE entry_fts MATCH :query
  AND e.created_at >= :start_ts
  AND e.created_at < :end_ts
  AND (:module_type IS NULL OR e.module_type = :module_type)
ORDER BY f.rank
LIMIT :limit;
```

### FTS5 Index Size
~50–100% of indexed text size. For 5 years of moderate journal usage (~1825 entries, 200–500 words each), expect 5–20 MB. Acceptable on mobile. Reduce with `detail=column` if needed.

---

## 6. Aggregation & Insights

### Window Function Availability
SQLite 3.25+ (2018) has window functions. With `BundledSQLiteDriver` (3.46), they are guaranteed. Required for streak calculation.

### Key Query Examples

**Mood average per week:**
```sql
SELECT year_week,
  AVG(CAST(json_extract(payload, '$.score') AS REAL)) AS avg_mood,
  COUNT(*) AS check_ins
FROM timeline_entries
WHERE module_type = 'mood' AND created_at >= :start AND created_at < :end
GROUP BY year_week ORDER BY year_week;
```

**Sleep hours per week:**
```sql
SELECT year_week,
  SUM((ends_at - created_at) / 3600000.0) AS total_hours,
  AVG((ends_at - created_at) / 3600000.0) AS avg_hours
FROM timeline_entries
WHERE module_type = 'sleep' AND ends_at IS NOT NULL
  AND created_at >= :start AND created_at < :end
GROUP BY year_week;
```

**Habit streak (gaps-and-islands with window functions):**
```sql
WITH daily AS (
  SELECT DISTINCT date(created_at/1000, 'unixepoch') AS d,
    json_extract(payload, '$.habit_id') AS habit_id
  FROM timeline_entries WHERE module_type = 'habit'
    AND json_extract(payload, '$.habit_id') = :habit_id
),
ranked AS (
  SELECT d,
    date(d, '-' || ROW_NUMBER() OVER (ORDER BY d) || ' days') AS island_key
  FROM daily
)
SELECT MIN(d) AS streak_start, MAX(d) AS streak_end, COUNT(*) AS length
FROM ranked GROUP BY island_key ORDER BY streak_start DESC;
```

The "island key" trick: subtracting the row number of days from the date produces the same value for all consecutive days in the same streak, grouping them together.

---

## 7. Cross-Module & Tag Filtering

### Tag Filter Query
```sql
selectByTag:
SELECT e.id, e.created_at, e.module_type, e.title, e.note
FROM timeline_entries e
INNER JOIN entry_tags et ON et.entry_id = e.id
INNER JOIN tags t        ON t.id = et.tag_id
WHERE t.name = :tag_name COLLATE NOCASE
  AND (e.created_at, e.id) < (:cursor_ts, :cursor_id)
ORDER BY e.created_at DESC, e.id DESC
LIMIT :limit;
```

### Combined Filter: Date + Module Types + Tag + Media
```sql
selectFiltered:
SELECT DISTINCT e.id, e.created_at, e.module_type, e.title, e.note, e.payload
FROM timeline_entries e
LEFT JOIN entry_tags et ON et.entry_id = e.id
LEFT JOIN tags t        ON t.id = et.tag_id
WHERE e.created_at >= :start_ts AND e.created_at < :end_ts
  AND e.module_type IN :module_types
  AND (:tag IS NULL OR t.name = :tag COLLATE NOCASE)
  AND (:has_media = 0 OR e.media_uri IS NOT NULL)
ORDER BY e.created_at DESC
LIMIT :limit;
```

---

## 8. "On This Day" Query

```sql
onThisDay:
SELECT id, created_at, module_type, title, note, payload
FROM timeline_entries
WHERE month_day = :month_day   -- e.g., '03-14'
ORDER BY created_at DESC;
```

At 10,000 entries over 10 years: ~27 entries per calendar day on average. Index lookup on `month_day` scans those ~27 rows. Under 1 ms at any realistic scale.

---

## 9. WAL Mode & PRAGMA Configuration

Apply at database open time (once, before any reads/writes):

```sql
PRAGMA journal_mode = WAL;       -- readers never block writers; <1ms per write vs ~30ms
PRAGMA synchronous = NORMAL;     -- safe with WAL; removes extra fsync
PRAGMA foreign_keys = ON;
PRAGMA cache_size = -8000;       -- 8 MB page cache
PRAGMA temp_store = MEMORY;      -- temp tables in RAM
PRAGMA mmap_size = 134217728;    -- 128 MB memory-mapped I/O for reads
```

**WAL mode is the single highest-impact performance setting.** Without it, every write causes a ~30 ms fsync. With WAL + NORMAL, writes are <1 ms and reads run concurrently.

---

## 10. Write Performance & Batch Imports

Expected daily write load: 15–70 rows/day. Not a write-heavy workload — index maintenance overhead is negligible.

**Always use transactions for batch inserts:**
```kotlin
database.transaction {
    importedEntries.forEach { entry ->
        queries.insertEntry(entry.createdAt, entry.moduleType, entry.title, entry.note, entry.payload)
    }
}
```

A single transaction is 2–20x faster than individual auto-committed inserts. For imports >10,000 rows, chunk into batches of 1,000–5,000 rows per transaction to allow incremental progress and avoid holding the write lock too long.

---

## 11. SQLDelight-Specific Patterns

### `asFlow()` Behavior
Re-emits whenever **any write** touches the queried table — not just writes to the visible rows. Always add `distinctUntilChanged()`. Add `debounce(50)` if writes are high-frequency (health data sync batches).

### `executeAsList()` vs `asFlow().mapToList()`

| | `executeAsList()` | `asFlow().mapToList()` |
|---|---|---|
| Type | Synchronous, one-shot | Asynchronous, reactive |
| Thread | Caller's (must be IO) | Specify `Dispatchers.IO` |
| Use case | Migrations, tests, one-off reads | Live UI data |

Always specify `Dispatchers.IO` explicitly: `.mapToList(Dispatchers.IO)`.

### ViewModel State Pattern
```kotlin
val timelineState: StateFlow<TimelineUiState> =
    repository.observePageBefore(Long.MAX_VALUE, Long.MAX_VALUE, 50L)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TimelineUiState.Loading
        )
```

`WhileSubscribed(5000)` keeps the upstream Flow active for 5 seconds after the last subscriber disconnects — survives configuration changes without re-querying.

### Known SQLDelight 2.x Pitfalls
1. All flows on a table re-emit on any write to that table. Use `distinctUntilChanged()`.
2. Always specify `Dispatchers.IO` in `mapToList()`.
3. Don't call `database.transaction {}` inside a Flow builder — transactions are blocking.
4. FTS5 triggers must be created after the FTS table in migration order.
5. Separate named queries per zoom level (not CASE-based variable columns) — cleaner generated types.
