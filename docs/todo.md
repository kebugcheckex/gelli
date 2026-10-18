# To Do

Known issues and deferred work that is not part of a plan document.

## Current song row in the player queue has no download icon

**Bug:** In the Flat and Card players, the row for the current song at the top of the queue panel never shows the stored or missing icon, and is never dimmed for a missing song. Saving the song while it plays doesn't update it either. The rows for the songs after it show the icon correctly.

**Cause:** The current song row is a separate `current_song` view (`<include layout="@layout/item_list"/>` in `fragment_flat_player.xml` and `fragment_card_player.xml`), not a row of the queue list. The queue list is scrolled to start at the next song, so the current song's own row is hidden above it. The layout is shared, but the binding code is not:

- Queue rows are bound by `SongAdapter.onBindViewHolder()`, which sets the title, text, availability icon and missing alpha, and refreshes through a `LocalLibrary` listener.
- The current song row is bound by `updateCurrentSong()`, duplicated in `FlatPlayerFragment` and `CardPlayerFragment`, which sets only the title and text and doesn't listen to the library.

**Proposed fix:** Keep the shared layout and share the binding.

- Move the per-song binding (availability icon, missing alpha) into one method, e.g. `MediaEntryViewHolder.bindAvailability(Song)` or a static helper next to `SongAdapter`, and call it from `SongAdapter.onBindViewHolder()` and both `updateCurrentSong()` methods.
- Register a `LocalLibrary.Listener` in each player fragment while its view exists, and re-bind the current song row when the library changes.

Making the current song a pinned row inside the queue list would remove the second binding path entirely. It was rejected as too invasive (pinned row, drag-and-drop interaction, scroll offsets in both fragments).
