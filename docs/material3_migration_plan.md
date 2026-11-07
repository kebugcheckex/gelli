# Material 3 migration

Written November 5, 2026. All phases and the cleanup are done. The phone chrome moved out of Phase 5 and came before the presets, so each preset was checked on the final surfaces.

## Goal

Bring the phone layout and every remaining legacy component onto Material 3, so the whole app matches the expanded layout from the large-screen work (`docs/large_screen_layout_plan.md`).

Decisions already made:

- **Color comes from a preset, not a color wheel.** The separate primary and accent colors become a single color preset. Each preset is a Material 3 tonal scheme generated from one seed color. On Android 12 and later, a wallpaper option uses dynamic color.
- **No album-color backgrounds in the library or on detail screens.** Grid tiles lose the palette footer, and album and artist headers sit on the surface. The now-playing screens keep their palette color (see *Not changed*).
- **The Card now-playing screen is removed.** Flat is the only phone player, so the now-playing screen preference goes away too.

Backward compatibility isn't a goal. The old color, footer, and now-playing preferences are simply dropped.

## Starting state

This section records the app as it was when the plan was written. The app theme already builds on `Theme.Material3` (`res/values/parents.xml`). Its color roles are neutral greys, and the user's colors are applied at runtime. The theme's view inflater already turns plain `<Button>`, `<CheckBox>`, and `<TextView>` tags into their Material versions. Four theme items hold the phone look in place:

```xml
<item name="actionBarSize">@dimen/toolbar_height</item>                <!-- 56dp -->
<item name="toolbarStyle">@style/Widget.AppCompat.Toolbar</item>
<item name="tabStyle">@style/Widget.Design.TabLayout</item>
<item name="appBarLayoutStyle">@style/Widget.Design.AppBarLayout</item>
```

What's still legacy:

| Area | Today | Where |
|---|---|---|
| Dialogs | `com.afollestad.material-dialogs` 0.9.6 (`core` and `commons`) | 13 dialogs, listed in Phase 2 |
| Multi-select bar | `com.afollestad:material-cab` 2.0.1 | `AbsMultiSelectAdapter.kt`, `CabHolder`, `MainActivity`, and the album, artist, genre, and playlist detail activities |
| Colors | `PRIMARY_COLOR` and `ACCENT_COLOR` preferences picked with the QuadFlask color wheel (`ColorPreference`), applied by hand in more than 15 places | See Phase 4 |
| Phone toolbar, tabs, and app bar | Pinned old styles, primary-color background, `Toolbar` style with a dark action bar overlay and a 4dp shadow | 14 layouts use `@style/Toolbar` |
| Status bar | Primary color darkened by 20% (`ThemeUtil.getColorDark`), drawn by `StatusBarView` | `AbsThemeActivity`, `AbsMusicPanelActivity`. `MainActivity` overrides this at expanded width. |
| Grid tiles | Square cover with a palette-colored footer (`palette_color_container`) | `layout/item_grid.xml`, `layout/item_grid_card_horizontal.xml` |
| Detail headers | Palette color on the app bar, status bar, and CAB | `AlbumDetailActivity`, `ArtistDetailActivity` |
| Now-playing screens | Card and Flat, chosen in Settings through a `CircleIndicator` pager | `fragments/player/card/`, `NowPlayingPreferenceDialog` |
| Progress indicators | `me.zhanghai.android.materialprogressbar` and plain `ProgressBar` | Mini player, flat player controls, splash, downloads, landscape player |
| Sleep timer | `com.github.kabouzeid:SeekArc` | `dialog_sleep_timer.xml`, `SleepTimerDialog` |
| Cards | `androidx.cardview.CardView` | About cards, downloads, horizontal album row |
| Buttons | 8 explicit `AppCompatButton`s (About's Special Thanks card) and `ImageButton`s in the player | |
| Settings | androidx.preference with framework `SwitchPreference` and mismatched overlays (`PreferenceThemeOverlay` in dark themes, `PreferenceThemeOverlay.v14.Material` in light) | `res/xml/pref_*.xml` |
| Login screen | `LoginScreen` style on `Theme.MaterialComponents.Light.DarkActionBar` | `styles.xml` |

## Phase 1: Remove the Card player

Do this first, so the later phases only have one phone player to migrate.

1. Delete `fragments/player/card/` (`CardPlayerFragment`, `CardPlayerPlaybackControlsFragment`) and `layout/fragment_card_player.xml` and `fragment_card_player_playback_controls.xml`.
2. `AbsMusicPanelActivity` always creates `FlatPlayerFragment` (or `LandscapePlayerFragment`). It implements `CardPlayerFragment.Callbacks` today. That interface is declared in `AbsPlayerFragment`, so name it there. Drop `currentNowPlayingScreen` and the recreate check in `onResume`.
3. Delete `NowPlayingScreen`, `NowPlayingPreferenceDialog`, `preference_dialog_now_playing.xml`, `preference_dialog_now_playing_item.xml`, `np_card.png`, and `np_flat.png`.
4. `pref_now_playing.xml` only holds this preference, so delete it along with its `addPreferencesFromResource` call and the `NOW_PLAYING_SCREEN` handling in `SettingsActivity`. Delete `NOW_PLAYING_SCREEN`, `getNowPlayingScreen()`, and `setNowPlayingScreen()` from `PreferenceUtil`.
5. Remove the `me.relex:circleindicator` dependency and the unused strings (`card`, `flat`, `pref_header_now_playing_screen`, and the preference title and summary).

Leave `drawable/card.xml` and the app widget named "Card". They belong to the home-screen widgets, not the player.

Done when: the app builds without `fragments/player/card/`, the phone player is always Flat, and Settings has no now-playing section.

## Phase 2: Dialogs

Replace `MaterialDialog.Builder` with `MaterialAlertDialogBuilder` inside the existing `DialogFragment`s. The fragments and their `create()` factories stay the same.

| Dialog | Uses | Replacement |
|---|---|---|
| `ConfirmLogoutDialog`, `DeletePlaylistDialog`, `RemoveFromDeviceDialog`, `RemoveFromPlaylistDialog` | content, positive, negative | `setMessage`, `setPositiveButton`, `setNegativeButton` |
| `AddToPlaylistDialog`, `SongShareDialog` | `items` with `itemsCallback` | `setItems` |
| `CreatePlaylistDialog`, `RenamePlaylistDialog` | `input`, `inputType` | A new `dialog_text_input.xml` with a `TextInputLayout` (`Widget.Material3.TextInputLayout.OutlinedBox`), passed with `setView`. Enable the positive button only when the text isn't empty, as `input` did. |
| `SongDetailDialog` | `customView`, positive | `setView` |
| `SleepTimerDialog` | `customView`, positive, neutral (cancel timer), show listener | `setView`, `setNeutralButton`, `setOnShowListener` on the created dialog. The arc becomes a slider in Phase 6. |
| `CategoryPreferenceDialog` | `customView`, `autoDismiss(false)`, neutral (reset) | `setView`. `AlertDialog` always dismisses on a button press, so attach the neutral and positive click handlers in `setOnShowListener` through `getButton()` to keep the dialog open for reset and for an invalid selection. |
| `DirectPlayPreference` | `customView`, positive, negative | `setView` |
| `NowPlayingPreferenceDialog` | | Deleted in Phase 1 |

`onCreateDialog` returns the `AlertDialog` from `builder.create()`. Code that reached into `MaterialDialog` (`getCustomView()`, `DialogAction`) uses the view binding instead. Remove both `com.afollestad.material-dialogs` dependencies once the last import is gone.

Done when: no `com.afollestad.materialdialogs` imports remain, and every dialog uses the M3 shape and colors in all three themes.

## Phase 3: Multi-select bar

Replace material-cab with AppCompat's `ActionMode`, styled by the M3 theme.

1. Theme: set `windowActionModeOverlay` to true and `actionModeStyle` to a surface-colored style (`colorSurfaceContainer` background, `colorOnSurface` title), so the action mode covers the toolbar instead of pushing the content down.
   The mode inherits from the toolbar's context, so `@style/Toolbar.OnColor`'s dark overlay would turn its icons white in the light theme. `actionModeTheme` sets those roles back to match the surface and clears the popup theme, which would otherwise replace the neutral roles. The mode also covers the status bar, so `AbsMusicPanelActivity` matches the status icons to the mode's surface while it is open.
2. `AbsMultiSelectAdapter.kt` starts the mode with `activity.startSupportActionMode(callback)`, updates `mode.title`, and calls `mode.finish()` when nothing is checked. The `onCreateActionMode`, `onActionItemClicked`, and `onDestroyActionMode` callbacks map onto today's `onCreate`, `onSelection`, and `onDestroy`.
3. AppCompat allows one mode at a time, because starting a mode finishes the open one, and it closes the open mode on Back by itself. `CabHolder` and the Back handling in `MainActivity` and the detail activities are therefore removed.
   The mode is drawn above the whole window rather than inside each layout, so `AbsMusicPanelActivity` tracks it and closes it whenever the player panel changes state. `MainActivity` and `LibraryFragment` also close it on a library switch and on a tab change.
4. Remove the `cab_stub` `ViewStub`s (`activity_album_detail.xml`, `activity_artist_detail.xml`, `activity_genre_detail.xml`, `activity_playlist_detail.xml`, `fragment_library.xml`, and their `layout-w840dp`/`layout-w1200dp` versions), the `R.id.cab_stub` argument in the `AlbumAdapter`, `ArtistAdapter`, `PlaylistAdapter`, and `SongAdapter` constructors, the unused `cabHolder` parameter in the adapter constructors, the `cab.backgroundColor(null, getPaletteColor())` calls in the detail activities, and the `com.afollestad:material-cab` dependency.

Check on a device that the mode covers the toolbar in each activity that has one (`setSupportActionBar`), including the expanded layout with the rail.

Done when: multi-select works in the library tabs and every detail screen, and material-cab is gone.

## Phase 4: Surface chrome and color presets

The work runs in three steps, each with its own commit:

1. Move the phone chrome onto the surface color, using the existing greys. Done.
2. Add the presets: the generator, the overlays, `ColorPreset`, the black overlay, the earlier `setTheme` call, and the picker. Done.
3. Remove the remaining hand tinting and the old color preferences. Done.

### Step 1: Phone chrome (done)

- The four pinned theme items are gone, so the top bar is the M3 default of 64dp on `colorSurface` with no shadow.
- There are still two toolbar styles, on purpose. `Toolbar.Surface` is used by the library, playlist, genre, about, downloads, search, login, select, and settings layouts. `Toolbar.OnColor` keeps the dark overlay and the 56dp `toolbar_height` for the album, artist, and Flat player layouts, because they sit on album color. Album and artist move to the surface in Phase 5, and Flat keeps `Toolbar.OnColor`.
- The phone album and artist layouts pin `Widget.Design.AppBarLayout` until Phase 5, so the app bar doesn't replace their palette color with the surface.
- Lift on scroll is off (`liftOnScroll="false"`) in the library, and a `MaterialDivider` sits under the tabs. Updating the lift target on every page change would add code for very little visible effect.
- Tabs and the navigation drawer use M3 defaults. Library rows in the drawer are checkable, because the M3 pill only shows on a checkable item. The drawer header is on `colorSurfaceContainerLow`.
- `AbsThemeActivity.setColor` uses `colorSurface` with no darker band, so the `MainActivity` override and `ThemeUtil.getColorDark` are gone.
- `setColor` now calls `setDrawUnderStatusBar` before the light flags. That call replaces every system UI flag, so light status bar icons never took effect, which the old dark status band had hidden.
- `AbsMusicPanelActivity` only applies a light navigation bar while the panel is collapsed. Below Android 15 the expanded panel's bar takes the palette color, so the light flag stays off there.
- Toolbar menu icons have white fills, so `menu_main`, `menu_downloads`, `menu_detail_genre`, and `menu_detail_playlist` tint them with `colorOnSurfaceVariant`.

Android 15 ignores the window bar colors, so with 3-button navigation the system draws its own contrast scrim. On the Flat player in the light theme, that's a grey scrim with dark icons, at about 3:1 contrast. This isn't a regression, and it belongs with the edge-to-edge work (see *Not changed*).

### Step 2: Presets (done)

The *Model*, *Generated overlays*, *Applying the preset*, and *Picker* sections below describe what was built, except for these changes:

- The picker is a plain `Preference` that opens `ColorPresetDialog`, so there is no `ColorPresetPreference` class.
- The swatch ring is `colorPrimaryContainer`, because `colorSurfaceContainerHigh` nearly disappeared against the dialog.
- The generated overlays also set `cardBackgroundColor`. The dynamic overlay only sets Material roles, so `ThemeOverlay.Gelli.Color.Wallpaper` goes on top of it to fill that in.
- `ThemeOverlay.Gelli.Black.Neutral` inherits from `ThemeOverlay.Gelli.Black` and adds the `#2E2E2E` containers.
- The generator pins `@material/material-color-utilities` to exactly 0.3.0, because the ESM build of 0.4.0 doesn't load (an extensionless import in `color_spec_2025.js`). To regenerate, run `npm install` and `npm run colors` in `scripts/`.
- `AppShortcutIconGenerator` already moved to `themedContext`. Shortcut icons only regenerate when the preset or theme changes in Settings, so with Wallpaper they keep their colors after a wallpaper change until then.

Checked on an Android 15 emulator in light, dark, and black, including Wallpaper with the Monet overlays enabled. That image ships with them disabled, which makes the wallpaper roles fall back to heavy framework defaults. Android 14 and below are untested, and the path where Wallpaper is unavailable is only covered by `ColorPresetTest`.

### Model

- A new `ColorPreset` enum: `NEUTRAL`, about ten seeds, and `WALLPAPER`. `NEUTRAL` is the current grey scheme from `parents.xml`, so the expanded layout's tuning stays available. Example seeds: Indigo `#3F51B5` (the old default primary), Blue, Teal, Green, Amber, Orange, Red, Pink, Purple, Brown. Settle the list and the default on a device. Indigo is the starting proposal.
- `WALLPAPER` appears only when `DynamicColors.isDynamicColorAvailable()` returns true (Android 12 and later). If a stored `WALLPAPER` value meets an older device, it falls back to the default.
- One preference, `COLOR_PRESET`, replaces `PRIMARY_COLOR` and `ACCENT_COLOR`.

### Generated overlays

Theme attributes can't be set to arbitrary values at runtime below Android 12, so every preset is a pre-built theme overlay.

- `scripts/generate_color_presets.mjs` uses `@material/material-color-utilities` (`SchemeTonalSpot`, standard contrast). For each seed it writes `ThemeOverlay.Gelli.Color.<Name>.Light` and `.Dark` with the full role set (`colorPrimary` through `colorOutlineVariant`, including the surface container roles, plus `android:colorBackground`, `colorOnBackground`, and `colorAccent`, which the base themes set separately) into `res/values/color_presets.xml`. The output file is committed. Run the script by hand when the preset list changes.
- `NEUTRAL` has no overlay: the grey roles stay in the base themes.
- The black theme becomes an overlay, `ThemeOverlay.Gelli.Black`, that forces the surface roles to the current black values and is applied *after* the preset. Today `Theme.Phonograph.Black` is a base theme, and a preset applied on top of it would bring back grey surfaces. `Theme` keeps `LIGHT`, `DARK`, and `BLACK`. `BLACK` maps to the dark base theme plus this overlay. The overlay only sets surfaces, so the preset's containers stay tinted. `NEUTRAL` with `BLACK` gets a small overlay of its own for the containers (`#2E2E2E`).

### Applying the preset

- `AbsThemeActivity.onCreate` calls `setTheme`, then `getTheme().applyStyle(preset overlay, true)`, then the black overlay, all **before** `super.onCreate`. Today `setTheme` runs after `super.onCreate`, so fragments restored in `super.onCreate` can inflate with the wrong theme. For `WALLPAPER`, call `DynamicColors.applyToActivityIfAvailable(this)` in place of the preset overlay. It applies the base theme's `dynamicColorThemeOverlay`, which the M3 light and dark themes already set, so the light or dark scheme follows the base theme.
- `onResume` recreates the activity when the theme or the preset changes, as it does for the colors today. It has to compare the `Theme` value and the preset, not the style ID, because `BLACK` and `DARK` will share a base style.
- `SettingsActivity`'s `PRIMARY_COLOR` and `ACCENT_COLOR` case becomes `COLOR_PRESET`.
- Code outside an activity resolves colors through a new `ThemeUtil.themedContext(context)`, a `ContextThemeWrapper` with the base theme and the preset applied. `AppShortcutIconGenerator` uses it for the colored shortcuts (`colorPrimaryContainer` and `colorOnPrimaryContainer`).
- `LoginScreen` becomes a child of the app theme, so the login screen picks up the preset. Recheck the select button, which sets `?colorOnSurface` by hand since step 1, against the presets.

### Step 3: Removing the hand tinting (done)

Every remaining `getPrimaryColor()` and `getAccentColor()` call is gone. Most became nothing at all, because the M3 component already reads the right role. Step 1 already removed the status bar, toolbar, tab, drawer, login, and select sites.

| Site | Before | After |
|---|---|---|
| `LibraryFragment.setUpRail` | Accent icon tint and indicator | Remove. The M3 rail defaults use `colorSecondaryContainer` and `colorOnSecondaryContainer`. |
| `DownloadsActivity` | Accent for `coverageProgress` and `loading` | `colorPrimary` |
| Fast scroller (`ViewUtil.setUpFastScrollRecyclerViewColor`, called from `PlaylistDetailActivity`, `GenreDetailActivity`, and `AbsLibraryPagerRecyclerViewFragment`) | Accent, passed in by each caller | `colorPrimary` |
| `ShuffleButtonSongAdapter` | Accent | `colorPrimary` |
| `MiniPlayerFragment`, `PlayerBarFragment` | Accent for the progress | `colorPrimary` |
| `AbsBaseActivity` Snackbar | Accent action text | Remove (M3 uses `colorPrimaryInverse`) |
| `SleepTimerDialog` | Accent arc | `colorPrimary` until the slider's default colors take over in Phase 6 |
| `JellyPreferenceCategory` | `ACCENT_COLOR`, falling back to `R.color.color_accent` | `colorPrimary` |

`getPrimaryColor()`, `getAccentColor()`, `PRIMARY_COLOR`, `ACCENT_COLOR`, `ColorPreference`, `ColorCircleDrawable`, `preference_color.xml`, `color_primary`, `color_accent`, the `pref_title_primary_color`, `pref_title_accent_color`, `pref_summary_primary_color`, and `pref_summary_accent_color` strings in every locale, and the `com.github.QuadFlask:colorpicker` dependency are deleted too.

Checked on an API 36 emulator against a local mock server, in light, dark, and black with Indigo and Neutral, and in light and dark with Orange: the settings headers, fast scroller, mini player, sleep timer arc, downloads screen, and Snackbar action. The rail and player bar were checked above 840dp in dark Indigo and light Orange. Neutral's primary is plain white or black, so its settings headers only differ from the rest of the text by size.

### Picker

A `color_preset` preference replaces the two color preferences in `pref_interface.xml`. It opens `ColorPresetDialog`, a `MaterialAlertDialogBuilder` dialog with a grid of swatches. Each swatch shows the preset's `colorPrimary` on its `colorPrimaryContainer`, resolved through `ThemeUtil.themedContext` for the current theme. A check mark shows the current choice. The wallpaper swatch is shown only when dynamic color is available.

Done when: the phone and the expanded layout both follow the chosen preset in light, dark, and black. Changing the preset recreates the open screens. No code reads a user color directly.

## Phase 5: Album colors

### Grid tiles (done)

- The expanded width tile moved to `layout/item_grid.xml`, so phones and large screens share one tile.
- `item_grid_card_horizontal.xml` (the artist screen's album row) is that tile at a fixed width. `HorizontalAdapterHelper` lines its first cover up with the rest of the screen.
- `grid_padding` went from 2dp to 4dp on phones, which puts the covers 16dp from the edge of the screen.
- The colored footers, their menu item, and their five preferences are gone, along with `paletteColorContainer` and the `defaultFooterColor` attribute. The adapters load covers with Glide and skip `.palette()`.
- `CustomPaletteTarget` falls back to `colorSurfaceContainerHighest` when an image has no usable color. The player, the widgets, and the notifications still use it.

### Detail headers (done)

- `AlbumDetailActivity` and `ArtistDetailActivity` use the surface for the header, app bar, and status bar, with `colorOnSurface` and `colorOnSurfaceVariant` text and icons. The layouts use `Toolbar.Surface`, and the pinned `Widget.Design.AppBarLayout` is gone. The covers are rounded to match the grid tile, and the transition names stay.
- Neither activity implements `PaletteColorHolder` anymore. Only `AbsPlayerFragment` keeps it.
- The `layout-w1200dp` two-column layouts got the same treatment.
- The shuffle icons in `menu_detail_album` and `menu_detail_artist` needed `app:iconTint`, because they were white and relied on the dark toolbar.
- `ThemeOverlay.Gelli.ActionMode` no longer sets text and icon roles. They only undid the dark overlay on `Toolbar.OnColor`, and the detail screens were the last place an action mode sat on that toolbar.

### Player panel (done)

- Mini player: `colorSurfaceContainer` background, `colorOnSurface` text, and a 2dp `LinearProgressIndicator` along the top edge. It uses the `Legacy` style, because the default style's gap and stop dot don't suit a line that thin. This came ahead of the rest of the Phase 6 widget swap, since the bar was moving anyway.
- The Flat player keeps its palette background and status bar color (see *Not changed*), and its toolbar sits on that color as it does now.

Checked in light, dark, and black on a phone, and the expanded player bar for regressions.

## Phase 6: Remaining widgets

| Widget | Change |
|---|---|
| `CardView` in `card_about_app.xml`, `card_author.xml`, `card_special_thanks.xml`, `card_support_development.xml`, `activity_downloads.xml` | Done. `MaterialCardView` (`Widget.Material3.CardView.Filled`). `cardBackgroundColor` and the `androidx.cardview` dependency are gone: the server card and the login button read `colorSurfaceContainerHigh`, and search rows sit on the surface like every other list. |
| 8 `AppCompatButton`s in `card_special_thanks.xml` | Done. `MaterialButton` with `Widget.Material3.Button.TextButton`, in `colorPrimary` instead of brand colors. |
| `ImageButton`s in the player controls, player bar, queue sheet, and landscape player | Done. `MaterialButton` with `Widget.Gelli.IconButton`, or its `Filled` child for the player bar and landscape play buttons. The animated play/pause drawable runs through `setIcon`. It needed tint support and had to honor the origin of its bounds. |
| `MaterialProgressBar` in `fragment_flat_player_playback_controls.xml` | Done. A `LinearProgressIndicator` fills the buffering strip behind the slider, and the dependency is gone. |
| Plain `ProgressBar` in `activity_splash.xml`, `activity_downloads.xml`, and `fragment_landscape_player.xml` | Done. The splash and the landscape ring are circular, and the downloads bars are linear. |
| `SeekBar` for playback position | Done. Kept, because M3's `Slider` has no buffered-progress track. `Widget.Gelli.SeekBar` uses `colorPrimary` and `colorSurfaceContainerHighest` on the player bar. `Widget.Gelli.SeekBar.OnColor` is white for the flat and landscape players, which sit on the album color. |
| `SeekArc` in the sleep timer | Done. A `Slider` from 1 to 120 minutes above the time text, with the "finish last song" checkbox below. The dependency is gone. |
| `IconImageView` | Done. Tints with `colorOnSurfaceVariant`, and `iconColor` is gone. |
| `roundSelector`, `rectSelector`, `rectSelectorStrong` | Done. `?attr/selectableItemBackgroundBorderless` and `?attr/selectableItemBackground`, so presses use M3's state layers. Rows and tiles that can be chosen in multi-select use `@drawable/selectable_item_activated`, because `selectableItemBackground` has no activated state. |

The home-screen widgets (`app_widget_*.xml`) and the notification layouts are built from `RemoteViews` and stay as they are.

Done when: the dependencies above are gone and no layout uses `androidx.cardview`, `AppCompatButton`, or `MaterialProgressBar`.

## Phase 7: Settings

androidx.preference has no Material 3 theme, so style it directly.

1. One preference theme overlay for all three themes, based on `PreferenceThemeOverlay`, which fixes today's light/dark mismatch. Category titles use `colorPrimary`, titles use `colorOnSurface`, and summaries use `colorOnSurfaceVariant`.
2. The 9 `SwitchPreference`s become `SwitchPreferenceCompat` with a `widgetLayout` that holds a `MaterialSwitch`.
3. `ListPreference` is the only built-in preference dialog in use, and it builds a plain AppCompat `AlertDialog`. `MaterialAlertDialogBuilder` sets the rounded background itself, so a theme overlay can't match it. `onDisplayPreferenceDialog` in `SettingsActivity` shows `ListPreferenceDialog`, which keeps the library's logic and only swaps the builder.
4. Settings keeps the content-width cap from the large-screen work.

Done when: Settings matches the rest of the app in all three themes, including its dialogs.

## Cleanup

Done. The attributes, drawables, and dimens nothing reads anymore are gone. `toolbar_elevation` and `toolbar_height` stay, because `Toolbar.OnColor` on the flat player still reads them. `docs/large_screen_layout_plan.md` no longer describes the pinned phone styles or the neutral-only theme.

## Typography

Done. Theme text colors follow `colorOnSurface` and `colorOnSurfaceVariant`, and layouts use the M3 type roles instead of AppCompat text appearances and their own font and size overrides. Row titles use `BodyLarge`, because `TitleMedium` is heavier than a list of songs needs.

Text on album color keeps its own colors: `ThemeOverlay.Gelli.LandscapePlayer` pins white, and the flat player reads `color_text_*` through `ThemeUtil`. The landscape player's title also keeps its custom bold size.

## Not changed

- **Now-playing palette color.** The Flat player still tints its background and status bar with the album color, and the landscape player keeps its palette gradient. Only library and detail backgrounds drop it. This can be revisited after Phase 5.
- **`SlidingUpPanelLayout`.** It's the shell for every content activity. `BottomSheetBehavior` is the closest Material equivalent, but switching would mean rewriting the shell, so only the panel's contents are restyled.
- **Fast scroller and `advrecyclerview`.** M3 has no fast scroller, so the existing one only changes color. Drag-to-reorder isn't a visible component.
- **Home-screen widgets and notifications.** `RemoteViews` can't use Material components. The colored notification option still uses the album color.
- **Edge-to-edge.** With `targetSdk 35`, Android 15 forces edge-to-edge, and the status and navigation bar color calls have no effect there. `StatusBarView` covers the status bar today. Proper inset handling deserves its own plan.
- **Following the system dark mode.** The app keeps its explicit light, dark, and black themes rather than moving to `DayNight`.
