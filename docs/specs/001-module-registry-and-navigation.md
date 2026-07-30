# Spec 001 — Module registry and navigation

**Status:** shipped 2026-07-29
**Created:** 2026-07-28
**Decisions closed:** 2026-07-28
**Plan:** `docs/plans/001-module-registry-and-navigation.md`

---

## Problem

Time Flux is pitched as a *modular* life timeline, but the app has no concept of a module the user
can choose. `ALL_MODULES` lists ten modules in `shared/src/commonMain/kotlin/com/timeflux/module/LifeModule.kt`
and nothing in the codebase reads it. The add-entry sheet hardcodes two cards. There is one screen
and no way to reach a second one, so every future surface — search, On This Day, settings, insights
— has nowhere to live. And a new user's first experience is an empty timeline with no explanation
of what the app is for.

For the user, this means the app currently presents itself as "a milestone and mood logger" rather
than "a timeline you compose from the parts of your life you care about". The modularity claim is
invisible.

## Why now

Three things are blocked on it. The modularity thesis is the product's core differentiator and is
currently unproven in the running app — the module picker is the screen that makes it real. Every
subsequent feature needs somewhere to go, and adding a navigation host once, early, is far cheaper
than retrofitting it after three features have each grown their own bottom sheet. And first-run
onboarding has nowhere to live until the picker exists.

It also converts `ALL_MODULES` from dead code into the thing that drives the UI.

## User-visible behaviour

### First launch

A new user lands on the **Modules** screen rather than an empty timeline. A short line explains that
Time Flux is built from modules and they can change this any time. Milestones and Mood are already
switched on, so the fastest path is to press "Start my timeline" and go. Below the modules, a
quiet footer names what's coming — journal, habits, sleep, and the rest — so the user understands
the app has further to go without being shown eight switches that don't work.

They never see this screen automatically again.

### Afterwards

From the timeline, the user taps a modules icon in the app bar and reaches the same screen — a full
destination, not a sheet, since it's a place you visit rather than a quick action. Here it has a
back arrow instead of a start button.

Each built module is a card: name, a one-line description of what it adds to the timeline, its
accent colour, and a switch. Turning a module off removes it from the add-entry sheet and from the
filter sheet's module list. **Entries the user already logged stay on the timeline** — nothing
disappears from a switch.

A module that's been switched off reveals a second, secondary option: *hide these entries from my
timeline*. This is the escape hatch for someone who tried Mood for three weeks, stopped, and wants
the noise gone. Hidden entries are removed from the timeline list; they are not deleted, and turning
the module back on restores them. Switching a module back on clears the hide option — a module you
can create entries in is never one whose entries you can't see.

The choices survive closing and reopening the app.

## Non-goals

- **No multi-step onboarding.** First run shows the picker and a start button — no welcome carousel,
  no sample data, no permission requests, no account.
- **No per-module settings screens.** Toggle only. Mood scale configuration, habit schedules, etc.
  come with the modules that need them.
- **No new modules implemented.** Journal, Habits, Sleep and the rest are named in the footer only.
  This spec ships the mechanism, not the content.
- **No bottom navigation bar.** Two destinations don't justify one.
- **No individual rows or switches for unbuilt modules.**
- **Hiding is not deleting.** Hidden entries stay in the database, unmodified, and no delete or
  bulk-delete affordance is added here.
- **Hiding applies to the timeline list only.** Search and On This Day don't have screens yet; when
  they do, their spec decides whether hidden modules are excluded there too.
- **No general app-settings screen** (media resolution, app lock, export).
- **No iOS UI.** The registry lives in shared code so iOS can reuse it; no iOS screens are built.
- **No module reordering or favouriting.** Order is the product's priority order, fixed.

## Decisions

**D1 — Module state is stored in key-value preferences, not in the database.**
Rationale: principle 1 says the database's job is timestamped events on a timeline. Which modules a
user has enabled is a preference, not something that happened at a point in time. `multiplatform-settings`
is already a declared dependency with zero usages and is the natural home. Rejected: a `module_config`
table, which would need a migration and would later sync as if it were timeline data.

**D2 — The registry lives in `shared`, exposing state as a `Flow`.**
Rationale: iOS will need the same state and the same defaults. A `Flow` means the add sheet, filter
sheet and timeline react to a toggle without manual refresh plumbing. Consistent with the existing
repository contract (reactive reads are `Flow`, writes are `suspend`).

**D3 — Disabling a module hides its creation and filter affordances; existing entries stay visible
by default, with an explicit opt-in to hide them.**
Rationale: a life timeline app that makes logged memories vanish from a settings toggle commits the
trust-destroying failure principle 3 exists to prevent — so that is never the *default*. But the
user who abandoned a module has a real need, and the honest answer is to let them ask for it
deliberately rather than pretend the need doesn't exist. Two separate states, two separate
intentions. Rejected: disabling implicitly hiding entries (indistinguishable from data loss);
rejected: no escape hatch at all (leaves abandoned-module noise permanent).

**D4 — Hiding is only available for disabled modules, and enabling a module clears its hidden flag.**
Rationale: "enabled but hidden" is an incoherent state — the user could create an entry and watch it
not appear. Constraining hidden to disabled modules makes that state unreachable rather than
merely discouraged.

**D5 — Only built modules get rows; unbuilt ones are named in a footer line.**
Rationale: the picker should communicate the product's direction without listing eight specific
things the user might sit waiting for, and without eight dead switches that make the screen feel
broken. A single footer sets the expectation at a fraction of the visual weight. Rejected: ten rows
with "Coming soon" chips (advertises unshipped work as if it were nearly here); rejected: showing
nothing about future modules (makes a two-row picker look pointless and hides the thesis).

**D6 — Navigation uses a proper navigation host with typed destinations, entered from an app bar
icon.**
Rationale: the alternative — a boolean `showModulesScreen` in `TimelineScreen` — gives no back-stack
behaviour and doesn't survive the third screen. An app bar icon rather than a bottom bar because
Modules is a settings-shaped destination, not a peer of the timeline. The bottom bar becomes right
when Search and Insights exist as genuine peers; that's a later spec's call.

**D7 — First run reuses the same Modules screen in a different mode, gated on a stored completion
flag.**
Rationale: two screens that show the same list would drift apart. One composable with a mode
(first-run vs settings) differing only in the header copy and the bottom action — "Start my
timeline" versus a back arrow. The completion flag lives in the same preferences store as the rest
of the registry state.

## Constraints

- **Principle 1** (everything in the DB is a timestamped event) — keeps registry state out of SQLite.
- **Principle 3** (soft delete, never destroy) — its spirit drives the hide-vs-delete distinction.
- **Principle 4** (new modules must not touch existing data) — the registry keys off `ModuleType.id`
  strings, so adding a module is a data change, not a schema change.
- **Principle 5** (time queries are first-class) — hiding must not degrade timeline paging; it
  belongs in the query, not in a post-filter over loaded pages.
- **v1 is local-only** — no account-scoped or synced preferences.
- Module order and naming follow the priority matrix in `docs/architecture-and-modules.md`.

## Acceptance criteria

**First run**
- **Given** a fresh install, **When** the user opens the app, **Then** the Modules screen is shown
  with Milestones and Mood already enabled and a "Start my timeline" action.
- **Given** the first-run Modules screen, **When** the user presses "Start my timeline", **Then**
  the timeline opens and system back does not return to the picker.
- **Given** the user has completed first run, **When** they relaunch the app, **Then** the timeline
  opens directly.

**Navigation**
- **Given** the timeline, **When** the user taps the modules icon in the app bar, **Then** the
  Modules screen opens, and system back returns to the timeline with scroll position intact.

**Picker contents**
- **Given** the Modules screen, **When** it renders, **Then** Milestones and Mood appear as switch
  rows in priority order, and a single footer line names the modules still to come.

**Enable / disable**
- **Given** Mood is enabled, **When** the user disables it and opens the add-entry sheet, **Then**
  only the Milestone option is offered.
- **Given** Mood is disabled, **When** the user opens the filter sheet, **Then** Mood is not offered
  as a module filter, and any active Mood filter has been cleared.
- **Given** Mood is disabled and the timeline contains mood entries, **When** the user views the
  timeline, **Then** those entries are still shown and still open in the detail sheet.
- **Given** every module is disabled, **When** the user opens the add-entry sheet, **Then** it says
  no modules are enabled and offers a route to the Modules screen rather than showing an empty sheet.
- **Given** the user disables Mood, **When** the app is killed and relaunched, **Then** Mood is
  still disabled.

**Hiding**
- **Given** Mood is enabled, **When** the user looks at its row, **Then** no hide option is offered.
- **Given** Mood is disabled, **When** the user enables "hide these entries", **Then** mood entries
  disappear from the timeline while the timeline continues to page correctly through the remaining
  entries.
- **Given** mood entries are hidden, **When** the user re-enables the Mood module, **Then** the hide
  option is cleared and the mood entries reappear.
- **Given** mood entries are hidden, **When** the app is killed and relaunched, **Then** they are
  still hidden and still present in the database.

## Open questions

1. ~~Should there be an opt-in "hide entries from disabled modules" preference?~~ **Closed
   2026-07-28: yes** — included in this spec, constrained to disabled modules (D3, D4).
2. ~~Should the module picker also serve as first-run onboarding?~~ **Closed 2026-07-28: yes** —
   same screen, two modes (D7).
3. **Where do global app settings live** (media resolution, app lock, export)? *Proposed: a separate
   Settings destination added by whichever spec first needs one; Modules stays modules-only.*
   **Deferred, non-blocking** — the nav host built here is what it will plug into.
4. **Should hidden modules also be excluded from search results and On This Day?** Neither screen
   exists yet. **Deferred to whichever spec builds them**, which should treat the hidden set as a
   general "not in my view" signal rather than a timeline-list quirk.
