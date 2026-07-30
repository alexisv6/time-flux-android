# Time Flux Docs

Two kinds of document live here: **context** (durable, describes the world) and **work**
(spec + plan pairs, describe one change and then go stale on purpose).

## Context

| Doc | Purpose |
|---|---|
| [current-state.md](current-state.md) | What is actually built, what's designed-but-unbuilt, known gaps. Update it when a feature lands. |
| [data-model-principles.md](data-model-principles.md) | Ten rules governing every data decision. |
| [architecture-and-modules.md](architecture-and-modules.md) | Engine / UI / module inventory, module priority matrix, v1 scope recommendation. |
| [competitive-analysis.md](competitive-analysis.md) | Market research; the gap each module targets. |
| [kmp-setup-research.md](kmp-setup-research.md) | KMP tooling and project-structure research. |
| [data-storage-and-query-research.md](data-storage-and-query-research.md) | SQLDelight / SQLite / FTS5 / query-shape research. |
| [storage-sync-archiving-research.md](storage-sync-archiving-research.md) | v2/v3 sync, encryption, tiered archiving research. |

## Research

Open questions being investigated before they're ready to spec — see
[research/README.md](research/README.md) for the queue.

| Doc | Question | Status |
|---|---|---|
| [user-profile-and-places.md](research/user-profile-and-places.md) | How the app builds an understanding of the user over time, and how places/people become referenceable entities | Pending |
| [conversational-capture.md](research/conversational-capture.md) | Talking to the app instead of typing — interaction model, extraction into entries, and trust | Pending |

## Work

| # | Feature | Spec | Plan | Status |
|---|---|---|---|---|
| 001 | Module registry + navigation | [spec](specs/001-module-registry-and-navigation.md) | [plan](plans/001-module-registry-and-navigation.md) | **Shipped** 2026-07-29 |

---

## How specs and plans work here

The point is alignment before code: disagreements are cheap in a spec, expensive in a diff.

### 1. Spec — *what and why*, never how

`docs/specs/NNN-slug.md` from [specs/TEMPLATE.md](specs/TEMPLATE.md).

A spec is finished when someone who disagrees with it can say so precisely. It contains the
problem, the user-visible behaviour, explicit **non-goals**, decisions with their rationale, and
acceptance criteria written as Given/When/Then. It contains no class names, no file paths, no
schema. If a question can't be resolved, it goes in **Open questions** and the spec is not
approved until every one is closed or explicitly deferred.

### 2. Plan — *how*, in verifiable phases

`docs/plans/NNN-slug.md` from [plans/TEMPLATE.md](plans/TEMPLATE.md).

A plan decomposes the spec into phases that each end at a **working, verifiable state** — never
"phase 1: write all the code, phase 2: wire it up". Each phase names the files it touches, the
tests worth writing (see the testing stance in `CLAUDE.md` — pragmatic, not universal), and how a
human confirms it works. Plans carry a **Do not** list so scope creep is visible.

### 3. Execute

Work the plan phase by phase, checking phases off in the plan file as they land. If reality
contradicts the plan, stop and amend the plan rather than silently diverging — the amendment is
the useful artifact. When the feature ships, update `current-state.md` and set the status above.

### Numbering

Specs and plans share one number. `001-module-registry-and-navigation` is both
`specs/001-…md` and `plans/001-…md`. Numbers are never reused, even if a spec is abandoned —
mark it `Status: abandoned` and say why.
