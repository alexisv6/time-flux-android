# Plan NNN — <Feature name>

**Spec:** `docs/specs/NNN-<slug>.md`
**Status:** draft | approved | in progress | done
**Created:** YYYY-MM-DD

---

## Approach

The shape of the implementation in a paragraph or two: what gets built where, and why this way
rather than the obvious alternative. Name the alternative.

## Do not

Scope guards. Things that are tempting while in this code and are out of bounds for this plan —
each with where they belong instead (a later phase, a future spec, or "never").

## Phases

Each phase ends at a state a human can run and check. Never split as "write everything" then
"wire it up".

### Phase 1 — <name>

**Goal:** one sentence — what is true at the end of this phase that wasn't before.

**Changes**
- `path/to/File.kt` — what changes and why
- `path/to/Other.kt` *(new)* — what it contains

**Tests** *(this repo writes tests where they pay off — say which apply, or "none, and why")*
- …

**Verify**
- Automated: `.\gradlew.bat …`
- Manual: what to click in the app and what should happen

**Done when:** the checkable condition.

---

### Phase 2 — <name>

…

---

## Risks

What could go wrong or turn out harder than it looks, and the fallback for each.

## Follow-ups

Things this deliberately leaves for later, with enough detail to write the next spec from.
