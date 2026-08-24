# TangoView

The production viewer is an audited, minimally branded build of upstream scrcpy 4.1. It must preserve upstream video, input, and protocol behavior.

The local PoC uses upstream scrcpy 4.1 directly. Do not copy its SDK-server or host binaries into a release package. A production package is permitted only after the source revision, patch set, output hashes, and licenses are pinned in `windows/runtime/components.lock.json` and `windows/runtime/licenses/`.
