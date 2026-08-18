# macOS app

The macOS implementation uses Swift, SwiftUI, AppKit, and JSON persistence.

- App and UI: `TangoProMac/TangoProApp.swift`
- Core models and behavior: `TangoProMac/TangoCore.swift`
- Study archives: `TangoProMac/StudyArchive.swift`
- Core verification: `TangoProMac/CoreSelfTest.swift`
- Shared product rules: `../DESIGN_DOC.md`
- Development procedure: `../docs/DEVELOPMENT.md`

Use `build_macos.sh` and `package_dmg.sh` only when a macOS artifact is required. Local packages are ad-hoc signed unless an approved release process supplies Developer ID signing and notarization.
