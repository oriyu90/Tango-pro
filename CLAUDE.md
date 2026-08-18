# Tango pro AI context

The canonical repository instructions are in `AGENTS.md`. Read that file before making changes.

Key constraints:

- Android lives in `app/`; macOS lives in `macos/TangoProMac/`; shared behavior is specified in `DESIGN_DOC.md`.
- Read `docs/CSV_FORMAT.md` and `docs/STUDY_ARCHIVE_FORMAT.md` before changing either data contract.
- Changes to shared behavior must be applied to both native apps and documented in `DESIGN_DOC.md`.
- Run the repository, version, site, and cross-platform checks listed in `AGENTS.md`.
- Do not change any app or bundle version without an explicit user request.
- Use Conventional Commits with an Android, macOS, archive, site, docs, or repo scope.
- Do not commit, tag, push, or publish a release until the user has reviewed the diff and verification results and explicitly approved it.
- Never commit credentials, signing material, local configuration, APKs, DMGs, or build output.
