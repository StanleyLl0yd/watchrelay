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

A signed universal APK may also be produced as a secondary artifact for direct installation, testing, and GitHub distribution. The APK is not the primary Google Play deliverable.

Release signing is configured only through environment variables and CI secrets. Signing material must never be committed to the repository.

Required release secrets:

- `ANDROID_KEYSTORE_BASE64`;
- `ANDROID_KEYSTORE_PASSWORD`;
- `ANDROID_KEY_ALIAS`;
- `ANDROID_KEY_PASSWORD`;
- `ANDROID_CERT_SHA256`.

The release workflow restores the keystore only in the runner's temporary directory, builds the release with signing required, verifies the AAB and APK signatures against the expected certificate fingerprint, and removes the temporary keystore afterward.

## ABI policy

The current WatchRelay dependency graph contains no native/JNI/NDK libraries. Therefore release artifacts contain no architecture-specific `.so` files and are ABI-neutral: they run on supported Android ABIs, including `arm64-v8a`, without shipping redundant native binaries.

Do not add `abiFilters` while the app remains native-code-free; doing so would add an unnecessary device restriction without reducing native payload.

If any direct or transitive dependency starts packaging native code, release CI must fail until the native dependency set is explicitly reviewed. Before allowing such a dependency, verify at minimum:

1. `arm64-v8a` is present and fully supported;
2. every additional packaged ABI is required by an actual supported device class;
3. obsolete or unnecessary ABIs are removed;
4. every 64-bit `.so` is compatible with 16 KB memory page sizes;
5. the final AAB/APK packaging remains 16 KB aligned;
6. the native SDK/library vendor documents 16 KB compatibility or the binaries are independently verified.

## 16 KB page-size compatibility

Google Play requires apps targeting Android 15/API 35 or newer to support 16 KB memory page sizes on 64-bit devices. WatchRelay enforces this in CI.

For the current native-code-free application:

- there are no ELF shared libraries whose segment alignment can be incompatible;
- the generated AAB must report `PAGE_ALIGNMENT_16K` through bundletool;
- unexpected `.so` files in either the AAB or release APK are treated as a release-blocking change;
- the APK is checked with `zipalign -P 16` when a signed release APK is built.

The verification is implemented by `scripts/verify-play-artifacts.sh` and is run for the release bundle in normal CI and for both signed release artifacts in the release workflow.

## Release workflow

`.github/workflows/release.yml` runs only for an existing semantic-version tag (`vX.Y.Z`) or a manual request referencing such a tag.

A release build must:

1. match the tag to `versionName`;
2. pass unit tests and Android lint;
3. build a signed AAB and signed APK;
4. validate the AAB with pinned bundletool;
5. verify 16 KB packaging and the native-library policy;
6. verify both artifact signatures against `ANDROID_CERT_SHA256`;
7. create SHA-256 checksums;
8. upload the AAB as the Google Play artifact and the APK as the secondary direct-install artifact.

Do not publish a release when any of these checks fails.
