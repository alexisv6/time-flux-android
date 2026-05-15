# Time Flux — Architecture Breakdown & Module Inventory

*Derived from competitive analysis, May 2026*

---

## How to read this document

Everything in the app fits into one of three layers:

- **Core Engine** — foundational infrastructure that runs regardless of which modules the user enables. You build this once.
- **UI / Presentation** — the views and visualizations that surface the engine's data. Some are universal, some are module-specific.
- **Modules** — discrete, opt-in feature sets that plug into the engine. Users pick the ones that matter to them.

The key architectural principle: **every module writes the same kind of data — a timestamped entry — to the same timeline store.** A sleep log, a milestone, a mood check-in, and a journal entry are all just entries with different schemas. The engine doesn't care which module created them; it just knows when they happened.

---

## Core Engine

These are non-negotiable — they underpin everything.

### 1. Timeline Data Store
The central database. Every entry from every module is stored here with:
- Timestamp (start, and optionally end, for duration-based entries like sleep)
- Module type (identifies which module owns the entry)
- Payload (module-specific data as a flexible schema)
- Tags (cross-module, user-defined)
- Media attachments (photos, audio, video)
- Auto-metadata: GPS coordinates, weather at time of entry, device context

**Why this matters:** Every competitor keeps data siloed per feature. Mood data lives in one place, sleep in another, journal somewhere else. The entire value proposition of Time Flux is that all of this lives in one queryable store.

### 2. Module Registry
A lightweight system that knows which modules are installed and enabled. Manages:
- Module on/off state per user
- Module configuration (e.g., habit schedules, mood scale settings)
- Routing: when a module writes an entry, where it goes; when the timeline renders, which modules contribute

**Why this matters:** This is what makes the app modular rather than just "a big app with everything." A user who only wants milestones and journal never sees sleep or habit UI.

### 3. Time Navigation Engine
The logic layer for traversing the timeline. Handles:
- Zoom levels: hour → day → week → month → year → decade
- Pagination and lazy loading of entries across large time ranges
- "Jump to date" and "jump to first entry" navigation
- "On This Day" queries (same calendar date, any year)

**Why this matters:** Lifely had bad UX here — difficult zooming was a top complaint. Getting time navigation right is what separates a life timeline from a list.

### 4. Notification & Reminder Engine
Modules register scheduled alerts with this engine. Handles:
- Daily check-in reminders (mood, journal, habits)
- Habit due notifications with quick-action completion (check off from notification)
- Future event/milestone reminders
- Smart timing (learns when user typically opens the app)

### 5. Privacy & Security Layer
- Local-first: all data stored on-device by default, no account required to use the app
- Biometric / PIN app lock
- Optional encrypted cloud sync (user opt-in)
- No data leaves the device without explicit user consent

**Why this matters:** Privacy-first is a consistent unmet demand across all competitor categories. Loop (habit) and Bearable (health) built loyal audiences partly on this. It also removes signup friction.

### 6. Health Platform Bridge
- Android: Google Health Connect integration
- iOS (future): Apple HealthKit integration
- Reads: steps, sleep, heart rate, workouts, weight — surfaces these as auto-populated entries that modules can consume
- Writes: relevant module data back to Health Connect (e.g., sleep logs)

### 7. Export Engine
- Universal export: all timeline data to JSON (full fidelity) or CSV (tabular per module)
- Module-specific exports: e.g., sleep report PDF, habit summary
- Import: bring in data from Day One, Daylio, or a previous Time Flux backup

### 8. Search & Filter
- Full-text search across all entry content
- Filter by module, tag, date range, location, media type
- Cross-module queries: "show me all entries from the week I moved to Austin"

---

## UI / Presentation Layer

### Universal Views (core app, always present)

| View | Description | Inspired By |
|---|---|---|
| **Linear Timeline** | The primary view. Vertical scrollable timeline, all enabled modules visible as entry types. Zoom from day to decade. | Lifely (but better UX) |
| **Day View** | Hourly breakdown of one day. Shows all module entries for that day inline. | Structured, Tiimo |
| **Month Calendar** | Standard month grid, each day shows color-coded module activity at a glance. | Fantastical, Day One |
| **Year Grid ("Life Year")** | One cell per day of the year, colored by a user-chosen signal (mood, sleep quality, habit score, or composite). | Pixels, Daylio |
| **Life in Weeks** | Zoomed-out grid — one cell per week of the user's life. Cells fill with color/data as modules log entries. | Life Calendar, Lifetime |
| **Entry Detail** | Full-screen view for any entry type. Renders the module-specific payload (journal text, sleep metrics, mood data, etc.) |  |
| **"On This Day"** | Surfaces all entries from the same calendar date across all past years. | Day One |
| **Module Picker** | Onboarding + settings screen. Cards for each available module — user toggles them on/off. Shows a preview of what each adds to the timeline. | Bearable's factor picker |
| **Insights Dashboard** | Cross-module correlations and patterns. "Your best sleep days correlate with days you logged a workout." | Bearable, Oura |

### Module-Specific Views
Each module gets its own settings screen and optional stats screen. These are only visible if the module is enabled.

---

## Modules

Ranked roughly by universality (how many people would want this) and implementation simplicity.

---

### Module 1: Milestones
**What it is:** Manual log of significant life events — career changes, relationships, moves, achievements, firsts.

**Entry fields:** Title, date (or date range), description, photos/media, category (career / family / health / travel / personal), tags

**Timeline contribution:** Appears as a prominent "pin" or marker on the timeline. Distinguished visually from daily check-ins — these are the anchor points of a life.

**Competitor gap:** Lifely attempted this but was removed from iOS. No surviving app does this as a first-class concept. This is the most differentiating module Time Flux can have.

**Complexity:** Low — purely manual, no sensor data, no recurring logic.

---

### Module 2: Mood
**What it is:** Quick daily (or intra-day) mood check-in. One tap, under 5 seconds.

**Entry fields:** Mood score (1–5 or custom scale), optional emotion tags (user-defined), optional short note, optional photo

**Timeline contribution:** Color-coded dots on the timeline. Drives the color of the Year Grid view.

**Competitor gap:** Daylio (19M downloads, 4.8★) proves the market. But Daylio has no timeline, no integration with other life data.

**Complexity:** Low.

---

### Module 3: Journal
**What it is:** Long-form text entries with rich media. Auto-captures location and weather at time of writing.

**Entry fields:** Title (optional), body (markdown), photos/video/audio, auto-location, auto-weather, tags

**Timeline contribution:** Journal entry icons on the timeline. Searchable across the full store.

**Competitor gap:** Day One is the gold standard here but has no milestone tracking, habits, or mood. Journal entries in Time Flux sit alongside all other life data on the same timeline.

**Complexity:** Medium — need a good text editor component, media handling, and auto-metadata capture.

---

### Module 4: Habits
**What it is:** Recurring habits with flexible scheduling, streak tracking, and a "habit strength" score (inspired by Loop's algorithm, not a pure streak counter).

**Entry fields:** Habit name, icon, color, schedule (daily / X per week / specific days), completion log

**Timeline contribution:** Habit completion dots per day. Contributes to the Year Grid coloring. Habit score visible in the Insights dashboard.

**Competitor gap:** Streaks (4.82★, Apple Design Award) and Loop (4.8★, 5M downloads) prove the market, but both are standalone. Neither shows habit completions alongside life events on a timeline.

**Complexity:** Medium — scheduling logic, streak calculation, notification per habit.

---

### Module 5: Sleep
**What it is:** Sleep logging. Manual entry or auto-import from Google Health Connect / Apple Health.

**Entry fields:** Bedtime, wake time, sleep quality rating (1–5), notes, auto-import option (Health Connect)

**Timeline contribution:** Sleep blocks on the timeline (duration-based entries, not point-in-time). Color indicates quality.

**Competitor gap:** Sleep Cycle (4.3★, 214K reviews) and AutoSleep dominate standalone sleep tracking, but neither connects sleep data to the rest of life. Time Flux shows "you slept 4 hours before that job interview" because both are on the same timeline.

**Complexity:** Medium — duration-based entry type is a new pattern vs. point-in-time entries. Health Connect integration adds setup complexity.

---

### Module 6: Notes / Quick Capture
**What it is:** Lightweight, unstructured notes. Faster than Journal — no formatting, no required fields. Think of it as a "voice memo for life."

**Entry fields:** Text or voice (transcribed), optional tags, optional photo

**Timeline contribution:** Small note icons. Easily mistaken for journal — the difference is intent: quick capture vs. deliberate reflection.

**Complexity:** Low.

---

### Module 7: Health & Body
**What it is:** Biometric and symptom tracking. Flexible — users define which metrics they care about (weight, energy level, custom symptoms, medications).

**Entry fields:** User-defined metrics (numeric, boolean, or scale), notes, optional Health Connect auto-import for steps/HR/workouts

**Timeline contribution:** Health entries on the timeline. Contributes rich data to the Insights dashboard (correlations with mood, sleep, habits).

**Competitor gap:** Bearable (4.8★) is the strongest player but health/symptom-only. Time Flux adds health data as one layer of a broader life picture.

**Complexity:** High — flexible schema for user-defined metrics, Health Connect bidirectional integration, correlation engine in Insights.

---

### Module 8: Tasks / Events
**What it is:** Scheduled future events and to-dos that appear on the timeline as upcoming entries. The planning layer.

**Entry fields:** Title, date/time, duration (optional), recurrence (optional), reminder, notes, completion state

**Timeline contribution:** Future-dated entries on the timeline — the app extends forward, not just backward. Completed tasks become historical data points.

**Competitor gap:** Structured (1.5M users, 4.8★) and Tiimo (iPhone App of the Year 2025) own daily planning beautifully but have no past/history timeline. Time Flux unifies past (what happened) and future (what's planned) on one scroll.

**Complexity:** Medium — future entries are a new pattern; recurrence and reminder logic overlaps with Habits.

---

### Module 9: Photos / Memories
**What it is:** Dedicated photo/video entries distinct from Journal. Think "camera roll that lives on your life timeline."

**Entry fields:** Photo(s) / video, caption, location, date (can be backdated)

**Timeline contribution:** Visual thumbnails on the timeline. Gallery view per time period.

**Competitor gap:** Day One auto-imports photos but buries them in journal entries. Momento pulls from social media passively. Time Flux treats a photo as a first-class timeline entry.

**Complexity:** Medium — media storage, backdating, gallery rendering.

---

### Module 10: Goals
**What it is:** Long-term goals with milestones and progress tracking. Different from Habits (which are recurring) — a Goal has a defined endpoint.

**Entry fields:** Goal title, target date, progress metric (percentage, milestone checkpoints, or numeric target), notes

**Timeline contribution:** Goal entries that span a time range. Progress updates appear as sub-entries. Completion is a milestone-like event.

**Complexity:** Medium — progress tracking logic, relationship between goal and milestone entries.

---

## Module Priority Matrix

| Module | User Universality | Build Complexity | Differentiating Power | MVP Priority |
|---|---|---|---|---|
| Milestones | High | Low | Very High (unique in market) | **1** |
| Mood | Very High | Low | High | **2** |
| Journal | High | Medium | Medium (Day One exists) | **3** |
| Habits | High | Medium | Medium (proves modularity) | **4** |
| Sleep | Medium | Medium | High (cross-domain value) | **5** |
| Notes / Quick Capture | Medium | Low | Low | **6** |
| Tasks / Events | Medium | Medium | High (future + past timeline) | **7** |
| Health & Body | Medium | High | Medium | **8** |
| Photos / Memories | High | Medium | Low | **9** |
| Goals | Medium | Medium | Medium | **10** |

---

## What to Build First: Recommendation

### The core thesis to prove with v1:
> "Multiple types of life data — at least two meaningfully different things — displayed together on a single scrollable timeline that you can navigate from today back through your whole life."

If v1 proves that thesis, the app has a reason to exist. Every module after that adds depth.

### Suggested v1 scope:

**Engine:**
- Timeline data store (Room/SQLite on Android)
- Module registry (on/off toggle, basic config)
- Time navigation (day → year zoom)
- Local-first storage, biometric lock

**UI:**
- Linear Timeline view
- Day view
- Module Picker screen
- Entry detail view

**Modules (two, to prove modularity works):**
- Milestones — the anchor. Proves the long-term life timeline concept. No competitor owns this.
- Mood — the daily layer. Proves quick check-ins alongside significant events. Low build cost, high user familiarity.

**Why these two together?**
A milestone ("started new job") and a mood check-in ("felt anxious") on the same day, visible on the same timeline, immediately demonstrates the value proposition that no other app delivers. You don't need habits, sleep, or journal to make that point — you just need two meaningfully different data types coexisting.

### What v2 adds:
Journal (adds depth to milestones and context to moods) + Habits (proves the recurring data pattern works alongside event-based data).

### What to defer:
Health & Body — highest complexity, most platform dependency (Health Connect), narrower initial audience. Powerful but not needed to prove the concept.
