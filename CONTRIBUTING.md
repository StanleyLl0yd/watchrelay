# Contributing

Thanks for helping improve WatchRelay.

## Before you start

Read:

- `AGENTS.md` for repository-wide engineering rules;
- `docs/ARCHITECTURE.md` for product boundaries;
- `docs/ROADMAP.md` for current priorities;
- `PRIVACY.md` and `SECURITY.md` for sensitive integration constraints.

For repository-wide cleanup or refactoring, also follow `docs/agent/audit-refactor.md`.

## Scope

Keep contributions aligned with WatchRelay's narrow purpose: reliable local playback tracking and watched-state synchronization.

Do not add media hosting, stream/torrent discovery, downloading, advertising, a WatchRelay cloud account, a paid tier, or a dependency on MyShows Pro unless the repository owner explicitly changes the product direction.

## Development principles

- Prefer the smallest correct implementation.
- Avoid speculative abstractions and dependencies.
- Preserve Android phone/tablet and Android TV/Google TV support.
- Prefer documented Android media APIs over invasive observation methods.
- Prefer a missed auto-match over a wrong watched mark.
- Keep source-code comments minimal, necessary, current, and in English only.
- Do not commit generated build output, credentials, keystores, tokens, local SDK paths, or personal data.

## Changes

Keep a pull request or commit focused on one coherent purpose. Update documentation in the same change when behavior, permissions, privacy, security, compatibility, architecture, or setup changes.

If you fix a bug in core playback, matching, persistence, or synchronization behavior, add the smallest regression test that captures the previous failure where practical.

## Verification

Run every applicable repository check. Once the Gradle project exists, use the checked-in wrapper rather than a locally installed Gradle version.

Typical checks will include the relevant subset of:

```text
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Additional platform/UI/instrumentation checks may be required depending on the change. `AGENTS.md` contains the verification matrix.

Do not state that a check passed unless you actually ran it successfully. If hardware, credentials, network access, signing material, or tooling prevented a check, say so in the change description.

## Compatibility claims

Do not mark a player/device path as supported based only on code review or successful compilation. Add a real validation result to `docs/COMPATIBILITY.md` before changing public support claims.

## Licensing

By contributing, you agree that your contribution is provided under the repository's [PolyForm Noncommercial License 1.0.0](LICENSE) unless a separate written agreement with the repository owner says otherwise.

Third-party code must have a compatible license and clear provenance. Do not copy code from a source whose terms are unknown or incompatible.

## Pull request checklist

Before requesting review, confirm as applicable:

- the change is scoped and does not alter unrelated behavior;
- tests cover new/changed core behavior;
- applicable tests/lint/builds were run;
- Android and TV implications were considered;
- new permissions/dependencies are justified;
- no secrets or sensitive playback URLs are present;
- privacy/security docs were updated if needed;
- compatibility claims reflect real testing;
- `CHANGELOG.md` was updated for user-visible changes.
