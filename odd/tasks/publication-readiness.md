# Publication readiness

## Objective
Prepare this adaptation for public sharing with honest upstream attribution and clear PlatformIO setup.

## Problem and scope
The Android/UDP bridge derives from ghostside-net's ESP32-Switch-Controller-Joycon, but the README does not credit that origin. Root ignore rules also hide existing Android tests.

Authorized: documentation and publication hygiene only. Preserve firmware behavior, LICENSE, history, remotes and pre-existing uncommitted files. No remote operations, push, PR or merge.

Artifacts remain English, matching existing documentation. Recommend retaining the fork relationship; repository creation or renaming requires a separate user decision.

## Tasks
- [x] P1: Document upstream credit, adaptation changes, VS Code PlatformIO setup, usage and publication checklist. Check source/config references, local Markdown links and `git diff --check`.
- [x] P2: Exclude local tooling and stop ignoring Android tests. Check ignore behavior and run the existing firmware build; report Android verification availability honestly. Do not stage pre-existing ignored/untracked test sources.

## Acceptance criteria
- Original creator and repository are credited prominently; adaptation maintainer is identified without claiming original authorship.
- LICENSE and history remain unchanged.
- Setup distinguishes repository-root PlatformIO project from companion Android project.
- Local artifacts stay out of Git; existing tests become visible for the maintainer to review/include before publication.
- All unavailable checks and remaining publication actions are disclosed.

## Checks and workflow
- Documentation/hygiene only: TDD not applicable; no behavior implementation. Explicit project TDD configuration was not found; do not infer enabled/disabled.
- Firmware runner: `pio run -e esp32dev`.
- Android runner: `./gradlew testDebugUnitTest lintDebug assembleDebug` from `android-app/`; Java is currently absent from PATH.
- RDD: off, global decision. No native review transaction.
- Branch: `feature/android-udp-bridge`; initial boundary `e7f2af3`.
- Delivery strategy: `ask-on-risk`; forecast about 300 authored changed lines for this documentation/hygiene scope. Existing 463-line untracked test suite is preserved, not included in this scope's commits. No PR planned.
- Every completed task has a Conventional Commit; no AI attribution.

## Progress
P1 and P2 complete locally. Next: review/include the seven pre-existing Android test files, install the required Android environment, rerun Android checks and test physical hardware before publication. No remote operation was performed.

## Evidence
- P1: `git diff --check` passed; Python/std lib checked 13 local Markdown file links across four documents (passed).
- P1: Source/config references checked against the firmware constants, PlatformIO/Gradle configuration and retained history. Physical runtime harness: N/A for documentation; hardware verification remains pending.
- P1 commit: `e762b289f84e2610af16d8b490d3deaa0ee7d400` (`docs: prepare attributed PlatformIO publication guides`).
- P1 rollback: README guides, CREDITS.md and docs/PUBLISHING.md only; no firmware or Android behavior changed.
- P2: `git check-ignore` assertions passed for .pio, .vscode, .atl, .codegraph and Android local.properties; all seven pre-existing Android test sources became visible/unignored and remain untouched/uncommitted.
- P2: `pio run -e esp32dev` passed (5.03 s), Espressif32 7.0.1, ESP-IDF 4.4.7. RAM 60,944/327,680 bytes (18.6%); flash 1,370,265/3,866,624 bytes (35.4%). Existing mixed-framework warning: Arduino component uses default esp32 variant.
- P2: `./gradlew testDebugUnitTest lintDebug assembleDebug` skipped: Java unavailable on PATH. No installation/environment change attempted; Android tests, lint and APK build are not verified.
- P2: `git diff --check` passed. LICENSE, firmware/PlatformIO behavior and tracked sdkconfig bytes unchanged after build. Physical runtime harness: N/A for ignore changes; firmware upload and physical device checks remain pending.
- P2 work-unit commit: `chore: keep local tooling private and expose Android tests`; resolve its exact identity with `git log -1 --format=%H -- .gitignore` (avoids a self-referential hash).
- P2 rollback: root .gitignore tooling exclusions and removal of the broad test rule; existing test sources are not part of this commit.
- Publication scope authored changes: 203 additions+deletions against e7f2af3 (before P2 commit); delivery threshold not reached. RDD disabled/unmanaged; ordinary functional checks above apply.

## Rollback
Revert publication documentation and ignore changes independently of the existing Android/UDP implementation.
