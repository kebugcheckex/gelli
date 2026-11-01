# Live Lyrics

High-level design. No code in this document is a commitment to start the work. The feature is large; the point of this note is to decide what is feasible against the current player, which libraries are worth taking, and which problems belong to the architecture rather than to lyrics formats.

## Goal

While a song plays, the expanded player can show lyrics that follow playback. The user can turn this on and off. It is off by default.

Two presentations, chosen by orientation:

- **Portrait: line scroll.** The current line is highlighted and kept in view. Neighboring lines are visible and dimmed. The list moves when the line changes.
- **Landscape: karaoke fill.** The current line is large. A second color gradually covers the base color as time moves through the line, the way a karaoke system does. Previous and next lines may sit above and below at a smaller size, but the fill is the point of this layout.

Orientation picks the presentation. It is not a second preference. Rotating the device recreates the activity (`AndroidManifest` does not set `configChanges` for orientation), so each layout can choose its presentation at inflate time.

Unsynced lyrics (no timestamps) use the scroll presentation in both orientations. A color sweep with nothing to sweep against is a fake.

## What the server already gives us

Jellyfin, since 10.9, serves lyrics at `GET /Audio/{itemId}/Lyrics`. A missing lyric is `404`. The body is `LyricDto`:

| Field | Meaning |
|---|---|
| `Lyrics[]` | Lines, in order |
| `Lyrics[].Text` | The line, after the server's own whitespace cleanup |
| `Lyrics[].Start` | Start time in **ticks**, or null when the line is not synced. 1 ms = 10,000 ticks. Same unit as `positionTicks` in `PlaybackReporter` and `EventListener`. |
| `Lyrics[].Cues[]` | Optional word-or-slice timing inside the line. Each cue is a character range (`Position`, `EndPosition`) into `Text`, plus `Start` and optional `End` in ticks. |
| `Metadata.Offset` | Lyric-versus-audio offset in ticks, from an LRC `[offset:]` tag. |
| `Metadata.IsSynced` | False for a plain `.txt` or an unsynced embedded lyric. |

The server, not the app, reads the well-known files. Sidecar `.lrc` and `.elrc` next to the audio, a plain `.txt`, and embedded lyrics (ID3 `USLT` / `SYLT`, Vorbis, FLAC) are normalized into this DTO. A lyrics plugin such as LrcLib writes a sidecar; the app then sees it through the same endpoint. The current LRC parser (`LrcLyricParser` on Jellyfin master) already turns enhanced-LRC word tags into `Cues`.

That is the contract to build on. Do not also call LrcLib, Musixmatch, or a scraper from the app. Those need a second identity, a second cache, and a policy for songs the server already has lyrics for. Remote search and upload exist on the server (`/Audio/{itemId}/RemoteSearch/Lyrics`, `POST /Audio/{itemId}/Lyrics`) but require the lyric-management policy. They are library administration, not playback.

An open server PR (jellyfin/jellyfin#17011) adds TTML, YRC, KRC, syllable objects, and translation tracks. It is not shipped. Do not block on it. A client model of "ordered lines, each with optional timed spans" can absorb syllables later without a second UI.

## Feasibility

Feasible, and it does not require a playback-stack change.

The clock is already there. `MusicPlayerRemote.getSongProgressMillis()` reads `MusicService`, which reads Media3 in-process (`MusicService` has no `android:process`). Position is per media item, and each item's `mediaId` is `song.id` (`LocalPlayer.createMediaItem`). Gapless transitions stay valid as long as lyrics are keyed to the current item, not to a session-wide clock. Repeat-one stays on the same lyric. There is no user-facing playback speed, so the clock does not have to scale.

The data path fits the migration already in progress. New server calls go through `JellyfinSdkSession.createApiOrNull()` and the Kotlin SDK (`jellyfin-core` 1.8.8), as `QueryUtil` and `PlaybackReporter` do. `GET /Audio/{itemId}/Lyrics` maps to the generated `lyricApi`. Confirm, when implementation starts, that the generated `LyricLine` still has `cues`. Line text and `start` have been stable since 10.9; cues are the field a stale OpenAPI snapshot would drop. Line-level scroll and a line-level sweep do not need cues. Word-level fill does.

What is not feasible as a drop-in:

- The progress views update once a second (`MusicProgressViewUpdateHelper`, `UPDATE_INTERVAL_PLAYING = 1000`). A karaoke fill driven by that timer steps. Scroll mode also skips lines that are shorter than the poll, which is common in fast passages.
- `LocalPlayer` logs `onPositionDiscontinuity` and does not tell the UI. A seek from the slider updates the controls immediately; a seek from the session (`EventListener`) only shows up on the next poll. Lyrics have to sample position themselves, not wait for the progress helper.
- Nothing in `Song`, `LocalSong`, or the queue stores lyrics. That is correct. Do not add them there (see below).

## Library choice

No third-party lyrics view.

The usual Android LRC widgets (`lrcview` and its forks) parse LRC themselves, highlight a whole line, and scroll. They do not know Jellyfin ticks or `Cues`, they do not follow palette colors, and the maintained-looking ones still sit on old support libraries. Taking one means re-parsing a file the server has already parsed, then fighting the widget to get a real color sweep.

Media3's subtitle `Cue` and `SubtitleView` are the wrong surface. Sidecar and plugin lyrics are not in the stream the player opens, a transcode would drop them even if they were embedded, and the subtitle view is a caption strip at the bottom. Injecting a text track into `LocalPlayer` would couple a UI feature to gapless queue rebuilds for no gain.

Parsing LRC in the app is also the wrong layer. The server already did it, including enhanced LRC. A local parser is only justified later, if offline playback must show lyrics for a song that was saved before this feature existed and the normalized DTO was never cached. Even then it should be a small internal parser, not a dependency.

The view is custom, and small. Two techniques cover both presentations:

- **Scroll.** A `RecyclerView` (already a dependency) of lines. Current line uses the accent color and a larger style. `smoothScrollToPosition` on line change. No per-frame work.
- **Karaoke fill.** One custom `View` for the current line. Draw the text twice, base color then accent color, and clip the accent pass with `canvas.clipRect` from the start edge by the fraction of the line that has elapsed. That is the "second color gradually covers the first" effect, and it needs no library. Word-level fill uses the same clip; only the fraction changes (see below). Clip from the start edge in the current layout direction, not hard-coded from the left, so RTL lyrics fill the right way.

Colors come from the theme and from the palette callback the player already has (`PaletteColorHolder`). No new color system.

The fetch is a coroutine on the SDK client the app already uses. No Retrofit endpoint, no Volley call.

## Where it sits in the player

This is the expensive part. The player is four hand-built layouts, and all four are built around a square cover and a queue.

| Layout | Cover | Everything else |
|---|---|---|
| `layout/fragment_flat_player.xml` | `WidthFitSquareLayout` (`album_cover_container`), full width | Controls and queue in an inner `SlidingUpPanelLayout` |
| `layout/fragment_card_player.xml` | Same square, over a palette `color_background` | Controls over the art; queue is an overlay panel |
| `layout-land/fragment_flat_player.xml` | `HeightFitSquareLayout` (`cover_container`) on the left | Controls and queue in the column to the end of the cover |
| `layout-land/fragment_card_player.xml` | Same left square | Controls in the right column; queue slides over them |

`FlatPlayerFragment` and `CardPlayerFragment` each branch on `Util.isLandscape()` into a portrait and a landscape implementation. Portrait measures the cover and then forces the inner panel tall enough for the controls (`setUpPanelAndAlbumCoverHeight`). The cover is not just an image: `PlayerAlbumCoverFragment` is a `ViewPager` that calls `MusicPlayerRemote.playSongAt` on swipe.

Lyrics must not be copied into those four implementations.

Recommended placement:

- **One host, one controller.** A `LyricsView` (or a one-layout fragment) with the same id in all four player layouts. Visibility and which presentation to use are decided in one place, called from `AbsPlayerFragment`, not reimplemented in `FlatPlayerFragment` and `CardPlayerFragment`. The two skins keep their own backgrounds and palette animation; the lyrics view only asks for an accent color.
- **Portrait: the cover slot.** The square is already most of the expanded player above the controls, which is the right shape for several visible lines. When lyrics are showing, hide `player_album_cover_fragment` and show the scroll view in that slot. Do not overlay text on the art. Overlay fights contrast, and the cover pager would still steal horizontal swipes and change the song. Hiding the pager removes that gesture while lyrics are up; that is acceptable.
- **Landscape: the right column, not the square.** A height-fit square is the wrong shape for one long line. On a phone in landscape the column to the end of the cover is the wide region. Put the karaoke line there and keep the cover on the left so the song is still recognizable and both skins still look like themselves. Controls stay. The queue remains reachable (it already lives in that column, as a list or as a sliding panel) but it is not the lyrics surface: lyrics replace the queue list while the mode is on, and the queue comes back when lyrics are off or the user opens the queue. If a line cannot fit at a readable size even with autosize, that line falls back to the scroll presentation instead of wrapping. A wrapped line breaks a single horizontal clip.
- **Do not grow the portrait cover slot.** `setUpPanelAndAlbumCoverHeight` sizes the queue panel from whatever is left under the cover. A lyrics view that takes the cover's measured height avoids reopening that math. A lyrics view that wants "the rest of the screen" has to be taught to both portrait implementations, in both skins. Not worth it for a first version.
- **Mini player shows nothing.** `AbsPlayerFragment.onShow` / `onHide` already track the outer panel. The frame or poll clock runs only while the expanded player is visible and lyrics are on. Notification, lock screen, widget, and the Media3 session migration do not carry lyrics.

The toolbar already handles player actions in `AbsPlayerFragment.onMenuItemClick`, and `menu_player.xml` has one icon (`action_toggle_favorite`). A lyrics action next to it is the on/off control. Persist it with `PreferenceUtil`, and mirror it under Now Playing (`pref_now_playing.xml`) so it can be found with the other player preferences. One preference, two entry points.

When the preference is on but the current song has no lyrics, keep the cover. Do not swap in an empty panel. The toolbar icon can show "on, nothing for this song" without moving layout. Playback never waits on lyrics.

## Clock

One sampler for both presentations. Do not reuse `MusicProgressViewUpdateHelper` as the driver; the seek bar can keep its 1-second updates.

- Sample `getSongProgressMillis()` about every 100 ms while lyrics are visible and the song is playing. That is enough to catch seeks, including session seeks that today are invisible until the next progress tick, and enough that scroll mode never skips a short line.
- Karaoke does not redraw only on that sample. On each sample, remember `(position, elapsedRealtime)`. A `Choreographer` frame callback extrapolates `position + (now - sampledAt)` while `isPlaying()`, and stops extrapolating on pause. Resample also on `onPlayMetadataChanged` and `onPlayStateChanged`. Extrapolation is wrong for at most one sample after a seek, then it snaps. That is a better trade than calling into the player every frame, and a much better trade than adding a position listener to `MusicService` for a UI feature.
- Convert with integer division by 10,000. Apply `Metadata.Offset` in the same unit before comparing. **Verify the sign against a file with a known `[offset:]` rather than assuming it.** The server parser does not bake the offset into `Start`; it is left for the client, and LRC players disagree about the sign.
- Line lookup is a binary search on `Start`. The end of a line is the next line's start, or the song duration for the last line. Empty LRC lines are real lines (instrumental breaks) and must be kept, so the highlight can clear.
- A fetch is tied to a song id. Drop the result if `MusicPlayerRemote.getCurrentSong()` has moved on. Prefetch the next queue item while lyrics are on, so a gapless transition does not flash the cover.

Word-level fraction, only when rendering the landscape fill and the current line has cues whose character ranges fall inside `Text`:

- Find the cue that contains the position.
- The filled width is the width of the text before `cue.Position`, plus the fraction of this cue's own width corresponding to the time between `cue.Start` and `cue.End` (if `End` is null, use the next cue's start or the line end).
- Measure with the same paint the view draws with. Do not assume a monospace advance.

If cues are missing, empty, or their indices do not land in `Text`, sweep the whole line linearly from its start to the next line's start. Say so only in code comments; the user still sees a fill. Jellyfin issue 14254 is real: the LRC parser trims and collapses whitespace, which can desync cue indices from the displayed string. Falling back is the mitigation. Do not try to re-trim on the client to "fix" the indices.

## Cache and offline

`Song` is a Room entity, a `Parcelable` copied through the queue, and compared by id. A lyric blob does not belong on it, and it does not belong on `LocalSong` either. Lyrics are large, optional, and useless to every list.

Cache the normalized DTO, not the original `.lrc`. Key it by account and item id, same identity as `local_songs`. A JSON file under the library root (`<root>/<account>/lyrics/<itemId>.json`) matches the "files keyed by item id" rule in the local-library plan and needs no Room migration. A negative entry (404, with a timestamp) belongs in the same store so a song with no lyrics is not refetched on every play, but it should expire (a day is enough) so a lyric added on the server shows up later.

Fetch only when the preference is on. Use the same network gate as browsing, not the streaming gate: lyrics are a small JSON request, and a saved song should still be able to pick up lyrics on a network that is not allowed to stream. When the network is unavailable, show the cached DTO or keep the cover. Never fail playback because lyrics failed.

Saving a song does not need to download lyrics inside `SaveWorker`. The player fetch writes the cache as a side effect. A song that was saved and is then played offline without ever having been played online with lyrics on will have no lyrics. That is acceptable. Backfilling lyrics for the whole local library is out of scope.

## Challenges that are actually architectural

1. **Four layouts, two skins, inconsistent ids.** Portrait uses `album_cover_container`. Landscape uses `cover_container`. Card landscape overlays the queue; flat landscape does not. A shared lyrics host only stays shared if the four XMLs grow the same id and the show/hide logic does not learn Flat versus Card. The moment karaoke measurement is copied into `LandscapeImpl` in both fragments, the feature will drift. This is the main reason to do the host first and the fill second.

2. **The cover pager is a transport control.** Hiding it while lyrics are up also hides swipe-to-change-song. Do not keep both gesture targets in the same rectangle. Previous and next still exist on the controls.

3. **Panel height is derived from the cover.** Replacing the cover with a different-sized child in portrait will shrink or grow the queue panel and can push the controls off-screen. First version: same slot, same height.

4. **The progress helper is the wrong clock, and seek is not an event.** Solved by sampling, not by a new `MusicService` callback. Do not add lyrics state to the session or the notification as part of this work. `docs/media3_session_migration_plan.md` is already the place where session state gets more honest; lyrics should not become another hand-built extra.

5. **Timestamp trust.** Ticks versus milliseconds is a one-line bug with a silent symptom (lyrics stuck at the first line, or finished immediately). Offset sign is the second. Transcoded playback can also drift from the original file's timestamps; v1 does not try to estimate that drift. Local original files and direct play should match. A per-song manual offset is a reasonable later addition and should be a client-only adjustment on top of `Metadata.Offset`, not a write back to the server.

6. **Word-level data is best-effort.** Standard LRC, plain text, and unsynced embedded lyrics have no cues. Enhanced LRC does, when the server parser succeeds. The landscape fill must look intentional in both cases. Linear line sweep is the normal case, not a degraded mode to apologize for.

7. **Text measurement.** Autosize, RTL, and CJK all change the clip position. The fill fraction is a fraction of measured width, not of character count. A line that wraps cannot be clipped as one rectangle; refuse to wrap in the karaoke presentation.

8. **Accessibility.** Color alone cannot be the current-line signal. Scroll mode already has size and position. Karaoke mode should expose the current line as a content description updated on line change, not on every frame, and should not animate in a way that makes TalkBack re-announce continuously.

9. **Stale responses and gapless.** The queue can move on before the HTTP call returns. Key by `song.id` and ignore mismatches. Prefetch the next item only; do not prefetch the whole queue.

## Suggested order

The feature can land in slices that each leave playback alone.

1. **Data and scroll.** SDK fetch, tick conversion, offset (once the sign is verified), file cache, negative cache, portrait scroll in the cover slot, toolbar toggle, cover kept when there is nothing to show. Both skins, portrait only is acceptable for this slice if landscape simply keeps the cover. This proves the clock and the cache without the custom draw path.

2. **Landscape fill.** Shared host in the right column, line-level clip, autosize, fallback to scroll when a line does not fit or has no timestamps. Cue-level clip in the same view once a song with real cues is in hand to test against. If the generated SDK model has no `cues`, decode that array from the raw response or bump the SDK; do not block slice 1 on it.

3. **Polish, only if the first two are used.** Next-track prefetch, a quiet manual offset, TalkBack pass, and a decision about whether the queue in landscape should be a second toggle or stay hidden for the whole time lyrics are on.

## Out of scope

- Uploading, deleting, or searching remote lyrics from the app.
- A client-side LrcLib (or any other) provider.
- Parsing TTML, YRC, KRC, or syllable tracks until the server actually serves them.
- Lyrics on the mini player, notification, lock screen, widget, or in `MusicService`.
- Editing timestamps, and writing an offset back to the server.
- Backfilling lyrics for every saved song.
- A third now-playing skin. Flat and Card stay as they are; lyrics is a mode inside both.
- Changing the Media3 session. The session migration stays a separate plan.

## Open points to settle before implementation

- Confirm `lyricApi.getLyrics` and `LyricLine.cues` in the resolved `jellyfin-core` 1.8.8 artifact. If cues were generated under a different name, or dropped, line-level still proceeds.
- Confirm `Metadata.Offset` sign with one real file.
- Decide whether landscape lyrics hide the queue for the whole time the mode is on, or whether the existing queue handle brings the queue back over the lyric. Recommendation: hide the list, keep the handle, so the queue is one gesture away and the fill has the column.
