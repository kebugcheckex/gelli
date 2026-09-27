# ExoPlayer to Android Media3 Migration Plan

## Status

The Media3 1.5.1 migration is ready for review for personal use. The maintainer reports that live playback testing looks good and playback functions work. Debug APK assembly, all 63 JVM unit tests, and release R8 shrinking passed.

A signed release APK and runtime testing of that APK are deferred: the maintainer uses the app personally and does not plan to publish it to Google Play. These are not merge requirements for this migration. Detailed device/API coverage is recorded in `media3_smoke_test_matrix.md`; Phase 5 remains a separate follow-up.

---

## What Was in Scope

- Replace ExoPlayer 2 (`com.google.android.exoplayer2.*`) with AndroidX Media3 (`androidx.media3.*`) with no playback behavior regressions.
- Keep queue/repeat/shuffle semantics unchanged.
- Keep existing `MediaSessionCompat` and notification behavior working during initial migration.

## What Was Explicitly Out of Scope

- No full re-architecture to `MediaSessionService`/`androidx.media3.session.MediaSession` in the first pass.
- No UI redesign or player feature expansion.

---

## Phase 0: Baseline and Guardrails — Done

Smoke checklist captured in `media3_smoke_test_matrix.md`. Basic playback was previously reported as verified on a physical device before and after migration; this does not establish that every checklist scenario was run.

## Phase 1: Dependency Layer Migration — Done

Replaced `com.google.android.exoplayer:exoplayer:2.19.1` with these Media3 1.5.1 modules:

```
androidx.media3:media3-exoplayer
androidx.media3:media3-exoplayer-hls   ← required for transcode/HLS path
androidx.media3:media3-datasource
androidx.media3:media3-database
androidx.media3:media3-common
```

Note: `media3-exoplayer-hls` must be listed explicitly. Unlike ExoPlayer 2, Media3 does not bundle HLS in the core artifact. Without it, `DefaultMediaSourceFactory` cannot handle `MimeTypes.APPLICATION_M3U8` and transcoded streams fail at runtime.

## Phase 2: Source API Migration — Done

Updated all `com.google.android.exoplayer2.*` imports to `androidx.media3.*` in:

- `LocalPlayer.java`
- `Playback.java`
- `QueueManager.java`
- `MusicService.java`

Static constants (`MEDIA_ITEM_TRANSITION_REASON_*`, `PLAY_WHEN_READY_CHANGE_REASON_*`, `STATE_*`, `REPEAT_MODE_*`) were verified identical between ExoPlayer 2.19.1 and Media3 1.5.1 by inspecting bytecode — no behavioral delta from constant drift.

`MediaItem.localConfiguration` is `@Nullable` in Media3 but is always non-null for items created by `LocalPlayer.createMediaItems()` since all items have an explicit URI.

Cache release order in `LocalPlayer.stop()` was corrected to release the player before the cache (`exoPlayer.release()` then `simpleCache.release()`).

## Phase 3: Behavioral Parity Verification — Playback Accepted

The maintainer reports successful live playback testing and working playback functions. The earlier device check covered basic playback and queue/mode behavior. See `media3_smoke_test_matrix.md` for coverage and scenarios without individual results; the general playback report is not a full matrix sign-off.

## Phase 4: Compatibility and Integration Hardening — Build Checks Passed; Release Testing Deferred

- [x] Build debug APK and run JVM unit tests (`.\gradlew.bat assembleDebug testDebugUnitTest --console=plain`): successful; 63 tests passed, with no failures, errors, or skipped tests.
- [x] Run release code shrinking (`.\gradlew.bat :app:minifyReleaseWithR8 --console=plain`): successful. This verifies release compilation and R8 processing, not complete APK assembly or runtime behavior of the shrunk app.
- **Deferred:** Signed release APK assembly (`.\gradlew.bat assembleRelease`) and release-device smoke testing. Release signing is not configured and a release APK is not required for the current personal-use workflow.
- **Follow-up coverage:** Explicit notification controls, `MediaButtonIntentReceiver`/external media buttons, and `MediaSessionCompat` state/metadata synchronization results were not supplied separately. Keep these and the other unrecorded matrix scenarios available for future regression testing; do not mark them individually passed based on the general playback report.

## Phase 5: Optional Follow-Up Modernization (Separate PR)

- Evaluate migration from `MediaSessionCompat` to `androidx.media3.session.MediaSession` / `MediaSessionService`.
- If migrated, update notification/session integration accordingly.
- Decide whether to keep `MediaButtonIntentReceiver` or adopt Media3 session command routing.
