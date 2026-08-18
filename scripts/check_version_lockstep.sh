#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
android_file=${TANGO_ANDROID_VERSION_FILE:-"$repo_root/app/build.gradle.kts"}
macos_file=${TANGO_MACOS_VERSION_FILE:-"$repo_root/macos/TangoProMac/Info.plist"}

android_version=$(sed -nE 's/^val appVersionName = "([^"]+)"/\1/p' "$android_file")
android_build=$(sed -nE 's/^[[:space:]]*versionCode = ([0-9]+)/\1/p' "$android_file")

if [[ -x /usr/libexec/PlistBuddy ]]; then
  macos_version=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$macos_file")
  macos_build=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$macos_file")
else
  macos_version=$(python3 -c 'import plistlib,sys; print(plistlib.load(open(sys.argv[1], "rb"))["CFBundleShortVersionString"])' "$macos_file")
  macos_build=$(python3 -c 'import plistlib,sys; print(plistlib.load(open(sys.argv[1], "rb"))["CFBundleVersion"])' "$macos_file")
fi

if [[ -z "$android_version" || -z "$android_build" ]]; then
  echo 'Android version values could not be read.' >&2
  exit 1
fi

if [[ "$android_version" != "$macos_version" || "$android_build" != "$macos_build" ]]; then
  echo "Version mismatch: Android=$android_version ($android_build), macOS=$macos_version ($macos_build)" >&2
  exit 1
fi

echo "Version lockstep: $android_version ($android_build)"
