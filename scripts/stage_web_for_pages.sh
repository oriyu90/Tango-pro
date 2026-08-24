#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
destination=${1:-}
if [[ -z "$destination" ]]; then
  echo "Usage: $0 /absolute/path/to/studio-rizi/website/projects/tango-pro/web" >&2
  exit 2
fi
if [[ "$destination" != /* || "$destination" != */website/projects/tango-pro/web ]]; then
  echo "Refusing an unexpected destination: $destination" >&2
  exit 2
fi

cd "$repo_root"
./gradlew --no-configuration-cache --no-build-cache clean :webApp:wasmJsBrowserDistribution

build_dir=$(./gradlew -q :webApp:properties | sed -n 's/^buildDir: //p' | head -n 1)
distribution="$build_dir/dist/wasmJs/productionExecutable"
if [[ ! -f "$distribution/index.html" || ! -f "$distribution/tango-pro-web.js" ]]; then
  echo "Web distribution was not produced at $distribution" >&2
  exit 1
fi

mkdir -p "$destination"
rsync -a --delete --exclude '*.map' "$distribution/" "$destination/"

python3 - "$destination" "$(git rev-parse HEAD)" "$(git status --porcelain | wc -l | tr -d ' ')" <<'PY'
import datetime
import hashlib
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1]).resolve()
commit = sys.argv[2]
dirty = int(sys.argv[3]) > 0

# Webpack emits one license sidecar per minified chunk, even when their
# contents are identical. Publish one auditable license inventory and point
# each generated chunk header at it so the static-site duplicate-file guard
# remains meaningful.
license_paths = sorted(root.glob("*.LICENSE.txt"))
if license_paths:
    unique_licenses = {}
    for license_path in license_paths:
        raw_license = license_path.read_text(encoding="utf-8")
        normalized_license = "\n".join(line.rstrip(" \t") for line in raw_license.splitlines()) + "\n"
        content = normalized_license.encode("utf-8")
        unique_licenses.setdefault(hashlib.sha256(content).hexdigest(), (license_path.name, content))
        script = root / license_path.name.removesuffix(".LICENSE.txt")
        if script.is_file():
            text = script.read_text(encoding="utf-8")
            script.write_text(text.replace(license_path.name, "THIRD_PARTY_LICENSES.txt"), encoding="utf-8")
    inventory = bytearray(b"Tango pro Web third-party license notices\n")
    for source_name, content in unique_licenses.values():
        inventory.extend(f"\n===== {source_name} =====\n".encode("utf-8"))
        inventory.extend(content)
        if not content.endswith(b"\n"):
            inventory.extend(b"\n")
    (root / "THIRD_PARTY_LICENSES.txt").write_bytes(inventory)
    for license_path in license_paths:
        license_path.unlink()

excluded = {"build-info.json", "sw.js"}
files = sorted(
    path.relative_to(root).as_posix()
    for path in root.rglob("*")
    if path.is_file() and path.name not in excluded and not path.name.endswith(".map")
)
worker = root / "sw.js"
worker_text = worker.read_text(encoding="utf-8")
if "__BUILD_ID__" not in worker_text:
    raise SystemExit("sw.js does not contain the build ID placeholder")
digest = hashlib.sha256()
for relative in files:
    path = root / relative
    digest.update(relative.encode("utf-8"))
    digest.update(b"\0")
    digest.update(hashlib.sha256(path.read_bytes()).digest())
# Hash the Service Worker template before token replacement. This avoids a
# circular hash while ensuring a worker-only change installs a fresh cache.
digest.update(b"sw.js\0")
digest.update(hashlib.sha256(worker_text.encode("utf-8")).digest())
build_id = digest.hexdigest()[:16]
source = f"{commit}-dirty" if dirty else commit
query_assets = [
    relative for relative in files
    if relative.endswith(".js") and b"Expecting vfs=opfs" in (root / relative).read_bytes()
]
if len(query_assets) != 1:
    raise SystemExit(f"Expected one SQLite OPFS worker asset, found {len(query_assets)}")
metadata = {
    "format": "tango-pro-web-build",
    "version": 1,
    "appVersion": "2.1.0-web",
    "sourceCommit": source,
    "buildId": build_id,
    "builtAt": datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
    "basePath": "/projects/tango-pro/web/",
    "assets": files,
    "queryAssets": query_assets,
}
(root / "build-info.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
worker.write_text(worker_text.replace("__BUILD_ID__", build_id), encoding="utf-8")
print(f"Staged Tango pro Web build {build_id} from {source}")
PY

bash scripts/verify_web_distribution.sh "$destination"
