#!/usr/bin/env bash
set -euo pipefail

verify="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/verify-release.sh"
temp_dir="$(mktemp -d)"
trap 'rm -rf "$temp_dir"' EXIT
cd "$temp_dir"
mkdir release-notes
printf '#!/usr/bin/env bash\nprintf "version: %%s\\n" "${RELEASE_TEST_VERSION:-4.0.0}"\n' > gradlew
chmod +x gradlew

printf 'Release notes\n' > release-notes/v4.0.0.md
bash "$verify" v4.0.0

expect_failure() {
    if bash "$verify" "$1" >/dev/null 2>&1; then
        printf 'Expected release check to reject %s\n' "$1" >&2
        exit 1
    fi
}

expect_failure v9.0.0
printf 'Release notes\n' > release-notes/v9.0.0.md
expect_failure v9.0.0
printf 'Release notes\n' > release-notes/v4.0.0-SNAPSHOT.md
export RELEASE_TEST_VERSION=4.0.0-SNAPSHOT
expect_failure v4.0.0-SNAPSHOT
printf 'Release metadata checks passed\n'
