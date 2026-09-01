# WatchRelay

**Automatic watch tracking and sync companion for Android, Android TV and Google TV.**

> **Project status:** Android MVP development / real-device validation. The repository does not contain a production-ready app yet, and no player path is advertised as supported until it is validated on real devices.

WatchRelay is a free, local-first companion app that observes supported media playback, determines what was actually watched, identifies the movie or episode conservatively, and synchronizes the final watched state to a connected tracking service.

The first integration target is:

```text
LazyMedia Deluxe → WatchRelay → MyShows
```

WatchRelay is not a video player, streaming service, media catalog, downloader, or replacement for either application. Its purpose is deliberately narrow: **detect playback, identify the movie or episode, decide whether it was really watched, and sync the result.**

## Goals

- Android phones and tablets.
- Android TV and Google TV with proper D-pad/focus support.
- Background tracking with minimal user interaction after setup.
- Movies and TV episodes.
- Configurable watched threshold, with 80% as the default.
- Watched progress based on actual viewed intervals rather than only the final playback position.
- Local history, offline queue, retry, and undo.
- Conservative content matching: ambiguous matches require confirmation instead of creating a wrong history entry.
- No WatchRelay account, backend, advertising, subscription, or paid tier.

## MyShows integration

WatchRelay is designed to work with a regular free MyShows account. It does **not** depend on MyShows Pro and does not use the paid MyShows scrobbling flow (`/scrobble/start`, `/scrobble/pause`, `/scrobble/stop`).

The flow is:

```text
playback observation
        ↓
actual viewed intervals
        ↓
watched threshold reached
        ↓
conservative MyShows resolution
        ↓
local history + durable sync queue
        ↓
ordinary MyShows watched state
```

For episodes, WatchRelay uses the ordinary watched/unwatched operation. For movies, it uses the ordinary movie status with `finished` representing watched. Authentication and API use must remain compatible with the current MyShows service and its terms before a public release.

Synchronization is durable **at-least-once state synchronization**. WatchRelay prevents duplicate local queue entries for the same operation. If the app dies after MyShows accepts a state mutation but before local success is committed, the equivalent state-setting request may be retried because MyShows does not expose a server-side idempotency key. Repeated requests therefore have to converge on the same remote state.

## Playback observation and external-player bridge

WatchRelay currently implements two complementary Android paths:

1. background observation of active `MediaSession` / `MediaController` state after the user grants notification-listener access;
2. a narrow `video/*` external-player relay that can receive a playback request from a source application, extract only allowlisted identification metadata, and immediately forward the original transient intent to a user-selected installed video player.

The MediaSession adapter uses monotonic Android time for playback accounting and projects stale `PlaybackState.position` from `lastPositionUpdateTime` and playback speed. Wall-clock time is kept separate and is used only for history timestamps.

The external-player relay remembers only the selected player package and safe content metadata such as title, year, season/episode, IMDb/Kinopoisk identifiers, and media type when supplied. Playback URLs, stream/torrent locations, credentials, and raw intents are not persisted. Safe bridge metadata expires quickly and is consumed only by the intended player session.

Accessibility-based observation is **not** a default architecture choice and must not be added unless cleaner Android media APIs are proven insufficient and the privacy/UX trade-off is explicitly justified.

## Android MVP currently implemented

The `v0.4` development line now contains the product-facing phone/tablet shell and the end-to-end software pipeline needed for device validation:

- Home / Setup / History / Settings / Diagnostics surfaces;
- notification-listener setup and refresh after returning from Android settings;
- regular MyShows account connection with Android Keystore-backed token storage and explicit disconnect;
- background MediaSession polling with per-session playback pipelines;
- viewed-interval accounting, pause/resume, seek handling, playback-speed support, item changes, abrupt end handling, and configurable watched threshold;
- safe external-player relay with remembered player selection and an in-app reset option;
- allowlisted external-intent metadata extraction without persisted playback URLs;
- conservative movie/show/episode matching using saved mappings, external IDs, title/year evidence, and exact season/episode coordinates;
- durable ambiguous-match and retry-required attention items that survive process restart;
- user candidate selection before synchronization when a match is ambiguous;
- local Room-backed history and durable sync queue;
- pending, authentication-required, failed, synced, undo-pending, and undone states;
- WorkManager network-constrained synchronization with retry/backoff;
- safe movie/episode undo based on the previous remote state;
- configurable watched threshold from 50% to 100%, default 80%;
- redacted diagnostic export containing aggregate/app/device state but no titles, credentials, playback metadata, history content, or URLs;
- Room 3 schema export configured through the official Room Gradle plugin;
- CI verification for unit tests, lint, debug build, signed release AAB/APK, Google Play bundle checks, ABI/native inspection, signing checks, and 16 KB package/native alignment requirements.

The current development build identifies itself as `0.4.0-dev` (`versionCode = 4`).

## Matching and failure behavior

Matching deliberately prefers a missed mark over a wrong one. Evidence priority is saved user mapping, external IDs, title/year evidence, and exact episodic coordinates. Unknown media type is not silently assumed to be a movie.

A completed watch can end in one of these paths:

```text
strong match      → durable sync queue
ambiguous match   → Needs attention → user selection → durable sync queue
network/catalog   → Retry required → retry later
insufficient data → unresolved / no automatic write
```

If catalog lookup fails after a valid watched threshold has already been reached, the completed-watch decision is persisted as a retry-required attention item rather than discarded. User-confirmed mappings are reused on later equivalent metadata, while previous remote state is read fresh before a synchronized mark so undo does not rely on stale state.

## Planned supported playback paths

Real-device validation is still required for:

- LazyMedia Deluxe built-in player;
- LazyMedia Deluxe → VLC;
- LazyMedia Deluxe → MX Player;
- LazyMedia Deluxe → ViMu;
- Android MediaSession behavior on phones/tablets and Android TV/Google TV.

**Implementation is not a compatibility claim.** Actual support is recorded only after a reproducible real-device result is added to [Compatibility](docs/COMPATIBILITY.md). Follow [Phase 0 testing](docs/PHASE0-TESTING.md) for the validation protocol.

## Android baseline

The current baseline is:

- `minSdk = 26`;
- `targetSdk = 36`;
- `compileSdk = 37`;
- JDK 17;
- Kotlin 2.4.10;
- Android Gradle Plugin 9.3.1;
- Gradle 9.5.0 wrapper;
- Compose BOM 2026.08.00;
- Room 3.0.2.

Android 16/API 36 remains the target behavior until Android 17 target-specific behavior has been explicitly validated; compiling against API 37 does not itself raise the runtime requirement.

## Release and Google Play

The primary release artifact is a **signed Android App Bundle (`.aab`)** for Google Play. A signed APK may also be produced as a secondary artifact for direct installation or GitHub distribution.

WatchRelay contains no project-owned NDK code, but the current dependency graph can package native AndroidX libraries. AAB ABI splitting is enabled. CI requires `arm64-v8a`, checks packaged 64-bit ELF load alignment for 16 KB memory pages, validates the AAB as `PAGE_ALIGNMENT_16K`, and applies the 16 KB `zipalign` check to signed release APKs.

Release signing uses environment/CI secrets only; signing keys and passwords are never stored in Git. See [Android release requirements](docs/RELEASE.md).

## Product principles

- **Free:** no WatchRelay subscription and no required MyShows Pro subscription.
- **Local-first:** playback analysis and local history stay on the device unless synchronization requires a third-party request.
- **Private:** no WatchRelay cloud account or required telemetry.
- **Small:** avoid speculative features and infrastructure.
- **Reliable:** a missed automatic mark is preferable to a wrong mark.
- **Independent:** integrations stay behind narrow boundaries so future sources/providers do not redefine the product.

## Non-goals

WatchRelay does not provide or discover streams, torrents, downloads, or copyrighted media. It does not host a content catalog and does not expose source URLs from playback history.

A future discovery/catalog product may be built separately, but it is intentionally outside the scope of WatchRelay 1.0.

## Architecture

```text
Playback source / external-player intent
                ↓
        playback adapter
                ↓
 viewed-interval session engine
                ↓
        content matcher
          ↙          ↘
confirmed        needs attention
    ↓                 ↓
local history + durable sync queue
                ↓
      tracker provider (MyShows)
```

See [Architecture](docs/ARCHITECTURE.md) for boundaries and design constraints.

## Roadmap

The project is developed behind explicit release gates:

1. real-device technical validation of LMD/player observation and ordinary MyShows writes;
2. playback/session core;
3. durable persistence and synchronization;
4. conservative content matching;
5. Android MVP and real-device end-to-end validation;
6. first-class Android TV/Google TV UX;
7. compatibility hardening;
8. public beta, then v1.0.

See [Roadmap](docs/ROADMAP.md) for detailed phases and release gates.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
- [Phase 0 testing](docs/PHASE0-TESTING.md)
- [Compatibility](docs/COMPATIBILITY.md)
- [Android release requirements](docs/RELEASE.md)
- [Privacy](PRIVACY.md)
- [Security](SECURITY.md)
- [Contributing](CONTRIBUTING.md)
- [Changelog](CHANGELOG.md)
- [Agent rules](AGENTS.md)

## Independence and trademarks

WatchRelay is an independent project and is not affiliated with, endorsed by, or sponsored by LazyCat Software, LazyMedia Deluxe, MyShows, or the developers of supported third-party media players. Third-party product names and trademarks belong to their respective owners. See [NOTICE](NOTICE.md).

## License

WatchRelay is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE). Commercial use is not permitted by that license unless separately authorized by the licensor.
