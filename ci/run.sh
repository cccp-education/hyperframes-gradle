#!/usr/bin/env bash
# GBL-013 US-5 (wave 2) — CI contract for hyperframes-gradle.
# The real-render workflow (Node/Chromium/ffmpeg) stays on the host adapter;
# this contract covers the hermetic build gate.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/hyperframes-plugin"

job="${1:-}"
case "$job" in
  fast)
    ./gradlew compileKotlin --no-daemon --console=plain
    ;;
  test)
    ./gradlew check --no-daemon --console=plain
    ;;
  *)
    printf 'unsupported job for hyperframes-gradle: %s\n' "${job:-<none>}" >&2
    printf 'supported: fast | test\n' >&2
    exit 2
    ;;
esac
