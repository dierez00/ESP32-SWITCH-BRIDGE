# Publish with clear attribution

**Prefer a GitHub fork** while this remains a direct adaptation of the original
ESP32 controller. A fork makes the origin visible and simplifies upstream
comparison. You can still give your adaptation its own name and roadmap.

An independent repository makes sense if you need a distinct product identity,
but loses GitHub's fork relationship. It must still retain the GPL license,
credits, change notices and corresponding source; a new repository does not
make the original code your authorship. Keep the existing Git history in either case.

## Local readiness checklist

- [ ] Read [the main setup guide](../README.md) and [Android instructions](../android-app/README.md).
- [ ] Preserve [LICENSE](../LICENSE), [CREDITS.md](../CREDITS.md) and existing dependency notices.
- [ ] Run `git status --short --untracked-files=all` and review every file to include.
- [ ] Review/include the existing `android-app/app/src/test/` sources before publication;
      they were hidden by the old root `test` ignore rule and are not automatically
      included by this documentation change.
- [ ] Confirm no credentials, signing keys, APKs, build output, SDK paths or local
      tooling indexes are staged. Use explicit paths, not a blind `git add .`.
- [ ] Run `git diff --check`, `pio run -e esp32dev` and, with the required Android
      environment, `./gradlew testDebugUnitTest lintDebug assembleDebug` in `android-app/`.
- [ ] Test upload, Switch pairing, Android input, disconnect/neutral recovery and
      focus/background behavior on physical devices. Record limitations honestly.

## GitHub publication

1. Choose a fork or independent destination under your own account. If a fork
   already exists, use it rather than creating an unnecessary second copy.
2. Check `git remote -v` locally and confirm the intended destination before
   any push. An `upstream` remote is useful, but does not prove GitHub classifies
   your repository as a fork.
3. Review the branch and commits, then push only when you are ready. Do not
   delete `.git`, rewrite inherited history or force-push to erase the origin.
4. Use a description such as: “ESP32 Switch Pro Controller bridge with Android
   UDP input, based on ghostside-net/ESP32-Switch-Controller-Joycon.” Keep the
   upstream credit near the top of the README.
5. If you publish a firmware binary or APK, provide the matching source revision,
   build instructions, license and notices with the release. Keep private
   signing material outside Git.

This guide does not create a repository, push changes or alter remotes. Those
remain maintainer actions after reviewing the local changes.
