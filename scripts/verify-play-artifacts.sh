#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
    echo "Usage: $0 <bundletool.jar> <app.aab> [app.apk]" >&2
    exit 2
fi

bundletool="$1"
aab="$2"
apk="${3:-}"

test -s "$bundletool"
test -s "$aab"

java -jar "$bundletool" validate --bundle="$aab"
config="$(java -jar "$bundletool" dump config --bundle="$aab")"
printf '%s\n' "$config"
grep -q 'PAGE_ALIGNMENT_16K' <<< "$config"

native_entries="$(zipinfo -1 "$aab" | grep -E '(^|/)lib/[^/]+/[^/]+\.so$' || true)"
if [[ -n "$native_entries" ]]; then
    echo "Native/JNI libraries were found in the release bundle:" >&2
    printf '%s\n' "$native_entries" >&2
    echo "WatchRelay currently has no approved native dependency set. Review every ABI and 16 KB ELF alignment before allowing native libraries into release artifacts." >&2
    exit 1
fi

echo "No native/JNI libraries are packaged. The current release is ABI-neutral, including arm64-v8a, and has no native ELF page-size compatibility risk."

if [[ -n "$apk" ]]; then
    test -s "$apk"
    native_apk_entries="$(zipinfo -1 "$apk" | grep -E '^lib/[^/]+/[^/]+\.so$' || true)"
    if [[ -n "$native_apk_entries" ]]; then
        echo "Native/JNI libraries were found in the APK:" >&2
        printf '%s\n' "$native_apk_entries" >&2
        exit 1
    fi

    zipalign_bin="$(find "${ANDROID_HOME:?ANDROID_HOME is required}/build-tools" -type f -name zipalign -print | sort -V | tail -1)"
    test -n "$zipalign_bin"
    "$zipalign_bin" -c -P 16 -v 4 "$apk"
fi
