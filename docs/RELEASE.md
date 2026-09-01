# Android Release Requirements

WatchRelay targets Android phones/tablets and Android TV/Google TV from one application package.

## SDK baseline

The current build contract is:

- `minSdk = 26` — Android 8.0 and newer;
- `targetSdk = 36` — Android 16 target behavior;
- `compileSdk = 37` — compile against the newest API supported by the current stable AGP toolchain;
- JDK 17;
- Android Gradle Plugin 9.3.1;
- Gradle 9.5.0.

`targetSdk` must not be increased solely because a newer SDK can be compiled. A target-level increase opts the application into platform behavior changes and therefore requires explicit compatibility testing on phone/tablet and Android TV/Google TV before release.

## Release formats

The primary Google Play artifact is a **signed Android App Bundle (`.aab`)**.

A signed APK may also be produced as a secondary artifact for direct installation, testing, and GitHub distribution. The APK is not the primary Google Play deliverable.

Release signing is configured only through environment variables and CI secrets. Signing material must never be committed to the repository.

Required release secrets:

- `ANDROID_KEYSTORE_BASE64`;
- `ANDROID_KEYSTORE_PASSWORD`;
- `ANDROID_KEY_ALIAS`;
- `ANDROID_KEY_PASSWORD`;
- `ANDROID_CERT_SHA256`.

The release workflow restores the keystore only in the runner's temporary directory, builds the release with signing required, verifies the AAB and APK signatures against the expected certificate fingerprint, and removes the temporary keystore afterward.

## ABI policy

WatchRelay itself does not contain C/C++ or NDK source, but the current AndroidX/Compose dependency graph transitively packages `libandroidx.graphics.path.so`. The release bundle currently contains this library for:

- `arm64-v8a`;
- `armeabi-v7a`;
- `x86_64`;
- `x86`.

`arm64-v8a` is mandatory and is verified by CI whenever native code is present.

WatchRelay keeps Android App Bundle ABI splitting explicitly enabled. Google Play therefore delivers only the native ABI required by the target device rather than installing all packaged architectures on every device. No manual `abiFilters` are applied while the dependency itself supports these Android ABIs: filtering them would reduce device/emulator compatibility without reducing the native payload delivered to an individual Play installation.

If the native dependency set changes, CI enumerates the packaged `.so` files and rejects unknown ABI directories. A new native dependency must be reviewed before release for provenance, supported ABIs, and 16 KB compatibility.

## 16 KB page-size compatibility

Google Play requires apps targeting Android 15/API 35 or newer to support 16 KB memory page sizes on 64-bit devices. WatchRelay enforces this in CI at the final-artifact level rather than assuming dependency compatibility from version numbers alone.

The checks are:

1. validate the AAB with pinned bundletool;
2. require bundletool configuration to report `PAGE_ALIGNMENT_16K`;
3. enumerate every packaged native `.so`;
4. require `arm64-v8a` whenever native code is present;
5. inspect every packaged `arm64-v8a` and `x86_64` ELF with `readelf` and require every `LOAD` segment to have alignment of at least `0x4000` (16 KB);
6. for a release APK, repeat the native ELF checks and run `zipalign -c -P 16 -v 4`.

The verification is implemented by `scripts/verify-play-artifacts.sh` and is run for the release bundle in normal CI and for both signed release artifacts in the release workflow.

This policy means a future transitive dependency update cannot silently introduce a 64-bit native binary that is incompatible with 16 KB page-size devices.

## Release workflow

`.github/workflows/release.yml` runs only for an existing semantic-version tag (`vX.Y.Z`) or a manual request referencing such a tag.

A release build must:

1. match the tag to `versionName`;
2. pass unit tests and Android lint;
3. build a signed AAB and signed APK;
4. validate the AAB with pinned bundletool;
5. verify native ABIs, 16 KB ELF alignment, and package alignment;
6. verify both artifact signatures against `ANDROID_CERT_SHA256`;
7. create SHA-256 checksums;
8. upload the AAB as the Google Play artifact and the APK as the secondary direct-install artifact.

Do not publish a release when any of these checks fails.
