# Compatibility

This file records **verified** playback/platform compatibility. Planned support is not the same as tested support.

## Status legend

- ✅ **Supported** — validated end to end and suitable for normal use.
- 🧪 **Experimental** — partially validated or known to have limitations.
- ❌ **Unsupported** — known not to provide what WatchRelay requires.
- ⏳ **Not yet validated** — planned for investigation; no support claim.

## Platforms

| Platform | Status | Notes |
| --- | --- | --- |
| Android phone | ⏳ Not yet validated | Technical proof pending. |
| Android tablet | ⏳ Not yet validated | Technical proof pending. |
| Android TV | ⏳ Not yet validated | D-pad/background/media-session validation pending. |
| Google TV | ⏳ Not yet validated | D-pad/background/media-session validation pending. |

## LazyMedia Deluxe playback paths

| Playback path | Android | Android TV / Google TV | Notes |
| --- | --- | --- | --- |
| LMD built-in player | ⏳ | ⏳ | Need to inspect MediaSession metadata, duration, position, and item identity. |
| LMD → VLC | ⏳ | ⏳ | Need to inspect launch intent and VLC MediaSession metadata. |
| LMD → MX Player | ⏳ | ⏳ | Need to inspect launch intent and playback metadata. |
| LMD → ViMu | ⏳ | ⏳ | Need to inspect launch intent and playback metadata. |
| WatchRelay bridge → external player | ⏳ | ⏳ | Implement only if it materially improves reliability. |

## MyShows

| Capability | Status | Notes |
| --- | --- | --- |
| Regular free account | ⏳ Not yet validated in-app | Required product invariant; Phase 0 must prove end-to-end use. |
| Episode lookup | ⏳ | Validate current API/auth flow. |
| Mark episode watched | ⏳ | Must work without MyShows Pro. |
| Undo episode watched | ⏳ | Must work without MyShows Pro. |
| Movie lookup | ⏳ | Validate current API/auth flow. |
| Mark movie `finished` | ⏳ | Must work without MyShows Pro. |
| Restore previous movie state | ⏳ | Required for safe undo where API semantics allow it. |
| MyShows Pro scrobble API | ❌ Out of scope | WatchRelay must not depend on paid scrobble endpoints. |

## Validation record format

When a path is tested, record at minimum:

- WatchRelay version/commit;
- device/model or emulator;
- Android/API version;
- LazyMedia Deluxe version where relevant;
- player/version;
- observed metadata quality;
- position/duration reliability;
- movie result;
- episode result;
- seek/pause/autoplay behavior;
- known limitations.

Do not replace a prior result without preserving important regression context in Git history or release notes.

## Support rule

README, release notes, store listings, and UI must not describe a path as supported until this file contains a corresponding real validation result.
