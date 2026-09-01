# Architecture

## Status

WatchRelay is in technical-validation stage. The playback-session core, durable persistence/synchronization layer, and conservative content-matching core exist; playback-source adapters and product UI remain under development.

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

The matcher converts source metadata into a tracker entity and is intentionally conservative. It is deterministic and testable without Android UI or network transport.

Current evidence order:

1. a user-confirmed mapping for the exact normalized source signature;
2. IMDb or Kinopoisk external ID when supplied by the playback source;
3. normalized original/localized title plus year;
4. exact season/episode coordinates inside a resolved show.

Common playback labels and filenames are normalized before matching. The normalizer extracts common `SxxExx` / `NxNN` coordinates, removes file extensions and common release-quality/codec noise, and normalizes punctuation/case/diacritics for comparison. Explicit structured metadata supplied by the playback source takes precedence over values parsed from a filename.

The current title/year score is deliberately strict. Exact normalized title evidence is not sufficient by itself for automatic synchronization; an exact year raises an otherwise exact title match into the automatic-confirm range. Automatic confirmation also requires a margin over the runner-up candidate so equally plausible titles remain ambiguous. Unknown media type is unresolved instead of guessed as a movie.

The matcher produces exactly one of three outcomes:

- **confirmed** — evidence is strong enough to create a tracker target;
- **ambiguous** — one or more plausible candidates exist but require explicit user choice;
- **unresolved** — required evidence is missing or no safe candidate exists.

For episodic content, resolving a show is not enough: the requested season and episode must exist exactly in that show's MyShows episode catalog. Missing or duplicate coordinates are not silently guessed.

User-confirmed corrections are persisted locally using a deterministic signature of the normalized source metadata. The saved mapping stores only tracker identity; it does **not** cache previous remote watched state. Before a confirmed item is handed to synchronization, WatchRelay reads the current previous MyShows state again so later undo restores the state that existed immediately before WatchRelay's mutation.

The current mapping set is small, contains no secrets, and does not justify expanding the Room schema. It is therefore stored in dedicated private `SharedPreferences` with synchronous persistence on an IO dispatcher. If future mapping requirements need querying, migrations, or richer relational data, moving mappings into Room can be reconsidered with an explicit migration.

The Android MVP owns the ambiguous-selection UI. The matching core only exposes candidates and confirm/forget operations; UI code must not invent its own matching heuristics.

## Local persistence

The current Android persistence layer uses Room 3 and stores data needed for durable synchronization and local history. Current tables cover:

- local watch history;
- pending/retry synchronization operations;
- previous remote state needed for undo;
- synchronization/error state and attempt metadata.

Content mappings are currently persisted separately as described above. Future schema additions may include current/recent playback recovery data and user settings when those phases are implemented.

Raw stream URLs, torrent URLs, passwords, and reusable tracker credentials do not belong in the database. Database version changes must use explicit migrations; destructive fallback is not an acceptable production default.

## Sync queue

Synchronization is durable and locally idempotent.

A new watched event is written to local history and its remote operation is enqueued in one Room transaction. After a remote attempt, the queue outcome and corresponding history state are also committed in one Room transaction so a process death cannot leave a terminal queue row paired with stale pending history. Failed retryable operations remain pending. Authentication failures move the operation into an explicit authentication-required state without deleting the local history. Undo is represented as a separate deterministic mutation.

WorkManager performs deferred network synchronization with a connected-network constraint and exponential backoff. Unique work uses append-or-replace semantics so a new event scheduled while another queue drain is active is not stranded after that worker exits. App startup also schedules a drain, allowing durable pending work to recover after process restart. If a valid encrypted token survived a restart, the worker can restore auth-blocked operations to pending; a token rejected by MyShows with HTTP `401` or `403` is deleted immediately so stale credentials cannot create a retry loop.

Delivery semantics are **at least once**, not exactly once. WatchRelay prevents duplicate local enqueue operations using deterministic operation IDs. However, if the process dies after the tracker accepts a request but before local success is committed, the same remote request may be sent again. MyShows does not expose a server-side idempotency key, so the provider operations used by WatchRelay must be state-setting operations whose repeated execution converges on the same remote state.

The queue handles:

- no network;
- tracker outage;
- authentication expiry;
- repeated retries;
- duplicate local enqueues;
- process death;
- already-watched remote state where provider data is available.

A single completed playback must not create multiple local equivalent mutations. Equivalent remote retry requests after an unavoidable crash window must not create a different final tracker state.

## MyShows provider

MyShows is the initial tracker provider.

Product invariant: **MyShows Pro is not required.** WatchRelay performs progress/scrobble logic locally and writes only the final ordinary watched state.

Current ordinary operations used by the synchronization layer:

- episode watched;
- episode watched/unwatched restoration for undo when the prior remote episode state is known;
- movie status `finished` for watched;
- restoration of the previous movie state for undo where the remote API permits it.

Current ordinary/public operations used by content resolution include show search, movie catalog search, external-ID lookup, show details with episodes, and authenticated profile episode-state lookup. Response parsing is isolated and covered by sanitized fixtures rather than live credentials.

Episode undo is deliberately conservative. After an episode is resolved to a MyShows show/episode ID, WatchRelay reads the authenticated watched-episode list for that show. Presence means the previous state was `watched`; successful absence means it was `unwatched`. That state is placed on the sync target before WatchRelay's own mutation. If previous state cannot be established because authentication/provider access fails, a final sync target is not fabricated.

Do not call the paid MyShows scrobbling endpoints as part of the normal WatchRelay product.

The current diagnostic client can obtain a MyShows session token for technical validation, but production authentication must be selected only after the supported public third-party Android flow is verified. Credentials must never pass through a WatchRelay backend.

## Credential storage

Long-lived tracker credentials are protected with Android Keystore-backed AES-256-GCM encryption. Only ciphertext and IV are persisted outside the Keystore, and encrypted token persistence is committed synchronously before the authentication flow reports success.

Rules:

- never log tokens or passwords;
- never include them in diagnostic export;
- never place plaintext credentials in preferences or Room;
- exclude secrets from backups;
- if a password is temporarily required to obtain a token/session, discard it after the request;
- communicate with tracker services over HTTPS with normal certificate validation.

## UI boundary

The UI presents state; it does not own core watch decisions or matching heuristics.

Primary mobile surfaces:

- onboarding/permissions;
- connection status;
- recent history;
- items needing attention, including ambiguous matches;
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

Current/expected categories, subject to implementation need:

- Kotlin / AndroidX;
- Jetpack Compose and TV-appropriate Compose components;
- Room 3;
- AndroidX SQLite;
- WorkManager;
- Android media-session APIs.

A small `org.json` JVM artifact is test-only so sanitized parser fixtures exercise real JSON behavior outside Android's mockable test jar; it is not shipped in the app.

This is not a mandatory dependency list. Every library must earn its place when implementation begins.

## Failure behavior

WatchRelay should fail conservatively:

- insufficient metadata → unresolved, not guessed;
- unknown media type → unresolved, not assumed to be a movie;
- ambiguous/low-confidence match → ask, do not auto-sync;
- missing exact season/episode → unresolved, do not substitute another episode;
- inability to read current remote state → do not fabricate a sync target for destructive undo semantics;
- sync failure → keep local event pending when retryable;
- expired auth → clear rejected credentials and surface reconnection without losing local history;
- unknown prior episode state → do not issue destructive automatic undo;
- permanent provider error → preserve local history and expose failed state instead of spinning forever;
- missing permission → explain the unavailable function;
- unsupported player → report unsupported/experimental status, not silent false success.

## Future integrations

The architecture may later support additional playback sources and tracker providers such as Trakt or Simkl, but no abstraction should be added solely for an unimplemented future integration.

New providers must preserve the same core invariants: local watch decision, privacy, durable state synchronization, and explicit compatibility claims.
