# Architecture

## Status

WatchRelay is in technical-validation stage. This document describes the intended boundaries and constraints, not a claim that every component already exists.

## Product boundary

WatchRelay is a companion that turns playback into a reliable watched-state event and forwards that event to a tracker.

Initial path:

```text
LazyMedia Deluxe
        ↓
   WatchRelay
        ↓
     MyShows
```

WatchRelay does not provide media, discover streams, host a catalog, download content, or act as a general-purpose video player.

## Design priorities

In order:

1. Correct watched-state decisions.
2. Low false-positive rate.
3. Privacy and credential safety.
4. Android + Android TV/Google TV parity.
5. Resilience to process death, network loss, duplicate events, and retries.
6. Minimal architecture and dependency surface.

A missed automatic mark is preferable to marking the wrong title or episode.

## High-level flow

```text
Playback source
      │
      ▼
Playback adapter
      │
      ▼
Playback session engine
(viewed intervals / state)
      │
      ▼
Content matcher
      │
      ├──────────────► Needs user confirmation
      │                 when confidence is insufficient
      ▼
Watch decision
      │
      ├──────────────► Local history
      │
      ▼
Durable sync queue
      │
      ▼
Tracker provider
(initially MyShows)
```

## Playback source boundary

Playback observation must prefer documented Android mechanisms:

1. `MediaSession` / `MediaController` metadata and playback state.
2. Metadata and intent extras passed when an external player is launched.
3. A narrow bridge mode where WatchRelay receives the playback intent, records only the metadata required for identification, and forwards playback to the configured player.

Accessibility-based observation is not an architectural default. It may only be considered after standard Android mechanisms are proven insufficient for an important supported path and the permission/privacy/store-policy trade-off is documented.

### Playback adapter responsibilities

A playback adapter may provide:

- title and subtitle metadata;
- original title when available;
- season and episode numbers when available;
- year and external IDs when available;
- playback state;
- duration and current position;
- stable session/item identity when available;
- source/player identity needed for compatibility diagnostics.

It must not make the final watched decision or write tracker state directly.

## Playback session engine

The session engine owns playback accounting.

A session tracks enough information to distinguish real viewing from simple seeking. The watched percentage must be based on the union of actually viewed time intervals, not solely on `currentPosition / duration`.

Example:

```text
00:00–12:10 viewed
12:10–31:25 viewed
seek to 51:00
51:00–55:00 viewed
```

The jump from 31:25 to 51:00 must not be counted as viewed time.

The engine must tolerate:

- pause/resume;
- seeks forward and backward;
- duplicate callbacks;
- player restarts;
- autoplay next episode;
- brief metadata changes;
- process/background transitions;
- player crash or abrupt stop;
- repeated observation of an already completed item.

The default watched threshold target is 80%, configurable by the user. Threshold changes apply prospectively according to the product behavior defined when implementation exists.

## Content matching

The matcher converts source metadata into a tracker entity.

Preferred evidence order:

1. stable external ID supplied by the source;
2. IMDb or Kinopoisk ID where supported;
3. original title + year;
4. localized title + year;
5. season/episode numbers for episodic content;
6. normalized/fuzzy title matching as a fallback.

The matcher must produce one of three outcomes:

- **confirmed** — safe to sync automatically;
- **ambiguous** — require user choice;
- **unresolved** — do not sync.

User-confirmed mappings should be persisted so the same source title does not repeatedly require confirmation.

Do not create broad heuristics that increase automatic coverage at the expense of correctness.

## Local persistence

Once persistence is implemented, local storage should contain only what is necessary for:

- current/recent playback sessions where required for recovery;
- local watch history;
- content mappings;
- pending/retry sync operations;
- previous remote state needed for safe undo;
- user settings;
- non-secret diagnostic metadata.

The intended Android persistence technology is Room unless a simpler proven solution meets all persistence and migration requirements.

Raw stream URLs, torrent URLs, passwords, and reusable tracker credentials do not belong in the database.

## Sync queue

Synchronization must be durable and idempotent.

A watch event is recorded locally before or atomically with enqueueing the remote operation. Failed operations remain pending and are retried when appropriate.

WorkManager is the preferred mechanism for deferred network synchronization once the Android project exists.

The queue must handle:

- no network;
- tracker outage;
- authentication expiry;
- repeated retries;
- duplicate enqueues;
- process death;
- already-watched remote state.

A single completed playback must not create multiple equivalent remote mutations.

## MyShows provider

MyShows is the initial tracker provider.

Product invariant: **MyShows Pro is not required.** WatchRelay performs progress/scrobble logic locally and writes only the final ordinary watched state.

Intended operations:

- episode watched;
- episode un-watched for undo where appropriate;
- movie status `finished` for watched;
- restoration of the previous movie state for undo where the remote API permits it.

Do not call the paid MyShows scrobbling endpoints as part of the normal WatchRelay product.

Authentication implementation must be selected only after the current supported MyShows flow is verified for a public third-party Android client. Credentials must never pass through a WatchRelay backend.

## Credential storage

Long-lived tracker credentials must be protected with Android Keystore-backed encryption.

Rules:

- never log tokens or passwords;
- never include them in diagnostic export;
- never place them in unencrypted preferences;
- exclude secrets from backups;
- if a password is temporarily required to obtain a token/session, discard it after the request;
- communicate with tracker services over HTTPS with normal certificate validation.

## UI boundary

The UI presents state; it does not own core watch decisions.

Primary mobile surfaces:

- onboarding/permissions;
- connection status;
- recent history;
- items needing attention;
- settings;
- mappings/undo where useful.

Primary TV surfaces:

- status;
- recent history;
- connection/pairing;
- settings requiring TV interaction.

TV must support D-pad focus navigation throughout. Long secret entry with a remote should not be a primary flow.

## TV pairing

If phone-assisted TV authorization is implemented, QR/pairing data must contain only short-lived pairing material, never a reusable MyShows token in plaintext.

The preferred model is an encrypted local pairing session with explicit user confirmation. A WatchRelay cloud relay is out of scope unless the product direction explicitly changes.

## Module strategy

Do not pre-emptively split the project into many Gradle modules.

Start with the smallest structure that keeps responsibilities clear. Separate Gradle modules are justified only when they provide concrete value such as:

- materially different platform/dependency surfaces;
- reusable independently tested core logic;
- build isolation;
- clear ownership or release boundary.

Interfaces are justified at real integration boundaries, not merely because there may be a second implementation someday.

## Dependencies

Expected categories, subject to implementation need:

- Kotlin / AndroidX;
- Jetpack Compose and TV-appropriate Compose components;
- Room;
- WorkManager;
- an HTTP client/serialization stack;
- Android media-session APIs.

This is not a mandatory dependency list. Every library must earn its place when implementation begins.

## Failure behavior

WatchRelay should fail conservatively:

- insufficient metadata → unresolved, not guessed;
- ambiguous match → ask, do not auto-sync;
- sync failure → keep local event pending;
- expired auth → surface reconnection without losing local history;
- missing permission → explain the unavailable function;
- unsupported player → report unsupported/experimental status, not silent false success.

## Future integrations

The architecture may later support additional playback sources and tracker providers such as Trakt or Simkl, but no abstraction should be added solely for an unimplemented future integration.

New providers must preserve the same core invariants: local watch decision, privacy, idempotent sync, and explicit compatibility claims.
