# Live Lyrics

Revised November 6, 2026. Slice 1 is done, and slices 2 to 4 have not been started. The server's real responses are still unverified, except for the `404`. See the open points at the end.

High-level design. No code in this document is a commitment to start the work. The point of this note is to decide what is feasible against the current player, which libraries are worth taking, and which problems belong to the architecture rather than to lyrics formats.

## Goal

While a song plays, the expanded player can show lyrics that follow playback. The user can turn this on and off. It is off by default.

The current line of a synced lyric always fills: a second color gradually covers the base color as time moves through the line, the way a karaoke system does. Two presentations use that same line:

- **Portrait: line scroll.** Neighboring lines are visible and dimmed. The list keeps the current line centered and moves when the line changes.
- **Landscape: focused line.** The current line is large. Previous and next lines sit above and below at a smaller size when there is room.

Orientation picks the presentation. It is not a second preference. `R.bool.landscape_player` already picks `FlatPlayerFragment` or `LandscapePlayerFragment`, and rotating recreates the activity because `AndroidManifest` does not set `configChanges`.

Unsynced lyrics (no timestamps) use a plain list in both orientations, with no fill and no auto-follow. A color sweep with nothing to sweep against is a fake.

## What the server gives us

The target server is Jellyfin 12.2. Older servers aren't a target, so nothing here works around their behavior. GitHub's comparison lists no lyric-named files changed between v12.2 and v12.4, so the latest release should behave the same.

The server serves lyrics at `GET /Audio/{itemId}/Lyrics`. A missing lyric is `404`. The body is `LyricDto`:

| Field | Meaning |
|---|---|
| `Lyrics[]` | Lines, sorted by start time when synced |
| `Lyrics[].Text` | The line. A timed line with no text is an instrumental break. |
| `Lyrics[].Start` | Start time in **ticks**, or null when the line is not synced. 1 ms = 10,000 ticks. Same unit as `positionTicks` in `PlaybackReporter` and `EventListener`. |
| `Lyrics[].Cues[]` | Optional word-or-slice timing inside the line. Each cue is a character range (`Position`, `EndPosition`) into `Text`, plus `Start` and optional `End` in ticks. Empty for ordinary LRC, and null for plain text. |

**Do not read `Metadata`.** `Offset` and `IsSynced` still exist in the DTO, but nothing fills them for local lyrics. In v12.2 the LRC parser returns `new LyricDto { Lyrics = lyricList }`, and the `[offset:]` tag line is dropped as an untimed line. jellyfin-web's lyrics page doesn't read `Offset` either. So a lyric is synced when every line has a `Start`, and there is no offset to apply.

v12.2 serves cues, so word-level fill needs no server work. It depends only on having enhanced LRC files. A missing or empty list falls back to a line-level fill.

### Where lyrics come from

The server reads the files, and the app sees one DTO whatever the source. This is from reading the v12.2 source. It hasn't been run against a real server yet.

- **Sidecar file.** A `.lrc`, `.elrc`, or `.txt` next to the audio file. This takes priority over tags.
- **Tags.** When a scan finds no sidecar, the server extracts embedded lyrics and saves them as `.lrc`, in the media folder or the internal metadata folder (the `SaveLyricsWithMedia` library option). Synced tags take priority over plain ones. Once extracted, a later edit to the tag isn't picked up without a full metadata replacement.
- **Plain text saved as `.lrc`.** The LRC parser drops untimed lines, finds nothing, and falls through to the text parser, which returns lines with a null `Start`. So unsynced tags arrive correctly as unsynced.
- **Repeated timestamps.** `[00:10.00][01:20.00]Chorus` becomes two lines, sorted into place, with no cues.
- **Word timing.** Enhanced LRC, such as `[00:12.00]<00:12.00>Every <00:12.50>night`, is the only source confirmed to produce cues. Whether word timing survives extraction from tags is unconfirmed.
- **Plugins.** `jellyfin-plugin-lrclib` fetches lyrics on the server, and the app sees them through the same endpoint. Check that it supports 12.2 before relying on it.

That is the contract to build on. Do not also call LrcLib, Musixmatch, or a scraper from the app. Those need a second identity, a second cache, and a policy for songs the server already has lyrics for. Remote search and upload exist on the server but require the lyric-management policy. They are library administration, not playback.

An open server PR (jellyfin/jellyfin#17011) adds TTML, YRC, KRC, syllable objects, and translation tracks. It is not merged. Do not block on it. A client model of "ordered lines, each with optional timed spans" can absorb syllables later without a second UI.

## Feasibility

Feasible, and it does not require a playback-stack change.

The clock is already there. `MusicPlayerRemote.getSongProgressMillis()` reads `MusicService`, which reads Media3 in-process. On the main thread `LocalPlayer.getProgress()` calls `exoPlayer.getCurrentPosition()` directly (`getOnMain` short-circuits). Position is per media item, and each item's `mediaId` is `song.id`. Gapless transitions stay valid as long as lyrics are keyed to the current item, not to a session-wide clock. Repeat-one stays on the same lyric. There is no user-facing playback speed, so the clock does not have to scale.

The data path fits the migration already in progress. The call goes through `JellyfinSdkSession.createApiOrNull()` and `lyricsApi.getLyrics(UUID)` in `jellyfin-core` 1.8.8. The jar's `LyricLine` has `text`, `start`, and `cues`. `LyricLineCue` has `position`, `endPosition`, `start`, and `end`.

What is not feasible as a drop-in:

- The progress views update once a second (`MusicProgressViewUpdateHelper`, `UPDATE_INTERVAL_PLAYING = 1000`). A fill driven by that timer steps, and a list driven by it skips short lines.
- `LocalPlayer` logs `onPositionDiscontinuity` and does not tell the UI. A seek from the session (`EventListener`) only shows up on the next poll, so lyrics have to read position themselves.
- Nothing in `Song`, `LocalSong`, or the queue stores lyrics. That is correct. Do not add them there (see *Cache and offline*).

## Library choice

No third-party lyrics view.

The usual Android LRC widgets (`lrcview` and its forks) parse LRC themselves, highlight a whole line, and scroll. They do not know Jellyfin ticks or `Cues`, and they do not follow the player's colors. Taking one means re-parsing a file the server has already parsed, then fighting the widget to get a real color sweep.

Media3's subtitle `Cue` and `SubtitleView` are the wrong surface. Sidecar and plugin lyrics are not in the stream the player opens, a transcode would drop them even if they were embedded, and the subtitle view is a caption strip. Injecting a text track into `LocalPlayer` would couple a UI feature to gapless queue rebuilds for no gain.

Parsing LRC in the app is also the wrong layer. The server already did it, including enhanced LRC.

The view is custom, and small:

- **`LyricLineView`.** One line, in either presentation. It holds a `StaticLayout`, draws it in the base color, then draws it again in the fill color with one `canvas.clipRect` for each visual line. This is what lets a wrapped line fill properly, so there's no need to refuse wrapping or to switch presentation in the middle of a song.
- **Line scroll.** A `RecyclerView` (already a dependency) of `LyricLineView` rows, with a center-snapping `LinearSmoothScroller` on line change. Every row has the same text size, so a line change doesn't reflow the list. The current row's position and fill mark it.
- **Focused line.** One large `LyricLineView`, with optional dimmer neighbors.

The fill is a fraction of the laid-out width, not of the character count. For each visual line in order, fill it completely if the filled width reaches past its end, and otherwise clip it partway. Clip from the start edge given by `getParagraphDirection`, so RTL fills the right way. Mixed-direction lines are approximate.

Colors are passed to the view, which has no color logic of its own. There's no new color system, and nothing reads `getAccentColor()`, which Material 3 Phase 4 removes.

## Where it sits in the player

There are two players, and they share little layout:

| Player | Layout | Lyrics host | Colors |
|---|---|---|---|
| `FlatPlayerFragment` | `layout/fragment_flat_player.xml` | The cover slot, `album_cover_container` | Palette background, text color from `ThemeUtil.prefersDarkContent` |
| `LandscapePlayerFragment` | `layout/fragment_landscape_player.xml` | The tab content, `landscape_player_tab_content` | White over the existing dark gradient |

One `LyricsController` owns the current song's lyric, the clock, and whether lyrics are showing. Both fragments reach it through `AbsPlayerFragment`. A shared `LyricsView` holds the two presentations, and each fragment supplies only its host and colors.

### Portrait

- **The cover slot.** Put `LyricsView` beside the cover fragment in `album_cover_container`. `WidthFitSquareLayout` measures itself from its width, so the panel height math in `setUpPanelAndAlbumCoverHeight` doesn't change.
- **Hide the cover with `INVISIBLE`, not `GONE`.** The palette color comes from the cover page's image load, and that must keep working while lyrics are up. Hiding the pager also removes swipe-to-change-song while lyrics are showing. That's acceptable, because previous and next are still on the controls. Do not overlay text on the art.
- **Background.** Paint the slot with the palette color, as a third target in `createDefaultColorChangeAnimatorSet`, so the status bar, slot, and controls read as one surface. Choose text color by the rule the controls use.
- **The toolbar.** It floats over the slot, so `LyricsView` has top padding of `toolbar_height`. The palette background still fills the slot, and the list is laid out and clipped below the toolbar, so lines never pass behind the icons. The top lines still sit over the lower part of the scrim. Tapping the cover can hide the toolbar, and the toolbar holds the lyrics toggle, so entering lyrics mode forces it visible.
- **Scrolling.** The outer panel's anti-drag view is `player_panel`, set in `setUpPanelAndAlbumCoverHeight`, and the slot is outside it. The list calls `requestDisallowInterceptTouchEvent` on `ACTION_DOWN` whenever it can scroll, which keeps the drag from reaching the panel. A handoff to the panel at the list's end doesn't work, because the panel's recorded initial motion would be stale. So a drag on the lyrics never closes the player, while the toolbar, the X and back still do.
- **Auto-follow.** A manual scroll pauses following for a few seconds. The next line change after that scrolls back.

### Landscape

- **Tall windows (h480dp, `landscape_player_tabs`).** A third tab: Up next, Lyrics, Details. `setUpTabs` becomes a three-way switch. `landscape_player_tab_content` is already the panel's anti-drag view, so scrolling is safe.
- **Short windows (most phones).** There are no tabs, so `menu_landscape_player.xml` gets a checkable Lyrics item, visible only then, as Details is. It swaps lyrics into the queue's slot, and turning it off brings the queue back. On a 1080x2400 phone at density 420 the slot measures 374x87dp.
- **Text size.** Slice 1 uses the same text size as portrait, which fits about two rows. A three-row line fits only with its edges faded. Fewer than 35 characters fit at 24sp in 374dp, so wrapping is normal. Autosize each line within a small range. A line that still overflows at the minimum is ellipsized, and it gets a line-level fill if its cues no longer land in the visible text.
- **Neighbors.** Only when the host has room. In a short window, show the current line alone.
- **The cover stays.** It keeps its pager and gestures, and the song stays recognizable.

### Toggle and preference

One preference, off by default, with no Settings entry. The player is where it's used.

- **Portrait.** A toolbar action in `menu_player.xml`, next to the favorite.
- **Landscape, short window.** The overflow item. Both this and the toolbar action are handled in `AbsPlayerFragment.onMenuItemClick`.
- **Landscape, tabs.** Selecting Lyrics turns the preference on, and selecting either other tab turns it off. The tab is restored from the preference when the fragment is created, so lyrics stay on across a rotation.

Nothing is fetched while the preference is off.

When the current song has no lyrics, or they haven't arrived yet, portrait keeps the cover and a short landscape window keeps the queue. Do not swap in an empty panel. The Lyrics tab is the exception: the user chose it, so it shows a short "no lyrics" message. Playback never waits on lyrics.

The mini player shows nothing. `onShow` and `onHide` already track the outer panel, and the clock runs only while the expanded player is visible and lyrics are showing. Notification, lock screen, widget, and the Media3 session do not carry lyrics.

## Clock

One clock for both presentations. Do not reuse `MusicProgressViewUpdateHelper` as the driver. The seek bar keeps its 1-second updates.

- **Playing, synced.** A `Choreographer` frame callback reads `getSongProgressMillis()` each frame, updates the current line if the position has left it, and invalidates only that line's view.
- **Paused, synced.** Poll about every 250 ms, so a seek made while paused still moves the lyric.
- **Unsynced.** No clock.
- **Also resample** on `onPlayMetadataChanged` and `onPlayStateChanged`.

Reading position directly avoids extrapolating between samples, which is what would lag behind a seek. Check two things on a device in the first slice: that the per-frame read is cheap, and that position advances smoothly enough to draw from. If it steps visibly, extrapolate from the last read instead. Do not add a position listener to `MusicService` for a UI feature.

Timing rules:

- Convert ticks to milliseconds once, with integer division by 10,000, when the lyric is loaded.
- Line lookup is a binary search on `Start`. Before the first line, nothing is current.
- A line ends at the next line's start, or at the song's duration for the last line. Plain LRC has no line end, so a line before a long instrumental break sweeps slowly across it. That's accepted for now.
- Timed lines with no text are real lines and must be kept, so the highlight can clear.
- A fetch is tied to a song id. Drop the result if `MusicPlayerRemote.getCurrentSong()` has moved on.

Word-level fill applies when the current line has cues whose character ranges fall inside `Text`:

- Find the cue that contains the position. Whitespace-only slices have no cue, so between cues the fill holds at the end of the previous one.
- The fill position is the character position interpolated between `cue.Position` and `cue.EndPosition` over the cue's time. If `End` is null, use the next line's start, or the duration.
- `Layout.getLineForOffset` and `getPrimaryHorizontal` turn that into a visual line and an x position, using the same layout the view draws. Earlier visual lines are fully filled.

If cues are missing, empty, or their indices do not land in `Text`, sweep the whole line linearly from its start to its end. In v12.2 the parser computes cue positions from the same string it returns as `Text`, so the indices should line up, but a range check is cheap and a bad file shouldn't break the fill. Do not try to re-trim on the client to "fix" the indices.

## Cache and offline

`Song` is a Room entity, a `Parcelable` copied through the queue, and compared by id. A lyric blob does not belong on it, and it does not belong on `LocalSong` either. Lyrics are large, optional, and useless to every list.

Cache the app's own normalized model, not the DTO or the original `.lrc`, so an SDK bump doesn't invalidate the files. Key it by account and item id, the same identity as `local_songs`. A JSON file at `<root>/<account>/lyrics/<itemId>.json`, through a new `LocalLibrary.getLyricsDir`, matches the "files keyed by item id" rule and needs no Room migration.

Every entry records when it was fetched:

- **Lyrics found.** Show the cached copy immediately. After about a week, refresh in the background when online, so a corrected lyric on the server eventually shows up.
- **`404`.** Store a negative entry that expires after a day, so a lyric added on the server shows up later.
- **Any other failure.** Store nothing. A network error must not look like a song with no lyrics, and it must not replace a good cached copy.

Fetch only when the preference is on and the player's fragment is resumed. Every open activity has its own player, and all of them hear the service's events, so without the second condition each screen on the back stack sends its own request. A fragment re-reads the preference and catches up on the song when it resumes. This also means songs that pass while the app is in the background aren't fetched.

There's no browsing network policy to share. `NetworkPolicy` covers streaming and saving, so follow `PlaybackReporter`: skip the call in offline mode, and otherwise let it try. Lyrics are a small JSON request, so a saved song can still pick them up on a network that isn't allowed to stream. Never fail playback because lyrics failed.

Saving a song does not download lyrics inside `SaveWorker`. The player fetch writes the cache as a side effect. A song that is saved and then played offline, without ever having been played online with lyrics on, has no lyrics. That is acceptable. Backfilling the whole local library is out of scope.

## Challenges that are actually architectural

1. **The cover pager is a transport control.** Hiding it while lyrics are up also hides swipe-to-change-song. Do not keep both gesture targets in the same rectangle.

2. **Nested panels own vertical drags.** The outer panel, the inner queue panel, and the lyrics list all want the same gesture in portrait. This is the main unknown in the first slice.

3. **Panel height is derived from the cover.** First version: same slot, same height. A lyrics view that wants "the rest of the screen" would reopen `setUpPanelAndAlbumCoverHeight`. Not worth it yet.

4. **The progress helper is the wrong clock, and seek is not an event.** Solved by reading position, not by a new `MusicService` callback. Do not add lyrics state to the session or the notification.

5. **Timestamp trust.** Ticks versus milliseconds is a one-line bug with a silent symptom (lyrics stuck at the first line, or finished immediately). Transcoded playback can also drift from the original file's timestamps, and v1 does not try to estimate that. A badly timed file has no server-side offset to help it. A per-song manual offset is a reasonable later addition, as a client-only adjustment.

6. **Word-level data is best-effort.** Standard LRC and plain text have no cues. The fill must look intentional either way. Linear line sweep is the normal case, not a degraded mode to apologize for.

7. **Accessibility.** Color alone cannot be the current-line signal. In the list, position carries it. The focused line should expose the current line as a content description updated on line change, not on every frame, so TalkBack doesn't re-announce continuously.

8. **Stale responses and gapless.** The queue can move on before the HTTP call returns. Key by `song.id` and ignore mismatches.

## Working alongside Material 3

This work runs in parallel with Phases 4–7 of `docs/material3_migration_plan.md`. Neither blocks the other, but they touch some of the same files:

- **Colors (Phase 4).** Lyrics never reads `getAccentColor()` or `getPrimaryColor()`. Both players use palette or fixed colors, so removing those calls doesn't affect it.
- **Toolbar (Phase 5).** `@style/Toolbar` loses its dark overlay. The player toolbar's icons, including the new lyrics action, need checking on the palette background afterward.
- **Widgets (Phase 6).** New layouts use `?attr/selectableItemBackground` and `?attr/selectableItemBackgroundBorderless`, not `roundSelector` or `rectSelector`, and don't add `ImageButton`, `iconColor`, or `MaterialProgressBar` uses that Phase 6 would have to convert.
- **Merges.** `fragment_flat_player.xml`, `fragment_landscape_player.xml`, and `LandscapePlayerFragment` are the likely conflict points. Keep lyrics changes to them small, with the logic in the controller and the view.

## Suggested order

The feature can land in slices that each leave playback alone.

1. **Data and scroll.** SDK fetch, tick conversion, file cache, negative cache, `LyricsController`, the toggle, and the list in both players, with the current line highlighted but not yet filled. Includes the portrait gesture work. This proves the data path and the cache without the custom draw path.

2. **Line-level fill.** `LyricLineView` with per-visual-line clipping and the frame clock, in the portrait list and the landscape focused line. Autosize and neighbors. Includes the device check on the per-frame read.

3. **Word-level fill.** Cue-level position in the same view. Do this only once an enhanced LRC file is in hand to test against, and only if the library actually has such files.

4. **Polish, only if the first slices are used.** Next-track prefetch so a gapless transition doesn't flash the cover, a manual offset, tap-to-seek on a line, and a TalkBack pass.

## Out of scope

- Uploading, deleting, or searching remote lyrics from the app.
- A client-side LrcLib (or any other) provider.
- Parsing TTML, YRC, KRC, or syllable tracks until the server actually serves them.
- Lyrics on the mini player, notification, lock screen, widget, or in `MusicService`.
- Editing timestamps, and writing an offset back to the server.
- Backfilling lyrics for every saved song.
- Changing the Media3 session. The session migration stays a separate plan.

## Open points to settle before implementation

**Test data**, which only needs the server's admin access. The server is 12.2, and no LRC files have been collected yet:

- Find or make LRC files. A hand-written `.lrc` for one song is enough to start.
- Put together a few test songs:
  - A `.lrc` sidecar with the same base name as the audio file.
  - A file with synced lyrics in its tags.
  - A file with plain lyrics in its tags.
  - A `.txt` sidecar, or any song with no lyrics, to check the unsynced and `404` paths.
  - One hand-made `.elrc` with word timing, only if slice 3 is wanted.
- Rescan the library, then confirm what `GET /Audio/{itemId}/Lyrics` returns for each one. This checks the source-reading conclusions above, including that `Metadata` is empty.

Slice 1 was tested on an emulator against the real 12.2 server through a local proxy. It answered the lyrics endpoint with responses written by hand from this document, so a real server has only confirmed the `404`. Nothing yet shows that a real server parses LRC files into that shape.

**On a device, during slice 2:**

- Whether a per-frame position read is cheap and smooth enough.

**On a real phone, not yet checked:**

- How the list feels to scroll by finger. The emulator tests used injected swipes.
