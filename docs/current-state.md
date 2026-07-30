# Time Flux — Current State

*Snapshot: 2026-07-29. Update this file whenever a feature lands.*

Everything through spec 001 (module registry + navigation) is committed on `master`; no uncommitted
work. Pushing needs an interactive terminal — the SSH key is passphrase-protected — so `master` may
sit ahead of `origin/master`.

---

## What runs today

A fresh install opens the **module picker** — Milestones and Mood are on, a footer names the modules
still to come, and "Start my timeline" goes through to the timeline and never shows onboarding
again.

After that the app opens on the **timeline**: a reverse-chronological list of entries with a FAB to
add one, a filter icon, a modules icon, and tap-to-open detail sheets. Two module types can be
created: Milestone and Mood. Modules can be turned on and off; a disabled module disappears from
the add sheet and the filter list while its existing entries stay on the timeline, with an opt-in
control to hide those too.

### Shared engine (`shared/`) — built

| Piece | Where | Notes |
|---|---|---|
| Timeline store | `sqldelight/com/timeflux/db/TimeFlux.sq` | `timeline_entries`, `tags`, `entry_tags`, `media_attachments`, `daily_summaries`, `monthly_summaries`, `entry_fts` (FTS5). Generated columns written by the repository. |
| Repository | `data/repository/TimelineRepositoryImpl.kt` | Cursor pagination (before/after/by-module, with optional module exclusion), `getById`, FTS `search`, tags read/write, insert/update/softDelete/restore, `observeOnThisDay`, `observeYearGrid`. |
| Domain models | `domain/model/` | `TimelineEntry`, `ModuleType` (10 values), `Tag`, `YearGridRow`, `Outcome`. |
| Module registry | `module/ModuleRegistry.kt`, `module/SettingsModuleRegistry.kt` | Enabled / hidden / first-run state over `multiplatform-settings`, keyed on `ModuleType.id`. Enforces: enabling clears hidden, only disabled modules can be hidden, unavailable modules can't be toggled. |
| Module catalogue | `module/LifeModule.kt` | `ALL_MODULES` plus `AVAILABLE_MODULES` / `UPCOMING_MODULES`; `isAvailable` marks what's actually built. |
| Modules | `module/milestone/`, `module/mood/` | Payload + Create + Update use cases each. Milestones have significance levels and category smart fields; mood has energy, a 24-word emotion vocabulary, and contextual factors. |
| Infra | `util/Ulid.kt`, `data/json/AppJson.kt`, `data/db/DriverFactory.kt` (+ android/ios), `data/db/FtsTriggers.kt`, `di/KoinSetup.kt` | FTS sync triggers are raw SQL in the driver's `onCreate` because SQLDelight can't parse FTS5 delete syntax. |

### Android app (`androidApp/`) — built

`MainActivity` → `TimeFluxTheme` → `TimeFluxNavHost` with typed routes: `TimelineRoute`,
`ModulesRoute`, `FirstRunRoute`. Start destination depends on the stored first-run flag.

- `ui/TimeFluxNavHost.kt` — navigation graph; renders nothing until the first-run flag resolves.
- `ui/timeline/TimelineScreen.kt` — Scaffold, timeline list, entry cards, active-filter chips.
- `ui/timeline/TimelineViewModel.kt` + `TimelineUiState.kt` — paging, add/update, enabled/hidden modules, snackbar messages.
- `ui/timeline/FilterBottomSheet.kt` + `TimelineFilter.kt` — module / date range / tags / significant-only.
- `ui/add/AddEntryBottomSheet.kt` — module cards driven by the enabled set, milestone form, mood form, tag input, empty state.
- `ui/timeline/EntryDetailBottomSheet.kt` — view + edit forms.
- `ui/modules/ModulesScreen.kt` + `ModulesViewModel.kt` + `ModulesUiState.kt` — the picker, in first-run or settings mode.
- `ui/ModuleUiExtensions.kt` — per-module accent colour, emoji, display name.

### Tests

17, all passing via `.\gradlew.bat :shared:testDebugUnitTest`.

- `shared/src/commonTest/.../SettingsModuleRegistryTest.kt` — 12: defaults, round-trips, persistence across instances, both registry invariants, no-ops for unavailable modules, flow re-emission, first-run flag.
- `shared/src/androidUnitTest/.../TimelineRepositoryPagingTest.kt` — 5: module exclusion, the empty-set fallback, excluded entries not consuming page slots, a full cursor walk, forward paging. Lives in `androidUnitTest` because `JdbcSqliteDriver` is JVM-only.

---

## Designed but not built

| Thing | Designed in | State in code |
|---|---|---|
| Time navigation (day/week/month/year/decade zoom) | architecture doc §Core Engine 3 | Queries exist (`selectWeekSummary`, `selectYearGrid`, `selectDecadeHighlights`); no UI, no zoom state. |
| Search UI | architecture doc §Core Engine 8 | FTS5 table, triggers and `search()` all work; **no screen calls them**. |
| On This Day | architecture doc §UI | `observeOnThisDay()` works; no screen calls it. |
| Media / photos | product decisions (1440p WebP) | `media_attachments` table + queries exist; no capture, no compression, no thumbnails, no rendering. `timeline_entries.media_uri` is written but never populated. |
| Summary tables | data-storage research | `daily_summaries` / `monthly_summaries` tables and upserts exist; **nothing ever writes rows**. |
| Export / import ZIP | v1 product decision | Not started. |
| Biometric / PIN lock | architecture doc §Core Engine 5 | Not started. |
| Notifications & reminders | architecture doc §Core Engine 4 | Not started. |
| Health Connect bridge | architecture doc §Core Engine 6 | Not started (deferred past v1 by design). |
| Settings screen (media resolution, app lock, export) | spec 001 open question 3 | Not started; the nav host is what it plugs into. |
| Modules 3–10 (journal, habits, sleep, notes, tasks, photos, goals, health) | architecture doc | `ModuleType` values exist and the picker names them as upcoming; no payloads, use cases, or forms. |
| iOS app | tech stack decision | `iosMain` has a driver, Koin module and settings binding; **no iOS UI project exists**, and the iOS source set has never been compiled (Windows host). |

---

## Known gaps and rough edges

1. **Tag and date filtering is still client-side over loaded pages only** (`TimelineScreen` filters
   `state.entries` in memory). Module *exclusion* moved into the query in spec 001 phase 5 and can
   serve as the precedent, but with 50-entry pages the remaining filters silently miss older matches.
2. **Summary tables are dead weight** until something writes them; year-grid/insights work should
   either populate them or drop them.
3. **`ModuleType.fromId()` falls back to `MILESTONE` for unknown ids** — a forward-compat guard that
   will silently mis-render entries written by a newer version. Fine for now; revisit before sync.
4. **Hiding applies to the timeline list only.** Search and On This Day have no screens yet; when
   they do, they should honour the hidden set (spec 001 open question 4).
5. **`isEnriched` is written but never read.** The add sheet's "Save — add more later" marks entries
   the user meant to come back to, and nothing surfaces or acts on it. Needs its own spec.
6. **No UI tests.** Compose screens are verified by running the app on a device.
7. **iOS is unverified.** Kotlin/Native iOS targets don't compile on a Windows host, so `iosMain`
   code — including the `NSUserDefaultsSettings` binding — has never been through a compiler.
