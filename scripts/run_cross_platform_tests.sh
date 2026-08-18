#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"

work_dir=$(mktemp -d "${TMPDIR:-/tmp}/tango-cross-platform.XXXXXX")
trap 'rm -rf "$work_dir"' EXIT

version=$(sed -nE 's/^val appVersionName = "([^"]+)"/\1/p' app/build.gradle.kts)
fixture_output_dir=${TANGO_FIXTURE_OUTPUT_DIR:-$work_dir}
mkdir -p "$fixture_output_dir"
android_fixture="$fixture_output_dir/v${version}-android.zip"
macos_fixture="$fixture_output_dir/v${version}-macos.zip"
core_binary="$work_dir/tango-core-self-test"

bash scripts/check_version_lockstep.sh
./gradlew --no-daemon test lint assembleDebug assembleRelease

sdk_path=$(xcrun --sdk macosx --show-sdk-path)
swiftc -swift-version 5 -sdk "$sdk_path" \
  -framework AppKit -framework Combine -framework CryptoKit \
  macos/TangoProMac/TangoCore.swift \
  macos/TangoProMac/StudyArchive.swift \
  macos/TangoProMac/CoreSelfTest.swift \
  -o "$core_binary"

TANGO_ANDROID_FIXTURE_OUTPUT="$android_fixture" \
  ./gradlew --no-daemon --rerun-tasks testDebugUnitTest \
  --tests 'com.example.StudyArchiveCodecTest.producer output can be parsed again'

core_arguments=(
  --android-fixture "$android_fixture"
  --write-fixture "$macos_fixture"
)
if compgen -G 'testdata/fixtures/*.zip' > /dev/null; then
  core_arguments+=(--fixture-directory testdata/fixtures)
fi
"$core_binary" "${core_arguments[@]}"

TANGO_ARCHIVE_FIXTURE="$macos_fixture" \
  ./gradlew --no-daemon --rerun-tasks testDebugUnitTest \
  --tests 'com.example.StudyArchiveCodecTest.macOS archive fixture is Android compatible when supplied'

echo "Cross-platform archive verification: PASS ($version)"
