#!/bin/zsh
set -euo pipefail

script_dir=${0:A:h}
project_dir=${script_dir:h}
app_path="$project_dir/dist-macos/Tango pro.app"
dmg_path="$project_dir/dist-macos/Tango-pro-2.1.0-universal.dmg"
stage_dir=$(mktemp -d /private/tmp/tango-pro-dmg.XXXXXX)
trap 'rm -rf "$stage_dir"' EXIT

"$script_dir/build_macos.sh"
ditto "$app_path" "$stage_dir/Tango pro.app"
ln -s /Applications "$stage_dir/Applications"
hdiutil create -volname "Tango pro" -srcfolder "$stage_dir" -format UDZO \
  -imagekey zlib-level=9 -ov "$dmg_path"

# DiskImages2 can briefly retain a newly created image as an unmounted device.
# Detach only the device whose image-path exactly matches this release artifact.
attached_device=$(hdiutil info | awk -v target="$dmg_path" '
  /^image-path/ { active = index($0, target) > 0 }
  active && /^\/dev\/disk/ { print $1; exit }
')
if [[ -n "$attached_device" ]]; then
  hdiutil detach "$attached_device" >/dev/null || true
fi

verified=false
for attempt in 1 2 3; do
  if hdiutil verify "$dmg_path"; then
    verified=true
    break
  fi
  sleep "$attempt"
done
if [[ "$verified" != true ]]; then
  echo "DMG verification failed after 3 attempts: $dmg_path" >&2
  exit 1
fi
shasum -a 256 "$dmg_path"
