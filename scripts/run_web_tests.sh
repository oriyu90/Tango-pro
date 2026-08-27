#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"

node scripts/test_browser_bridge.mjs

./gradlew --no-configuration-cache --no-build-cache clean \
  :shared:allTests \
  :webApp:wasmJsBrowserTest \
  :webApp:wasmJsBrowserDistribution

echo 'Web tests: PASS'
