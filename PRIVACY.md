# Privacy

## Current status

WatchRelay is under development and is not yet a production release. This document defines the project's privacy requirements and must be updated before release whenever the actual implementation changes them.

## Core privacy model

WatchRelay is designed to be **local-first**.

The project does not require a WatchRelay account, WatchRelay cloud backend, advertising network, or analytics service. Playback analysis, local history, mappings, settings, and pending synchronization are intended to remain on the user's device except when data must be sent to a tracker explicitly connected by the user.

## Data WatchRelay may process locally

To provide watch tracking, the app may process and store only what is needed for the feature, such as:

- movie/show title and normalized title;
- year;
- season and episode number;
- external content IDs when available;
- playback duration and viewed progress/intervals;
- watch timestamps;
- selected player/source identity for compatibility and diagnostics;
- content mappings confirmed by the user;
- synchronization status and retry state;
- settings;
- previous tracker state needed for undo.

Local retention should be limited to what is needed for history, reliability, correction, and synchronization.

## Playback URLs and source data

WatchRelay does not need to build a history of media source URLs.

If a bridge mode temporarily receives a media URI or source URL in order to forward playback to another player, that value must be treated as transient and must not be persisted in history, normal logs, analytics, diagnostic exports, or synchronization payloads unless a future supported protocol strictly requires a non-sensitive identifier.

Torrent URLs, magnet links, streaming URLs, authentication headers, and source credentials must not be persisted by WatchRelay.

## MyShows and other tracker services

When the user connects MyShows, WatchRelay sends the information required to authenticate and perform user-requested tracker operations directly to MyShows over HTTPS.

The intended product sends final watched-state operations after WatchRelay has made the watched decision locally. WatchRelay does not require the paid MyShows Pro scrobbling endpoints.

Data processed by MyShows is governed by MyShows' own terms and privacy practices. WatchRelay is an independent project and does not control MyShows' handling of data after it reaches that service.

Any future tracker integration must be documented here before release.

## Credentials

Long-lived tracker credentials must be protected with Android Keystore-backed encryption.

WatchRelay must not:

- store passwords in plaintext;
- log passwords or tokens;
- include credentials in diagnostic exports;
- include credentials in device backup;
- transmit credentials to a WatchRelay-operated server;
- commit credentials or secrets to the source repository.

If a password is temporarily required by a supported authentication flow to obtain a session/access token, it must be transmitted only to the intended service over HTTPS and discarded immediately after the authentication attempt.

## Permissions

WatchRelay must request only permissions that are necessary for its supported features.

The expected product may require access related to active media sessions/notifications or other documented Android media mechanisms. Permission screens must explain why a permission is needed and what stops working when it is not granted.

Accessibility Service is not a default product dependency. If it is ever considered necessary, this privacy policy and the public documentation must be updated before release, and the app must explain exactly what is observed and why.

## Diagnostics

Diagnostic reporting must be opt-in and user-initiated unless a future explicit privacy decision says otherwise.

Diagnostic output must redact:

- passwords;
- tokens/session credentials;
- private keys/signing material;
- streaming/torrent/source URLs;
- authorization headers;
- unrelated personal data.

Diagnostics should include only the minimum technical metadata needed to investigate compatibility or synchronization failures.

## Analytics and advertising

The default WatchRelay product has no advertising and no remote analytics/telemetry requirement.

An analytics, crash-reporting, advertising, attribution, or remote-observability SDK must not be introduced silently. Any such addition requires an explicit product decision, dependency/security review, user-facing disclosure where required, and an update to this document before release.

## Data deletion

Users must be able to clear WatchRelay's local history/mappings/settings through the app or by clearing/uninstalling the application data.

Deleting WatchRelay local data does not automatically delete data already synchronized to a third-party tracker. Where practical, WatchRelay should provide undo for recent mutations; broader tracker-data deletion remains governed by the tracker's own controls.

## Backups and device transfer

Reusable credentials and sensitive transient playback/source information must be excluded from Android backup. If non-sensitive settings/history are ever backed up, the behavior must be documented and tested before release.

## Children

WatchRelay is a utility for media tracking and does not intentionally collect data for a WatchRelay service. If distribution requirements impose child-directed-app obligations, those requirements must be reviewed before store publication.

## Changes

Privacy behavior and this document must change together. A release must not ship with a privacy statement that describes a more private implementation than the actual application.

The Git history provides the revision history for this policy.
