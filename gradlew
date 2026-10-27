#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BASE="$ROOT/.gradle-dist"
GRADLE="$BASE/gradle-8.9/bin/gradle"
if [ ! -x "$GRADLE" ]; then
    mkdir -p "$BASE"
    if [ ! -f "$BASE/gradle-8.9-bin.zip" ]; then
        if command -v curl >/dev/null 2>&1; then
            curl -fL "https://services.gradle.org/distributions/gradle-8.9-bin.zip" -o "$BASE/gradle-8.9-bin.zip"
        else
            wget "https://services.gradle.org/distributions/gradle-8.9-bin.zip" -O "$BASE/gradle-8.9-bin.zip"
        fi
    fi
    unzip -oq "$BASE/gradle-8.9-bin.zip" -d "$BASE"
fi
cd "$ROOT"
exec "$GRADLE" "$@"
