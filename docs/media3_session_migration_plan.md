# MediaSessionCompat to Media3 Session Migration Plan

## Status

Not started. This is the "Phase 5: Optional Follow-Up Modernization" item from `media3_migration_plan.md`.

Interim fixes are in place in `MusicService.updateMediaSessionState()`:

- Playback speed is `0` whenever playback is not advancing. Previously the session reported `PAUSED` with speed `1`, which contradicts the `PlaybackState` contract.
- `STATE_BUFFERING` is reported while a stream is loading.
- `STATE_STOPPED` is reported once the queue has ended (`Playback.isEnded()`). Previously the session kept reporting `PLAYING` after the last track.

---

## Motivation

On some car head units, pausing from the car pauses the music, but the car's play/pause button doesn't update. Investigation showed that the app does publish `STATE_PAUSED` to its session immediately. Android's AVRCP target (`MediaPlayerWrapper` / `MediaPlayerList` in the Bluetooth module) forwards that change as a "playback status changed" message to the car. So the main suspect is the head unit, but the investigation also showed that the app hand-builds every piece of session state, and each edge case is app code:

| Gap in the hand-built session | Media3 behavior (verified against `media3-session` 1.5.1 bytecode) |
|---|---|
| Speed was `1` while paused (fixed in the interim patch). | Speed is `0` unless the player is playing. |
| Buffering was not reported (fixed in the interim patch). | `STATE_BUFFERING` maps to `STATE_BUFFERING`, or `PAUSED` if play isn't requested. |
| After the last track ends with repeat off, ExoPlayer is `STATE_ENDED` but `playWhenReady` stays `true`. `isPlaying()` therefore returned `true`, and the session kept reporting `PLAYING` (fixed in the interim patch for the session only; the in-app UI still uses `isPlaying()`). | `STATE_ENDED` maps to `STATE_STOPPED`. |
| Player errors are never reported. | A player error maps to `STATE_ERROR`. |
| `MEDIA_SESSION_ACTIONS` is a fixed bitmask, whatever the state (for example, "next" is advertised on the last track). | Available commands follow the player's state. |
| `startForeground`/`stopForeground` are managed by hand in `PlayingNotification.update()`. | `MediaSessionService` manages the foreground state and the notification. |

Media3's `MediaSession` exposes a single `Player` to Media3 controllers and, through its built-in legacy stub, to every platform controller: Bluetooth AVRCP, lock screen, the system media controls, Wear OS and Android Auto. All of them see a state derived from one source.

---

## What Is in Scope

- Replace `MediaSessionCompat` with `androidx.media3.session.MediaSession` hosted in a `MediaSessionService`.
- Derive session state and metadata from the player instead of hand-building `PlaybackStateCompat` and `MediaMetadataCompat`.
- Move the playback notification and its foreground handling to Media3.
- Keep queue, shuffle, repeat, sleep timer, Jellyfin playback reporting and widget behavior unchanged.

## What Is Explicitly Out of Scope

- Migrating in-app UI from the `MusicPlayerRemote` local binder to `MediaController`.
- Android Auto browsing (`MediaLibraryService`).
- Replacing `QueueManager` with ExoPlayer's playlist and shuffle handling.

---

## Current Architecture (Inventory)

| File | Session-related role today |
|---|---|
| `service/MusicService.java` | Plain `Service`. Owns `MediaSessionCompat` (`initMediaSession`), its callback, `updateMediaSessionState()`, `updateMediaSessionMetadata()` (album art via Glide), `MEDIA_SESSION_ACTIONS`, the `ACTION_*` intents from widgets and notification, and the noisy-audio receiver. |
| `service/playback/LocalPlayer.java` | Wraps `ExoPlayer` behind `Playback`. `createMediaItems()` sets only the URI and `mediaId`, with no `MediaMetadata`. `getOnMain()` marshals reads from other threads onto the main thread. |
| `service/QueueManager.java` | Authoritative queue, position, shuffle and repeat state. The ExoPlayer playlist is a mirror pushed by `onQueueChanged()`. |
| `service/receivers/MediaButtonIntentReceiver.java` | Manifest receiver plus `handleIntent()`. Single click toggles play/pause, double click skips to next, triple click goes back. Used by the session's `onMediaButtonEvent`. |
| `service/notifications/PlayingNotification*.java` | `Nougat` uses `androidx.media` `MediaStyle` with the session token. `Marshmallow` ("classic") uses custom `RemoteViews`. Both call `startForeground`/`stopForeground`. Preferences: `CLASSIC_NOTIFICATION`, `COLORED_NOTIFICATION`. |
| `helper/MusicPlayerRemote.java` | Binds to `MusicService` with an action-less intent and calls its methods directly through `MusicBinder`. |
| `views/widgets/AppWidget*.java` | Driven by `MusicService.notifyChange` broadcasts. Unaffected by the session type. |

**Why commands must not go straight to ExoPlayer.** `QueueManager` only tracks track changes that `MusicService` initiates (`playNextSong`, `playPreviousSong`, `playSongAt`) or that happen automatically (`MEDIA_ITEM_TRANSITION_REASON_AUTO`/`REPEAT`). Suppose a Media3 session were given the raw `ExoPlayer`, and a car sent "next". ExoPlayer would seek with `REASON_SEEK`, and `QueueManager` would silently fall out of sync.

---

## Target Architecture

- **`MusicService extends MediaSessionService`.** It builds the `MediaSession` in `onCreate()`, returns it from `onGetSession()`, and releases it in `onDestroy()`.
- **Session player:** a `ForwardingSimpleBasePlayer` (available in `media3-common` 1.5.1) wrapping the `ExoPlayer`. It overrides `handleSetPlayWhenReady`, `handleSeek` and `handleStop` to call `MusicService.play()`/`pause()`/`playNextSong()`/`back()`/`seek()`/`quit()`. This keeps `QueueManager` authoritative. State reads pass through to ExoPlayer.
- **Metadata:** `LocalPlayer.createMediaItems()` sets `MediaItem.mediaMetadata` (title, artist, album artist, album, year, track number, artwork URI). Media3 then publishes it to every controller. `updateMediaSessionMetadata()` goes away.
- **Artwork:** a custom `BitmapLoader` (`androidx.media3.common.util.BitmapLoader`) backed by `CustomGlideRequest`, set with `MediaSession.Builder.setBitmapLoader()`. This keeps the Glide disk cache, Jellyfin image URLs, and the `SHOW_ALBUM_COVER`/`BLUR_ALBUM_COVER` preferences.
- **Media buttons:** `MediaSession.Callback.onMediaButtonEvent()` (present in 1.5.1) delegates to `MediaButtonIntentReceiver.handleIntent()`. This keeps the multi-click behavior. The manifest receiver switches to `androidx.media3.session.MediaButtonReceiver`, so a button press with no running service starts the session service.
- **Binding:** override `onBind()`. Return `super.onBind(intent)` when the intent has an action (Media3 and the platform bind with `MediaSessionService.SERVICE_INTERFACE` or the legacy browser action). Otherwise return the existing `MusicBinder`, so `MusicPlayerRemote` keeps working unchanged.
- **`onStartCommand()`:** handle the app's own `ACTION_*` intents first, then delegate to `super.onStartCommand()` for media-button intents.
- **Notification:** `DefaultMediaNotificationProvider`, or a custom `MediaNotification.Provider` (see Open Decisions). Foreground start and stop are left to `MediaSessionService`.

---

## Phase 0: Baseline

- Add Bluetooth/AVRCP and session scenarios to `media3_smoke_test_matrix.md` (see Validation below).
- Record current behavior on the affected car: `adb shell dumpsys media_session` after pausing from the car, plus a Bluetooth HCI snoop log. This shows whether the head unit registers for `PLAYBACK_STATUS_CHANGED` notifications at all. If it doesn't, no app-side change will update its button. The migration is still worth doing for the other gaps.

## Phase 1: Dependency and Metadata (No Behavior Change)

- Add `androidx.media3:media3-session:1.5.1`.
- Populate `MediaItem.mediaMetadata` in `LocalPlayer.createMediaItems()`.
- The app still runs on `MediaSessionCompat`, so this phase can ship on its own.

## Phase 2: Session Player Wrapper

- Implement the `ForwardingSimpleBasePlayer` command routing described above.
- Advertise only the commands the app supports: play/pause, previous/next, seek in the current item, stop. Leave out shuffle and repeat for now; `QueueManager` implements shuffle by reordering, not through ExoPlayer's shuffle mode.
- Unit-test the routing logic against a fake `MusicService` and `QueueManager` interface where practical.

## Phase 3: Swap the Session

- `MusicService` extends `MediaSessionService`. Build the session with the wrapper, the Glide `BitmapLoader`, `setSessionActivity()` (opens `MainActivity`), and the callback.
- Delete `initMediaSession()`, `updateMediaSessionState()`, `updateMediaSessionMetadata()`, `MEDIA_SESSION_ACTIONS` and `getMediaSession()`.
- Update the manifest: add the `androidx.media3.session.MediaSessionService` intent filter to `MusicService` and replace the media-button receiver.
- Decide `onTaskRemoved()` behavior. The current app keeps playing after a swipe-away. `pauseAllPlayersAndStopSelf()` exists if stopping is preferred while paused.

## Phase 4: Notification

- Remove `startForeground`/`stopForeground` from `PlayingNotification`. `MediaSessionService` posts the notification and drives the foreground state.
- Implement the chosen notification option (see Open Decisions).
- Once nothing uses `MediaStyle` or `MediaSessionCompat`, drop the `androidx.media:media` dependency.

## Phase 5: Cleanup and Validation

- Remove dead code paths and update `CLAUDE.md` (Playback section).
- Run the validation steps below.
- Optional follow-ups, each its own PR:
  - `onPlaybackResumption()`, so the Android 13+ media controls can resume after the service dies.
  - `ExoPlayer.setHandleAudioBecomingNoisy(true)`, replacing `becomingNoisyReceiver`.
  - Migrating UI to `MediaController`.
  - `MediaLibraryService` for Android Auto.

---

## Open Decisions

1. **Notification styles.** Either adopt `DefaultMediaNotificationProvider` and retire the `CLASSIC_NOTIFICATION` and `COLORED_NOTIFICATION` preferences, or write a custom `MediaNotification.Provider` that reproduces them. On Android 13+, the system media controls ignore custom notification layouts anyway.
2. **Headset multi-click.** Keep the app's single/double/triple click mapping through `onMediaButtonEvent` (the planned default), or accept Media3's built-in handling.
3. **Swipe-away behavior.** Keep playing (current behavior) or stop when paused.

## Risks

- **Threading.** The session reads the player on the application looper. ExoPlayer is created in `MusicService.onCreate()` on the main thread, which is fine, but the wrapper must not block on `getOnMain()` from the main thread in a way that deadlocks.
- **Queue desync.** Any command path that bypasses the wrapper (for example a future `MediaController` call made directly against ExoPlayer) reintroduces drift between `QueueManager` and ExoPlayer.
- **Foreground-service restrictions (Android 12+).** Media3 handles media-button starts. Widget and notification `ACTION_*` intents still go through `startService`, which already has a fallback in `MediaButtonIntentReceiver.startService()`. Re-test them.
- **Behavior visible to external controllers changes.** End of queue becomes `STOPPED`, and available commands become state-dependent. Head units may render differently. That is the intent, but it needs a car test.

## Validation

- Build and tests: `./gradlew assembleDebug testDebugUnitTest --console=plain`, plus `./gradlew :app:minifyReleaseWithR8` (add keep rules if R8 strips session classes).
- New smoke-matrix rows:

| # | Scenario | Pass criteria |
|---|---|---|
| S1 | Pause, then play, from a car head unit over Bluetooth | Music pauses or resumes, and the car's play/pause button flips within ~1 s |
| S2 | Next/previous from the car | Track changes; the in-app queue position matches; the car shows the new title |
| S3 | Let the last track finish with repeat off | `dumpsys media_session` shows `STOPPED`/`PAUSED`, not `PLAYING`; the car shows a play button |
| S4 | Stream buffering at track start | Session shows `BUFFERING`; the car keeps showing "playing" (AVRCP maps buffering to playing) |
| S5 | Lock-screen and system media controls | Play/pause/next/previous/seek work, and the state stays in sync |
| S6 | Headset single, double and triple click | Toggle, next and previous, according to Open Decision 2 |
| S7 | Media button with the service not running | Service starts and playback resumes |
| S8 | Widget and notification controls | Same as today; no foreground-service crash on Android 12+ |
