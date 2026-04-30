# Offline Cached Music Plan

## Goal

Implement a true offline mode so the app can browse and play cached/downloaded music without network connectivity.

This document describes:

- How local caching works today
- What already works offline
- Gaps that currently block offline mode
- A phased implementation plan

## Current Local Caching Architecture

There are three independent cache layers.

### 1) Explicit audio downloads (user initiated)

- Download actions come from song/album/artist/genre menus and start `DownloadService`.
- `DownloadService` requests Jellyfin `/Items/{id}/Download`.
- It writes to a temp file under `location_cache/download/{songId}`.
- It then copies the file to the final music path from `MusicUtil.getFileUri(song)`.
- Finally, it inserts a row in the Room `cache` table.

Practical result: downloaded songs are saved as normal files in the configured download location.

### 2) ExoPlayer media cache (implicit streaming cache)

- `LocalPlayer` creates ExoPlayer `SimpleCache` at `location_cache/exoplayer`.
- Cache size is controlled by `media_cache_size` preference.
- Data source uses `CacheDataSource` + `CacheDataSink` + LRU evictor.

Practical result: streamed media may be cached automatically, but this path is separate from explicit downloads.

### 3) Glide image disk cache

- Glide uses `location_cache/glide`.
- Cache size is controlled by `image_cache_size`.

Practical result: album art caching is separate from audio caching.

## Playback Behavior Today

Playback already uses a local-first strategy:

- For each song, `LocalPlayer` checks whether `MusicUtil.getFileUri(song)` exists.
- If yes, it plays from file URI.
- If not, it falls back to transcode/stream URI.

This means offline playback can already succeed for songs that are present at the local download path.

## What Works Offline Right Now

- Previously downloaded songs can play if the queue already contains them.
- Queue and playback position are persisted via Room (`songs` + `queueSongs`) and restored on startup.

## Current Gaps / Limitations

### 1) No true offline app mode

- On offline/auth failure broadcast, `AbsMusicContentActivity` navigates to login.
- App does not provide an offline browsing state.

### 2) Library browsing is network-only

- Main library fragments (`Songs`, `Albums`, `Playlists`, etc.) call Jellyfin APIs directly.
- There is no Room-backed fallback for offline browsing.

### 3) Cached state is weakly modeled

- UI cached badge is driven by `cache` table (`cacheDao.isCached(song.id)`).
- There is no systematic file existence verification during rendering/reconciliation.
- `CacheDao.getSongs(ids)` exists but is not used by runtime browsing/playback flows.

### 4) Cache location preference inconsistency

- `location_cache` exists in `PreferenceUtil`.
- Settings UI currently exposes only `location_download`, not `location_cache`.
- In practice, cache location defaults to app cache dir unless set elsewhere.

## Proposed Implementation Plan

## Phase 1: Define offline-playable truth

- Treat a song as offline-playable only when its final file exists at `MusicUtil.getFileUri(song)`.
- Keep `cache` table as an index/hint, but reconcile it with filesystem state.

Deliverable: consistent function/check used by playback + UI to determine local availability.

## Phase 2: Add offline app state

- Replace forced login redirect on offline state with an app-level offline mode.
- Keep main UI accessible with clear offline indication.
- Disable/hide actions that strictly require network.

Deliverable: app remains usable when network/auth is unavailable.

## Phase 3: Build offline catalog

- Persist metadata for downloaded songs in a stable local catalog (Room-backed).
- Update catalog on download completion and cleanup paths.
- Add startup reconciliation job to repair stale entries.

Deliverable: reliable local dataset for offline library screens.

## Phase 4: Library fallback strategy

- When online: existing API flows remain.
- When offline: query local catalog instead.
- Start with a minimal first screen (for example: "Downloaded Songs"), then expand to albums/artists grouping.

Deliverable: users can discover cached content without network.

## Phase 5: Playback hardening

- Before opening queue offline, filter to locally available songs.
- Surface clear UX when requested tracks are unavailable locally.
- Keep existing local-first URI behavior in `LocalPlayer`.

Deliverable: predictable playback outcomes in offline mode.

## Phase 6: Data integrity and maintenance

- Add periodic/on-start reconciliation between DB and filesystem.
- Add explicit remove-download action that updates file + DB atomically.
- Optionally expose cache cleanup tools in settings.

Deliverable: long-term correctness of offline index and user trust.

## Risks and Notes

- ExoPlayer streaming cache is opaque for user-facing "downloaded" semantics; explicit downloads should remain the primary offline guarantee.
- Existing queue persistence currently stores songs from active queue; this is not equivalent to full offline library storage.
- Introducing offline mode affects navigation/login flow and should be tested across process restarts and account switching.

## Suggested Test Matrix (when implementation starts)

- Download a song, kill app, disable network, relaunch, play song.
- Download album, remove one file manually, verify reconciliation and UI status.
- Start with empty offline catalog and no network.
- Offline queue restore after reboot/process death.
- Switch account/server and verify offline data isolation rules.
