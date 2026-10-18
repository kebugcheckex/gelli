# Local Library and Offline Playback Plan

## Goal

Every song that is played or explicitly downloaded is persisted on the device and stays there until the user removes it. Playback always prefers the local copy and only streams when a song is not stored yet. When offline, stored songs remain browsable and playable.

This replaces the earlier "offline cached music" plan. The guiding decisions:

- There is one **local library**, not a cache. Nothing is evicted automatically.
- Downloads are owned by the app, not by Media3's `DownloadManager`. Media3 is used for playback only.
- The on-disk format is an implementation detail. Visibility to other apps is optional and deferred.
- Existing downloads from the legacy implementation are dropped, but only after the new save pipeline replaces them.
- Existing features keep working at every commit on `master`. New pieces are added alongside the legacy ones, and legacy code is removed only once nothing depends on it.

## Current State (before this plan)

- `DownloadService` downloads `/Items/{id}/Download` into a temp file, copies it to a metadata-derived path (`<location_download>/music/<artist>/<album>/<disc>.<track> - <title>.<ext>`), and inserts a `(id, cache)` row in the `cache` table. No metadata is stored.
- `LocalPlayer.createMediaItems()` checks whether that derived path exists and plays it, otherwise streams.
- Media3 `SimpleCache` (LRU, `location_cache/exoplayer`) caches streamed bytes. Glide caches images in `location_cache/glide`.
- Offline/auth failure redirects to login (`AbsMusicContentActivity`). `NetworkReceiver` exists but is never registered.

Known defects this plan removes:

- The final file is created before the download starts, so a failed download leaves a zero-byte file that playback then prefers over streaming.
- `DownloadService` is not a foreground service and can be killed in the background.
- Both download and cache locations default to `getCacheDir()`, which the system may clear.
- Metadata-derived paths orphan files when tags change on the server.

## Design

### Overview

```
                 ┌─────────── Local library (persistent, manual cleanup only) ───────────┐
 play / download │  Room: local_songs (metadata, state, file info, timestamps)          │
 ───────────────▶│  Files: <root>/<account>/audio/<itemId>.<ext>                        │
                 │         <root>/<account>/images/<albumId>.jpg                        │
                 └───────────────────────────────────────────────────────────────────────┘
                          ▲ writes                                   │ reads
                 Save queue (WorkManager)                     LocalPlayer: stored? → local file
                          ▲                                          else → stream (if policy allows)
        explicit "Download" action ── or ── playback save trigger
```

### Storage layout

- Root defaults to `Context.getExternalFilesDir(null)/library` (app-specific, not cleared with cache, removed on uninstall). Falls back to `getFilesDir()/library` if external storage is unavailable.
- Files are keyed by Jellyfin item ID, never by metadata: `<root>/<account>/audio/<itemId>.<ext>`.
- `<account>` isolates accounts and servers from each other. The local `User.id` is `server + jellyfinUserId` and contains characters such as `://` and `:` that are not safe in a directory name, so the directory is named after a hash of it (first 16 hex characters of its SHA-256). The `local_songs.accountId` column stores the raw `User.id`. Cross-account deduplication is out of scope.
- A custom root may be offered later. It must never default to a cache directory.

### Data model

New Room entity `local_songs` (primary key `(accountId, itemId)`):

| Field | Purpose |
|-------|---------|
| `accountId`, `itemId` | Identity |
| Song metadata | Title, album/artist IDs and names, track/disc, year, duration, image tag, blurhash, favorite (snapshot) |
| `state` | `QUEUED`, `DOWNLOADING`, `COMPLETE`, `FAILED`, `MISSING` |
| `source` | `EXPLICIT` or `PLAYED` |
| `variant` | `ORIGINAL` or `TRANSCODED` |
| `filePath`, `fileSize` | Actual stored file |
| `container`, `codec`, `bitRate` | Of the **stored file**, which may differ from the server item when transcoded |
| `serverDateModified` | Detects server-side changes later |
| `addedAt`, `lastPlayedAt` | Cleanup and sorting |
| `failureCount`, `lastError` | Retry and diagnostics |

Album cover art is stored once per album under `images/`. Glide's own cache stays a real, evictable cache for browsing.

A song is **available locally** only when `state = COMPLETE` and the file exists. A single `LocalLibrary.isAvailable(song)` lookup replaces the path-existence check in `LocalPlayer` and the `cacheDao().isCached()` badge check in `SongAdapter`.

During the transition (until legacy removal), `LocalLibrary.resolve(song)` returns the library file if available, otherwise the legacy metadata-derived file if it exists and is non-empty, otherwise nothing (stream). The badge shows for either. This keeps legacy downloads playable while the new pipeline comes online, and the non-empty check removes the zero-byte-file defect immediately.

### Missing files

A row whose file has disappeared (deleted externally, storage removed) is not dropped silently:

- A consistency check (on startup, and whenever a lookup finds the file gone) moves `COMPLETE` rows with no file to `MISSING`.
- `MISSING` songs stay in lists but are shown greyed out with a missing indicator, in both online and offline views.
- Trying to play a `MISSING` song re-queues it for saving (subject to the saving network policy). Meanwhile playback streams it if the streaming policy allows. Otherwise it is skipped like any other unplayable song.
- A successful re-download returns the row to `COMPLETE`. So does the consistency check if the file reappears (e.g. remounted storage), since `filePath` is absolute.

### Original vs transcoded file

- Setting `library_original_cap_mb`, default **15 MB**.
- If the original is at or under the cap, store it unchanged via `/Items/{id}/Download`.
- If it is over the cap, store a progressive transcode via `/Audio/{id}/universal` with `TranscodingProtocol=http` (never HLS, so the result is a single playable file).
- Size decision: the worker starts the original download and reads `Content-Length`, which is the original's exact size. If the header is missing, it estimates from the row's `bitRate × duration`. If the size exceeds the cap, it aborts and switches to transcode. `local_songs` has no column for `Song.size`, and the header makes one unnecessary, at the cost of one aborted request for large songs.
- Transcode bitrate: `cap_bits / duration_seconds × 0.95`, clamped to a preset ladder (320 / 256 / 192 / 160 / 128 / 96 / 64 kbps), picking the highest step that fits.
- Transcode codec follows the existing `transcode_codec` preference: `mp3` is stored as MP3, `aac` as ADTS (progressive MP4 is not streamable while written). The request offers no direct-play containers, so the server must transcode into the requested container, and the row records that container/codec and the chosen bitrate. VBR encoding is disabled, and `LocalPlayer` enables constant-bitrate seeking, since neither format carries a seek index.
- If even the lowest step exceeds the cap (very long tracks), store at the lowest step anyway. The cap selects quality and is not a hard limit.
- The cap is a list setting (unlimited / 50 / 25 / 15 / 10 / 5 MB) in a "Local Library" category of the settings screen. Changing it affects future saves only.

### Save queue

- The Room `state` column is the source of truth. Execution uses **WorkManager** (new dependency): persistence across process death, retry with backoff, network constraints, and foreground execution with a progress notification. This replaces `DownloadService`.
- The `local_songs` rows are the queue. A single serial worker (`SaveWorker`, unique work `library_save` with `APPEND_OR_REPLACE`) drains `QUEUED` rows oldest first, so starting it is all an enqueue has to do. One worker per song was dropped because concurrent foreground workers sharing one notification conflict in WorkManager. A row left `DOWNLOADING` by process death is reset to `QUEUED` when the worker next runs, and app start restarts the worker if any rows are still queued.
- Download into `<root>/<account>/tmp/<itemId>.part`, fsync, then rename atomically into `audio/`. Mark `COMPLETE` only after the rename. A failure deletes the `.part` file. Network errors, 408, 429 and 5xx put the row back to `QUEUED` and back off the whole queue, up to 3 attempts per song. Other errors (e.g. 404) record `FAILED` with `lastError`.
- Cancel in the notification drops every `QUEUED`/`DOWNLOADING` row. Any other stop (constraints, system) leaves them queued.
- The progress notification needs `POST_NOTIFICATIONS` on Android 13+, which the manifest does not request yet. Saving works without it, but the notification is hidden.
- Requests are idempotent: enqueuing a song that is already `QUEUED`, `DOWNLOADING` or `COMPLETE` is a no-op, except that `EXPLICIT` upgrades the `source` of a `PLAYED` row.
- Two entry points feed the same queue:
  - **Explicit**: existing Download actions in song/album/artist/genre/playlist menus and detail screens.
  - **Played**: the playback save trigger (below).
- Cover art for the song's album is saved in the same job if missing.
- `LocalLibrary` notifies listeners on the main thread whenever a save completes, songs are removed, or the consistency check changes rows. Song lists refresh their badges from it while attached.

### Save trigger for played songs

Setting `library_save_trigger`:

- **Immediately**: enqueue when playback of a non-stored song starts.
- **After N seconds** (default): enqueue after N seconds of actual listening, not counting time paused. N is configurable (default 30).
- **After completion**: enqueue when the track reaches its natural end.
- **Never**: only explicit downloads are stored.

The default is "After N seconds" with N = 30, so skipped songs are not stored.

The first version streams and downloads in parallel, so a song is fetched twice on its first play. This is accepted, especially on unmetered networks.

### Network policy

Settings, applied separately to streaming and to saving:

- `network_streaming`: **Any network** / **Unmetered only**
- `network_saving`: **Any network** / **Unmetered only** (default unmetered only, which bounds the double download to unmetered networks)
- `offline_mode`: manual switch that behaves as if there is no network. No streaming, no saving, no server calls, local library only.

"Unmetered only" is evaluated with `NetworkCapabilities.NET_CAPABILITY_NOT_METERED` via `ConnectivityManager` (requires `ACCESS_NETWORK_STATE`, which the manifest does not declare yet). This covers typical Wi-Fi and Ethernet, and excludes cellular and metered hotspots. For saving, it maps directly to WorkManager's `NetworkType.UNMETERED` constraint, so queued jobs wait for an unmetered network instead of failing. Explicit downloads made on a metered network under "Unmetered only" are queued and run later.

**Deferred**: automatic offline detection (deciding the server is unreachable vs. the network being down vs. auth failure) and switching modes automatically. This needs its own design. Until then, automatic behavior is limited to "this network is unmetered or not".

### Playback integration

- `LocalPlayer.createMediaItems()` resolves each song through `LocalLibrary`: stored → `file://` URI (progressive), otherwise the stream URI if the network policy allows streaming right now.
- Songs that are neither stored nor streamable under the current policy are skipped, with a single toast per queue action.
- When a save completes for a song in the current queue (not the current item), its media item is replaced with the local one (`Player.replaceMediaItem`).
- Playback reporting and favorite toggles are suppressed in offline mode. Queuing them for later sync is deferred.
- The Media3 `SimpleCache` is reduced to a small buffer (or removed) since played songs are persisted by the library.

### Offline app state and browsing

- Offline mode (manual for now) must not redirect to login. `AbsMusicContentActivity` routes offline into an app-level offline state instead.
- Library screens get a data source seam: online → existing `QueryUtil` / SDK calls; offline → `local_songs`.
- Order of offline screens: Songs → Albums → Artists → (Genres, Playlists later, which need extra metadata).
- Network-only actions (search, add to playlist, favorite) are hidden or disabled while offline.

### Cleanup

Nothing is removed automatically.

- "Remove from device" sits next to every Download action (song, queue and playlist menus, multi-select, album/artist/genre/playlist detail screens). Each removes the file and the row together, deletes the album cover once no song of its album is left, and drops queued songs from the save queue. Removing more than one song asks for confirmation. A song removed while it is saving fails its save without backing off the queue.
- A storage screen with total usage, breakdown by artist/album, filters by source (explicit/played) and variant, and bulk actions (e.g. "remove songs not played in N days", "remove all").

### Legacy data

Legacy storage is retired in two steps so downloads keep working throughout:

- Room migration v8 → v9 (Phase 1) is additive: it only creates `local_songs`. The `cache` table, `Song.cache`, `DownloadService` and the legacy file layout are untouched, and legacy files stay playable through the `resolve()` fallback.
- Room migration v9 → v10 (legacy removal, after the save pipeline replaces `DownloadService`): drop the `cache` table and rebuild `songs`/`queueSongs` without `Song.cache`. Losing the persisted playback queue once during this migration is acceptable.
- Legacy files, deleted in the same step as the v10 migration: if `location_download` is still the app's own cache directory, delete `<cacheDir>/music` and `<location_cache>/download`. If it points to a user-chosen external folder, leave the files untouched and ignore them.
- Also in that step: remove the `location_download` preference, `FilePreference`, `MusicUtil.getFileUri`, the legacy fallback in `resolve()`, and the external storage permission request in `AbsMusicServiceActivity`.

## Phases

### Phase 1: Library foundation

1. `local_songs` entity, DAO, additive Room migration v9. `LocalLibrary` facade: storage paths (hashed account directory), `isAvailable`, lookups, delete-song (file + row). Consistency check that marks rows with missing files as `MISSING`, run off the main thread after login. No caller changes, so no behavior change.
2. `LocalLibrary.resolve()` with the legacy fallback. Switch `LocalPlayer.createMediaItems()` to it, and the `SongAdapter` badge to library-or-legacy. Greyed-out rendering for `MISSING` songs.

Deliverable: one source of truth for what is stored, read by playback and the UI. Nothing populates it yet, and legacy downloads keep working.

### Phase 2: Save pipeline

1. Add WorkManager. Implement the save worker (temp + atomic rename, foreground notification, retry, one serial worker draining the queue) storing originals only, which matches current behavior. Cover art saving. Route existing Download menu actions to the new queue through `NavigationUtil.startDownload()`. Delete `DownloadService`, `DownloadNotification` and the manifest entry.
2. Original vs transcoded selection with the size cap. New "Local Library" settings category, starting with `library_original_cap_mb`.
3. Basic "Remove from device" menu action.

Deliverable: explicit downloads are reliable, metadata-complete, and removable. Legacy downloads still play through the fallback.

### Phase 2b: Legacy removal

- Room migration v10, legacy file cleanup, and removal of the legacy preference, path helper, fallback and storage permission request, as described under Legacy data.

Deliverable: the local library is the only storage. Legacy downloads are gone, as accepted.

### Phase 3: Playback integration

- Save trigger setting and listening-time tracking in `MusicService`.
- Replace queued media items when their save completes.
- Re-queue `MISSING` songs when the user tries to play them.
- Shrink or remove the Media3 streaming cache.

Deliverable: played songs persist automatically according to the trigger.

### Phase 4: Network policy

- `ACCESS_NETWORK_STATE`, a network-state helper, and streaming/saving unmetered-only settings.
- Manual offline mode switch: suppress server calls, reporting and streaming.
- Skip non-playable songs when building the queue.

Deliverable: the user controls when the app uses the network.

### Phase 5: Offline browsing

- Remove the login redirect for the offline state.
- Data source seam in library fragments. Offline Songs/Albums/Artists from `local_songs`.
- Disable network-only actions offline.

Deliverable: with offline mode on, the app is fully usable against the local library.

### Phase 6: Storage management

- Storage screen with usage breakdown and bulk cleanup actions.

Deliverable: explicit, convenient control over disk usage.

## Deferred

- Automatic offline detection and mode switching.
- Saving the stream's bytes while playing, to avoid the double fetch for direct-play songs played start to finish.
- Exporting stored songs to MediaStore so other apps can see them.
- **Server-side removals and changes.** Direction so far: items deleted on the server are removed from the local library lists and recorded in a separate log (e.g. an "expired" list) rather than vanishing silently. How removals are detected, whether the local file is kept, and how retagged items (`serverDateModified`) are handled all still need design.
- Syncing play reports and favorites made offline.
- Sharing stored files across accounts on the same server.

## Resolved Decisions

- "Wi-Fi only" means **unmetered network** (`NET_CAPABILITY_NOT_METERED`).
- Default save trigger is **after 30 seconds** of listening.
- Missing files are marked `MISSING`, shown greyed out, and re-downloaded when the user tries to play them.
- Losing legacy downloads and the persisted playback queue during migration is acceptable.

## Test Matrix

- Explicit download of a small (< cap) and a large (> cap) song. Verify the variant, file and row.
- Kill the app mid-download. Verify there is no partial file in `audio/` and that the job resumes or retries.
- Each save trigger: skip early, listen past N seconds, play to completion.
- Unmetered-only saving on cellular: the job stays queued and runs when an unmetered network returns.
- Delete a stored file outside the app: the song shows as missing (greyed out), and playing it re-queues the download.
- Offline mode: launch, browse, play stored songs, and confirm that unstored songs are skipped.
- A save completes while the song is later in the queue: the item switches to the local file.
- Delete a song/album: file and row are both gone, and the badge updates.
- Upgrade from v8 to v9 with legacy downloads: they still play and show the badge. A zero-byte legacy file streams instead.
- Upgrade from v9 to v10 with legacy downloads in the default and in a custom location: default-location files are deleted, custom-location files are left alone, and the app starts with an empty queue.
- Two accounts: stored songs are not visible across accounts.
