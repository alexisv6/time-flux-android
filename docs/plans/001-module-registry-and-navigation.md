# Plan 001 — Module registry and navigation

**Spec:** `docs/specs/001-module-registry-and-navigation.md`
**Status:** in progress — Phases 1–4 of 7 landed
**Created:** 2026-07-28
**Revised:** 2026-07-28 — spec decisions closed; hide-entries and first-run phases added.

---

## Approach

Build the state layer first, in `shared`, where it can be tested without an emulator: a
`ModuleRegistry` backed by `multiplatform-settings` (already a declared, unused dependency) holding
three things — which modules are enabled, which disabled modules have their entries hidden, and
whether first run is complete. It exposes state as `Flow` and mutations as `suspend` functions,
mirroring the repository's contract. `LifeModule` gains an availability field so the UI can partition
built modules from planned ones without a second source of truth.

Then navigation in `androidApp` with `androidx.navigation:navigation-compose` and typed routes:
`MainActivity` hosts a `NavHost` with `Timeline` and `Modules` destinations instead of calling
`TimelineScreen()` directly. The alternative — a `showModules` boolean inside `TimelineScreen` — is
rejected in the spec: no back stack, and it dies at the third screen.

The Modules screen ships read-only first so navigation can be verified alone, then gets its enable
switches, then the enabled set is consumed by the add and filter sheets, then hiding, then the
first-run mode. Hiding is the only phase that touches the data layer: it must filter in the query,
not over loaded pages, or it inherits the paging bug described in `docs/current-state.md` gap 2.

Every phase leaves the app buildable and runnable.

## Do not

- **Do not implement any new module** (journal, habits, sleep…). The footer names them; each gets
  its own spec.
- **Do not add delete or bulk-delete affordances.** Hiding is not deleting.
- **Do not apply hiding to search or On This Day.** Those screens don't exist; spec open question 4.
- **Do not add a bottom navigation bar.** Revisit when Search and Insights exist.
- **Do not build a general Settings screen.** Modules destination only.
- **Do not change the SQLDelight schema.** New *queries* are fine — no DDL, no migration.
- **Do not fix the client-side filter gap wholesale.** Phase 5 moves module *exclusion* into the
  query; the existing tag/date filters stay as they are and get their own spec.
- **Do not start iOS UI.** Shared registry only.

## Phases

### Phase 1 — Registry in shared, with persistence ✅ landed 2026-07-28

**Goal:** enabled / hidden / first-run state exists, persists, and is observable — verifiable by
tests alone, with no UI.

**Changes**
- `shared/src/commonMain/kotlin/com/timeflux/module/LifeModule.kt` — add `isAvailable: Boolean`,
  true only for `MILESTONE` and `MOOD`. Rename `isEnabled` to `enabledByDefault` so it can't be
  mistaken for live state.
- `shared/src/commonMain/kotlin/com/timeflux/module/ModuleRegistry.kt` *(new)* — interface:
  - `fun observeEnabled(): Flow<Set<ModuleType>>`
  - `fun observeHidden(): Flow<Set<ModuleType>>`
  - `suspend fun setEnabled(type: ModuleType, enabled: Boolean)`
  - `suspend fun setHidden(type: ModuleType, hidden: Boolean)`
  - `suspend fun isFirstRunComplete(): Boolean` / `suspend fun completeFirstRun()`

  KDoc the contract the way `TimelineRepository` is documented.
- `shared/src/commonMain/kotlin/com/timeflux/module/SettingsModuleRegistry.kt` *(new)* —
  implementation over `ObservableSettings`. Keys `module.enabled.<id>`, `module.hidden.<id>`,
  `app.firstRunComplete` — id strings, so adding a module stays a data change (principle 4). Unset
  enabled key falls back to `enabledByDefault`; unset hidden falls back to false. Invariants
  enforced here, not in the UI: `setEnabled(type, true)` also clears that module's hidden flag
  (spec D4), `setHidden` is a no-op for an enabled or unavailable module, and `setEnabled` is a
  no-op for an unavailable module.
- `shared/src/androidMain/kotlin/com/timeflux/di/PlatformModule.kt` — provide `ObservableSettings`
  via `SharedPreferencesSettings` from the `Context` this module already has for `DriverFactory`.
- `shared/src/iosMain/kotlin/com/timeflux/di/PlatformModule.kt` — provide `ObservableSettings` from
  `NSUserDefaults`.
- `shared/src/commonMain/kotlin/com/timeflux/di/KoinSetup.kt` — `single<ModuleRegistry> { SettingsModuleRegistry(get()) }`.

**Tests** — worth it: this is pure logic, and the invariants above are exactly what a UI-only check
would miss.
- `shared/src/commonTest/kotlin/com/timeflux/module/SettingsModuleRegistryTest.kt` *(new)*, using
  `MapSettings`: defaults match `ALL_MODULES`; enable/disable round-trips and persists; hidden
  round-trips; **enabling clears hidden**; hiding an enabled module is a no-op; toggling an
  unavailable module is a no-op; `observeEnabled`/`observeHidden` re-emit after a write; first-run
  flag defaults false and sticks once set.
- This is the repo's first test — confirm `.\gradlew.bat :shared:allTests` actually runs and
  reports. If `multiplatform-settings-test` isn't on the classpath, add it to `commonTest` in the
  version catalog and `shared/build.gradle.kts`.

**Verify**
- Automated: `.\gradlew.bat :shared:allTests`
- Manual: none — no UI yet.

**Done when:** registry tests pass and the Android app still builds unchanged.

**Outcome:** 12 tests, all passing via `:shared:testDebugUnitTest`. `getBooleanFlow` worked as
documented — the `MutableStateFlow` fallback in Risks wasn't needed. Two additions beyond the plan:
`AVAILABLE_MODULES` / `UPCOMING_MODULES` derived from `ALL_MODULES`, so Phase 2's footer and the
registry's availability checks share one definition. The iOS `NSUserDefaultsSettings` binding is
**written but unverified** — Kotlin/Native iOS targets can't compile on a Windows host. It compiles
first on a Mac, or whenever the iOS app is started.

---

### Phase 2 — Navigation host with a read-only Modules screen ✅ landed 2026-07-28

**Goal:** the app has two destinations and the user can reach the module list and come back.

**Changes**
- `gradle/libs.versions.toml` — add `navigation-compose` (2.8.x alongside the Compose BOM
  `2024.12.01`; confirm the patch version resolves).
- `androidApp/build.gradle.kts` — add the dependency.
- `androidApp/src/main/kotlin/com/timeflux/android/ui/TimeFluxNavHost.kt` *(new)* — `NavHost` with
  serializable typed routes `Timeline` and `Modules`; start destination `Timeline` for now
  (Phase 6 makes it conditional).
- `androidApp/src/main/kotlin/com/timeflux/android/MainActivity.kt` — call `TimeFluxNavHost()`.
- `androidApp/src/main/kotlin/com/timeflux/android/ui/timeline/TimelineScreen.kt` — accept
  `onOpenModules: () -> Unit`; add an app-bar icon beside the existing filter icon. Leave the
  filter icon's behaviour untouched.
- `androidApp/src/main/kotlin/com/timeflux/android/ui/modules/ModulesScreen.kt` *(new)* — Scaffold
  with back arrow; rows for `ALL_MODULES.filter { it.isAvailable }` showing name, description,
  accent colour and emoji from `ui/ModuleUiExtensions.kt`; footer line built from the *unavailable*
  entries' display names, so it stays correct as modules ship. Switches present but disabled in this
  phase.

**Tests** — none; composition and wiring, verified by running the app.

**Verify**
- Automated: `.\gradlew.bat :androidApp:assembleDebug`
- Manual: launch → tap the modules icon → two rows plus a footer naming the eight unbuilt modules →
  system back returns to the timeline with scroll position preserved.

**Done when:** both destinations are reachable and back works.

**Outcome:** builds clean — `navigation-compose` 2.8.5 with typed routes works against Kotlin 2.3.20
and Compose BOM 2024.12.01, so the version-alignment risk is closed and the sealed-class fallback
isn't needed. Verified on a Galaxy S24 Ultra: app-bar icon opens the picker, both rows and the
footer render, back returns to the timeline. Switches show as checked-but-dimmed — the intended
read-only state until Phase 3.

---

### Phase 3 — Live enable/disable toggles ✅ landed 2026-07-28

**Goal:** toggling a module writes through the registry and survives a restart.

**Changes**
- `androidApp/src/main/kotlin/com/timeflux/android/ui/modules/ModulesViewModel.kt` *(new)* — collects
  `observeEnabled()` and `observeHidden()` into one UI state; exposes `setEnabled` / `setHidden`
  (the latter unused until Phase 5).
- `androidApp/src/main/kotlin/com/timeflux/android/di/AppModule.kt` — register the ViewModel.
- `ModulesScreen.kt` — bind switches to the ViewModel.

**Tests** — none beyond Phase 1; the ViewModel is a pass-through.

**Verify**
- Manual: disable Mood → switch stays off → kill from recents → relaunch → still off. Re-enable and
  confirm it sticks.

**Done when:** toggle state persists across a cold start.

**Outcome:** verified on a Galaxy S24 Ultra. Disabling Mood wrote `module.enabled.mood=false` to
`shared_prefs/time_flux_settings.xml`; after `am force-stop` and relaunch the switch was still off.
Re-enabling wrote `module.enabled.mood=true` **and** `module.hidden.mood=false` — the D4 invariant
firing on a real device, not just in tests. Note when reading prefs during testing:
`SharedPreferencesSettings` uses `apply()`, so the XML on disk can lag the in-memory state by a
moment.

Added `ModulesUiState.isLoaded`, which wasn't in the plan: `stateIn` needs an initial value, and an
empty enabled-set would render every switch off for one frame before flipping. The screen renders
no rows until the registry's first emission instead.

---

### Phase 4 — Enabled modules drive the add and filter sheets ✅ landed 2026-07-29

**Goal:** disabling a module actually changes what the user can do.

**Changes**
- `androidApp/src/main/kotlin/com/timeflux/android/ui/timeline/TimelineViewModel.kt` — take
  `ModuleRegistry`; collect `observeEnabled()` into state.
- `androidApp/src/main/kotlin/com/timeflux/android/ui/timeline/TimelineUiState.kt` — add
  `enabledModules: Set<ModuleType>`.
- `androidApp/src/main/kotlin/com/timeflux/android/di/AppModule.kt` — extra constructor arg.
- `androidApp/src/main/kotlin/com/timeflux/android/ui/add/AddEntryBottomSheet.kt` — replace the two
  hardcoded `ModuleCard`s with a grid over enabled ∩ available modules, plus the empty state from
  the spec: a message and a button that navigates to Modules.
- `androidApp/src/main/kotlin/com/timeflux/android/ui/timeline/FilterBottomSheet.kt` — build module
  chips from the enabled set.
- `TimelineViewModel` — when a module is disabled while its filter is active, clear that filter
  (spec acceptance criterion) rather than leaving an unreachable chip.
- `TimelineScreen.kt` — pass the navigation callback down for the add sheet's empty state.

**Tests** — none automated (Compose UI); the state underneath is covered by Phase 1.

**Verify**
- Manual: disable Mood → add sheet offers Milestone only → no Mood chip in the filter sheet → an
  active Mood filter clears itself → existing mood entries still render and still open. Disable both
  → add sheet shows the empty state and its button opens Modules.

**Done when:** every enable/disable acceptance criterion passes by hand.

**Outcome:** verified on a Galaxy S24 Ultra — add sheet shows both cards with both modules on, only
Milestone with Mood off, and the "No modules are enabled" state with a working *Open Modules*
button when both are off; the filter sheet drops the Mood chip when Mood is off; and mood entries
stay on the timeline throughout.

**One criterion is implemented but unverified:** clearing an active module filter when that module
is disabled. The check needs the app foregrounded through a nav round-trip and the device was in
use, so it was abandoned rather than blind-tapped. To confirm: filter to Mood, disable Mood in
Modules, return to the timeline — the Mood chip should be gone and all entries showing.

Module lists in both sheets are derived from `AVAILABLE_MODULES`, not from `Set` iteration order,
so cards and chips stay in the product's priority order as modules ship.

---

### Phase 5 — Hide entries from disabled modules

**Goal:** the escape hatch works, and it works in the query rather than over loaded pages.

**Changes**
- `shared/src/commonMain/sqldelight/com/timeflux/db/TimeFlux.sq` — add `selectPageBeforeExcluding`
  and `selectPageAfterExcluding`: the existing paging queries plus `AND module_type NOT IN :excluded`.
  New queries only — **no DDL, no migration**.
  - SQLite renders an empty collection as `NOT IN ()`, which is a syntax error. The repository must
    call the existing non-excluding query whenever the hidden set is empty. Note this in a comment
    next to the query so it isn't "simplified" later.
- `shared/src/commonMain/kotlin/com/timeflux/domain/repository/TimelineRepository.kt` — add
  `excludedModules: Set<String> = emptySet()` to `getPageBefore` / `getPageAfter`. Default keeps
  every existing caller compiling unchanged.
- `shared/src/commonMain/kotlin/com/timeflux/data/repository/TimelineRepositoryImpl.kt` — pick the
  query by whether the set is empty.
- `androidApp/.../TimelineViewModel.kt` — collect `observeHidden()`; pass the ids down on every page
  load; reload the first page when the hidden set changes.
- `androidApp/.../ui/modules/ModulesScreen.kt` — reveal a secondary "Hide these entries from my
  timeline" control on disabled rows only; enabling the module clears it (already enforced in the
  registry — the UI just reflects state).

**Tests** — worth it: this is data-layer behaviour with a genuine SQL trap.
- `shared/src/commonTest/kotlin/com/timeflux/data/repository/TimelineRepositoryPagingTest.kt` *(new)*,
  in-memory driver: excluding a module omits exactly its entries; an **empty** exclusion set behaves
  identically to the old query; a full page's worth of excluded entries doesn't truncate the page or
  break the cursor (the case a post-filter over loaded pages gets wrong).

**Verify**
- Automated: `.\gradlew.bat :shared:allTests`
- Manual: with enough mood entries to span two pages, disable Mood → hide → mood entries gone,
  scrolling still pages smoothly to the oldest entry → re-enable Mood → hide option cleared and
  entries back → kill and relaunch to confirm persistence.

**Done when:** every hiding acceptance criterion passes and paging is visibly unaffected.

---

### Phase 6 — First-run mode

**Goal:** a fresh install lands on the picker; everyone else lands on the timeline.

**Changes**
- `androidApp/src/main/kotlin/com/timeflux/android/ui/modules/ModulesScreen.kt` — add a `mode`
  parameter (first-run vs settings): first-run shows the explanatory header and a "Start my
  timeline" button, settings shows the back arrow. One composable, two modes (spec D7).
- `androidApp/src/main/kotlin/com/timeflux/android/ui/TimeFluxNavHost.kt` — start destination chosen
  by `isFirstRunComplete()`. It's a `suspend` read, so hold it as nullable state and render nothing
  until it resolves — a wrong-then-corrected start destination would flash the timeline at a new
  user. "Start my timeline" calls `completeFirstRun()` and navigates with the picker popped off the
  back stack, so system back can't return to it.
- `ModulesViewModel.kt` — expose `completeFirstRun()`.

**Tests** — the flag itself is covered by Phase 1; the flow is manual.

**Verify**
- Manual: uninstall (or clear app data) → launch → picker with Milestones and Mood on and the
  footer visible → "Start my timeline" → timeline → system back exits the app rather than returning
  to the picker → kill and relaunch → straight to the timeline.

**Done when:** first run and subsequent runs both behave as specified.

---

### Phase 7 — Documentation

**Goal:** the repo's context reflects reality again.

**Changes**
- `docs/current-state.md` — move module registry, module picker and navigation into "what runs
  today"; drop stale gaps (3), (5), (6); note that module exclusion is now query-level while tag and
  date filters are still client-side, narrowing gap (2) rather than closing it.
- `docs/README.md` — spec 001 → shipped.
- Spec and plan headers → `Status: shipped` / `done`.

**Done when:** a fresh reader of `current-state.md` would not be surprised by the running app.

## Risks

- **`multiplatform-settings` flow support.** Reactive reads need `ObservableSettings` plus the
  coroutines artifact; both are already declared in `shared/build.gradle.kts`, but the API surface
  (`getBooleanFlow` / `SettingsListener`) should be confirmed against version 1.2.0 before writing
  the implementation. Fallback: back the registry with an internal `MutableStateFlow` seeded from
  settings at construction and written through on each change.
- **`NOT IN` with an empty collection.** Called out in Phase 5 with a test for the empty case
  specifically, because it fails at runtime, not at compile time.
- **Navigation version alignment.** `navigation-compose` versions independently of the Compose BOM.
  If 2.8.x conflicts with Kotlin 2.3.20 or the Compose compiler plugin, fall back to a hand-rolled
  sealed-class screen state for two destinations and revisit at the third screen.
- **First-run start-destination flicker.** Handled by rendering nothing until the flag resolves;
  worth checking on a cold start where preferences read slowest.
- **First tests in the repo may surface toolchain friction** (KMP test source sets, in-memory driver
  setup). Budget for it in Phase 1 — a one-time cost that unblocks every later plan.

## Follow-ups

- Move tag and date filtering into the query layer (`current-state.md` gap 2), now partially
  precedented by Phase 5's excluding queries.
- Extend hiding to search and On This Day when those screens exist (spec open question 4).
- A Settings destination for media resolution, app lock and export (spec open question 3) — the nav
  host built here is what it plugs into.
- Spec the "save now, enrich later" capture model: `isEnriched` is written by the add sheet but
  nothing surfaces or acts on it yet.
