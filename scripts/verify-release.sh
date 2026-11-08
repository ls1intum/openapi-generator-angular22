#!/usr/bin/env bash
set -euo pipefail

tag="${1:?Pass the release tag, for example v4.0.0}"
notes="release-notes/${tag}.md"

if [[ ! -s "$notes" ]]; then
    printf 'Release notes are missing or empty: %s\n' "$notes" >&2
    exit 1
fi

version="$(./gradlew -q properties | awk -F ': ' '$1 == "version" { print $2 }')"
if [[ -z "$version" || "$tag" != "v$version" ]]; then
    printf 'Release tag %s does not match project version %s\n' "$tag" "$version" >&2
    exit 1
fi
if [[ "$version" == *-SNAPSHOT ]]; then
    printf 'Cannot release a snapshot version: %s\n' "$version" >&2
    exit 1
fi
