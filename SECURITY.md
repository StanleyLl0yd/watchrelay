# Security Policy

## Project status

WatchRelay is currently in early development. Security requirements in this repository apply from the first implementation commit onward.

## Supported versions

Until the first stable release, security fixes target the current development branch. After stable releases begin, this section must be updated to state which release lines receive fixes.

## Reporting a vulnerability

If GitHub's private vulnerability reporting is enabled for this repository, use **Security → Report a vulnerability**.

Do not publish credentials, tokens, private keys, signing material, sensitive playback URLs, or exploit details in a public issue.

If private vulnerability reporting is not available, contact the maintainer through the GitHub profile associated with this repository and disclose only enough information publicly to establish a private communication path.

For non-sensitive security hardening or policy questions, a normal GitHub issue is acceptable.

## Security invariants

WatchRelay must:

- never require a WatchRelay backend for the core product;
- never commit or log credentials, tokens, passwords, signing keys, keystores, API secrets, or authorization headers;
- protect long-lived tracker credentials with Android Keystore-backed encryption;
- exclude reusable credentials from backup and diagnostic export;
- use normal HTTPS/TLS certificate validation for third-party services;
- request the minimum Android permissions required for supported features;
- keep Android components non-exported unless external access is intentionally required;
- validate and constrain any incoming intents/URIs handled by a bridge mode;
- avoid persisting raw streaming/torrent/source URLs;
- keep synchronization idempotent so retries cannot multiply remote mutations;
- treat third-party metadata and network responses as untrusted input;
- keep dependencies current enough to avoid known material vulnerabilities, while avoiding churn with no security or maintenance benefit.

## Authentication

Authentication must use the current supported flow of the connected tracker.

If a username/password flow is temporarily necessary to obtain a token/session:

- send credentials only to the intended service over HTTPS;
- never route them through a WatchRelay server;
- never persist the password;
- discard the password immediately after the authentication attempt;
- store only the minimum reusable credential required by the service, protected by Android Keystore-backed encryption.

Do not weaken authentication or TLS validation to work around integration problems.

## Android security

Changes involving any of the following require explicit security review:

- exported activities/services/receivers/providers;
- notification-listener/media-session access;
- Accessibility Service;
- boot receivers/background services;
- deep links/app links;
- file/content providers;
- URI permissions;
- WebView or browser-based authentication;
- local network pairing;
- QR pairing payloads;
- backup/restore rules;
- signing configuration.

TV pairing, if implemented, must not place a reusable tracker token directly in a QR code. Pairing material should be short-lived and bound to an explicit user-approved session.

## Logging and diagnostics

Production logs and exported diagnostics must not contain secrets or playback source URLs.

Redaction should happen before values enter the logging/reporting layer rather than relying only on post-processing.

When debugging authentication or playback integration, use synthetic/sanitized values in committed tests and fixtures.

## Dependencies and supply chain

- Use the Gradle wrapper once introduced and keep wrapper changes reviewable.
- Pin dependency/plugin versions through the repository's chosen version-management mechanism.
- Review dependency updates for source, release notes, license compatibility, and security impact where practical.
- Do not add dependencies from untrusted repositories or arbitrary binary downloads when a maintained standard source is available.
- Do not commit third-party binaries without a clear reason, provenance, and license review.

## Release security

- Signing material belongs only in the developer/CI secret store, never in Git.
- Release artifacts must be built from the intended tagged commit.
- Verify the signing certificate fingerprint when release signing is configured.
- Do not print keystore passwords, encoded keystores, or secrets in CI logs.
- Release notes and privacy/security documentation must match the actual artifact.

## Security fixes

Security fixes should include the smallest regression test or verification that proves the issue is addressed when such a test is practical and does not expose sensitive exploit material.

Do not claim a vulnerability is fixed until the relevant verification has actually been run.
