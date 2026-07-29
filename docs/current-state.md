# Time Flux — Current State

*Snapshot: 2026-07-28. Update this file whenever a feature lands.*

Last commit: `b182b3e` "Timeline filtering: filter sheet, active chips, client-side matching".
**13 files of uncommitted work** are in the tree (entry detail sheet + update use cases +
mood/milestone payload expansion) — commit or discard before starting new work.

---

## What runs today

Launch the app and you get **one screen**: a reverse-chronological timeline of entries, with a FAB
to add one, a filter icon in the app bar, and tap-to-open detail sheets. Two module types can be
created: Milestone and Mood. That's the whole app.

### Shared engine (`shared/`) — built

| Piece | Where | Notes |
|---|---|---|
| Timeline store | `sqldelight/com/timeflux/db/TimeFlux.sq` | `timeline_entries`, `tags`, `entry_tags`, `media_attachments`, `daily_summaries`, `monthly_summaries`, `entry_fts` (FTS5). Generated columns written by the repository. |
| Repository | `data/repository/TimelineRepositoryImpl.kt` | Cursor pagination (before/after/by-module), `getById`, FTS `search`, tags read/write, insert/update/softDelete/restore, `observeOnThisDay`, `observeYearGrid`. |
| Domain models | `domain/model/` | `TimelineEntry`, `ModuleType` (10 values), `Tag`, `YearGridRow`, `Outcome`. |
| Modules | `module/milestone/`, `module/mood/` | Payload + Create + Update use cases each. Milestones have significance levels and category smart fields; mood has energy, a 24-word emotion vocabulary, and contextual factors. |
| Infra | `util/Ulid.kt`, `data/json/AppJson.kt`, `data/db/DriverFactory.kt` (+ android/ios), `data/db/FtsTriggers.kt`, `di/KoinSetup.kt` | FTS sync triggers are raw SQL in the driver's `onCreate` because SQLDelight can't parse FTS5 delete syntax. |

### Android app (`androidApp/`) — built

`MainActivity` → `TimeFluxTheme` → `TimelineScreen`. No navigation host, one destination.

- `ui/timeline/TimelineScreen.kt` — Scaffold, timeline list, entry cards, active-filter chips.
- `ui/timeline/TimelineViewModel.kt` + `TimelineUiState.kt` — paging, add/update, snackbar messages.
- `ui/timeline/FilterBottomSheet.kt` + `TimelineFilter.kt` — module / date range / tags / significant-only.
- `ui/add/AddEntryBottomSheet.kt` — module picker (2 hardcoded cards), milestone form, mood form, tag input.
- `ui/timeline/EntryDetailBottomSheet.kt` — view + edit forms. *(uncommitted)*
- `ui/ModuleUiExtensions.kt` — per-module accent colour, emoji, display name.

---

## Designed but not built

| Thing | Designed in | State in code |
|---|---|---|
| Module registry (enable/disable modules) | architecture doc §Core Engine 2 | `module/LifeModule.kt` + `ALL_MODULES` exist and **nothing references them**. No persistence, no UI. |
| Module picker screen | architecture doc §UI | Not started. |
| Time navigation (day/week/month/year/decade zoom) | architecture doc §Core Engine 3 | Queries exist (`selectWeekSummary`, `selectYearGrid`, `selectDecadeHighlights`); no UI, no zoom state. |
| Search UI | architecture doc §Core Engine 8 | FTS5 table, triggers and `search()` all work; **no screen calls them**. |
| On This Day | architecture doc §UI | `observeOnThisDay()` works; no screen calls it. |
| Media / photos | product decisions (1440p WebP) | `media_attachments` table + `insertMediaAttachment`/`selectMediaForEntry` exist; no capture, no compression, no thumbnails, no rendering. `timeline_entries.media_uri` is written but never populated. |
| Summary tables | data-storage research | `daily_summaries` / `monthly_summaries` tables and upserts exist; **nothing ever writes rows**. |
| Export / import ZIP | v1 product decision | Not started. |
| Biometric / PIN lock | architecture doc §Core Engine 5 | Not started. |
| Notifications & reminders | architecture doc §Core Engine 4 | Not started. |
| Health Connect bridge | architecture doc §Core Engine 6 | Not started (deferred past v1 by design). |
| Modules 3–10 (journal, habits, sleep, notes, tasks, photos, goals, health) | architecture doc | `ModuleType` values exist; no payloads, use cases, or forms. |
| iOS app | tech stack decision | `iosMain` has a driver + Koin module and the framework builds; **no iOS UI project exists**. |

---

## Known gaps and rough edges

1. **No tests at all.** `shared/src/commonTest` is empty; the in-memory SQLite driver is already on
   the test classpath, so there's no setup cost blocking the first test.
2. **Filtering is client-side over loaded pages only** (`TimelineScreen` filters `state.entries`
   in memory). With 50-entry pages, filtering a long timeline silently misses older matches —
   filters need to move into the query layer as data grows.
3. **`ALL_MODULES` claims 10 modules; 2 exist.** Any picker UI must distinguish available from
   coming-soon or it will offer forms that don't exist.
4. **Summary tables are dead weight** until something writes them; year-grid/insights work should
   either populate them or drop them.
5. **`multiplatform-settings` is a declared dependency with zero usages** — it's the obvious home
   for module enable/disable state and app preferences.
6. **No navigation dependency.** Adding a second screen means choosing a navigation approach.
7. **`ModuleType.fromId()` falls back to `MILESTONE` for unknown ids** — a forward-compat guard that
   will silently mis-render entries written by a newer version. Fine for now; revisit before sync.
8. Build/test commands in `CLAUDE.md` have not been re-verified in this session.
