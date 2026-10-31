# DTP Spin Repository Setup

## Summary

Maintain DTP Spin as a full-source Android product fork of Mixxx. Keep upstream history intact, preserve general desktop buildability for easier merges, but release and qualify only Android. Synchronize upstream monthly and before DTP Spin releases.

## Repository and Branch Model

- Keep `origin` as `oscoDOTblog/dtp-spin`.
- Add `upstream` pointing to `https://github.com/mixxxdj/mixxx.git`.
- Reserve `main` as an exact, unmodified mirror of `upstream/main`.
  - Protect it from direct commits.
  - Update it only by fast-forwarding from upstream.
- Use `spin/main` as the persistent DTP Spin integration branch.
  - Rename or replace the current `dev` branch with `spin/main`.
  - All product-specific Android work ultimately merges here.
- Create short-lived branches from `spin/main`, named by purpose:
  - `android/audio-routing`
  - `android/touch-ui`
  - `build/android-ci`
- Create temporary `release/<version>` branches only during stabilization. Tag releases as `dtp-spin-vX.Y.Z`.
- Never rebase published `spin/main` history. Merge `upstream/main` into it so upstream boundaries and conflict resolutions remain visible.

## Maintenance and Automation

- Perform a monthly upstream-sync PR:
  1. Fetch `upstream/main`.
  2. Fast-forward the fork’s clean `main`.
  3. Merge `main` into `spin/main`.
  4. Run compilation and Android smoke tests.
  5. Resolve conflicts in the sync PR rather than on feature branches.
- Keep DTP changes narrow and easy to classify:
  - Android lifecycle, permissions, packaging, and native integration stay in Android-specific areas.
  - Touch-focused presentation stays in QML/resources rather than altering the audio engine.
  - Cross-platform fixes remain isolated and suitable for optional upstream contribution.
  - Product branding and configuration should not be scattered through engine code.
- Add fork-owned CI that:
  - Builds a signed internal ARM64 APK from `spin/main`.
  - Uploads it as a retained artifact with commit and version metadata.
  - Runs inexpensive Linux compilation checks to detect accidental divergence from upstream.
  - Runs broader checks and produces release artifacts from `release/*` and version tags.
  - Stores test-signing and release-signing credentials separately; release credentials remain protected.
- Retain Mixxx’s GPL license, notices, and corresponding-source obligations. Review Mixxx naming and artwork separately before distributing DTP-branded builds.

## Interfaces and Documentation

- No source APIs need to change during repository setup.
- Add a short fork-maintenance document describing:
  - Remote and branch roles.
  - Monthly synchronization procedure.
  - Required CI checks.
  - APK signing and release ownership.
  - Human device-testing requirements inherited from the repository’s `AGENTS.md`.
- Keep upstream’s README and contribution history, adding a prominent DTP Spin section or separate product README rather than replacing provenance.

## Verification

- Confirm `main` and `upstream/main` resolve to the same commit after setup.
- Confirm `spin/main` contains the full Mixxx ancestry plus only intentional DTP commits.
- Exercise a mock upstream merge and verify conflicts are resolved on a sync branch.
- Require CI to produce an installable APK from `spin/main`.
- Install the APK on a physical ARM64 Android device and smoke-test launch, media access, track loading, playback, pause/resume, and relaunch.
- Verify test and release signing keys cannot overwrite one another’s installed builds unexpectedly.

## Assumptions

- DTP Spin is a long-lived Android product fork, not primarily an upstream contribution branch.
- Android is the only supported DTP release target; desktop checks exist only to control upstream drift.
- Upstream changes are integrated in scheduled monthly batches and before releases.
- The complete Mixxx source and Git history remain in this repository; an overlay-only, subtree, or patch-stack architecture will not be used.
