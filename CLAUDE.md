# Time Flux — Working Agreement

Time Flux is a **modular life timeline**: every kind of life data — milestones, moods, journal
entries, sleep, habits — is a timestamped entry on one scrollable timeline you can navigate from
today back through your whole life. The thesis the product has to prove is that *two meaningfully
different data types on one timeline* is more valuable than either app alone.

Kotlin Multiplatform. Shared engine (`shared/`), native UI per platform (`androidApp/` today,
iOS later). Android is the only shipping target right now.

---

## Read this first

| Doc | What it's for |
|---|---|
| `docs/current-state.md` | **What actually exists in the code today** vs. what's only designed. Start here. |
| `docs/README.md` | How specs and plans work in this repo, and the index of all docs. |
| `docs/data-model-principles.md` | The ten rules every schema/data decision is judged against. Non-negotiable. |
| `docs/architecture-and-modules.md` | The engine/UI/module breakdown and the module priority order. |
| `docs/competitive-analysis.md` | Why each module exists and which competitor gap it fills. |

Research docs (`kmp-setup-research.md`, `data-storage-and-query-research.md`,
`storage-sync-archiving-research.md`) are background — consult when touching those areas.

---

## Scope guardrails

- **v1 is local-only.** No backend, no accounts, no cloud sync. Export/import is a ZIP the user
  controls. Don't propose sync solutions inside v1 work.
- **v2** is PowerSync + Supabase + Cloudflare R2 with client-side E2EE. **v3** is zero-knowledge.
  Today's schema must not *block* those, but must not *build* them either.
- Media is WebP q80–85, 1440p default / 1080p storage-saver, 150px thumbnails at import.
- Modules ship in priority order: Milestones → Mood → Journal → Habits → Sleep → the rest.
  Health & Body is deliberately last.

## Code conventions (as established in the existing code)

- **Every repository/use-case method returns `Outcome<T>`** (`domain/model/Outcome.kt`). Callers
  handle failures with an exhaustive `when`. No exceptions across layer boundaries.
- **IDs are ULIDs**, generated client-side via `util/Ulid.kt`. Never a server or autoincrement id.
- **Soft delete only.** `deleted_at` is set; rows are never removed. Every read filters
  `deleted_at IS NULL`.
- **Module data lives in the `payload` JSON column**, typed by a `@Serializable` payload class per
  module (`module/<name>/<Name>Payload.kt`). Adding a module must not require an `ALTER TABLE` on
  `timeline_entries`.
- **Reactive reads return `Flow`; one-shot reads and all writes are `suspend`.**
- Time columns are epoch **milliseconds UTC**. `month_day` / `year_month` / `year_week` are written
  by the repository on insert so date filters stay index-sargable — keep them in sync on any write.
- Schema changes are **numbered SQLDelight migrations**, verified at build time
  (`verifyMigrations = true`). Never edit a shipped migration.
- Never edit anything under `shared/build/generated/` — that's SQLDelight output.
- ViewModels live in `androidApp`, use cases and repositories in `shared`. UI never touches
  SQLDelight queries directly.

## Testing

Tests where they pay off — this repo does **not** mandate TDD for every change. Write tests for:
payload serialization round-trips, use-case validation rules, repository query behaviour, and any
date/time math. Compose UI is verified by running the app. `shared/src/commonTest` exists but is
currently empty; the in-memory SQLite driver is already on the test classpath.

## Commands

```
.\gradlew.bat :androidApp:assembleDebug     # build the Android app
.\gradlew.bat :shared:allTests              # run shared KMP tests
.\gradlew.bat :shared:generateSqlDelightInterface   # regenerate DB code after .sq edits
```

(Windows: use `gradlew.bat`; only the `.bat` wrapper is checked in.)

## Workflow

Non-trivial features get a **spec** (`docs/specs/NNN-slug.md`) agreed before a **plan**
(`docs/plans/NNN-slug.md`) is written, and the plan is agreed before code is written. See
`docs/README.md`. Small fixes skip straight to code.
