#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"

forbidden_pattern='(^|/)(local\.properties|\.env|\.local-archive/|dist-android/|dist-macos/)|\.(apk|aab|dmg|jks|keystore|idsig)$'
tracked_forbidden=$(git ls-files | grep -E "$forbidden_pattern" || true)
if [[ -n "$tracked_forbidden" ]]; then
  echo 'Forbidden generated or local files are tracked:' >&2
  echo "$tracked_forbidden" >&2
  exit 1
fi

if git grep -nE -e '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----' -- ':!scripts/check_repository_hygiene.sh'; then
  echo 'Private key material marker found in tracked files.' >&2
  exit 1
fi

git diff --check
echo 'Repository hygiene: PASS'
