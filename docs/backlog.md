# Backlog

Unfinished work, known issues, and ideas. The finished plans (large-screen layout, Material 3, Media3, the local library, and live lyrics) were removed from `docs/`, and git history keeps them. Bigger designs that haven't been built yet have their own documents:

- `media3_session_migration_plan.md`: moving from `MediaSessionCompat` to a Media3 `MediaSessionService`. Not started.
- `downloads_storage_design.md`: the rest of the Downloads & storage screen, storage cleanup, and per-artist coverage.

## Bugs

### Current song row in the Flat player's queue has no download icon

In the Flat player, the row for the current song at the top of the queue panel never shows the stored or missing icon, and is never dimmed for a missing song. Saving the song while it plays doesn't update it either. The rows for the songs after it show the icon correctly.

**Cause:** The current song row is a separate `current_song` view (`<include layout="@layout/item_list"/>` in `fragment_flat_player.xml`), not a row of the queue list. The layout is shared, but the binding code is not:

- Queue rows are bound by `SongAdapter.onBindViewHolder()`, which sets the title, text, availability icon and missing alpha, and refreshes through a `LocalLibrary` listener.
- The current song row is bound by `updateCurrentSong()` in `FlatPlayerFragment`, which sets only the title and text and doesn't listen to the library.

**Proposed fix:** Move the per-song binding (availability icon, missing alpha) into one method, such as `MediaEntryViewHolder.bindAvailability(Song)`, and call it from both places. Register a `LocalLibrary.Listener` in the player fragment while its view exists, and re-bind the row when the library changes. Making the current song a pinned row inside the queue list was rejected as too invasive.

## Lyrics

### Word-level fill

Cues are already fetched, cached, and modeled (`Lyrics.Cue`), but `LyricLineView` only sweeps whole lines. This waits for an enhanced LRC file (`.elrc`, such as `[00:12.00]<00:12.00>Every <00:12.50>night`) to test with. None of the server's 19 test songs has cues.

- Find the cue that contains the position. Whitespace-only slices have no cue, so between cues the fill holds at the end of the previous one.
- Interpolate a character position between `position` and `endPosition` over the cue's time. If `endMs` is null, use the next line's start, or the duration.
- Turn it into a visual line and x position with `Layout.getLineForOffset` and `getPrimaryHorizontal`, on the layout the view draws. Earlier visual lines are fully filled.
- If cues are missing, or their indices don't land in the text, fall back to the line sweep. Don't re-trim the text on the client to "fix" the indices.
- An autosized landscape line that is ellipsized also falls back to the line sweep when its cues no longer land in the visible text.

### Polish

- Prefetch the next song's lyrics, so a gapless transition doesn't flash the cover.
- A per-song manual offset, kept on the client. The server's `[offset:]` tag is dropped by its parser.
- Tap a line to seek to it.
- TalkBack: the landscape focused line should expose the current line as its content description, updated on line change rather than every frame. Color can't be the only current-line signal.
- Plain LRC has no line end, so a line before a long instrumental break sweeps slowly across the break. Accepted so far.

### Unchecked

- How the portrait list feels when scrolled by finger. The tests used injected swipes.
- Which source each test song's lyrics came from (sidecar or tags), and whether word timing survives extraction from tags.
- Whether `jellyfin-plugin-lrclib` supports Jellyfin 12.2.

## Layout and theme

- **Edge-to-edge.** With `targetSdk 35`, Android 15 forces edge-to-edge and ignores the status and navigation bar colors. `StatusBarView` covers the status bar today. With 3-button navigation, the system draws its own scrim: on the Flat player in the light theme that's a grey scrim with dark icons, at about 3:1 contrast. A side navigation bar or a cutout on the right edge overlaps the queue sheet. Proper inset handling needs its own plan.
- **`SlidingUpPanelLayout`.** The umano library is unmaintained and is the shell for every content activity. `BottomSheetBehavior` is the closest Material equivalent, but switching means rewriting the shell.
- **Wallpaper shortcut icons.** With the Wallpaper preset, the colored app shortcuts keep their colors after a wallpaper change, until the preset or theme changes in Settings.
- **Now-playing palette color.** The Flat and landscape players still use the album color. This could be revisited now that the rest of the app is on the surface.
- **Player bar and queue sheet extras.** A volume slider and a History / Up next switch in the sheet were left out.
- **"From the album".** The landscape player always shows the current song's album, because the queue doesn't record where it was started from (a playlist, an artist, or a shuffle).
- **Fragment-based list-detail.** Opening an album in a pane beside the album grid would turn the detail activities into fragments hosted by `MainActivity`, losing the shared-element transitions and needing rework of multi-select and per-activity menus. Revisit only if the two-column detail layouts at 1200dp fall short.

## Local library and offline

- **Offline browsing.** The manual offline mode stops streaming and saving, but the library screens still only query the server, and `AbsMusicContentActivity.onStateOffline()` does nothing. Planned: a data source seam in the library fragments, so offline Songs, then Albums, then Artists read `local_songs` (Genres and Playlists need extra metadata). Hide or disable network-only actions (search, add to playlist, favorite) while offline.
- **Notification permission.** The manifest doesn't request `POST_NOTIFICATIONS`, so on Android 13 and later the save progress notification is hidden. Saving still works.
- **Automatic offline detection.** Telling an unreachable server from a missing network or an auth failure, and switching modes by itself. Needs its own design.
- **Offline play reports and favorites.** They are dropped in offline mode. Queue them and sync later.
- **Server-side removals and changes.** Items deleted on the server should leave the local library lists and go into a separate log (such as an "expired" list) rather than vanish. How to detect removals, whether to keep the local file, and how to handle retagged items (`serverDateModified`) still need design.
- **Double fetch on first play.** A played song is streamed and saved in parallel. Saving the stream's bytes while playing would avoid that for direct-play songs played start to finish.
- **Smaller ideas.** A custom library root (never a cache directory), exporting stored songs to MediaStore for other apps, and sharing stored files across accounts on the same server.

## Playback

- **Session migration.** See `media3_session_migration_plan.md`.
- **Release build.** A signed `assembleRelease` and a runtime test of the R8-shrunk app have never been done. Release signing isn't configured, and the app isn't published, so this is low priority.

## Device checks never recorded

These were planned but have no recorded result:

- Color presets on Android 14 and below. Only Android 15 and 16 emulators were used, and the path where Wallpaper is unavailable is only covered by `ColorPresetTest`.
- Drag-to-reorder in the queue sheet and the landscape Up next tab, and rotation while playing.
- Split-screen at 1/3, 1/2, and 2/3, resizing across 600dp and 840dp while music plays, a foldable folded and unfolded, and a large font scale.
- The playback regression scenarios listed in `media3_session_migration_plan.md`, beyond basic playback and queue modes.
- The local library test matrix: kill the app mid-download, each save trigger, unmetered-only saving on cellular, a file deleted outside the app, and two accounts.
