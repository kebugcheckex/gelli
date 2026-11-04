# Large-screen layout

Plan only. Written November 4, 2026. No implementation has started.

## Goal

Make Gelli work well on tablets, foldables, split-screen, and freeform windows. Today these windows get the phone layout, made bigger. The app should adapt to the **window width**, not to the device or its orientation.

## Current state

The app runs on large screens. It doesn't lock orientation, and it doesn't declare `configChanges`, so resizing and split-screen go through ordinary activity recreation. The only large-screen handling is:

| Where | What it does |
|---|---|
| `res/values-sw600dp/integers.xml` | More grid and list columns: default grid 4 in portrait and 6 in landscape (phone: 2 and 4), max 6 and 8 (phone: 4 and 6) |
| `res/values-sw600dp/dimens.xml` | Taller detail headers |
| `res/layout-land/fragment_flat_player.xml`, `fragment_card_player.xml` | Player with the cover on the left and controls and queue on the right |
| `Util.isTablet()` | Checks `smallestScreenWidthDp >= 600`. Nothing calls it. |

Three structural problems:

1. **Orientation is used as a stand-in for size.** `Util.isLandscape()` chooses the grid size in `AbsLibraryPagerRecyclerViewCustomGridSizeFragment`. It also picks `LandscapeImpl` or `PortraitImpl` in `FlatPlayerFragment` and `CardPlayerFragment`. Grid preferences are stored per orientation (`ALBUM_GRID_SIZE` and `ALBUM_GRID_SIZE_LAND`, and the same pair for songs and artists). A phone in landscape, a tablet in portrait, and a half-screen split window are all about 600–900dp wide, but each gets handled differently.
2. **The now-playing screen covers everything.** `AbsMusicPanelActivity` wraps every content activity in the umano `SlidingUpPanelLayout` (`sliding_music_panel_layout.xml`), and the expanded player fills the window. On a landscape tablet, you can't browse while you see the queue.
3. **Detail screens are separate activities.** `AlbumDetailActivity`, `ArtistDetailActivity`, `GenreDetailActivity`, `PlaylistDetailActivity`, and `SearchActivity` all extend `AbsMusicContentActivity`, so each one has its own player panel. A list-detail layout would need them to become fragments. On the other hand, a change to the shell in `AbsMusicPanelActivity` reaches every screen at once.

## Breakpoints

Use the Material window size classes, measured on the window's current width:

| Class | Width | Layout |
|---|---|---|
| Compact | < 600dp | Today's phone layout |
| Medium | 600–839dp | Phone shell; width-based grids; content-width cap; landscape player when expanded |
| Expanded | ≥ 840dp | Persistent now-playing pane beside the content |

Use width resource qualifiers (`values-w600dp`, `layout-w840dp`) and `Configuration.screenWidthDp` in code. Don't use `sw600dp` for layout decisions: it describes the device, not the window, so it's wrong in split-screen. Don't add `androidx.window` for now. It's only needed for hinge or posture awareness on foldables.

## Phase 1: Size by width, not orientation

Most of the stretched look comes from this phase, and it's the smallest change.

1. **Width-based grid spans.** In `AbsLibraryPagerRecyclerViewCustomGridSizeFragment`, compute `span = max(1, availableWidthDp / targetCellDp)`. The target cell width comes from a new **cell size** preference (small / medium / large, roughly 128 / 168 / 216dp; tune on a device). This preference replaces the per-orientation column-count preferences.
2. **Remove orientation-keyed state.** Delete the `*_GRID_SIZE_LAND` preferences, the `default_*_columns_land` and `max_columns*` integers, and the `values-sw600dp/integers.xml` file. Reduce the grid-size menu in `LibraryFragment` to the three cell sizes. Backward compatibility isn't a goal: the old preferences are simply dropped.
3. **List vs. grid.** The current code switches between list and grid layout when `getGridSize() > getMaxGridSizeForList()`. Make this an explicit list/grid toggle per tab. In list mode, the column count follows width as well: 1 column below 840dp and 2 at 840dp or wider.
4. **Content-width cap.** Single-column lists, detail tracklists, Settings, and About get a maximum width of about 840dp, centered, with a full-width background. This can be a `max_content_width` dimen applied by a small wrapper view, or horizontal padding computed from `screenWidthDp`.
5. **Replace `Util.isLandscape()` checks.** Each remaining call site asks a size question ("is there room for side-by-side?"). Replace each one with a width or aspect check, and remove `Util.isTablet()`.

Done when: the grid looks the same density on a phone, in split-screen, and on a 12" tablet; rotating keeps the same cell size; no layout decision reads `orientation`.

## Phase 2: Persistent now-playing pane at expanded width

This is the main tablet feature: library on the left, now playing and queue in a fixed column (about 360–400dp) on the right.

1. **Shell layout.** Add `layout-w840dp/sliding_music_panel_layout.xml`. It's a horizontal layout with `content_container`, a divider, and `player_fragment_container`, and has no `SlidingUpPanelLayout` and no mini player.
2. **`AbsMusicPanelActivity` pane mode.** Branch on whether the binding has `slidingLayout`. In pane mode:
   - call `playerFragment.onShow()` once and keep menu visibility on
   - `expandPanel()` and `collapsePanel()` do nothing, and `getPanelState()` reports expanded
   - `hideBottomBar()` hides or shows the whole pane depending on whether the queue is empty, and the content takes the full width when the pane is hidden
   - `setAntiDragView()` does nothing
   - drop the panel-slide color animation. Use a fixed status bar and navigation bar policy (the content's colors), and apply the palette color only inside the pane.
3. **Player fragments in a narrow pane.** A 400dp pane on a landscape tablet still resolves to `layout-land`, and the player fragments pick `LandscapeImpl` from orientation. Pass a `pane` fragment argument that forces the portrait-style layout and implementation, independent of resource qualifiers. Check that the inner queue `SlidingUpPanelLayout` in `fragment_flat_player.xml` and `fragment_card_player.xml` still behaves at pane height. In a tall pane it may be better to show the queue as a plain list under the controls.
4. **Back handling.** `AbsMusicPanelActivity.onBackPressed()` first lets the player fragment collapse its inner queue panel, then collapses the main panel if it's expanded, and only then finishes the activity. In pane mode, `getPanelState()` reports expanded and `collapsePanel()` does nothing, so with no change Back would never leave the screen. Pane mode must skip the panel step. It should also not use Back to collapse the queue inside the pane. `MainActivity.onBackPressed()` closes the drawer and the CAB first, and that part stays the same.
5. **Detail activities inherit it.** All `AbsMusicContentActivity` subclasses get the pane without per-screen changes. Check that the album and artist detail headers and the CAB still lay out correctly in the narrower content area.

Done when: on a landscape tablet and in a ≥840dp freeform window, you can browse, open detail screens, and search while the player and queue stay visible; resizing below 840dp goes back to the sliding panel with playback state intact.

## Phase 3: Detail screens at expanded width

Keep these as activities. Add `layout-w840dp` versions of the album, artist, and playlist detail layouts: cover, metadata, and actions in a left column, and the tracklist on the right. Together with the Phase 2 pane, this gives a three-column layout on very wide windows. Decide on a device whether the detail two-column layout should apply only when the pane is hidden or the window is wider than about 1200dp.

## Phase 4: Navigation (optional)

The drawer holds library switching, Settings, About, and Logout. The real top-level destinations are the `TabLayout` tabs, and tabs already work on tablets. Only do this phase if the drawer becomes a problem in use.

- **Cheap version:** a permanent `NavigationView` at w840dp, using an alternate `activity_main_drawer_layout.xml` with a `LinearLayout` in place of `DrawerLayout`, and null checks on `drawerLayout` in `MainActivity`.
- **Material version:** a `NavigationRail` for the library categories, replacing the tabs and `ViewPager`. This is a bigger change because the categories can be configured (`CategoryPreferenceDialog`).

## Not planned: Fragment-based list-detail

Opening an album in a pane next to the album grid would mean turning the detail activities into fragments hosted by `MainActivity`. That would lose the shared-element activity transitions in `NavigationUtil` and require reworking `CabHolder`, palette coloring, and per-activity menus. Phase 3 gets most of the benefit for a fraction of the work. Revisit after Phases 1–3 have been used on a device.

## Related decisions

- **The umano `SlidingUpPanelLayout`** is unmaintained. Phase 2 adds a code path that doesn't use it, which makes it easier to move the compact path to `BottomSheetBehavior` later. That's a separate change, not a requirement for this plan.
- **targetSdk 36:** Android 16 ignores orientation and resizability locks on large screens for apps that target API 36. Gelli doesn't use any, so raising `targetSdk` from 35 doesn't break anything here.
- **Edge-to-edge:** targetSdk 35 already forces edge-to-edge. The new pane and divider must respect window insets the same way the current `StatusBarView` / `status_bar` layouts do.

## Validation

There are no automated UI tests, so validation is on devices or emulators:

- Phone portrait and landscape (compact and medium)
- 10–11" tablet portrait (medium) and landscape (expanded)
- Split-screen at 1/3, 1/2, and 2/3 on a tablet, plus resizing across the 600dp and 840dp boundaries while music plays
- A foldable emulator, folded and unfolded, while music plays
- Both now-playing screens (Flat and Card), light and dark themes, large font scale
- Queue drag-to-reorder in the pane, the CAB on detail screens, and Back at each size class
