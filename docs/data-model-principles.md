# Time Flux — Data Model Design Principles

These are the north stars that govern every data model decision. Any proposed schema change or new module design is evaluated against all ten. A design that violates one needs an explicit, documented justification to proceed.

---

## 1. Every piece of data is a timestamped event on a timeline — full stop.

The data model has one job: represent things that happened at a point in time, or over a span of time. A mood check-in, a sleep session, a milestone, a habit completion — they're all the same shape at the base level. Never design a module's storage as if it's a separate app. It's always an entry first, module-specific data second.

---

## 2. Client-generated UUIDs, not server auto-increment IDs.

The app is local-first. Two devices must be able to create entries simultaneously, offline, without ID collision. Use ULIDs (time-ordered UUIDs) — they sort chronologically, are unique across devices, and work without a server. Server-assigned IDs break local-first architecture at the foundation.

---

## 3. Soft deletes everywhere — never hard-delete.

Mark entries with `deleted_at` instead of removing rows. Non-negotiable for three reasons:
- **Sync correctness** — tombstones tell other devices "this entry was deleted"
- **Undo support** — users can recover accidental deletions
- **Data recovery** — a life timeline app permanently deleting someone's memories on a misclick is a trust-destroying bug

---

## 4. The schema must absorb new modules without touching existing data.

Adding a new module should mean adding a new `module_type` value and a new payload shape. It should never require an `ALTER TABLE` on `timeline_entries` or a migration that rewrites existing rows. The JSON payload column exists precisely to enforce this constraint.

---

## 5. Time-based queries are first-class citizens, everything else is secondary.

Every design decision is tested against: "does this make date-range queries, 'on this day', and zoom-level aggregations fast?" The stored generated columns (`month_day`, `year_month`, `year_week`) exist because of this principle. If a query pattern requires a full table scan, the schema needs to change — not the query.

---

## 6. Metadata and content are separate concerns.

| Metadata | Content |
|---|---|
| `created_at`, `module_type`, `title`, `is_pinned` | Journal body, mood score, sleep phases, habit payload |
| Stays queryable | Gets encrypted |
| Stays local (stubs) when archiving | Moves to cold storage when archiving |
| Cheap to sync | Expensive to sync |

This distinction drives encryption, archiving, and sync design. Every field must clearly belong to one category.

---

## 7. The local database is canonical. The cloud is a mirror.

The source of truth lives on the user's device. Sync pushes local state to the cloud and pulls remote changes — it never treats the server as authoritative over the device. This means:
- No fields whose value is computed server-side and pushed back
- No schema that assumes an internet connection exists
- No foreign keys to server-side resources

---

## 8. Denormalize deliberately, normalize by default.

Normalization is the default. Break it only when there is a measured query performance reason, and document why.

| Decision | Reason |
|---|---|
| `year_month`, `year_week`, `month_day` stored generated columns | `strftime()` in WHERE is not index-sargable |
| Tags use a normalized junction table | JSON array membership doesn't scale past ~10K rows |

Every departure from normalization gets an explicit comment in the schema explaining the tradeoff.

---

## 9. Schema changes are migrations, always — no exceptions.

Every structural change is a numbered migration file verified at build time by SQLDelight. No ad-hoc `ALTER TABLE` in application code, no skipping migration verification in CI. A life timeline app that corrupts a user's 5-year history during an upgrade is finished.

---

## 10. Design for the 5-year user, not the day-one user.

The schema that's elegant for 100 entries must still be fast for 50,000. Every index, every summary table, every query is evaluated against what a user who has logged every day for 5 years will experience. Performance regressions at scale are unacceptable to discover in production.
