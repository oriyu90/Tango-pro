#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
distribution=${1:-}
if [[ -z "$distribution" ]]; then
  build_dir=$(cd "$repo_root" && ./gradlew -q :webApp:properties | sed -n 's/^buildDir: //p' | head -n 1)
  distribution="$build_dir/dist/wasmJs/productionExecutable"
fi

python3 - "$distribution" <<'PY'
import hashlib
import json
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1]).resolve()
required = ["index.html", "tango-pro-web.js", "browser-bridge.js", "sw.js", "manifest.webmanifest", "build-info.json"]
missing = [name for name in required if not (root / name).is_file()]
if missing:
    raise SystemExit(f"Missing Web distribution files: {', '.join(missing)}")
if list(root.rglob("*.map")):
    raise SystemExit("Source maps must not be included in the public bundle")

manifest = json.loads((root / "manifest.webmanifest").read_text(encoding="utf-8"))
expected_base = "/projects/tango-pro/web/"
for key in ("id", "start_url", "scope"):
    if manifest.get(key) != expected_base:
        raise SystemExit(f"manifest {key} must be {expected_base}")

build = json.loads((root / "build-info.json").read_text(encoding="utf-8"))
if build.get("format") != "tango-pro-web-build" or build.get("version") != 1:
    raise SystemExit("Invalid build-info format")
if not re.fullmatch(r"[0-9a-f]{16}", build.get("buildId", "")):
    raise SystemExit("Invalid build ID")
assets = build.get("assets")
if not isinstance(assets, list) or not assets:
    raise SystemExit("build-info assets are missing")
for relative in assets:
    path = pathlib.PurePosixPath(relative)
    if path.is_absolute() or ".." in path.parts or not (root / relative).is_file():
        raise SystemExit(f"Unsafe or missing build asset: {relative}")
query_assets = build.get("queryAssets")
if not isinstance(query_assets, list) or len(query_assets) != 1 or query_assets[0] not in assets:
    raise SystemExit("build-info must identify exactly one SQLite OPFS worker asset")

digest = hashlib.sha256()
for relative in sorted(assets):
    path = root / relative
    digest.update(relative.encode("utf-8"))
    digest.update(b"\0")
    digest.update(hashlib.sha256(path.read_bytes()).digest())
worker = (root / "sw.js").read_text(encoding="utf-8")
worker_template = worker.replace(build["buildId"], "__BUILD_ID__")
digest.update(b"sw.js\0")
digest.update(hashlib.sha256(worker_template.encode("utf-8")).digest())
if digest.hexdigest()[:16] != build["buildId"]:
    raise SystemExit("build-info content hash does not match the staged files")

if "__BUILD_ID__" in worker or build["buildId"] not in worker:
    raise SystemExit("Service Worker build ID was not staged")
if "?vfs=opfs" not in worker or "?vfs=opfs-wl" not in worker or "ignoreSearch: true" not in worker:
    raise SystemExit("Service Worker must precache and resolve both SQLite OPFS worker URL variants")
if not list(root.glob("*.wasm")):
    raise SystemExit("No Wasm binaries were produced")
if not (root / "composeResources/com.example.tangopro.web.generated.resources/font/tango_pro_unicode.ttf").is_file():
    raise SystemExit("The multilingual Unicode font resource is missing")
if not (root / "THIRD_PARTY_LICENSES.txt").is_file():
    raise SystemExit("The third-party license inventory is missing")
print(f"Web distribution: PASS ({len(assets)} assets, build {build['buildId']})")
PY
