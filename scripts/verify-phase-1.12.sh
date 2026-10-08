#!/usr/bin/env bash
# Phase 1.12 verification entry point; no GitHub Actions and no version auto-provisioning.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

JAVA_BIN="java"
JAVAC_BIN="javac"
if [[ -n "${JAVA_HOME:-}" ]]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
  JAVAC_BIN="$JAVA_HOME/bin/javac"
fi
if [[ ! -x "$JAVA_BIN" ]] && ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
  echo "ERROR: Java not found. Set JAVA_HOME to a Java 17 JDK." >&2
  exit 2
fi
if ! "$JAVA_BIN" -version 2>&1 | grep -Eq 'version "17(\.|")'; then
  "$JAVA_BIN" -version >&2 || true
  echo "ERROR: Minecraft 1.20.1 Forge requires Java 17 for Phase 1.12 verification." >&2
  exit 2
fi
if ! "$JAVAC_BIN" -version 2>&1 | grep -Eq '^javac 17(\.|$)'; then
  echo "ERROR: Java 17 JDK (javac), not only a JRE, is required." >&2
  exit 2
fi
if [[ ! -f "./gradlew" ]]; then
  echo "ERROR: Gradle wrapper is missing." >&2
  exit 2
fi

echo "Phase 1.12: Java 17 preflight passed; running Gradle unit, build and server GameTests."
bash ./gradlew --no-daemon --stacktrace clean test build verifyGameTests
echo "Automated checks completed. Client, GUI-scale and dedicated-server smoke tests remain MANUAL."
