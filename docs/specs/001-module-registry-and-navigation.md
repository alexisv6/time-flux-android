# Spec 001 — Module registry and navigation

**Status:** draft
**Created:** 2026-07-28
**Plan:** `docs/plans/001-module-registry-and-navigation.md`

---

## Problem

Time Flux is pitched as a *modular* life timeline, but the app has no concept of a module the user
can choose. `ALL_MODULES` lists ten modules in `shared/src/commonMain/kotlin/com/timeflux/module/LifeModule.kt`
and nothing in the codebase reads it. The add-entry sheet hardcodes two cards. There is one screen
and no way to reach a second one, so every future surface — search, On This Day, settings, insights
— has nowhere to live.

For the user, this means the app currently presents itself as "a milestone and mood logger" rather
than "a timeline you compose from the parts of your life you care about". The modularity claim is
invisible.

## Why now

Two things are blocked on it. First, the modularity thesis is the product's core differentiator and
it is currently unproven in the running app — the module picker is the screen that makes it real.
Second, every subsequent feature needs somewhere to go, and adding a navigation host once, early,
is far cheaper than retrofitting it after three features have each grown their own bottom sheet.

It also converts `ALL_MODULES` from dead code into the thing that drives the UI, which is how the
registry was meant to work.

## User-visible behaviour

From the timeline, the user taps a modules icon in the app bar and lands on a **Modules** screen —
a full screen, not a sheet, since it's a place you visit rather than a quick action.

The screen lists every module Time Flux has or plans to have, in the product's priority order, each
as a card showing its name, a one-line description of what it adds to the timeline, and its accent
colour. Modules that are built show a toggle. Modules that aren't yet built are shown greyed with a
"Coming soon" label instead of a toggle, so the user can see where the app is going.

Turning a module off removes it from the add-entry sheet and from the filter sheet's module list.
Entries the user already logged with that module **stay on the timeline** — nothing disappears.
Turning it back on restores its creation options. The choice survives closing and reopening the app.

On first launch, Milestones and Mood are on, matching today's behaviour.

## Non-goals

- **No onboarding flow.** The module picker is reachable from settings-like navigation, not shown
  on first run. Onboarding is its own spec.
- **No per-module settings screens.** Toggle only. Mood scale configuration, habit schedules, etc.
  come with the modules that need them.
- **No new modules implemented.** Journal, Habits, Sleep and the rest stay "coming soon". This spec
  ships the mechanism, not the content.
- **No bottom navigation bar.** Two destinations don't justify one.
- **No hiding or filtering out entries from disabled modules.** See the decision below.
- **No general app-settings screen** (media resolution, app lock, export). Related but separate.
- **No iOS UI.** The registry lives in shared code so iOS can reuse it; no iOS screens are built.
- **No module reordering or favouriting.** Order is the product's priority order, fixed.

## Decisions

**Decision: module enable/disable state is stored in key-value preferences, not in the database.**
Rationale: principle 1 says the database's job is timestamped events on a timeline. Which modules a
user has enabled is a preference, not something that happened at a point in time — it doesn't
belong in `timeline_entries` and doesn't deserve its own table. `multiplatform-settings` is already
a declared dependency with zero usages and is the natural home. Rejected: a `module_config` table,
which would need a migration and would sync as timeline data later.

**Decision: the registry lives in `shared`, exposing enabled modules as a `Flow`.**
Rationale: iOS will need the same state and the same defaults. A `Flow` means the add sheet and
filter sheet react to a toggle without manual refresh plumbing. Consistent with the existing
repository contract (reactive reads are `Flow`, writes are `suspend`).

**Decision: disabling a module hides its *creation and filter* affordances only — existing entries
remain visible on the timeline.**
Rationale: a life timeline app that makes a user's logged memories vanish from a settings toggle is
committing the trust-destroying failure that principle 3 exists to prevent. The user's mental model
of "off" is "stop offering me this", not "delete my past". Rejected: filtering disabled modules out
of timeline queries — it looks identical to data loss and is unrecoverable-looking even though the
rows are intact.

**Decision: unbuilt modules are shown as "Coming soon" rather than hidden.**
Rationale: the picker is where the product communicates what Time Flux is for. Showing ten modules
with two live sets the right expectation and doubles as a roadmap; showing two makes the app look
like a two-feature app and makes the registry pointless. Rejected: hiding them, and rejected:
letting users toggle them on to an empty experience.

**Decision: navigation uses a proper navigation host with typed destinations, entered from an app
bar icon.**
Rationale: the alternative — a boolean `showModulesScreen` in `TimelineScreen` — doesn't survive the
third screen and gives no back-stack behaviour. An app bar icon rather than a bottom bar because
Modules is a settings-shaped destination, not a peer of the timeline. The bottom bar becomes right
when Search and Insights exist as genuine peers; that's a later spec's call.

## Constraints

- **Principle 1** (everything in the DB is a timestamped event) — keeps registry state out of SQLite.
- **Principle 3** (soft delete, never destroy) — its spirit drives the "entries stay visible" rule.
- **Principle 4** (new modules must not touch existing data) — the registry must key off
  `ModuleType.id` strings so adding a module is a data change, not a schema change.
- **v1 is local-only** — no account-scoped or synced preferences.
- Module order and naming follow the priority matrix in `docs/architecture-and-modules.md`.

## Acceptance criteria

- **Given** a fresh install, **When** the user opens the app, **Then** Milestones and Mood are
  enabled and every other module is off, matching today's behaviour.
- **Given** the timeline screen, **When** the user taps the modules icon in the app bar, **Then**
  the Modules screen opens and the system back gesture returns to the timeline with scroll position
  intact.
- **Given** the Modules screen, **When** it renders, **Then** all ten modules appear in priority
  order, with toggles on Milestones and Mood and a "Coming soon" label on the other eight.
- **Given** Mood is enabled, **When** the user disables it and opens the add-entry sheet, **Then**
  only the Milestone card is offered.
- **Given** Mood is disabled, **When** the user opens the filter sheet, **Then** Mood is not offered
  as a module filter.
- **Given** Mood is disabled and the timeline contains mood entries, **When** the user views the
  timeline, **Then** those entries are still shown and still open in the detail sheet.
- **Given** the user disables Mood, **When** the app is killed and relaunched, **Then** Mood is
  still disabled.
- **Given** every module is disabled, **When** the user opens the add-entry sheet, **Then** it says
  no modules are enabled and points to the Modules screen rather than showing an empty sheet.

## Open questions

1. **Should there eventually be an opt-in "hide entries from disabled modules" preference?** Some
   users may genuinely want a module's history out of view. *Proposed: defer.* Cost of getting it
   wrong now: low — it's additive later. **Deferred to a future spec.**
2. **Should the module picker also serve as first-run onboarding?** The architecture doc lists
   "Module Picker (onboarding + settings)". *Proposed: defer* — onboarding needs a welcome flow and
   copy that don't exist yet, and the same screen can be reused then. **Deferred.**
3. **Where do global app settings live** (media resolution, app lock, export)? *Proposed: a separate
   Settings destination added by whichever spec first needs one; Modules stays modules-only.*
   **Deferred, non-blocking.**
