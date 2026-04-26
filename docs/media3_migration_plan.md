# ExoPlayer to Android Media3 Migration Plan

## Current State (Codebase Scan)
- Dependency: `com.google.android.exoplayer:exoplayer:2.19.1` in `app/build.gradle`.
- ExoPlayer API usage is concentrated in:
  - `app/src/main/java/com/dkanada/gramophone/service/playback/LocalPlayer.java`
  - `app/src/main/java/com/dkanada/gramophone/service/playback/Playback.java`
  - `app/src/main/java/com/dkanada/gramophone/service/QueueManager.java`
  - `app/src/main/java/com/dkanada/gramophone/service/MusicService.java`
- Notifications and transport controls currently rely on `MediaSessionCompat` (`MusicService` + `PlayingNotification*`) and are not directly ExoPlayer-bound.

## Migration Goals
- Replace ExoPlayer 2 (`com.google.android.exoplayer2.*`) with AndroidX Media3 (`androidx.media3.*`) with no playback behavior regressions.
- Keep queue/repeat/shuffle semantics unchanged.
- Keep existing `MediaSessionCompat` and notification behavior working during initial migration (session modernization can be a later phase).

## Non-Goals (Initial Pass)
- No full re-architecture to `MediaSessionService`/`androidx.media3.session.MediaSession` in the first migration pass.
- No UI redesign or player feature expansion.

## Phase 0: Baseline and Guardrails
1. Create a migration branch and capture a baseline smoke checklist:
   - Queue open/play
   - Next/previous/seek
   - Shuffle/repeat transitions
   - Headset/media button controls
   - Foreground notification controls
   - App restart + queue restore
2. Build and run baseline on at least one API 23+ device/emulator and one modern API level.
3. Snapshot logcat tags around playback (`LocalPlayer`, `MusicService`) for post-migration comparison.

## Phase 1: Dependency Layer Migration
1. Replace ExoPlayer artifact in `app/build.gradle` with required Media3 modules.
2. Add the exact Media3 modules needed by current code paths:
   - Player/core (`ExoPlayer`, `Player`, `MediaItem`, `PlaybackException`)
   - Data source/cache (`DefaultDataSource`, `FileDataSource`, `CacheDataSource`, `CacheDataSink`, `SimpleCache`)
   - Database provider (`StandaloneDatabaseProvider`)
3. Keep versions aligned across Media3 modules to avoid classpath conflicts.

## Phase 2: Source API Migration (Mechanical + Compile-Fix)
1. Update imports/package references from `com.google.android.exoplayer2.*` to `androidx.media3.*` in:
   - `LocalPlayer.java`
   - `Playback.java`
   - `QueueManager.java`
   - `MusicService.java`
2. Update static constant imports used in `MusicService` for media item transition and play-when-ready change reasons.
3. Resolve any signature/annotation deltas (for example `@Player.RepeatMode`, listener callback signatures, and `MediaItem` accessors).
4. Keep existing threading model and queue mutation logic unchanged in this phase.

## Phase 3: Behavioral Parity Verification
1. Verify `LocalPlayer` behavior parity:
   - `setQueue` incremental queue updates
   - `onMediaItemTransition` reason handling
   - `onPlaybackSuppressionReasonChanged` and buffering/loading logic
   - Error handling path (`onPlayerError` -> queue clear + prepare)
2. Validate cached/local/transcode path behavior:
   - Local file playback (`file://`)
   - Remote transcode/HLS path (`MimeTypes.APPLICATION_M3U8`)
   - Cache reads/writes and eviction behavior
3. Validate service lifecycle:
   - Foreground/notification updates
   - Audio-becoming-noisy handling
   - Queue persistence/restore on service restart

## Phase 4: Compatibility and Integration Hardening
1. Confirm `MediaSessionCompat` interactions still reflect accurate playback state and metadata after Media3 switch.
2. Re-test transport controls from:
   - Notification actions
   - `MediaButtonIntentReceiver`
   - External media button intents
3. Run release build (`minifyEnabled`) to catch any shrinker-related class issues.

## Phase 5: Optional Follow-Up Modernization (Separate PR)
1. Evaluate migration from `MediaSessionCompat` to `androidx.media3.session.MediaSession`/`MediaSessionService`.
2. Decide whether to keep compatibility receiver paths or adopt Media3 session command routing.
3. If migrated, update notification/session integration accordingly.

## Key Risks and Challenges
1. Media3 module split and dependency mismatch:
   - Risk: compile/runtime failures if required modules are missing or versions drift.
   - Mitigation: pin a single Media3 version and add modules explicitly.
2. Callback semantic drift:
   - Risk: transition/play-when-ready reason handling changes can break queue progression and pending-quit logic.
   - Mitigation: preserve existing branch logic and validate event-order assumptions with logs.
3. Cache behavior differences:
   - Risk: subtle cache read/write behavior changes can affect local vs transcode paths.
   - Mitigation: test both file and network playback with cache enabled and disabled.
4. Media session interoperability:
   - Risk: Media3 player with `MediaSessionCompat` may expose edge-case transport/state inconsistencies.
   - Mitigation: keep session layer unchanged first; verify notification and headset controls explicitly.
5. No automated playback test suite:
   - Risk: regressions are easy to miss.
   - Mitigation: codify and run a manual smoke matrix across API levels and lifecycle scenarios.
6. Existing queue mutation complexity:
   - Risk: `setQueue` incremental updates plus shuffle/repeat interactions are sensitive to event timing.
   - Mitigation: avoid refactors during migration; isolate to package/API conversion and targeted fixes only.

## Suggested PR Breakdown
1. PR 1: Dependency swap + mechanical import migration + compile green.
2. PR 2: Behavioral fixes from smoke testing (if needed).
3. PR 3: Optional media session modernization (if approved).
