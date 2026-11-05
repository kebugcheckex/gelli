# Large-screen layout

Written November 4, 2026. Phases 1–4 are implemented and need checking on a device.

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

### As implemented

- **One layout menu instead of two controls.** Each grid-capable tab (Albums, Songs, Artists, Album Artists) stores one `LibraryLayout` preference: `LIST`, `GRID_SMALL`, `GRID_MEDIUM`, or `GRID_LARGE`. The overflow menu's **Layout** submenu offers those four. Albums default to a medium grid, and the other tabs default to a list. The cell widths are the `grid_cell_*` dimens.
- **Column count comes from the measured width.** The span is first estimated from `screenWidthDp`, then recomputed from the RecyclerView's real width whenever that width changes. Phase 2 needs this because the content area becomes narrower than the window. In two-column list mode, the Songs shuffle row spans both columns.
- **Content-width cap.** `ViewUtil.capContentWidth()` grows a scrolling view's horizontal padding so its content stays within `max_content_width` (840dp), centered. The margins can still be scrolled. It's applied to the Genres and Playlists tabs, the album, artist, genre, and playlist tracklists, Search, Settings, About, and Downloads.
- **Player layout.** `FlatPlayerFragment` and `CardPlayerFragment` now pick `LandscapeImpl` based on whether the inflated layout has `cover_container`, not on `orientation`. The side-by-side layout itself still comes from `layout-land`. On Android N and later, that qualifier follows the window's aspect ratio, including in multi-window, so it answers "is the window wider than it is tall?". Phase 2's `pane` argument still has to override it.
- **Not changed:** `values-sw600dp/dimens.xml` (taller detail headers) and `values-land/` (shorter headers in wide, short windows). Phase 3 reworks those headers anyway.

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

### As implemented

- **Shell.** `layout-w840dp/sliding_music_panel_layout.xml` is a horizontal `LinearLayout`: `content_container` with weight 1, then `now_playing_pane` (a 1dp `dividerColor` line and `player_fragment_container`), `now_playing_pane_width` (360dp) wide. View binding requires every configuration to share the root ID, so the compact layout's root no longer has the `sliding_layout` ID. `AbsMusicPanelActivity` keeps the root in a nullable `slidingLayout` field, and `isPaneMode()` is true when that field is null.
- **Pane visibility.** The pane starts `invisible`, so the player measures itself during the first layout pass. Once that pass has run and the queue is known, `hideBottomBar()` sets the pane to `gone` (empty queue) or `visible`, and calls `playerFragment.onShow()` when it shows. When the pane is gone, the content takes the full width.
- **Panel API.** In pane mode, `expandPanel()`, `collapsePanel()`, and `setAntiDragView()` do nothing, and `getPanelState()` reports `EXPANDED`. `onPanelExpanded()` and `onPanelCollapsed()` are never called, so `MainActivity` never locks the drawer. The status bar, navigation bar, and task colors always follow the content (`contentOwnsSystemBars()`). The palette color only appears inside the pane.
- **Player layout: resource qualifier, not a `pane` argument.** `layout-w840dp/fragment_flat_player.xml` and `fragment_card_player.xml` are copies of the portrait layouts. Width qualifiers take precedence over `land`, so a landscape window that's 840dp or wider gets the portrait arrangement in the pane. Since the shell and the player layout use the same qualifier, they can't disagree. The cost is two duplicated layouts that have to be kept in sync with `layout/`. Forcing the layout through a fragment argument would have meant inflating with an overridden-configuration context, which also affects child fragments and view contexts.
- **Queue.** The portrait player's inner queue `SlidingUpPanelLayout` is unchanged. Check on a device whether a plain list under the controls works better in a tall pane.
- **Player toolbar.** In the pane, the player has no close button (`AbsPlayerFragment.isInPane()`).
- **Back.** In pane mode, Back goes straight to the normal activity behavior. It doesn't collapse the queue or the panel. `MainActivity` still closes the drawer and the CAB first.
- **Not done:** no window-inset handling for the pane beyond what the player layouts already do (`StatusBarView`). A side navigation bar or a cutout on the right edge would overlap the pane, just as it overlaps the sliding panel today.

## Phase 3: Detail screens at expanded width

Keep these as activities. Add `layout-w840dp` versions of the album, artist, and playlist detail layouts: cover, metadata, and actions in a left column, and the tracklist on the right. Together with the Phase 2 pane, this gives a three-column layout on very wide windows. Decide on a device whether the detail two-column layout should apply only when the pane is hidden or the window is wider than about 1200dp.

### As implemented

- **Threshold: `layout-w1200dp`.** At 840dp, the visible pane leaves the content only about 480dp, which is too narrow for two columns. A resource qualifier can't tell whether the pane is showing. Switching layouts when the queue empties or fills would also rebuild the screen under the user. So the two-column layout depends only on window width. At 1200dp, the content area is about 840dp beside the pane. Most 10–12" tablets in landscape are about 1280dp wide and get it. Unfolded foldables and portrait tablets keep the phone layout. To change the threshold, rename the directory.
- **Album and artist.** `layout-w1200dp/activity_album_detail.xml` and `activity_artist_detail.xml` replace the `CoordinatorLayout`/`AppBarLayout` collapsing header with a fixed `detail_side_column_width` (320dp) column on the left. The column holds a square cover and the metadata rows, and scrolls on its own if it's too tall. The tracklist is on the right. For the artist screen, the right side is the existing `InceptionScrollView` with the horizontal album row and the songs. The left column keeps the `header`, `image`, and transition names, so shared-element transitions and palette coloring work unchanged.
- **Code.** View binding makes `appBarLayout` nullable because the wide layouts don't have it. When it's null, the activities skip the offset listener (no header fade) and put the palette color on `header` instead. The compact layout still colors only the app bar. Coloring the elevated header there would add a second shadow.
- **Playlist and genre: no change.** Those screens have no header, only a toolbar and a list that `capContentWidth()` already centers, so a left column would have nothing to show.
- **Header dimens.** `values-sw600dp/dimens.xml` is now `values-h600dp/dimens.xml`. The override only existed to undo the `values-land` short header on tablets in landscape, and that's really a question of window height. A tablet split top/bottom now gets the short header, and a landscape tablet still gets the tall one.

## Phase 4: Navigation

The drawer holds library switching, Settings, About, and Logout. The real top-level destinations are the `TabLayout` tabs, and tabs already work on tablets. Only do this phase if the drawer becomes a problem in use.

- **Cheap version:** a permanent `NavigationView` at w840dp, using an alternate `activity_main_drawer_layout.xml` with a `LinearLayout` in place of `DrawerLayout`, and null checks on `drawerLayout` in `MainActivity`.
- **Material version:** a `NavigationRail` for the library categories, replacing the tabs and `ViewPager`. This is a bigger change because the categories can be configured (`CategoryPreferenceDialog`).

### As implemented

This shipped as the Material version, together with a restyle of the expanded layout (option A in the landscape mocks).

- **Material 3 theme.** `parents.xml` now builds on `Theme.Material3` with neutral grey color roles. The toolbar, tab, and app bar styles and the 56dp `actionBarSize` are pinned to the old widgets so phones keep their look. Popups, buttons, and the drawer pick up M3 styling everywhere.
- **Navigation rail.** `layout-w840dp/fragment_library.xml` replaces the tabs with a `NavigationRailView`. Its items are rebuilt from the configured categories (each `Category` now has an icon) and drive the `ViewPager`. The rail header has a menu button that opens the existing drawer and a shuffle button. The top bar is neutral and shows the current category, and the status bar uses the surface color.
- **Grid tiles.** `layout-w840dp/item_grid.xml` is a rounded cover with the text below and no palette footer, so the colored-footers option is hidden at this width.
- **Now-playing pane.** `layout-w840dp/fragment_flat_player.xml` is a plain stack on the surface: cover, title, controls, and the queue. `FlatPlayerFragment.PaneImpl` shows the palette color only as a fading wash at the top. The controls use the accent color for the seek bar and a filled play button. The pane always uses the flat player, and the w840dp card player layout is gone.

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
