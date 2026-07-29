# Research — User profile, entities, and the places people name themselves

**Status:** pending — scoped 2026-07-28, no investigation done yet
**Raised by:** Alexis
**Would inform:** an entity/profile spec, and the shape of onboarding beyond spec 001

> **Nothing below is a finding.** This document frames the questions and records why they matter.
> Everything in "Questions" is unanswered.

---

## The idea

Time Flux should accumulate an understanding of who the user is, and that understanding should make
logging easier rather than being a separate feature the user maintains. The motivating example:

> If the app knows where I've lived — and knows that I call that place "the Austin apartment",
> not "4501 Speedway, Austin, TX" — then references to it across unrelated timeline entries
> resolve to the same thing, and typing about it gets faster the longer I use the app.

The general shape is that **people, places, and organisations recur across a life**, and today
Time Flux has no way to know that two entries are about the same one.

## Why it matters

**It's the compounding-value mechanism.** A timeline that only stores what you typed is worth the
same in year five as in month one. A timeline that knows your recurring places and people gets
*better* the longer you use it — autocomplete improves, references resolve, and questions like "how
was my mood the year I lived in Austin?" become answerable. That's the difference between a log and
a model of a life.

**Today there's nothing.** Tags are flat strings with no identity or attributes. Location is
`location_lat` / `location_lng` / `location_name` denormalised onto each entry — two entries at the
same address share no link, and "the Austin apartment" is just text the user retyped. Nothing in
the schema can answer "which entries happened while I lived there?"

**It's cheapest to decide before the modules land.** Journal, Photos and Health all want to
reference people and places. If entity references arrive after those modules ship, every one of
them needs retrofitting.

## Questions to investigate

**Profile shape**
1. What actually belongs in a "profile"? Stated facts (birthday, home base), derived understanding
   (patterns, frequencies), or an entity graph (people, places, orgs) — or all three under one idea?
2. Which facts pay for themselves immediately at input time, and which are speculative data
   collection? Anything that doesn't make logging easier or recall better probably shouldn't be asked.

**Entities and naming**
3. Do places and people need to be first-class records with identity and aliases, or is a richer
   tag system enough? What breaks first at 5 years / 50k entries — the query, or the UX?
4. How do people actually refer to places? Vernacular ("the old place", "mom's house", "the office")
   is the natural unit, not addresses. Does the app ask what you call somewhere, infer it, or both?
5. One place, many names over time — "work" changes meaning when you change jobs. Are aliases
   time-bounded too?

**Time-bounded attributes — the interesting tension**
6. "I lived in Austin from 2019 to 2023" is itself a timestamped span, which is exactly what
   `timeline_entries` stores. Is residence history a *profile* record, or is it derived from
   Milestone entries the user already logs ("moved to Austin")? Data-model principle 1 says
   everything that happened is a timeline entry — a profile that duplicates residence history would
   be a second source of truth for the same fact.
7. If it's derived, what happens for users who never logged the move?

**Building the profile over time**
8. What's asked at onboarding, what's learned passively, and what's asked lazily at the moment it
   pays off ("you've logged 12 entries here — what do you call this place?"). Prompting at the
   moment of relevance is likely better than a form, but needs evidence.
9. How does the app avoid feeling like it's interrogating the user, or like a CRM for your own life?
10. What does the user see of their own profile? Is it editable, browsable, deletable?

**Reference resolution**
11. How does an entry point at an entity — explicit picker, autocomplete on typing, extraction from
    text after the fact? v1 is local-only with no LLM calls, so extraction has to be local and cheap.
12. What's the failure mode when resolution is wrong, and how does the user correct it?

**Privacy**
13. A profile is the most sensitive data in the app — where you live, who matters to you. Under
    principle 6, is it metadata (queryable, syncs cheaply) or content (encrypted, archivable)? The
    answer shapes the v2 E2EE design.

## Prior art worth studying

- **Day One** — places, weather, On This Day; entities exist but stay shallow
- **Monica (personal CRM)** — explicitly models people, relationships and recurring facts; closest
  existing thing to "a profile of your life", including how it feels to maintain one
- **Google Photos / Timeline** — place clustering, Home/Work labelling, and the passive-inference UX
- **Apple Photos Memories / People albums** — on-device entity recognition and the confirm-or-correct loop
- **Obsidian / Roam** — aliases, backlinks, and what unresolved references cost
- **Genealogy tools (GEDCOM, place authorities)** — decades of prior art on place identity across
  time, renaming, and hierarchy
- **Facebook life events** — the mainstream vocabulary for "significant life event" categories

## Constraints any answer has to respect

- **Principle 1** — the database stores timestamped events; a profile is not obviously that. Where
  profile state lives (entity tables? preferences? derived views?) needs an explicit justification.
- **Principle 3** — soft delete; deleting a person or place must not orphan or destroy entries.
- **Principle 4** — new modules must not require schema changes to existing data.
- **Principle 10** — design for the five-year user; entity counts grow with time.
- **v1 is local-only** — no server-side geocoding or entity resolution service.

## What this would unblock

- Whether an entity layer is engine-level (like tags) or a module
- The schema for places and people, if any
- Autocomplete and reference UX in every module's entry form
- How far onboarding should go beyond spec 001's module picker
- Cross-entity insights ("your mood while you lived in X")

## Relationship to spec 001

Spec 001's first run is deliberately just the module picker, with multi-step onboarding as an
explicit non-goal. **This research should not block phase 6.** If it later argues for asking about
places or people during onboarding, that's an additive spec on top of the flow 001 builds — not a
reason to hold it now.
