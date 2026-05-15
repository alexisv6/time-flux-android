# Time Flux — Storage, Cloud Sync, Archiving & Privacy Research

*Research date: May 2026*

---

## Quick Reference: Key Decisions

| Decision | Recommendation |
|---|---|
| v1 sync strategy | Local-only + manual export ZIP |
| v2 sync stack | PowerSync + Supabase (Postgres) + Cloudflare R2 |
| Encryption | Client-side AES-256-GCM before any data leaves device |
| Media storage | App-private files directory (NOT device gallery) |
| Media format | WebP at quality 80–85; user-selectable resolution: 1080p (storage saver) or 1440p (default); separate thumbnails at 150px |
| Database references | File paths in DB, never blobs |
| Conflict resolution | Per-field LWW with server timestamps + conflict history table |
| Archiving model | Hot (0–24mo local) / Warm (1–3yr cloud + local stub) / Cold (3yr+) |
| Backup for users | Export as ZIP (JSON + media) — always available, no server needed |

---

## 1. Local Storage Size Estimates

### Text Data (SQLite)

For a heavy user logging daily across all 6 modules over 5 years:

| Module | 5-Year Raw Text |
|---|---|
| Mood check-ins | ~90–365 KB |
| Journal entries | ~900 KB–5.5 MB |
| Sleep logs | ~135–550 KB |
| Habit completions | ~90–900 KB |
| Milestones | ~50–200 KB (sparse) |
| Health data | ~180–900 KB |
| **Raw text total** | **~2–8 MB** |

With SQLite overhead (indexes, page alignment, FTS5):

| Overhead Source | Size Impact |
|---|---|
| Page alignment waste | 10–30% of raw data |
| B-tree indexes | 20–40% of indexed column data |
| FTS5 index on journal text | 50–150% of indexed text |
| Summary tables | < 5% of raw data |
| **Total realistic DB size** | **15–80 MB** (5 years, heavy user) |

**Text data is never a real problem.** 80 MB of SQLite is invisible to users.

### Media Storage

| Scenario | Photos/Week | 5 Years (WebP q80, 1440p) | Thumbnails |
|---|---|---|---|
| Light | 1/week | ~78 MB | ~5 MB |
| Moderate | 2/week | ~156 MB | ~10 MB |
| Heavy | 3/week | ~234 MB | ~15 MB |
| Uncompressed originals | 2/week | ~1.6–3.1 GB | — |

**The real risk is uncompressed media.** Compressing photos to WebP 1440p reduces size 3–12× vs. full-resolution originals. This must be done at import time.

### When Does Storage Become a Problem?

- Under 500 MB: invisible to users
- 500 MB – 2 GB: visible in Settings > Storage; acceptable
- 2–5 GB: users notice and may complain
- Over 5 GB: significant friction, risk of app deletion

With compression: a 5-year heavy user stays well under 500 MB total.

---

## 2. Media Storage Strategy

### Where to Store Media
**App-private files directory — not the device media library.**
- Journal and milestone photos are private documents, not gallery photos
- Storing in Camera Roll/MediaStore exposes them to other apps, Google Photos, iCloud sync
- Android: `context.getExternalFilesDir()` or `context.filesDir`
- iOS: `Documents/` (backed up to iCloud) or `Library/Application Support/`

### Compression Pipeline
When user attaches a photo:
1. Resize based on user-selected quality setting:
   - **1440p** (default) — max 2560×1440; best quality, ~500 KB/photo
   - **1080p** (storage saver) — max 1920×1080; ~300 KB/photo, ~40% smaller
2. Encode as **WebP at quality 80–85** (Android native support since API 14; iOS via ImageIO)
3. Generate a **150×150 thumbnail** at JPEG quality 60 — stored in `/thumbnails/`
4. Optionally offer "keep original" as a user toggle (off by default)

The resolution setting lives in app preferences and can be changed at any time. It applies to new imports only — existing photos are not re-encoded.

### Database Schema for Media
```
media_attachments(id, entry_id, file_path, thumbnail_path, width, height, file_size_bytes, created_at)
```
Store relative file paths, not blobs. SQLite with large binary blobs causes memory and I/O pressure on every query that touches that table.

### Thumbnail Strategy
- Pre-generate at import time — never at render time during scroll
- Store flat in `/thumbnails/{uuid}.jpg`
- Keep a LRU in-memory cache of ~50 decoded thumbnails (~2.5–5 MB RAM)
- Lazy-load: only load thumbnails for visible cells; cancel pending loads on scroll

---

## 3. Cloud Sync Options Evaluated

### Option A: Firebase (Firestore + Firebase Storage)
**Verdict: Not recommended for Time Flux**

- No native KMP SDK — requires GitLive community wrapper with CocoaPods on iOS
- Firestore local cache capped at 40 MB — undermines local-first for 5-year histories
- Storage egress at $0.15/GB — punishing for media sync at scale
- Vendor lock-in (Google can deprecate Firebase products)
- Cloud-primary, not local-first

### Option B: Supabase + PowerSync ✅ Recommended for v2
**Verdict: Best fit for KMP + local-first + privacy**

- **Supabase**: Open-source Postgres backend; `supabase-kt` is a well-maintained KMP SDK covering auth, database, realtime, and storage
- **PowerSync**: The only production-grade KMP local-first sync SDK. Syncs SQLite ↔ Postgres. Integrates directly with SQLDelight. Handles offline queue and conflict resolution. Official Android + iOS KMP targets.
- Together they provide delta sync, offline writes, and conflict handling without building a custom sync protocol
- Storage egress: $0.09/GB (Supabase Storage) — moderate

### Option C: Custom Ktor + Cloudflare R2
**Verdict: Strong media storage choice; backend viable for teams with capacity**

- Build your own sync protocol (2–4 weeks for experienced dev)
- Cloudflare R2 for media: $0.015/GB stored + **$0 egress** — transformative for media-heavy apps
- At 100K users syncing 200 MB of media: Firebase egress = ~$3,000/month; R2 = **$0**
- Adds operational burden (server maintenance, incident response)
- **Recommendation**: Use R2 for media storage even in the Supabase stack (Supabase Storage has egress fees; R2 does not)

### Option D: PowerSync + ElectricSQL + Ditto (CRDT platforms)
- **PowerSync**: Recommended — see Option B above
- **ElectricSQL**: No KMP SDK; primarily JavaScript/React Native. Skip.
- **Ditto**: True CRDT + P2P sync; enterprise pricing; no KMP SDK. Overkill for a personal app.

### Option E: User-Owned Storage (Google Drive, iCloud)
**Verdict: Good for backup; inadequate for real-time sync**

- Uploading a 50–400 MB SQLite + media bundle on every change is inefficient
- No cross-platform story (iCloud = iOS only; Google Drive = requires OAuth)
- No conflict resolution
- Best use: v1 backup feature (user explicitly taps "Backup to Google Drive")
- iOS share sheet makes this trivially easy

### Cost Comparison at Scale (Recommended Stack: Supabase + PowerSync + R2)

| Users (MAU) | Supabase | PowerSync | Cloudflare R2 (50MB/user) | Total/mo |
|---|---|---|---|---|
| <500 (beta) | $0 | $0 | <$1 | ~$0 |
| 5,000 | $25 | $49 | ~$7 | ~$81 |
| 50,000 | ~$250 | $49 | ~$65 | ~$364 |
| 500,000 | ~$2,500 | $599 | ~$650 | ~$3,750 |

At 50K users paying $3/month for sync: ~$150K/year revenue against ~$364/month costs.

---

## 4. Conflict Resolution

### Strategy: Per-Field LWW with Server Timestamps

For a personal single-user app, true simultaneous conflicting edits on two devices are extremely rare. Users typically use one device at a time.

**Recommended approach:**
1. Each field carries its own `updated_at` timestamp
2. On sync, the server assigns authoritative timestamps (use `NOW()` server-side, not client clocks) — eliminates clock skew issues
3. On merge: each field independently takes the value with the latest server timestamp
4. Store the "losing" version in a `conflict_history` table for 30 days — nothing is ever permanently discarded

This is better than record-level LWW: if Phone edits journal text and Tablet adds a photo to the same entry while offline, both changes are preserved.

### Common LWW Edge Cases to Guard Against
- **Clock skew**: Device clock wrong → edit appears "older" and loses. **Fix**: Server-assigned timestamps.
- **Tombstone resurrection**: Entry deleted on Device A → Device B offline for 2 weeks → comes online with local edit. **Fix**: Tombstones must have timestamps and win over concurrent edits.
- **Partial sync failure**: Sync fails midway. **Fix**: Transactional sync batches; retry idempotently.

### How Comparable Apps Handle It
- **Day One**: E2EE + LWW per entry. Aggressively syncs on foreground to minimize conflict window.
- **Obsidian Sync**: File-based LWW with version history (up to 1 year retained). Conflicts recoverable.
- **Standard Notes**: LWW per note with server timestamps.

---

## 5. Archiving Strategy

### Tiered Storage Model

| Tier | Data Age | What Lives Locally | What Lives in Cloud |
|---|---|---|---|
| **Hot** | 0–24 months | Full entries + full media | Mirror copy (sync) |
| **Warm** | 1–3 years | Entry metadata + FTS stubs | Full text + media |
| **Cold** | 3+ years | Metadata stub only | Everything |

### How Archiving Works
1. Background job runs when app is backgrounded + charging
2. Selects entries older than threshold
3. Uploads full JSON + media to cloud archive; verifies success
4. Removes local media files; replaces entry text with a `content_stub` marker
5. Local record stays in DB with a reference to the cloud archive key

### User Experience
- Timeline still shows all historical entries (from local metadata stubs)
- Tapping an archived entry shows a loading state while fetching from cloud, then renders normally
- This is the exact pattern used by Apple Photos with iCloud "Optimize iPhone Storage"

### Search Across Archived Entries
Retain the local FTS5 index even after archiving full text. The FTS5 index for 5 years of journal text is only ~3–8 MB. Search always works locally; full text is fetched on tap.

### Archive vs. Backup vs. Sync

| Concept | Purpose | Recovery Speed | Mutable? |
|---|---|---|---|
| **Backup** | Safety copy against data loss | Fast (full restore) | No (point-in-time) |
| **Archive** | Cold storage; free local space | Slow (remote fetch) | No |
| **Sync** | Multi-device consistency | Real-time | Yes |

---

## 6. Privacy

### Data Sensitivity
Life timeline data (health metrics, mood states, journal entries, location history) is among the most sensitive personal data a user can generate. The privacy bar should be higher than for typical productivity apps.

### End-to-End Encryption (E2EE)
**Recommendation: E2EE on by default for entry content.**

- Encrypt on-device before any data leaves the device
- Server stores only ciphertext — even a breach reveals nothing readable
- Key never leaves the device (stored in Android Keystore / iOS Secure Enclave)
- Matches Day One's architecture (independently security-reviewed)

**What to encrypt**: entry text, journal body, payload fields, media files
**What can stay unencrypted**: entry dates, counts, module types (needed for sync coordination)

### Day One's E2EE Architecture (reference model)
1. Master key pair generated on device; only public key sent to server
2. Each entry encrypted client-side with user's public key
3. Server stores only encrypted payload — Day One cannot read entries
4. Private key in device keychain; optionally backed up to iCloud Keychain

### Zero-Knowledge Sync
The server is "blind" — it routes encrypted blobs but cannot read them. Worth implementing for a privacy-focused indie app. Implementation: ~1–3 months of engineering. Strong trust differentiator vs. competitors storing data in plaintext (Notion, Reflectly, etc.).

### Privacy by Backend Choice

| Backend | Server Reads Your Data? | Notes |
|---|---|---|
| Firebase | Google can | No E2EE built-in |
| Supabase | Supabase/AWS can | No E2EE built-in; implement client-side |
| Custom + R2 | You control | You control encryption |
| Local-only (v1) | Nobody | Maximum privacy; no server |

---

## 7. Phased Roadmap

### v1 — Local-Only + Export
No backend. No server costs. No privacy risk. Focus on polishing the app experience.

**Must include:**
- Full local SQLite + SQLDelight
- App-private media storage with WebP compression + thumbnails
- FTS5 full-text search (local)
- **Export as ZIP** (JSON data file + organized media folder) — user's data escape hatch
- **Import ZIP** — restore or migrate
- Optional: Android Auto Backup (covers text DB up to 25 MB free)
- Optional: "Save backup to Google Drive/iCloud" via platform share sheet

### v2 — Cloud Sync (when user demand is validated)
- PowerSync + Supabase Postgres for entry sync
- Cloudflare R2 for media (zero egress)
- Client-side E2EE before anything leaves device
- Per-field LWW conflict handling with server timestamps + conflict history
- Tiered archiving (Hot/Warm/Cold)
- Subscription pricing for sync (~$2–4/month or $20–40/year; local-only stays free)

### v3 — Trust & Extensibility
- Zero-knowledge architecture with independent security audit
- Selective sync (user chooses which modules sync)
- Self-hosting option for the technically inclined
- Open export format spec (enable third-party importers)

---

## Final Architecture Diagram

```
Device (Android/iOS)
├── SQLDelight + SQLite
│   ├── timeline_entries (all modules)
│   ├── FTS5 index (journal + milestone text)
│   ├── entry_tags junction table
│   ├── daily_summaries / monthly_summaries
│   └── media_attachments (file path references)
├── App files directory
│   ├── /photos/{year}/{uuid}.webp     (1440p compressed)
│   └── /thumbnails/{uuid}.jpg         (150px)
└── PowerSync SDK (v2+)
    ├── Offline write queue
    └── Delta sync engine

Cloud (v2+)
├── Supabase Postgres
│   └── Encrypted entry records (server is blind)
├── Cloudflare R2
│   └── Encrypted media blobs (zero egress)
└── PowerSync Sync Service
    └── SQLite ↔ Postgres delta sync

User Control (always)
├── Export ZIP  (JSON + media, any time, offline)
├── Import ZIP  (restore / migrate)
└── Archive     (entries > 2yr → R2 cold; stub remains local)
```
