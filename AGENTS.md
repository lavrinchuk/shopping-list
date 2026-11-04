# Codex instructions for ShoppingList

Applies to all work in this repository. Read this document and the relevant GitHub issue / roadmap before changing code.

## Project boundaries

- Android application in **Java** with Android Views/XML, Material 3, Gradle Kotlin DSL, and SQLiteOpenHelper.
- Keep existing layering (`Activity → ViewModel → Repository → Database`) and existing asynchronous persistence guarantees.
- Never replace the current local database or destructive-migrate its schema. Every migration must preserve existing lists, archived lists, purchased flags, and other user data, with regression tests.
- Do not add network APIs, new dependencies, permissions, backend services, or unrelated product features without explicit task justification.
- Treat `app/build.gradle.kts` Android package ID `ua.lavrinchuk.shoppinglist` as permanent. Do not change signing configuration, Firebase settings, or CI versioning in unrelated feature work.
- See the product roadmap in GitHub issue #1: https://github.com/lavrinchuk/shopping-list/issues/1 . Use its stable task IDs in PRs.

## Git workflow: feature branch → PR → human merge

1. Check `git status --short`, current branch and remotes first. Preserve uncommitted local changes. Never discard, reset, overwrite, stash, or include user changes without permission.
2. Start from an up-to-date `origin/master` (fetch first). For each scoped task create its own `feature/<task-id>-<short-description>` branch. For CI/docs maintenance use `chore/<short-description>`. If the working tree is dirty or branch switching would risk user work, stop and explain options rather than forcing checkout.
3. Implement only the requested scope. Add tests for new behavior and regressions, especially SQLite upgrades, archived lists and persisted state.
4. Run checks before committing (where tools/SDK are available):
   `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --no-configuration-cache`
   Run instrumented tests when an emulator/device is available; report explicitly when they were not run.
5. Review `git diff` and `git status`. Never commit credentials, keystores, local Android configuration, personal data, build products or test screenshots unless the user specifically requests safe inclusion. Prefer targeted `git add` over indiscriminate staging.
6. Create meaningful commit(s) on the feature branch. With GitHub permissions, push that branch and open a **Draft Pull Request targeting master**. If push/PR creation is unavailable, provide commands and the proposed PR text. Never claim to have completed actions you could not perform.
7. The PR description must include roadmap task ID, summary, affected areas and migration implications, commands/tests and results, manual QA steps, and outstanding risks. Include UI screenshots if appropriate.
8. **Never commit or push directly to master. Never merge a PR, enable auto-merge, bypass checks, or publish to Firebase unless explicitly requested by the user.** Leave final merge/release approval to the user.
9. When instructed to address PR feedback, update the existing feature branch and PR rather than opening an unrelated PR. Do not rewrite published history unless the user explicitly approves.

## CI and release separation

- `.github/workflows/android-ci.yml`: pull request validation; Java/Gradle build, unit tests, lint. No Firebase secrets or upload.
- `.github/workflows/firebase-distribution.yml`: delivery triggered by pushes to `master` or release tags, and manual invocation. Merging into `master` releases a build to the Firebase `friends` group.
- CI generates `versionCode` / `versionName` for distributed builds. Do not increment these manually for routine feature PRs or move secrets into source.
- A PR's successful CI does not mean device/UI tests passed; distinguish checks actually executed from checks omitted.

## Interaction expectations

- For ambiguous product behavior, make the smallest reversible implementation, clearly document assumptions, and ask only when the decision materially changes functionality, data loss risk, or security.
- Summarize exact modified files, tests run, their outcome, and the PR link in the final report.
