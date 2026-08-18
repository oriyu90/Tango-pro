# Android app

The Android implementation uses Kotlin, Jetpack Compose, Room, and Gradle.

- UI and application flow: `src/main/java/com/example/`
- Data and archive formats: `src/main/java/com/example/data/`
- Shared product rules: `../DESIGN_DOC.md`
- CSV contract: `../docs/CSV_FORMAT.md`
- Study-archive contract: `../docs/STUDY_ARCHIVE_FORMAT.md`
- Tests: `src/test/` and `src/androidTest/`

Run `./gradlew test lint assembleDebug assembleRelease` from the repository root. Do not put signing values in Gradle files; use the documented environment variables only for an approved release build.
