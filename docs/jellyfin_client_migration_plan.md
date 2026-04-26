# Jellyfin Client Migration Plan

## Purpose

This document captures the current status of the migration from `jellyfin-apiclient-java` to the Kotlin `jellyfin-sdk`, then lays out the next phases required to fully remove the legacy Java client from the app.

## Current Status

### What is already migrated

- The Kotlin SDK is already added in [app/build.gradle](/mnt/data/source/gelli/app/build.gradle:53) as `org.jellyfin.sdk:jellyfin-core:1.8.8`.
- A Kotlin bridge exists in [JellyfinSdkBridge.kt](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/util/JellyfinSdkBridge.kt:25) to let Java code call SDK-backed requests.
- One live feature is already using the Kotlin SDK:
  - [AlbumDetailActivity.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/activities/details/AlbumDetailActivity.java:77) fetches album songs through `JellyfinSdkBridge.getAlbumSongs(...)`.

### What is still on the legacy Java client

- The app still depends on `com.github.jellyfin.jellyfin-apiclient-java:android:0.7.3` in [app/build.gradle](/mnt/data/source/gelli/app/build.gradle:55).
- `settings.gradle` still contains local dependency substitution support for the Java client in [settings.gradle](/mnt/data/source/gelli/settings.gradle:5).
- `App` still exposes a global legacy `ApiClient` singleton in [App.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/App.java:29) and [App.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/App.java:66).
- A scan found 34 Java/Kotlin source files in `app/src/main/java` that still reference the legacy client or `App.getApiClient()`.
- The Kotlin SDK currently appears in only 2 app source files: [JellyfinSdkBridge.kt](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/util/JellyfinSdkBridge.kt:25) and [AlbumDetailActivity.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/activities/details/AlbumDetailActivity.java:34).

### Main legacy dependency clusters

1. Query and browse layer
   - [QueryUtil.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/util/QueryUtil.java:27) is still the main browse/query utility for libraries, albums, artists, songs, playlists, genres, and search-like item fetches.
   - Several fragments and detail screens still build legacy query DTOs directly (`ItemQuery`, `ArtistsQuery`, `ItemsByNameQuery`, `PlaylistItemQuery`).

2. App/session bootstrap
   - [App.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/App.java:66) constructs the legacy `ApiClient`.
   - [LoginActivity.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/activities/LoginActivity.java:86) authenticates through the Java client.
   - [LoginService.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/service/LoginService.java:59) restores auth, checks server availability, reports capabilities, and opens the WebSocket through the Java client.

3. Playback reporting and remote session events
   - [MusicService.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/service/MusicService.java:58) still imports legacy playback-reporting models and reports playback start/progress/stop through `App.getApiClient()`.
   - [EventListener.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/helper/EventListener.java:18) extends the Java client's event listener for remote session commands and library/session events.

4. Model mapping and DTO coupling
   - App model constructors still accept legacy DTOs directly:
     - [Song.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/model/Song.java:49)
     - [Album.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/model/Album.java:23)
     - `Artist`, `Genre`, `Playlist`, `PlaylistSong`, and `User` follow the same pattern.
   - [User.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/model/User.java:24) is still built from legacy `AuthenticationResult`.

5. Playlist and shortcut utilities
   - [PlaylistUtil.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/util/PlaylistUtil.java:18) still uses legacy playlist request/response types for fetch, create, update, delete, reorder, and rename flows.
   - [ShortcutUtil.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/util/ShortcutUtil.java:18) still uses legacy item query DTOs.

6. Image URL generation
   - [CustomGlideRequest.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/glide/CustomGlideRequest.java:98) still calls `App.getApiClient().GetImageUrl(...)`.

## Migration Constraints

- This is not just a query-layer migration. The legacy Java client currently provides:
  - authentication
  - request DTOs
  - callback wrappers
  - playback reporting
  - WebSocket/event listener hooks
  - image URL construction
- The current bridge pattern recreates SDK client state from the legacy client. That is useful during transition, but it cannot be the end state because it still depends on `ApiClient`.
- The model layer is still coupled to legacy DTO classes, which means query migration alone will not remove the Java dependency.

## Recommended Migration Phases

### Phase 1: Introduce a Real SDK Session Layer

Goal: stop treating the legacy `ApiClient` as the app's source of truth.

Work:

- Add a single SDK-backed session/provider abstraction that owns:
  - base URL
  - access token
  - user ID
  - device/client metadata
  - API creation
- Keep the API Java-callable where needed, but move the source of truth away from `App.getApiClient()`.
- Refactor [JellyfinSdkBridge.kt](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/util/JellyfinSdkBridge.kt:25) to use the new session/provider instead of reading credentials from the legacy client.
- Define how login persistence will feed this session object after authentication restore.

Exit criteria:

- New SDK calls no longer require `ApiClient` to exist.
- SDK setup is centralized instead of recreated ad hoc per request.

### Phase 2: Decouple App Models from Legacy DTOs

Goal: make app models independent of `org.jellyfin.apiclient.model.*`.

Work:

- Replace constructors like `new Song(BaseItemDto)` and `new Album(BaseItemDto)` with mapper/factory code that converts SDK models into app models.
- Apply the same approach to:
  - `Artist`
  - `Genre`
  - `Playlist`
  - `PlaylistSong`
  - `User`
- Prefer one dedicated mapper package rather than scattering conversion logic across activities and utilities.
- Preserve current field behavior exactly:
  - ID normalization
  - blurhash selection
  - favorite state
  - media source mapping
  - album/artist fallback logic

Exit criteria:

- App model classes no longer import legacy Jellyfin DTO classes.
- Both old and new call sites can map into the same app models during transition.

### Phase 3: Migrate Read-Only Library Queries

Goal: replace the biggest legacy surface area first: browsing and search reads.

Work:

- Replace `QueryUtil` with SDK-backed equivalents for:
  - libraries
  - albums
  - artists
  - songs
  - genres
  - playlists
  - mixed search results
- Stop exposing legacy query DTOs (`ItemQuery`, `ArtistsQuery`, `ItemsByNameQuery`) to UI code.
- Introduce app-owned query/filter objects or method parameters that reflect the actual UI needs:
  - parent/library scope
  - sort
  - limit/paging
  - recursive flag
  - filters like favorites
- Migrate the library fragments and search screens onto the new repository/query surface.

Priority files:

- [QueryUtil.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/util/QueryUtil.java:27)
- `fragments/library/*`
- [SearchActivity.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/activities/SearchActivity.java:27)
- [MainActivity.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/activities/MainActivity.java:37)

Exit criteria:

- Core browse/search flows no longer import legacy query DTOs.
- `currentLibrary` handling is preserved without depending on legacy `BaseItemDto`.

### Phase 4: Migrate Detail, Shortcut, and Playlist Workflows

Goal: remove the remaining user-facing fetch/mutation utilities that still depend on the Java client.

Work:

- Fold the album-song fetch from `JellyfinSdkBridge` into the same query/repository approach used elsewhere.
- Migrate shortcut queries in [ShortcutUtil.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/util/ShortcutUtil.java:18).
- Migrate playlist operations in [PlaylistUtil.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/util/PlaylistUtil.java:18):
  - get items
  - create playlist
  - rename playlist
  - add items
  - remove items
  - reorder items
  - delete playlist
- Update detail screens that still construct legacy playlist/query types directly.

Priority files:

- [AlbumDetailActivity.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/activities/details/AlbumDetailActivity.java:77)
- [PlaylistDetailActivity.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/activities/details/PlaylistDetailActivity.java:33)
- [ArtistDetailActivity.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/activities/details/ArtistDetailActivity.java:38)
- [GenreDetailActivity.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/activities/details/GenreDetailActivity.java:27)
- [ShortcutUtil.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/util/ShortcutUtil.java:18)
- [PlaylistUtil.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/util/PlaylistUtil.java:18)

Exit criteria:

- All browse/detail/playlist/shortcut flows are SDK-backed.
- The bridge is either much smaller or no longer needed for content queries.

### Phase 5: Migrate Authentication and Session Restore

Goal: remove reliance on legacy login/session objects.

Work:

- Replace legacy login calls in [LoginActivity.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/activities/LoginActivity.java:86) with SDK-backed authentication.
- Update [User.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/model/User.java:24) so persisted user records are not built from legacy `AuthenticationResult`.
- Refactor [LoginService.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/service/LoginService.java:59) to restore session state, validate connectivity, and report capabilities through SDK-compatible code.
- Decide whether server capability reporting stays as-is, is reimplemented with SDK APIs, or is deferred if not supported yet.

Exit criteria:

- Login and app restart no longer require `ApiClient`.
- Session restoration is owned by the new SDK session layer.

### Phase 6: Migrate Playback Reporting, Remote Commands, and Images

Goal: remove the non-browse legacy features that can block final dependency removal.

Work:

- Replace legacy playback reporting in [MusicService.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/service/MusicService.java:779):
  - playback start
  - progress
  - stop
  - mark played
- Replace or redesign [EventListener.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/helper/EventListener.java:18) for remote command and session event handling.
- Replace [CustomGlideRequest.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/glide/CustomGlideRequest.java:98) image URL generation with an SDK-compatible helper.

Notes:

- This phase may expose SDK gaps compared with the old client, especially around WebSocket/session event handling.
- If the SDK does not yet provide parity for one of these areas, isolate the remaining legacy usage behind a very small adapter and make that gap explicit.

Exit criteria:

- Playback reporting no longer imports legacy session models.
- Image loading no longer depends on `GetImageUrl(...)`.
- Any remaining legacy usage is confined to a clearly documented temporary shim.

### Phase 7: Remove the Legacy Dependency

Goal: complete the migration and verify that the Java client can be deleted cleanly.

Work:

- Remove the Java client dependency from [app/build.gradle](/mnt/data/source/gelli/app/build.gradle:55).
- Remove the local substitution block from [settings.gradle](/mnt/data/source/gelli/settings.gradle:5).
- Delete dead bridge/shim code that only existed for the parallel-client transition.
- Run a full source scan for:
  - `org.jellyfin.apiclient`
  - `App.getApiClient()`
  - legacy DTO/query/session classes
- Build debug and release variants after cleanup.

Exit criteria:

- No app source imports `org.jellyfin.apiclient.*`.
- The app builds and core manual flows still work.

## Suggested PR Sequence

1. PR 1: SDK session layer and model mappers.
2. PR 2: `QueryUtil` replacement plus library/search migration.
3. PR 3: detail screens, shortcuts, and playlists.
4. PR 4: login/session restore migration.
5. PR 5: playback reporting, event handling, and image URL migration.
6. PR 6: dependency removal and cleanup.

## Validation Checklist Per Phase

- Login works against a real Jellyfin server.
- Library selection still scopes content correctly.
- Albums, artists, songs, genres, playlists, and search all load expected data.
- Album detail still resolves track ordering and metadata correctly.
- Playlist create/rename/add/remove/reorder/delete all behave correctly.
- Playback still reports start/progress/stop and marks tracks played when expected.
- Remote/media-session behavior is unchanged if WebSocket/session commands are still supported.
- Image loading still resolves album/artist artwork correctly.
- App restart still restores the active account and can reconnect cleanly.

## Main Risks

1. The model layer is more coupled to legacy DTOs than the docs implied. This makes mapper work a prerequisite, not a cleanup task.
2. Several UI screens still construct legacy query classes directly, so `QueryUtil` replacement alone will not finish browse migration.
3. Playback reporting and remote session events may be the hardest parity area if the Kotlin SDK does not mirror the old Java client's helper surface.
4. Image URL generation currently relies on a convenience method from the Java client; replacing it may require custom URL construction or a dedicated image helper.

## Bottom Line

The migration is started, but it is still early. The Kotlin SDK is live in one narrow content path, while the legacy Java client remains the app-wide source of truth for session state, most queries, model construction, playback reporting, WebSocket events, playlists, shortcuts, and image URLs.

The next correct step is not to convert more one-off screens. It is to establish an SDK-owned session layer and DTO-to-model mapper layer first, then migrate browse/detail/auth/playback features in that order.
