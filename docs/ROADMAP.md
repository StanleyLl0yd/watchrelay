# Roadmap

This roadmap is outcome-based rather than date-based. A phase is complete only when its release gate is satisfied; compiling alone is not enough.

## v0 — Technical proof

Goal: prove the two risky integrations before building a large UI or architecture around assumptions.

### Playback investigation

Validate on real Android and Android TV/Google TV environments:

- LazyMedia Deluxe built-in player;
- LazyMedia Deluxe → VLC;
- LazyMedia Deluxe → MX Player;
- LazyMedia Deluxe → ViMu;
- available `MediaSession` metadata;
- playback state, duration, and position quality;
- metadata/IDs/intents passed to external players;
- autoplay/playlist behavior;
- whether a bridge mode materially improves identification.

### MyShows Free investigation

Using a regular non-Pro account, validate the complete flow for:

- authentication suitable for a third-party Android client;
- title/movie lookup;
- show/episode lookup;
- mark episode watched;
- undo episode watched;
- set movie watched (`finished`);
- read/restore previous movie state for undo;
- token/session expiry behavior;
- duplicate/idempotent operations.

Do not use MyShows Pro scrobble endpoints.

### Release gate

At least one movie and one episode must complete this real end-to-end path without MyShows Pro:

```text
LMD/player → WatchRelay observation → viewed threshold → match → ordinary MyShows watched state
```

The tested device/player paths and limitations must be recorded in `docs/COMPATIBILITY.md`.

## v0.1 — Playback core

Goal: build the minimal reliable watch-decision engine.

Implementation status: **complete and merged to `main`**. Real playback-source validation remains part of the broader v0 technical proof.

Planned work:

- playback event model;
- playback session lifecycle;
- viewed-interval accumulation;
- seek detection;
- pause/resume handling;
- item/session change detection;
- configurable watched threshold, default 80%;
- duplicate-event protection;
- deterministic unit tests.

### Release gate

Core tests cover normal play, pause/resume, forward/backward seek, duplicate callbacks, abrupt stop, replay, and autoplay transition without false watched decisions.

## v0.2 — Persistence and synchronization

Goal: never lose a valid watch event because the app or network disappears.

Implementation status: **complete, merged to `main`, and published in technical-preview release `v0.2.0`**.

Implemented work:

- local history;
- durable pending-sync queue;
- retry/backoff;
- local idempotency and convergent remote retries;
- WorkManager integration;
- authentication-expiry state and recovery;
- conservative undo model;
- secret storage backed by Android Keystore.

Synchronization semantics are durable **at-least-once state synchronization**. WatchRelay prevents duplicate local enqueue operations. If the process dies after MyShows accepts a mutation but before local success is committed, the same state-setting request can be retried because MyShows does not expose a server-side idempotency key. Equivalent retries must therefore be safe and converge on the same remote state.

### Release gate

A completed watch survives process death/offline state, remains durably queued, and converges to the intended remote state once connectivity returns. Duplicate local enqueue operations are rejected and equivalent retry requests do not create a different remote state.

## v0.3 — Content matching

Goal: identify content conservatively and make errors repairable.

Implementation status: **implemented and covered by deterministic release-gate tests; product-facing ambiguous-match UI remains part of v0.4**.

Implemented work:

- metadata normalizer with common season/episode patterns and release-noise cleanup;
- movie matching;
- series/season/episode matching;
- IMDb/Kinopoisk external-ID matching where available;
- title/year fallback;
- confidence model with explicit auto-confirm threshold and runner-up margin;
- explicit ambiguous/unresolved outcomes that cannot auto-sync;
- persistent user-confirmed mappings;
- correction/forget path;
- fresh previous MyShows state capture for safe undo;
- sanitized MyShows response fixtures and deterministic matcher tests.

Ambiguous-match selection UI is intentionally deferred to the Android MVP phase; the matching core already returns the candidates and persists an explicit user choice.

### Release gate

Known unambiguous fixtures auto-match; deliberately ambiguous and title-only low-confidence fixtures do not auto-sync; user corrections persist and are reused; MyShows response parsing is covered by sanitized fixtures.

## v0.4 — Android MVP

Goal: make the proven core usable on phones and tablets.

Planned surfaces:

- onboarding;
- required permission setup;
- MyShows connection;
- tracking status;
- recent history;
- pending/failed sync state;
- items needing attention;
- ambiguous-match selection/correction;
- watched-threshold setting;
- undo;
- privacy/security information;
- safe diagnostic export.

### Release gate

A new user can install, connect, grant required permissions, watch supported content, see the result, recover from a sync failure, resolve an ambiguous match, and undo an incorrect mark without developer tools.

## v0.5 — Android TV / Google TV

Goal: achieve first-class TV usability rather than stretching the phone UI onto a television.

Planned work:

- TV layouts;
- D-pad/focus navigation;
- visible focus states;
- remote-friendly onboarding;
- phone-assisted/QR connection where feasible;
- TV notification behavior;
- background/process-death testing on TV devices;
- large-screen history and settings.

### Release gate

All required TV flows are possible with a remote only, while long-secret entry is avoided in the primary path.

## v0.6 — Compatibility hardening

Goal: turn experimental integrations into an explicit support matrix.

Planned validation:

- LMD built-in player;
- VLC;
- MX Player;
- ViMu;
- multiple Android versions;
- Android TV/Google TV devices/emulators;
- autoplay/binge watching;
- long pause;
- player crash;
- application kill/restart;
- reboot;
- network loss/recovery;
- already-watched content;
- authentication expiry;
- malformed/partial metadata.

### Release gate

Every path advertised as supported has a recorded real validation result. Experimental/unsupported paths are labelled honestly.

## v0.7 — Public beta

Goal: collect compatibility evidence without weakening privacy.

Planned work:

- release packaging;
- reproducible CI build;
- signing workflow without repository secrets;
- opt-in diagnostic export with strict redaction;
- bug-report template;
- compatibility feedback loop;
- release notes and changelog discipline.

### Release gate

Beta artifacts are built from the tagged source, signed with the intended certificate, and the privacy/security documentation matches the actual app.

## v1.0 — Stable WatchRelay

Minimum v1.0 scope:

- Android phones/tablets;
- Android TV/Google TV;
- tested LazyMedia Deluxe playback path(s);
- movies and episodes;
- local viewed-interval tracking;
- configurable watched threshold;
- MyShows Free integration without MyShows Pro;
- offline durable sync;
- duplicate protection;
- conservative matching;
- user correction and undo;
- local history;
- secure credential handling;
- no WatchRelay account, ads, paid tier, or backend.

## After v1.0

Only after the initial path is stable:

### Additional tracker providers

Potential examples:

- Trakt;
- Simkl.

Do not add provider abstractions before a concrete provider implementation needs them.

### Additional playback sources

Potential examples:

- standalone VLC;
- Kodi;
- Jellyfin;
- Plex;
- other Android players/services that expose reliable metadata.

### Family profiles

Possible future model:

- multiple local profiles on a shared TV;
- separate tracker credentials/history per profile;
- quick active-profile selection.

For v1.0, one device/account mapping is sufficient unless testing proves a stronger requirement.

### Separate discovery product

A service for “what was newly released / where it is available” is intentionally not part of WatchRelay. It may later integrate with WatchRelay history, but should remain a separate product boundary unless there is a compelling reason to merge them.

## Roadmap discipline

- Do not mark an item complete because code exists; verify the release gate.
- Do not advertise support before adding the tested result to `docs/COMPATIBILITY.md`.
- When scope changes, update this roadmap and README in the same change.
- Remove obsolete roadmap items rather than letting completed or abandoned plans become misleading documentation.
