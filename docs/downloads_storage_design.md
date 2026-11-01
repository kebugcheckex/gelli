# Downloads & storage

Implementation in progress. Updated October 22, 2026. The initial account-wide coverage overview is implemented; detailed storage management and artist coverage are still planned.

## Implementation progress

The first three implementation commits are complete. They deliver the initial overview, not the entire design or three complete release slices.

| Commit | Completed work |
| --- | --- |
| `77718759` — Add account-scoped local library summaries | Distinct locally playable song IDs and counts; shared validation requiring a readable, nonempty regular file; measured audio usage across the account's known internal/external audio directories. Unavailable storage produces unknown usage rather than zero. Audio files without matching database rows still contribute to measured usage. |
| `711dd190` — Persist server song inventories for offline coverage | Room v11 account inventory tables and migration; persistent song IDs and successful-check timestamp; complete, paginated inventory refresh with deduplication and atomic replacement; account-switch and overlapping-request protection; coverage calculated from matching local/server IDs. Failed or incomplete refreshes retain the previous snapshot. |
| `c54d96f4` — Add the offline coverage overview | Settings > Local Library > Downloads & storage; count, percentage, progress bar, last successful library check, saved songs outside the inventory, and a Saved audio card. Loading, offline, unknown, empty, and error states; manual refresh, reconnect refresh, and local-library listener updates. State survives rotation and resets on account changes. Also fixes library discovery to use Jellyfin user views. |

The current scope is **Current account · All music libraries**, independent of the library selected for browsing. Library discovery includes accessible music libraries hidden from the server home screen. Percentages round down, positive coverage below 1% displays as **<1%**, and unknown or empty inventories have no percentage or bar. The current storage card measures **audio only**, excluding artwork and temporary files. There are no cleanup controls on this screen yet.

### Validation completed and pending

- The latest debug build and 34 targeted unit tests passed: 10 overview-state tests, 13 inventory-refresh tests, 5 coverage tests, and 6 inventory-loading tests. These cover account switching, delayed results, rotation/reopening state, coalesced local updates, offline/unknown totals, failures and recovery, pagination, and incomplete library responses.
- Android lint ran. It reports existing issues elsewhere in the app, with no findings in the files changed for the overview.
- Earlier foundation checks covered the local-summary behavior and host SQLite checks of the inventory migration, account isolation, replacement, and rollback.
- The corrected debug build is installed on the **Pixel 7**, with existing app data preserved. Device investigation found that a general CollectionFolder item query returned no libraries even though user views returned music libraries; the inventory now uses user views.
- **UI validation is owned by the user from this point onward.** The final corrected screen still needs user review, including light/dark themes, large text, rotation, accessibility, and online/offline/error states. Automated state tests do not establish device UI acceptance.

### What's next

1. Address feedback from the user's Pixel 7 UI validation of the current overview.
2. Finish the remaining overview features: artwork/temporary-file usage and storage-volume free space; waiting/downloading/failed status with notifications for every queue transition; drawer access, a Download settings link, and a reliable route to the screen when launching offline. Handling an offline state inside this screen does not complete offline startup or library browsing.
3. Make deletion report failures reliably and retain tracking for files that could not be removed before adding bulk cleanup controls.
4. Build Manage storage: source/age filters, file sizes and last-played dates, sorting, selection, removal review, and actual removal results with refreshed coverage and usage. Keep cleanup manual and usable offline.
5. Add artist coverage with complete album-artist membership, cached totals, artist rows, and detail headers. Count multi-artist songs once in the account overview and consistently within each artist.

Continue implementation one commit at a time. These remaining items are not constrained to a fixed number of commits.

## Design reference

![Three proposed screens: overview, storage cleanup, and artist coverage](downloads_storage_mockup.png)

[Open the scalable mockup](downloads_storage_mockup.svg). All names, counts, and sizes are illustrative. The screens show the proposed sequence of releases, not three screens required for the first version.

## Entry point and scope

**Implemented:** Settings > Local Library > **Downloads & storage**. **Planned:** a navigation drawer entry reachable online and offline, and a **Download settings** link from the overview. Saving preferences remain in Settings. Offline app startup and browsing are separate unfinished work in the local library plan.

Use **Current account · All music libraries** as the visible scope. This matches the account ownership of stored files and avoids a percentage that changes when switching library tabs. Other accounts' files are excluded. A selected-library filter can follow once local records have reliable library membership.

## First version: overview

This section describes the full overview target. The coverage card and audio-only storage card are implemented. Artwork/temporary-file breakdown, free space, and download status remain to be added after the first three commits.

The first card answers “How much of my music can I play offline?”

- **26% available offline** as the headline, with a determinate horizontal bar.
- **1,248 of 4,800 songs** directly below. Use song coverage, rather than bytes transferred, for this bar.
- **Includes downloads and songs saved while listening** explains automatic saves.
- **Library checked today, 10:42** identifies how current the server inventory is. A toolbar refresh updates the inventory; local completions and removals update the numerator immediately.

The next card answers “How much space is it using?”

- **6.4 GB on this device**, scoped to this account's local library.
- **Music 6.1 GB · Artwork 0.2 GB · Temporary files 0.1 GB**. A small segmented bar shows the composition of this usage, with text labels so color is never required.
- **18.2 GB free on this storage volume**. This is device free space, not a download quota. Other apps and accounts may also occupy the volume.
- In the storage-management release, add **Manage storage** here. Omit the button until that destination exists; the mockup marks its position as a later addition.

A compact status section below the cards keeps work in progress separate from coverage: **24 songs waiting for an unmetered network**, or **Downloading 1 of 24 songs**, with an optional **3 songs need attention** row. Queue details, retry controls, and cancel actions can follow separately; the first version only reports status. Hide this section when there is nothing pending or needing attention. Do not offer “Download all” on the overview.

## What the percentage means

Let `C` be the distinct song IDs in a successfully refreshed server inventory for the account's accessible music libraries, and `L` be its locally playable song IDs. Coverage is `|C ∩ L| / |C| × 100`.

- A locally playable song has state `COMPLETE` and an existing, nonempty readable audio file. Queued, downloading, failed, missing, and partial files are excluded.
- Songs saved by playback and explicitly downloaded songs count equally. Original and transcoded versions count equally, once per song ID. Playlist membership does not duplicate songs.
- Display whole percentages, rounded down, so **100%** appears only when every inventoried song is available. Keep the exact song counts visible. For nonzero coverage below 1%, show **<1%**.
- The inventory must finish successfully before replacing the previous snapshot. A failed or partial response is never treated as an empty library. Fetch IDs/count metadata rather than all audio metadata, without favorites/search/pagination filters from the browsing screen.
- Keep the last successful inventory, scoped by account, with its timestamp. Refresh on opening while online and on explicit refresh; do not fetch the inventory again for each completed save. Work and file verification run off the main thread.
- Local files absent from the inventory still occupy space. Show **N additional saved songs outside the last library snapshot** separately, and include them in storage management. Do not delete files just because an inventory changes.

This needs an inventory of song IDs, not just a server total: dividing all local rows by a server count can exceed 100% after server changes or include songs outside the selected scope.

| State | Overview behavior |
| --- | --- |
| First inventory loading | Keep the local saved count and storage visible when available; show “Checking music libraries…” and omit the percentage until a snapshot is available. |
| Offline, with snapshot | Show coverage against the saved inventory, “Offline · Library checked [date/time]”; current local changes still update it. |
| Offline or server error, no snapshot | Show “1,248 songs available offline” and “Connect to check total library size”; omit the percentage and its bar. On an online error, offer refresh. |
| No locally saved songs, known nonempty library | Show “0% available offline” and “0 of 4,800 songs”. A hint pointing to Download in song and album menus remains planned. |
| Empty server inventory | Show “No songs in your music libraries”; omit the percentage and bar. Existing local-only files remain visible in storage. |
| Everything stored | Show “100% available offline” and the exact matching counts. |
| File disappears or storage is unavailable | Reconcile local availability on open, refresh, or a local-library notification and update coverage. Report unmeasurable storage as unavailable, not 0 bytes. More detailed missing-file status remains planned. |
| Download fails or waits for network | Coverage excludes these songs. A failed/waiting status section below the cards remains planned. |

## Next: manage storage

Open a separate **Manage storage** screen from the storage card. Keep its scope identical to the overview, including saved files outside the current server inventory. It must work offline.

Start with two filters: **All saved songs / Saved while listening / Downloaded by you**, and **Any time / Not played in 30, 90, or 180 days / Never played**. Default to all saved songs; show a suggested **Review songs saved while listening, not played in 90 days** shortcut. Treat songs with no `lastPlayedAt` as “Never played”, and use `addedAt` to avoid suggesting newly saved songs for cleanup.

Show selectable songs with title, artist, file size, and last-played date. Sort by largest first by default, with oldest-played and name options. Selection starts empty. A fixed bottom action reads **Review removal · 186 songs · about 940 MB** only after selection. Album/artist grouping and original/transcoded filters can follow without changing this flow.

Review removal before deleting. The dialog says **Remove 186 songs from this device?**, shows estimated space freed, and explains: **The server library is unchanged. These songs will need streaming or downloading again.** If relevant, add **Songs saved while listening may be saved again when played.** Use **Cancel** and **Remove from device**. Do not label this “Clear cache”. Put “Remove all saved songs” in the overflow menu, using the same review step.

After removal, show the actual number removed and measured space recovered, update coverage and usage, and report any failures. A file that failed to delete must remain tracked. Do not promise Undo without keeping recoverable files. Removing completed songs leaves the queue alone; queue cancellation is a separate action. Nothing is evicted automatically.

## Later: artist coverage

Add **By artist** below the overview, with each row showing the artist name, **72 of 120 songs · 60% offline**, a small coverage bar, and **360 MB on device**. Offer alphabetical, least available, and most storage sorting. Tapping a row opens artist details with the same coverage header; keep download and removal actions in the detail screen.

Use the same artist membership as the existing Artists browser (album artists), with consistent membership for both numerator and denominator. Multi-artist songs may appear under multiple artists, but count only once in the account summary. Artist storage rows therefore are not necessarily additive. Fetch artist totals in batches or build them from an inventory, rather than one request per visible row. Cache artist inventories/totals for offline viewing with the same timestamp/unknown rules.

## Fit with the existing app

Use the app's toolbar, theme colors, Material cards, and type styles. The mockup uses the current default indigo and pink. Keep at least 48 dp touch targets, support large text without clipping counts, expose a single accessible coverage description (“1,248 of 4,800 songs, 26 percent available offline”), and avoid announcing every download completion. Dark mode uses theme surfaces; unavailable and failed states always have text labels.

Existing implementation seams:

- `LocalLibrary` and `LocalSongDao`: account-scoped local records, listeners, and removal. `LocalLibrarySummary` now supplies verified IDs/counts and measured audio usage; playback, verification, and summaries share nonempty/readable regular-file validation.
- `ServerSongInventory` / `JellyfinSdkSession`: account-specific library discovery through user views, followed by paginated audio queries. The separate inventory result preserves errors and partial failures rather than treating them as empty lists. Room stores song IDs and the successful-check timestamp together.
- `DownloadsOverview` / `DownloadsViewModel` / `DownloadsActivity`: retained screen state, lifecycle-bound listeners/rendering, and the Settings entry. Local scans are coalesced, and results from previous accounts are discarded.
- `LocalSong`: source, variant, size, timestamps, and one artist ID already exist. Complete album-artist membership remains an additional requirement for artist coverage.
- `SaveWorker`: pending/download/error states already exist. Overview status needs updates for enqueue and state changes as well as successful completions; existing local-library notifications do not cover every transition.
- Current storage measurement covers actual account audio files. Extend it to artwork and temporary files, and use free space on the volume holding those files. Glide's browsing image cache is separate. Bulk removal needs deletion-failure reporting before reusing the current removal path, which deletes rows even when a file deletion fails.

This extends Phase 6 of [the local library plan](offline_cached_music_plan.md).

## Delivery slices

These are feature stages, not a three-commit limit.

1. **Coverage and space summary — partially complete:** Settings entry, account inventory snapshot, verified local count, percentage, audio usage, and offline/error states are implemented. Drawer access/offline startup, full storage breakdown, free space, and queue status remain. No cleanup button until its destination ships.
2. **Space management — planned:** filtered song list, selection, review, reliable removal, and refreshed coverage/usage. Add album/artist grouping and quality filters as needed.
3. **Artist coverage — planned:** consistent artist membership and totals, artist rows and detail headers, cached offline values.

For each stage, verify small/empty/full libraries; partial inventory failures; files removed externally; account switches; and offline states with and without snapshots. The user handles device UI validation, including large fonts, rotation, screen-reader output, and light/dark themes. Failed deletions and queued downloads during cleanup must be checked when storage management is implemented.
