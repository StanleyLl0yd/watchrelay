# Repository Agent Rules

These rules apply to the entire repository and to every automated coding agent working on WatchRelay. Inspect the current implementation before changing it, preserve unrelated user changes, and prefer the smallest correct change that satisfies the task.

## Product invariants

- WatchRelay is a free, local-first companion app for Android phones/tablets and Android TV/Google TV.
- The initial product path is `LazyMedia Deluxe → WatchRelay → MyShows`.
- A regular free MyShows account must be sufficient for the intended integration. Do not make MyShows Pro a dependency and do not use the paid `/scrobble/start`, `/scrobble/pause`, or `/scrobble/stop` flow.
- WatchRelay tracks playback locally, decides when the watched threshold is reached, then writes the final watched state through the ordinary tracker integration.
- Default watched threshold is 80% unless the product requirement changes. Progress must represent actual viewed intervals, not merely the final playback position.
- Prefer a missed automatic mark over a wrong automatic mark. Ambiguous content matches require confirmation or must remain unresolved.
- No WatchRelay account, backend, advertising, subscription, or paid tier is part of the product unless explicitly approved by the repository owner.
- Do not add media discovery, streaming, torrent, download, or copyrighted-content delivery features to WatchRelay. Playback/source URLs must not become part of user-visible history.
- WatchRelay must remain independent from third-party brands. Do not imply endorsement by LazyMedia Deluxe, MyShows, VLC, MX Player, ViMu, or other integrations.
- Accessibility-based observation is not a default solution. Use standard Android media/session APIs first; any Accessibility Service requires an explicit product decision, documented necessity, and privacy review.

## Sources of truth

Use the narrowest authoritative source for the concern being changed:

- Product scope and public promises: `README.md` and `docs/ROADMAP.md`.
- Architecture and boundaries: `docs/ARCHITECTURE.md`.
- Verified device/player support: `docs/COMPATIBILITY.md` only after real testing.
- Privacy behavior: `PRIVACY.md`.
- Security expectations and disclosure: `SECURITY.md`.
- Release history: `CHANGELOG.md` and Git tags/releases once they exist.
- Build versions, SDK levels, dependencies, package IDs, and generated sources: the actual Gradle/build configuration once introduced.
- Existing behavior: executable tests plus the current implementation. Do not silently change behavior merely to make documentation and code agree; resolve conflicts deliberately.
- Full audit/refactor procedure: `docs/agent/audit-refactor.md`.

When two sources conflict, do not guess. Preserve working behavior where safe, identify the conflict, and update the authoritative source as part of the same change when the intended behavior is clear.

## Architecture boundaries

- Keep the architecture proportional to the product. Do not create Gradle modules, interfaces, repositories, use cases, wrappers, or dependency-injection layers merely for theoretical cleanliness.
- Start with clear package/component boundaries; split into separate modules only when the boundary provides measurable build, ownership, testability, platform, or dependency value.
- Playback-source integration, playback/session accounting, content matching, persistence/sync queue, tracker integration, credentials, and UI are separate responsibilities. Avoid leaking third-party API models into core playback logic.
- Treat playback adapters and tracker providers as replaceable integration boundaries, but do not generalize beyond concrete supported requirements.
- Core watched-state decisions must be deterministic and testable without Android UI.
- UI observes application state; UI code must not become the authoritative source for watch progress, matching, or sync state.
- The local database is the durable source for WatchRelay history/pending sync once persistence exists. Remote tracker state is authoritative only for the remote account state it represents.
- Network loss, process death, duplicate callbacks, repeated playback events, and retries must not create duplicate watched operations.
- Bridge/proxy playback mode, if implemented, must pass through only what is needed for playback and identification and must not persist stream URLs.

## Data / privacy / security

- Collect and persist only data required for playback tracking, matching, local history, troubleshooting, and user-requested synchronization.
- Never commit or log credentials, access/session tokens, passwords, private keys, signing material, keystores, API secrets, personal data, raw media URLs, torrent URLs, or generated secrets.
- Protect long-lived credentials with Android Keystore-backed encryption. Do not store reusable credentials in plaintext preferences, logs, crash reports, exported files, or backups.
- If login/password is ever used to obtain a session token, transmit it only directly to the intended service over HTTPS, never persist it, and discard it immediately after authentication.
- Diagnostic export must redact credentials, identifiers that are not needed for diagnosis, and playback/source URLs.
- Request the minimum Android permissions necessary. A new high-sensitivity permission requires a concrete functional need, documented rationale, and review of store-policy implications.
- No analytics, advertising SDK, remote telemetry, or WatchRelay backend may be introduced without an explicit product decision and corresponding privacy-document update.
- Do not weaken TLS, certificate validation, Android sandboxing, backup exclusions, exported-component restrictions, or secure credential storage for convenience.

## Platform constraints

- Android phones/tablets and Android TV/Google TV are first-class targets. A change is incomplete if it unnecessarily breaks either form factor.
- TV UI must be fully usable with D-pad/remote focus navigation; never rely solely on touch, hover, or free-form text input.
- Keep TV interactions short and avoid requiring long secrets to be typed with a remote. Prefer secure pairing/QR flows where applicable.
- Respect modern Android background-execution, notification, boot, process-death, and power-management constraints rather than relying on undocumented persistence tricks.
- Prefer `MediaSession`/`MediaController` and documented intent metadata for playback observation.
- Do not claim a player/device path is supported until it has been validated and recorded in `docs/COMPATIBILITY.md`.
- SDK/API requirements come from the build files once established. Do not raise `minSdk`, change target behavior, or drop supported form factors without a demonstrated need.
- Preserve accessibility of the WatchRelay UI itself, including focus visibility, content descriptions where needed, readable contrast, and scalable text.

## Dependency and generated-code policy

- Every dependency needs a concrete current use. Prefer Android/Kotlin/platform facilities when they solve the requirement with less total complexity.
- Do not add overlapping libraries for networking, serialization, storage, logging, image loading, dependency injection, or testing without a demonstrated need.
- Do not replace a mature dependency with custom code merely to reduce dependency count.
- Pin and update dependencies through the repository's chosen Gradle/version-management mechanism once created; avoid scattered version declarations.
- Generated code and build output must not be hand-edited or committed unless the build/tool explicitly requires the generated artifact to be versioned.
- Keep Gradle wrapper files versioned once introduced. Do not commit local SDK paths, IDE state, caches, build directories, APK/AAB outputs, or signing files.

## Verification matrix

Run the checks applicable to the change. Never claim a check passed unless it was actually run successfully.

| Change area | Minimum verification when available |
| --- | --- |
| Pure Kotlin/core logic | focused unit tests + full JVM unit suite |
| Playback/session accounting | interval/seek/pause/resume/duplicate-event regression tests |
| Content matching | deterministic matcher tests including ambiguous/no-match cases |
| MyShows/tracker integration | unit/contract tests with sanitized fixtures or mock server; never commit real credentials |
| Room/persistence | migration/DAO tests and retry/idempotency tests |
| Android UI | relevant unit/UI checks + `lint` + Android build |
| Android TV/Google TV UI | Android build + focus/D-pad validation on TV/emulator where available |
| Manifest/permissions/background components | `lint`, build, exported-component/permission review, relevant runtime test |
| Dependency/build changes | clean build, tests, lint, dependency/security review as available |
| Release changes | full test/lint/build suite plus signed-artifact verification when signing is available |
| Documentation-only changes | link/path/content consistency; no build claim unless a build was run |

Once Gradle exists, prefer repository-provided wrapper commands such as `./gradlew test`, `./gradlew lint`, and the relevant assemble/bundle tasks. Add narrower project-specific verification commands here only when they become stable repository contracts.

If hardware, credentials, network access, signing material, or tooling prevents a check, state that limitation explicitly.

## Release integrity

- Release from a clean, reviewed commit on the intended branch/tag.
- Keep `versionCode`, `versionName`, release notes, documentation, compatibility claims, and artifacts consistent.
- Update `CHANGELOG.md` for user-visible changes and keep `README.md`, privacy/security docs, roadmap, and compatibility documentation accurate after every release.
- Do not publish an APK/AAB that was built from uncommitted or different source than the tagged release.
- Signing keys and passwords must never enter the repository. CI signing material must be stored only in the platform's secret store.
- Verify release artifacts are actually signed with the expected certificate when signing is configured.
- Do not label an integration/player/device as stable solely because it compiled; support claims require the relevant runtime validation.

## Git and documentation discipline

- Keep changes focused and logically coherent. Preserve unrelated user changes.
- Keep the default branch buildable once executable code exists.
- Use clear commit messages that describe the change, not the tool that made it.
- Do not force-push, rewrite shared history, delete tags/releases, or perform destructive repository operations unless explicitly requested.
- Keep comments minimal, necessary, current, and in English only. Do not add comments that merely restate the code.
- Remove stale, misleading, redundant, and commented-out code. Keep TODO/FIXME only for real current constraints and make them actionable.
- Update documentation in the same change when behavior, permissions, privacy, compatibility, architecture, setup, or release process changes.
- Preserve established documentation structure and formatting unless there is a concrete reason to change it.
- Do not claim implementation, compatibility, security properties, tests, or release readiness that the repository does not actually provide.

## Full repository audit

For any request for a full audit, cleanup, optimization, simplification, or deep refactor, **read and follow `docs/agent/audit-refactor.md` before making repository-wide changes**. Its protocol is mandatory in addition to these repository-specific rules.
