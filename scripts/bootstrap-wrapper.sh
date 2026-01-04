#!/usr/bin/env bash
set -euo pipefail

if [[ -f "gradle/wrapper/gradle-wrapper.jar" ]]; then
  echo "Gradle wrapper jar already exists."
  exit 0
fi

if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle is not installed. Install Gradle or use your IDE's Gradle tooling to generate the wrapper." >&2
  exit 1
fi

gradle wrapper

echo "Gradle wrapper generated."
