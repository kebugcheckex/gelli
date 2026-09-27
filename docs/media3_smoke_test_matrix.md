# Media3 Migration — Smoke Test Matrix

Manual test matrix for verifying behavioral parity after the ExoPlayer 2 → Media3 migration.
For full compatibility coverage, run on at least one API 23 device/emulator and one modern API level (API 33+). This coverage has not been recorded for the current personal-use validation.

**Status:** The maintainer reports that live testing looks good and playback functions work. Earlier notes record physical-device verification of basic playback and queue/mode behavior. This is accepted for the current personal-use migration; it does not establish a pass for every scenario below.

## Validation Record

| Check | Result |
|---|---|
| Debug APK assembly | Passed: `.\gradlew.bat assembleDebug testDebugUnitTest --console=plain` |
| JVM unit tests | 63 passed; no failures, errors, or skipped tests. These are not a replacement for the device scenarios below. |
| Release compilation and R8 code shrinking | Passed: `.\gradlew.bat :app:minifyReleaseWithR8 --console=plain` |
| Live playback | Maintainer reports working playback functions; exact device/API and per-scenario results were not supplied with this report. |
| Signed release APK assembly and runtime testing | Deferred for personal use; no Google Play publication planned and signing is not configured. Not a merge requirement. |
| Detailed cache/HLS, service lifecycle, session synchronization, and edge-case coverage | No individual results recorded; retained as follow-up regression scenarios. |

The tables below describe test procedures and pass criteria, not individual recorded results. Successful R8 processing does not verify playback in a signed, shrunk release APK.

## Playback

| # | Scenario | Steps | Pass criteria |
|---|---|---|---|
| 1 | Basic play | Open an album, tap a track | Playback starts, progress bar moves, notification appears |
| 2 | Auto-advance | Let a track play to the end | Next track starts automatically (`REASON_AUTO`) |
| 3 | Next | Tap next button | Track advances, queue position increments |
| 4 | Previous | Tap previous within first 5 s of a track | Goes to previous track |
| 5 | Previous (rewind) | Tap previous after >5 s into a track | Seeks to 0, stays on current track |
| 6 | Seek | Drag the seek bar | Playback resumes from seeked position |

## Queue and Modes

| # | Scenario | Steps | Pass criteria |
|---|---|---|---|
| 7 | Shuffle on | Enable shuffle mid-queue | Queue reorders; currently playing song moves to position 0 |
| 8 | Shuffle off | Disable shuffle mid-play | Position syncs back to the song's index in the original queue |
| 9 | Repeat none | Set repeat = none, reach last track | Playback stops after last track |
| 10 | Repeat all | Set repeat = all, reach last track | Wraps back to first track |
| 11 | Repeat one | Set repeat = one | Same track repeats indefinitely |
| 12 | Add to queue | Add a song to the queue while playing | New song appears in queue without interrupting current track |
| 13 | Remove from queue | Remove a song that is not currently playing | Queue updates; playback unaffected |

## Cache and Streaming Paths

| # | Scenario | Steps | Pass criteria |
|---|---|---|---|
| 14 | Local/cached file | Play a song that has been downloaded (file:// path) | No buffering spinner; plays immediately |
| 15 | Remote transcode (HLS) | Play a song that requires transcoding | Brief buffering, then HLS stream plays correctly |
| 16 | Cache write | Play a remote song for the first time | Subsequent play of the same song loads faster (cache hit) |

## Service Lifecycle

| # | Scenario | Steps | Pass criteria |
|---|---|---|---|
| 17 | Notification controls | Use play/pause/next/prev from the notification | Each action takes effect; notification state stays in sync |
| 18 | Audio becoming noisy | Unplug headphones or disconnect Bluetooth while playing | Playback pauses automatically |
| 19 | Media button | Press a hardware media button or headset button | Play/pause toggles correctly |
| 20 | Queue restore | Start playback, force-stop the app, reopen | Queue, position, and progress restore to where they left off |
| 21 | Background play | Lock screen or switch to another app | Playback continues; lock screen controls work |

## Edge Cases

| # | Scenario | Steps | Pass criteria |
|---|---|---|---|
| 22 | Sleep timer | Set sleep timer, wait for current track to end | App quits after the track completes (`pendingQuit` path) |
| 23 | Unplayable file | Attempt to play a file with a broken/invalid URL | Toast appears; player recovers and queue does not freeze |
| 24 | Rapid next/prev | Tap next/previous repeatedly in quick succession | No crash; settles on the correct track |
| 25 | Volume offset | Change gain offset in settings while playing | Volume adjusts immediately without restarting playback |

## Notes

- The constant values for `MEDIA_ITEM_TRANSITION_REASON_*`, `PLAY_WHEN_READY_CHANGE_REASON_*`, `STATE_*`, and `REPEAT_MODE_*` were verified identical between ExoPlayer 2.19.1 and Media3 1.5.1 by inspecting bytecode — no behavioral delta expected from constant drift.
- `MediaItem.localConfiguration` is `@Nullable` in Media3 but is always non-null for items created by `LocalPlayer.createMediaItems()` since all items have an explicit URI.
