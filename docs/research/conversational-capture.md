# Research — Talking to the app: conversational capture and trust

**Status:** pending — scoped 2026-07-28, no investigation done yet
**Raised by:** Alexis
**Would inform:** a voice/conversational capture spec; possibly the shape of every entry form

> **Nothing below is a finding.** This document frames the questions and records why they matter.
> Everything in "Questions" is unanswered.

---

## The idea

Typing is the tax on logging a life. Most people can say in twenty seconds what they'd never sit
down and type — and what they'd say is richer, because speech is where people are honest,
associative and unedited. The question is whether Time Flux can let someone **talk about their day
and end up with real timeline entries**, without that feeling like dictating a form.

> "I had that interview I was dreading, it went better than I thought, but I only slept like four
> hours so I'm wrecked."

That's a milestone, a mood, and a sleep entry in one breath — plus a note nobody would have typed.

## Why it matters

**It's the input problem, and input is where life-logging apps die.** Every competitor's reviews
say the same thing: people love the idea, then stop logging. Friction at capture is the single
biggest determinant of whether there's a five-year timeline to look back on at all.

**Venting is already a habit people have.** People talk to friends, to voice notes, to themselves in
the car. If the app can sit where that habit already is, it doesn't have to manufacture a new one.

**It compounds with everything else.** Richer, more frequent capture makes the insights, the
entity graph (see [user-profile-and-places.md](user-profile-and-places.md)) and On This Day worth
having. Sparse data makes all of them hollow.

## The constraint that shapes every answer

**v1 is local-only and privacy-first, and this is the most intimate data the app will ever touch.**
Understanding free-form speech well enough to propose structured entries is exactly the workload
that wants a large model — and sending someone's venting to a server contradicts the positioning
that removes signup friction and earns trust in the first place.

So the research has to establish what's actually achievable **on-device** (Android's on-device
speech recognition, small local models, or plain transcription plus dumb heuristics), and what
genuinely requires a round trip. If some capability requires the cloud, the question becomes
whether it can be opt-in per capture without the whole app's privacy story collapsing into an
asterisk.

This also collides with v2: **content that is end-to-end encrypted cannot be processed
server-side.** A cloud-extraction design would either break E2EE or need to run before encryption,
on-device — which loops back to the same question.

## Questions to investigate

**Interaction model**
1. Does it converse (asks follow-ups: "how did you sleep?") or just listen (you ramble, it parses)?
   Conversation gets better data and costs more trust, latency and battery.
2. Is there a middle ground — listen freely, then ask *at most one* clarifying question?
3. Push-to-talk, session-based ("I'm doing my evening check-in"), or ambient? Ambient/always-on is a
   separate product with a separate trust problem — worth studying who's tried it and how it went.
4. Does the user see a live transcript while speaking, or is it eyes-free and reviewed after? Live
   text may make people self-edit, which is exactly what we're trying to avoid.

**From speech to entries**
5. One spoken session can produce several entries across several modules. What does the review step
   look like — a list of proposed entries the user confirms, edits or discards?
6. Should the raw transcript itself become an entry (a Note or Journal), with the derived entries
   linked to it? That preserves what was actually said, independent of what the parser understood.
7. Is the audio kept as a media attachment or discarded after transcription? Storage, privacy and
   sentimental value pull in different directions — a recording of your own voice from ten years ago
   may be worth more than the text.
8. **Provenance must be visible.** An entry the app inferred is not the same as one the user wrote,
   and the schema should say which is which — the existing `isEnriched` flag is precedent for this
   kind of marking.
9. How does this interact with the module registry? A capture that proposes a Sleep entry when Sleep
   is disabled is proposing something the user opted out of (spec 001).

**Trust and enjoyment**
10. What does the app do when it gets it wrong, and how cheap is correcting it? A high edit rate on
    proposed entries is the clearest signal extraction isn't good enough to ship.
11. Silent wrong data is worse than no data in a life record — a fabricated or mis-parsed memory that
    resurfaces in five years is a trust-ending bug. What's the confidence threshold for proposing at
    all versus just storing the transcript?
12. Why would someone *enjoy* this rather than complete it? Does the app reflect anything back, and
    does reflection help or does it feel like being analysed?
13. Latency budget: at what delay between finishing speaking and seeing results do people give up?
14. What happens when someone vents something serious? The app is not a therapist and shouldn't
    pretend to be, but silently filing a distressing entry and surfacing it later in On This Day is
    its own kind of failure. This needs a deliberate answer, not a default.

**Guidance and UI**
15. Does a blank "start talking" prompt work, or do people need a question to answer? Guided prompts
    raise completion rates but shape (and narrow) what gets said.
16. How does the app teach what it can understand without turning into a command syntax people have
    to learn?

**Measurement**
17. How would we know it's working — capture frequency, session completion, edit rate on proposals,
    retention at week four? Which of these can be measured locally, with no analytics backend?

## Prior art worth studying

- **Rosebud, Reflectly, Journey** — AI-assisted journaling; what the reflection loop feels like and
  where it gets tiring
- **Day One** — audio entries with transcription, without extraction; a deliberately conservative baseline
- **Google Recorder** — on-device transcription quality and speed; the best evidence for what's
  achievable without a server
- **Otter / Granola** — turning long unstructured speech into structured output, and how review UIs work
- **Rewind / Limitless and similar always-on wearables** — the trust and backlash case studies
- **Wysa, Youper, How We Feel** — conversational emotional check-ins, and how they handle serious disclosure
- **Finch, Duolingo** — habit and enjoyment mechanics, and where streaks turn into guilt
- **Siri / Google Assistant logging shortcuts** — why voice-to-app-entry hasn't stuck before

## Constraints any answer has to respect

- **v1 local-only** — no backend, no accounts, nothing leaving the device without explicit consent
- **v2 E2EE** — server-side processing of encrypted content is not possible by design
- **Principle 1** — whatever gets created is still a timestamped entry on the timeline
- **Principle 3** — never destroy; a mis-parsed entry must be correctable, not just deletable
- **Principle 4** — extraction must not require schema changes per module
- Module registry (spec 001) — capture only proposes entries for modules the user has enabled

## What this would unblock

- Whether voice capture is a module or an engine-level input method feeding all modules
- Provenance fields in the entry schema (user-authored vs inferred, confidence, source transcript)
- Whether audio becomes a first-class media type in v1
- The relationship between quick capture and the existing "save now, enrich later" (`isEnriched`) idea
- How much of the privacy story can survive any cloud dependency, and what the opt-in looks like
