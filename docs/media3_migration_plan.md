# ExoPlayer to Android Media3 Migration Plan

## Status

Phases 0–3 are complete. The branch compiles against Media3 1.5.1 and basic playback has been verified on a physical device.

Remaining: Phase 4 (release build + shrinker test) before merging. Phase 5 is a separate follow-up.

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

Smoke checklist captured in `media3_smoke_test_matrix.md`. Verified on a physical device before and after migration.

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

## Phase 3: Behavioral Parity Verification — Done

Basic playback verified on a physical device. See `media3_smoke_test_matrix.md` for the full scenario checklist.

## Phase 4: Compatibility and Integration Hardening — Pending

- [ ] Build release APK (`./gradlew assembleRelease`) and confirm no shrinker-related failures. The current proguard rules use `-keepnames class **.*` broadly, but Media3 uses service discovery for module registration (e.g. HLS factory) which can be affected by aggressive shrinking.
- [ ] Re-test transport controls from notification, `MediaButtonIntentReceiver`, and external media button intents.
- [ ] Confirm `MediaSessionCompat` state/metadata stays in sync after the Media3 switch.

## Phase 5: Optional Follow-Up Modernization (Separate PR)

- Evaluate migration from `MediaSessionCompat` to `androidx.media3.session.MediaSession` / `MediaSessionService`.
- If migrated, update notification/session integration accordingly.
- Decide whether to keep `MediaButtonIntentReceiver` or adopt Media3 session command routing.
