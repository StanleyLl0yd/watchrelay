# WatchRelay

**Automatic watch tracking and sync companion for Android, Android TV and Google TV.**

> **Project status:** early development / technical validation. The repository does not contain a production-ready app yet.

WatchRelay is a free, local-first companion app that observes supported media playback, determines what was actually watched, and synchronizes the final watched state to a connected tracking service.

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
- Configurable watched threshold, with 80% as the default target.
- Watched progress based on actual viewed intervals rather than only the final playback position.
- Local history, offline queue, retry, and undo.
- Conservative content matching: ambiguous matches require confirmation instead of creating a wrong history entry.
- No WatchRelay account, backend, advertising, subscription, or paid tier.

## MyShows integration

WatchRelay is designed to work with a regular free MyShows account. It will **not** depend on MyShows Pro and will not use the paid MyShows scrobbling flow (`/scrobble/start`, `/scrobble/pause`, `/scrobble/stop`).

The intended flow is:

```text
playback
   ↓
WatchRelay tracks viewed intervals locally
   ↓
watched threshold reached
   ↓
resolve MyShows movie / episode
   ↓
write the ordinary watched state to MyShows
```

For episodes, the integration target is the ordinary watched/unwatched operation. For movies, the target is the ordinary movie status with `finished` representing “watched”. Authentication and API use must remain compatible with the current MyShows service and its terms before a public release.

Synchronization is designed as durable **at-least-once state synchronization**. WatchRelay prevents duplicate local queue entries for the same operation. If the app dies after MyShows accepts a state mutation but before local success is committed, the same state-setting request can be retried; MyShows does not expose a server-side idempotency key, so exact-once network delivery cannot be guaranteed honestly. Equivalent retries must converge on the same remote state.

## Playback detection

The preferred integration order is:

1. Android `MediaSession` metadata and playback state.
2. Metadata/intent hand-off when LazyMedia Deluxe launches an external player.
3. A WatchRelay bridge mode for supported external players if that materially improves reliable identification.

Accessibility-based observation is **not** a default architecture choice and must not be added unless cleaner Android media APIs are proven insufficient and the privacy/UX trade-off is explicitly justified.

## Planned supported playback paths

The initial technical validation covers:

- LazyMedia Deluxe built-in player;
- VLC;
- MX Player;
- ViMu;
- Android MediaSession behavior on phones/tablets and Android TV/Google TV.

Actual support is recorded only after real-device validation. See [Compatibility](docs/COMPATIBILITY.md).

## Current technical proof

The repository contains a minimal Phase 0 Android diagnostic app, the playback core, durable synchronization, and the conservative content-matching core. Automatic tracking is not yet wired into a complete production UI flow. The current build provides:

- MediaSession inspection after the user grants Android notification-listener access;
- sanitized inspection of `video/*` intents sent to WatchRelay as an external-player handler;
- a MyShows Free diagnostic flow for authentication, movie watched/undo, and episode watched/undo without Pro scrobble endpoints;
- deterministic playback-session accounting based on actual viewed intervals, with conservative seek detection, pause/resume, playback speed, duplicate/stale callback protection, stop/replay handling, autoplay item transitions, and an 80% default watched threshold;
- Room-backed local watch history and durable pending-sync queue with atomic history/enqueue and terminal-outcome writes;
- deterministic local sync-operation IDs, retryable/auth-required/permanent-failure states, and explicit movie/episode undo operations;
- WorkManager network-constrained synchronization with exponential backoff and startup recovery after process restart;
- Android Keystore-backed AES-256-GCM tracker-token storage;
- metadata normalization for common movie/episode labels and filenames, including season/episode coordinates and release-noise cleanup;
- MyShows movie/show/episode resolution with IMDb/Kinopoisk external IDs first and conservative title/year fallback;
- confidence-based matching with explicit `confirmed`, `ambiguous`, and `unresolved` outcomes; low-confidence or competing candidates cannot auto-sync;
- persistent local user-confirmed mappings for ambiguous items, with fresh remote previous-state lookup before every synchronized mark so undo does not rely on stale state;
- deterministic matcher tests plus sanitized MyShows response fixtures;
- Android phone/tablet and Android TV/Google TV launcher entry points;
- CI verification for unit tests, lint, a debug build, signed release AAB/APK builds, and Google Play packaging constraints.

The UI for selecting an ambiguous match is intentionally part of the Android MVP phase. Until that UI exists, the matching core exposes candidates and a programmatic confirm/forget path without silently guessing.

Follow [Phase 0 testing](docs/PHASE0-TESTING.md) for real-device validation. Until results are recorded there and in the compatibility matrix, no LMD/player path is advertised as supported.

The Android baseline is `minSdk = 26`, `targetSdk = 36`, and `compileSdk = 37`, using JDK 17, Kotlin 2.4.10, Android Gradle Plugin 9.3.1, Gradle 9.5.0 in CI, and the stable Compose August 2026 BOM. Android 16/API 36 is kept as the target behavior until Android 17 target-specific behavior has been explicitly validated; compiling against API 37 does not by itself raise the runtime requirement.

## Release and Google Play

The primary release artifact is a **signed Android App Bundle (`.aab`)** for Google Play. A signed APK may also be produced as a secondary artifact for direct installation or GitHub distribution.

WatchRelay contains no project-owned NDK code, but the current AndroidX/Compose graph transitively packages `libandroidx.graphics.path.so` for `arm64-v8a`, `armeabi-v7a`, `x86_64`, and `x86`. AAB ABI splitting is explicitly enabled so a Play installation receives only its matching native ABI. CI requires `arm64-v8a`, validates all packaged 64-bit ELF `LOAD` segments for at least 16 KB alignment, validates the AAB as `PAGE_ALIGNMENT_16K`, and applies the 16 KB `zipalign` check to signed release APKs.

Release signing uses CI secrets only; signing keys and passwords are never stored in Git. See [Android release requirements](docs/RELEASE.md).

## Product principles

- **Free:** no WatchRelay subscription and no required MyShows Pro subscription.
- **Local-first:** playback analysis and local history stay on the device unless synchronization requires a third-party request.
- **Private:** no WatchRelay cloud account or telemetry is required by the product design.
- **Small:** avoid speculative features and infrastructure.
- **Reliable:** a missed automatic mark is preferable to a wrong mark.
- **Independent:** integrations are adapters, so additional playback sources or tracker services can be added later without redefining the product.

## Non-goals

WatchRelay does not provide or discover streams, torrents, downloads, or copyrighted media. It does not host a content catalog and does not expose source URLs from playback history.

A future discovery/catalog product may be built separately, but it is intentionally outside the scope of WatchRelay 1.0.

## Architecture

The intended high-level flow is:

```text
Playback source
      ↓
Playback adapter
      ↓
Playback session / viewed intervals
      ↓
Content matcher
      ↓
Local history + sync queue
      ↓
Tracker provider (initially MyShows)
```

See [Architecture](docs/ARCHITECTURE.md) for boundaries and design constraints.

## Roadmap

The project begins with a technical proof before the product UI:

1. Validate LMD/player metadata and MediaSession behavior on real Android and TV devices.
2. Validate the complete free-account MyShows movie/episode write flow.
3. Build the playback/session core and durable local sync queue.
4. Build conservative content matching.
5. Ship the Android MVP, including ambiguous-match resolution UI.
6. Complete Android TV/Google TV UX and pairing.
7. Build a tested compatibility matrix and harden reliability.
8. Public beta, then v1.0.

See [Roadmap](docs/ROADMAP.md) for release gates and detailed phases.

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
