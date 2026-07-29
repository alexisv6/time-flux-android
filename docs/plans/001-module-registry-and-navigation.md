# Plan 001 — Module registry and navigation

**Spec:** `docs/specs/001-module-registry-and-navigation.md`
**Status:** draft
**Created:** 2026-07-28

---

## Approach

Build the state layer first, in `shared`, where it can be tested without an emulator: a
`ModuleRegistry` backed by `multiplatform-settings` (already a declared, unused dependency) that
exposes `Flow<Set<ModuleType>>` and a `suspend` setter, mirroring the repository's reactive-read /
suspending-write contract. `LifeModule` gains an availability field so the UI can tell built modules
from planned ones without a second source of truth.

Then add navigation in `androidApp` with `androidx.navigation:navigation-compose` and typed routes:
`MainActivity` hosts a `NavHost` with `Timeline` and `Modules` destinations instead of calling
`TimelineScreen()` directly. The Modules screen ships read-only first so navigation can be verified
on its own, then gets its toggles, then the enabled set is consumed by the add and filter sheets.

The alternative — a `showModules` boolean and a conditional in `TimelineScreen` — is rejected in the
spec: it gives no back stack and doesn't survive a third screen.

Each phase leaves the app buildable and runnable.

## Do not

- **Do not implement any new module** (journal, habits, sleep…). Coming-soon cards only; each module
  gets its own spec.
- **Do not filter the timeline by enabled modules.** The spec explicitly keeps existing entries
  visible.
- **Do not add a bottom navigation bar.** Revisit when Search and Insights exist.
- **Do not build a general Settings screen.** Modules destination only.
- **Do not touch the SQLDelight schema.** Registry state is preferences, not timeline data.
- **Do not refactor `TimelineViewModel`'s paging or the client-side filtering gap** — real problems
  (see `docs/current-state.md`), but not this plan's.
- **Do not start iOS UI.** Shared registry only.

## Phases

### Phase 1 — Registry in shared, with persistence

**Goal:** enabled-module state exists, persists, and is observable — verifiable by tests alone,
with no UI.

**Changes**
- `shared/src/commonMain/kotlin/com/timeflux/module/LifeModule.kt` — add an availability field
  (`isAvailable: Boolean`, or a `ModuleAvailability` enum if a third state is wanted later) and set
  it true only for `MILESTONE` and `MOOD`. Keep `isEnabled` in `ALL_MODULES` as the *default*, and
  rename it to `enabledByDefault` so it can't be mistaken for live state.
- `shared/src/commonMain/kotlin/com/timeflux/module/ModuleRegistry.kt` *(new)* — interface:
  `fun observeEnabled(): Flow<Set<ModuleType>>`, `suspend fun setEnabled(type: ModuleType, enabled: Boolean)`,
  `suspend fun isEnabled(type: ModuleType): Boolean`. KDoc the contract like `TimelineRepository`.
- `shared/src/commonMain/kotlin/com/timeflux/module/SettingsModuleRegistry.kt` *(new)* — implementation
  over `ObservableSettings`. Key format `module.enabled.<ModuleType.id>` (id strings, so adding a
  module is a data change — principle 4). Unset key falls back to `enabledByDefault`. Only available
  modules can be enabled; `setEnabled` on an unavailable module is a no-op.
- `shared/src/androidMain/kotlin/com/timeflux/di/PlatformModule.kt` — provide `ObservableSettings`
  from `SharedPreferences` (`SharedPreferencesSettings`); needs the Android `Context` already
  available to that module.
- `shared/src/iosMain/kotlin/com/timeflux/di/PlatformModule.kt` — provide `ObservableSettings` from
  `NSUserDefaults`.
- `shared/src/commonMain/kotlin/com/timeflux/di/KoinSetup.kt` — bind
  `single<ModuleRegistry> { SettingsModuleRegistry(get()) }`.

**Tests** — worth it here; this is pure logic with defaults and fallbacks that are easy to get wrong.
- `shared/src/commonTest/kotlin/com/timeflux/module/SettingsModuleRegistryTest.kt` *(new)*, using
  `MapSettings` from `multiplatform-settings-test`: defaults match `ALL_MODULES`; `setEnabled`
  round-trips; a disable persists; `observeEnabled` re-emits after a write; toggling an unavailable
  module is a no-op.
- This is also the repo's first test — confirm `.\gradlew.bat :shared:allTests` actually runs and
  reports. If `multiplatform-settings-test` isn't on the classpath, add it to `commonTest` in the
  version catalog and `shared/build.gradle.kts`.

**Verify**
- Automated: `.\gradlew.bat :shared:allTests`
- Manual: none — no UI yet.

**Done when:** registry tests pass and the Android app still builds unchanged.

---

### Phase 2 — Navigation host with a read-only Modules screen

**Goal:** the app has two destinations and the user can reach the module list and come back.

**Changes**
- `gradle/libs.versions.toml` — add `navigation-compose` (2.8.x, matching the Compose BOM
  `2024.12.01`; confirm the exact patch version resolves).
- `androidApp/build.gradle.kts` — add the dependency.
- `androidApp/src/main/kotlin/com/timeflux/android/ui/TimeFluxNavHost.kt` *(new)* — `NavHost` with
  serializable typed routes `Timeline` and `Modules`; start destination `Timeline`.
- `androidApp/src/main/kotlin/com/timeflux/android/MainActivity.kt` — call `TimeFluxNavHost()`
  instead of `TimelineScreen()`.
- `androidApp/src/main/kotlin/com/timeflux/android/ui/timeline/TimelineScreen.kt` — accept an
  `onOpenModules: () -> Unit` and add an app-bar icon next to the existing filter icon. Keep the
  filter icon's behaviour untouched.
- `androidApp/src/main/kotlin/com/timeflux/android/ui/modules/ModulesScreen.kt` *(new)* — Scaffold
  with a back arrow, `LazyColumn` over `ALL_MODULES` rendering name, description, accent colour and
  emoji from `ui/ModuleUiExtensions.kt`. Read-only in this phase: available modules show a disabled
  switch, unavailable ones show a "Coming soon" chip.

**Tests** — none; this phase is composition and wiring, verified by running the app.

**Verify**
- Automated: `.\gradlew.bat :androidApp:assembleDebug`
- Manual: launch, tap the modules icon, see ten cards in priority order with two switches and eight
  "Coming soon" chips; press system back and land on the timeline with scroll position preserved.

**Done when:** both destinations are reachable and back works.

---

### Phase 3 — Live toggles

**Goal:** toggling a module writes through the registry and survives a restart.

**Changes**
- `androidApp/src/main/kotlin/com/timeflux/android/ui/modules/ModulesViewModel.kt` *(new)* — collects
  `registry.observeEnabled()` into a `StateFlow` of UI state (`List<LifeModule>` + enabled set),
  exposes `setEnabled(type, enabled)`.
- `androidApp/src/main/kotlin/com/timeflux/android/di/AppModule.kt` — register the ViewModel.
- `ModulesScreen.kt` — bind switches to the ViewModel; unavailable rows stay non-interactive.

**Tests** — none beyond Phase 1's; the ViewModel is a thin pass-through.

**Verify**
- Manual: disable Mood, confirm the switch stays off; kill the app from recents, relaunch, reopen
  Modules — Mood is still off. Re-enable and confirm it sticks.

**Done when:** toggle state persists across a cold start.

---

### Phase 4 — Enabled modules drive the add and filter sheets

**Goal:** disabling a module actually changes what the user can do.

**Changes**
- `androidApp/src/main/kotlin/com/timeflux/android/ui/timeline/TimelineViewModel.kt` — take
  `ModuleRegistry`, collect `observeEnabled()` into `TimelineUiState`.
- `androidApp/src/main/kotlin/com/timeflux/android/ui/timeline/TimelineUiState.kt` — add
  `enabledModules: Set<ModuleType>`.
- `androidApp/src/main/kotlin/com/timeflux/android/di/AppModule.kt` — extra constructor arg.
- `androidApp/src/main/kotlin/com/timeflux/android/ui/add/AddEntryBottomSheet.kt` — replace the two
  hardcoded `ModuleCard`s with a grid over enabled ∩ available modules. Add the empty state from the
  spec's last acceptance criterion: a message plus a button that navigates to Modules.
- `androidApp/src/main/kotlin/com/timeflux/android/ui/timeline/FilterBottomSheet.kt` — build module
  filter chips from the enabled set. If a filter is active for a module the user then disables, clear
  that filter rather than leaving an unreachable chip.
- `androidApp/src/main/kotlin/com/timeflux/android/ui/timeline/TimelineScreen.kt` — pass the
  navigation callback down for the add sheet's empty state.

**Tests** — none automated (Compose UI); the underlying state is already covered.

**Verify**
- Manual, against the spec's acceptance criteria: disable Mood → add sheet offers Milestone only →
  filter sheet has no Mood chip → existing mood entries still render on the timeline and still open
  in the detail sheet. Disable both → add sheet shows the empty state and its button opens Modules.

**Done when:** every acceptance criterion in the spec passes by hand.

---

### Phase 5 — Documentation

**Goal:** the repo's context reflects reality again.

**Changes**
- `docs/current-state.md` — move the module registry, module picker and navigation rows from
  "designed but not built" into "what runs today"; drop the now-stale gaps (3), (5), (6).
- `docs/README.md` — set spec 001's status to shipped.
- Spec and plan headers → `Status: shipped` / `done`.

**Done when:** a fresh reader of `current-state.md` would not be surprised by the running app.

## Risks

- **`multiplatform-settings` flow support.** Reactive reads need `ObservableSettings` plus the
  coroutines artifact; both are already declared in `shared/build.gradle.kts`, but the API surface
  (`getBooleanFlow` / `SettingsListener`) should be confirmed against version 1.2.0 before writing
  the implementation. Fallback: back the registry with an internal `MutableStateFlow` seeded from
  settings at construction, writing through on each change.
- **`SharedPreferences` needs a `Context`.** Verify the Android `PlatformModule` already receives
  one (it provides `DriverFactory`, which needs one); if not, that's a small DI change, not a
  redesign.
- **Navigation version alignment.** `navigation-compose` is versioned independently of the Compose
  BOM. If 2.8.x conflicts with Kotlin 2.3.20 or the Compose compiler plugin, drop to a hand-rolled
  sealed-class screen state for two destinations and revisit when a third screen arrives.
- **First test in the repo may surface toolchain friction** (KMP test source-set config, driver
  setup). Budget for it in Phase 1 rather than being surprised — it's a one-time cost that unblocks
  every later plan.

## Follow-ups

- Move timeline filtering from client-side to query-level (`current-state.md` gap 2) — the filter
  sheet is being touched here, which makes the gap more visible.
- Onboarding flow reusing the Modules screen (spec open question 2).
- A Settings destination for media resolution, app lock and export (open question 3) — the nav host
  built here is what it plugs into.
- Optional "hide entries from disabled modules" preference (open question 1).
