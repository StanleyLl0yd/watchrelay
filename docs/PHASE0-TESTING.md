# Phase 0 Test Plan

This document describes the real-device checks required before WatchRelay can claim the first integration path works. A successful build is not a compatibility result.

## Build under test

Use a debug build from the `phase0/technical-proof` work until it is merged. Record the exact commit SHA with every result.

## MediaSession checks

On each target device:

1. Install and open WatchRelay.
2. Open **MediaSession access settings** from the app and grant WatchRelay notification access.
3. Start playback from LazyMedia Deluxe using one playback path at a time.
4. Return to WatchRelay and press **Refresh**.
5. Record the session package, title/subtitle, media ID, playback state, duration/position quality, metadata keys, and extras keys.
6. Repeat after pause, resume, seek, and playback stop.
7. Repeat for the next episode/autoplay transition when the player supports it.

Run the sequence for:

- LMD built-in player;
- LMD → VLC;
- LMD → MX Player;
- LMD → ViMu.

Do not mark a path supported in `docs/COMPATIBILITY.md` until these checks have been run on a real device or representative emulator and the result is recorded.

## External-player intent probe

WatchRelay temporarily registers a diagnostic `video/*` handler.

1. In LazyMedia Deluxe, choose an external player flow and select **WatchRelay** when Android offers a handler choice.
2. WatchRelay displays the incoming action, MIME type, URI scheme, categories, and sanitized extras.
3. Record only metadata useful for identification, such as title/season/episode/IDs and the names of available extra fields.
4. Do not copy stream URLs, authorization data, cookies, tokens, or other source credentials into issues or documentation.

The current probe intentionally does not forward the media to another player and never persists the source URI.

## MyShows Free checks

Use a normal non-Pro MyShows account and test items whose current state is known.

1. Authenticate in the **MyShows Free probe**.
2. Confirm that the password field is cleared after authentication.
3. Enter a MyShows movie ID.
4. Mark the movie watched and confirm on MyShows that it becomes watched.
5. Use **Undo movie** and confirm the previous status is restored.
6. Enter a MyShows episode ID.
7. Mark the episode watched and confirm on MyShows.
8. Undo the episode and confirm the watched mark is removed.
9. Repeat a mutation to observe whether duplicate/idempotent calls are harmless.
10. Repeat after the session expires or is invalidated when practical.

The probe must never call `/scrobble/start`, `/scrobble/pause`, or `/scrobble/stop`.

## Recording results

For each tested path, add to `docs/COMPATIBILITY.md`:

- date;
- WatchRelay commit/version;
- device/model;
- Android version;
- LazyMedia Deluxe version;
- player/version;
- metadata quality;
- duration/position quality;
- pause/seek/autoplay behavior;
- limitations;
- result: supported, limited, or unsupported.

Never record MyShows credentials, session tokens, playback URLs, torrent URLs, or other sensitive source data.

## Phase 0 release gate

Phase 0 is complete only after at least one real movie and one real episode complete this path on a non-Pro MyShows account:

```text
LMD/player → WatchRelay observation → viewed threshold → match → ordinary MyShows watched state
```

The current diagnostic build validates observation and MyShows mutations separately. Automatic viewed-interval thresholding and content matching belong to the next implementation steps and are not yet complete.
