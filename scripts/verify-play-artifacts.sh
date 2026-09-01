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
command -v readelf >/dev/null
command -v unzip >/dev/null
command -v zipinfo >/dev/null

verify_64_bit_elf_alignment() {
    local archive="$1"
    local entry="$2"
    local temp_file
    temp_file="$(mktemp)"
    unzip -p "$archive" "$entry" > "$temp_file"

    local elf_class
    elf_class="$(readelf -h "$temp_file" | awk -F: '/Class:/ {gsub(/[[:space:]]/, "", $2); print $2; exit}')"
    if [[ "$elf_class" != "ELF64" ]]; then
        rm -f "$temp_file"
        echo "Expected a 64-bit ELF for $entry, found ${elf_class:-unknown}." >&2
        exit 1
    fi

    local load_alignments
    load_alignments="$(readelf -lW "$temp_file" | awk '$1 == "LOAD" {print $NF}')"
    rm -f "$temp_file"
    if [[ -z "$load_alignments" ]]; then
        echo "No ELF LOAD segments found in $entry." >&2
        exit 1
    fi

    while IFS= read -r alignment; do
        if (( alignment < 0x4000 )); then
            echo "$entry has LOAD alignment $alignment; 16 KB compatibility requires at least 0x4000." >&2
            exit 1
        fi
    done <<< "$load_alignments"

    echo "16 KB ELF alignment verified: $entry"
}

verify_native_archive() {
    local archive="$1"
    local pattern="$2"
    local entries
    entries="$(zipinfo -1 "$archive" | grep -E "$pattern" || true)"

    if [[ -z "$entries" ]]; then
        echo "No native/JNI libraries packaged in $archive."
        return
    fi

    echo "Native/JNI libraries packaged in $archive:"
    printf '%s\n' "$entries"

    local has_arm64=false
    while IFS= read -r entry; do
        case "$entry" in
            */lib/arm64-v8a/*.so|lib/arm64-v8a/*.so)
                has_arm64=true
                verify_64_bit_elf_alignment "$archive" "$entry"
                ;;
            */lib/x86_64/*.so|lib/x86_64/*.so)
                verify_64_bit_elf_alignment "$archive" "$entry"
                ;;
            */lib/armeabi-v7a/*.so|lib/armeabi-v7a/*.so|*/lib/x86/*.so|lib/x86/*.so)
                ;;
            *)
                echo "Unexpected native ABI in $entry." >&2
                exit 1
                ;;
        esac
    done <<< "$entries"

    if [[ "$has_arm64" != true ]]; then
        echo "Native code is packaged, but arm64-v8a support is missing." >&2
        exit 1
    fi
}

java -jar "$bundletool" validate --bundle="$aab"
config="$(java -jar "$bundletool" dump config --bundle="$aab")"
printf '%s\n' "$config"
grep -q 'PAGE_ALIGNMENT_16K' <<< "$config"
verify_native_archive "$aab" '(^|/)lib/[^/]+/[^/]+\.so$'

if [[ -n "$apk" ]]; then
    test -s "$apk"
    verify_native_archive "$apk" '^lib/[^/]+/[^/]+\.so$'

    zipalign_bin="$(find "${ANDROID_HOME:?ANDROID_HOME is required}/build-tools" -type f -name zipalign -print | sort -V | tail -1)"
    test -n "$zipalign_bin"
    "$zipalign_bin" -c -P 16 -v 4 "$apk"
fi
