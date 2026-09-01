# Changelog

All notable user-visible changes to WatchRelay will be documented in this file.

The project is currently in pre-release development; versioned release sections will be added when distributable builds begin.

## Unreleased

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
- GitHub Actions CI and Dependabot configuration for the Android project.
- Real-device Phase 0 test plan.
- Signed Android release workflow with AAB as the primary Google Play artifact and APK as a secondary direct-install artifact.
- Automated AAB validation, native ABI inspection, 16 KB ELF/package-alignment checks, signing-certificate verification, and release gates for native compatibility regressions.
- Android release requirements and signing/ABI policy documentation.

### Changed

- Android compatibility baseline is now explicitly `minSdk = 26`, `targetSdk = 36`, and `compileSdk = 37`.
- CI now builds and validates signed release AAB/APK artifacts with a disposable CI key in addition to unit tests, lint, and the debug APK.

### Status

- No production-ready Android application has been released yet.
- Phase 0 technical validation is in progress; no player path is considered supported until real-device results are recorded.
