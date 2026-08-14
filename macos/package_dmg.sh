#!/bin/zsh
set -euo pipefail

script_dir=${0:A:h}
project_dir=${script_dir:h}
app_path="$project_dir/dist-macos/Tango pro.app"
dmg_path="$project_dir/dist-macos/Tango-pro-1.2.1-universal.dmg"
stage_dir=$(mktemp -d /private/tmp/tango-pro-dmg.XXXXXX)
trap 'rm -rf "$stage_dir"' EXIT

"$script_dir/build_macos.sh"
ditto "$app_path" "$stage_dir/Tango pro.app"
ln -s /Applications "$stage_dir/Applications"
hdiutil create -volname "Tango pro" -srcfolder "$stage_dir" -format UDZO \
  -imagekey zlib-level=9 -ov "$dmg_path"
hdiutil verify "$dmg_path"
shasum -a 256 "$dmg_path"
