# Contributing to Tango pro

Thank you for helping improve Tango pro. Android and macOS are native implementations connected by shared specifications and compatibility tests.

## Before editing

Read `AGENTS.md`, `DESIGN_DOC.md`, and the relevant contract in `docs/`. Shared product behavior must remain aligned on both platforms.

## Branches and commits

Use a short branch name such as `android/import-fix`, `macos/layout-fix`, `archive/compatibility`, `site/install-guide`, or `docs/adr-title`.

Use Conventional Commits:

- `feat(android): add ...`
- `fix(macos): prevent ...`
- `test(archive): cover ...`
- `docs(site): explain ...`
- `chore(repo): harden ...`

Keep generated apps and signing material out of commits.

## Pull requests

1. Describe the behavior and affected platforms.
2. Update `DESIGN_DOC.md` for shared behavior changes.
3. Update the CSV or study-archive specification when its contract changes.
4. Add tests and compatibility fixtures where relevant.
5. Run the checks in `AGENTS.md` and complete the pull-request template.

Review checks correctness, Android/macOS parity, backward compatibility, accessibility, responsive behavior, secret hygiene, and version stability.

## Releases

Follow `docs/RELEASE_CHECKLIST.md`. Versions, tags, pushes, and releases are changed only for an explicitly approved release task.
