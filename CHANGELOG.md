# Changelog

All notable user-visible changes to WatchRelay will be documented in this file.

## Unreleased

No changes yet.

## 0.2.0 - 2026-09-01

Technical preview release covering the implemented playback core and the first durable persistence/synchronization layer. This is not yet a production-ready player-integration release.

### Added

- Initial product documentation and project boundaries.
- Architecture and phased roadmap.
- Privacy and security policies.
- Compatibility tracking document.
- Repository agent rules and mandatory full-audit/refactoring protocol.
- PolyForm Noncommercial License 1.0.0.
- Phase 0 Android diagnostic application for Android, Android TV, and Google TV.
- MediaSession inspection with notification-listener access.
- Sanitized external-player intent probe for LazyMedia Deluxe investigation.
- Non-Pro MyShows diagnostic flow for authentication, movie watched/undo, and episode watched/undo validation.
- Deterministic playback core with session lifecycle, viewed-interval accumulation, conservative seek handling, pause/resume, playback speed, duplicate/stale callback protection, autoplay transition handling, and configurable watched threshold.
- Unit coverage for normal playback, pause/resume, forward/backward seek, duplicate/stale callbacks, abrupt stop, replay, autoplay transition, playback speed, and threshold configuration.
- Room-backed local watch history and durable pending-sync queue.
- Atomic watch-history plus sync-enqueue transactions and deterministic local operation IDs.
- Atomic queue/history outcome commits after remote attempts to prevent split local state after process death.
- WorkManager-based network-constrained synchronization with exponential retry/backoff and startup recovery.
- Explicit MyShows Free watch/undo operations for movies and episodes without Pro scrobble endpoints.
- Authentication-required, retryable, permanent-failure, pending, synced, undo-pending, and undone synchronization states.
- Conservative episode undo that restores a known previous state and refuses destructive uncheck when the prior state is unknown.
- Android Keystore-backed AES-256-GCM protection for tracker tokens.
- Expired MyShows credential removal on HTTP 401/403 to prevent stale-token retry loops.
- Unit coverage for duplicate enqueue protection, retry/restart behavior, authentication expiry/resume, auth expiry during undo, movie/episode undo, permanent failures, and deterministic operation identity.
- GitHub Actions CI and Dependabot configuration for the Android project.
- Real-device Phase 0 test plan.
- Signed Android release workflow with AAB as the primary Google Play artifact and APK as a secondary direct-install artifact.
- Automated AAB validation, native ABI inspection, 16 KB ELF/package-alignment checks, signing-certificate verification, and release gates for native compatibility regressions.
- Android release requirements and signing/ABI policy documentation.

### Changed

- Android compatibility baseline is `minSdk = 26`, `targetSdk = 36`, and `compileSdk = 37`.
- CI builds and validates signed release AAB/APK artifacts with a disposable CI key in addition to unit tests, lint, and the debug APK.
- Tracker synchronization is modeled as durable at-least-once state synchronization: local duplicate enqueues are prevented, while an identical state-setting request may be retried after a crash window because MyShows does not expose a server-side idempotency key.
- WorkManager scheduling uses append-or-replace semantics so a watch event queued while another drain is active cannot be stranded after the active worker exits.

### Status

- Version `0.2.0` is a technical preview, not a production-ready release.
- Phase 0 technical validation is still in progress; no player path is considered supported until real-device results are recorded.
- Playback core and persistence/synchronization implementation are complete for this milestone.
