#!/bin/zsh
set -euo pipefail

script_dir=${0:A:h}
project_dir=${script_dir:h}
source_dir="$script_dir/TangoProMac"
output_dir="$project_dir/dist-macos"
sdk_path="/Library/Developer/CommandLineTools/SDKs/MacOSX15.4.sdk"
if [[ ! -d "$sdk_path" ]]; then
  sdk_path=$(xcrun --sdk macosx --show-sdk-path)
fi
stage_dir=$(mktemp -d "$script_dir/.stage.XXXXXX")
module_cache="/private/tmp/TangoProSwiftModuleCache"
mkdir -p "$module_cache"
trap 'rm -rf "$stage_dir"' EXIT

app_path="$stage_dir/Tango pro.app"
contents="$app_path/Contents"
mkdir -p "$contents/MacOS" "$contents/Resources/CSV"

common_args=(
  -swift-version 5
  -parse-as-library
  -O
  -sdk "$sdk_path"
  -module-cache-path "$module_cache"
  -framework SwiftUI
  -framework AppKit
  -framework Combine
  -framework CryptoKit
)

swiftc "${common_args[@]}" -target arm64-apple-macos13.0 \
  "$source_dir/TangoCore.swift" "$source_dir/StudyArchive.swift" "$source_dir/TangoProApp.swift" \
  -o "$stage_dir/TangoPro-arm64"

if swiftc "${common_args[@]}" -target x86_64-apple-macos13.0 \
  "$source_dir/TangoCore.swift" "$source_dir/StudyArchive.swift" "$source_dir/TangoProApp.swift" \
  -o "$stage_dir/TangoPro-x86_64"; then
  lipo -create "$stage_dir/TangoPro-arm64" "$stage_dir/TangoPro-x86_64" \
    -output "$contents/MacOS/Tango pro"
else
  cp "$stage_dir/TangoPro-arm64" "$contents/MacOS/Tango pro"
fi
chmod +x "$contents/MacOS/Tango pro"
cp "$source_dir/Info.plist" "$contents/Info.plist"
cp "$project_dir/app/src/main/assets/"*.csv "$contents/Resources/CSV/"

icon_source="$project_dir/app/src/main/res/drawable-nodpi/gold_book_icon.png"
image_python="/Users/yuki/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3"
if [[ ! -x "$image_python" ]]; then
  image_python=$(command -v python3)
fi
"$image_python" -c 'from PIL import Image; import sys; image=Image.open(sys.argv[1]).convert("RGBA"); image.save(sys.argv[2], sizes=[(16,16),(32,32),(64,64),(128,128),(256,256),(512,512),(1024,1024)])' \
  "$icon_source" "$contents/Resources/AppIcon.icns"

codesign --force --deep --sign - "$app_path"
mkdir -p "$output_dir"
output_app="$output_dir/Tango pro.app"
if [[ "$output_app" != "$project_dir/dist-macos/Tango pro.app" ]]; then
  echo "Unexpected output path: $output_app" >&2
  exit 1
fi
rm -rf "$output_app"
ditto "$app_path" "$output_app"

echo "$output_app"
