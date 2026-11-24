#!/usr/bin/env bash
# Dependency-free test runner (macOS / Linux / Git Bash).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME is not set. Example: export JAVA_HOME=/path/to/jdk" >&2
  exit 1
fi

MVN="${MVN:-mvn}"
"$MVN" -q clean test-compile

case "$(uname -s 2>/dev/null || true)" in
  MINGW*|MSYS*|CYGWIN*) SEP=';' ;;
  *) SEP=':' ;;
esac
CP="target/classes${SEP}target/test-classes"
"$JAVA_HOME/bin/java" -cp "$CP" cn.msscn.progress.ProgressSelfTest
