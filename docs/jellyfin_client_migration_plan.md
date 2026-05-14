# Jellyfin Client Migration Plan

## Purpose

This document captures the current status of the migration from `jellyfin-apiclient-java` to the Kotlin `jellyfin-sdk`, then lays out the next phases required to fully remove the legacy Java client from the app.

## Current Status

### What is already migrated

- The Kotlin SDK is already added in [app/build.gradle](/mnt/data/source/gelli/app/build.gradle:53) as `org.jellyfin.sdk:jellyfin-core:1.8.8`.
- An SDK session abstraction is in place in [JellyfinSdkSession.kt](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/util/JellyfinSdkSession.kt:16), and is initialized/populated from app startup and login restore paths.
- Model mapping is in place (`SdkSongMapper`, `SdkMediaMapper`, `SdkUserMapper`) and app models are no longer constructed directly from legacy DTO constructors. `SdkSongMapper` now also maps `PlaylistSong` via `fromPlaylistItem`.
- Read/query migration and all detail/shortcut/playlist flows are complete:
  - [QueryUtil.kt](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/util/QueryUtil.kt:32) replaced `QueryUtil.java` and executes all reads through the Kotlin SDK, including album song fetches (previously in `JellyfinSdkBridge`, now deleted). No legacy query DTO types remain in its public or internal API.
  - Library browse fragments (`Albums`, `Artists`, `Songs`, `Favorites`, `Genres`, `Playlists`) call SDK-backed `QueryUtil` methods with app-owned parameters; they no longer import or construct legacy query DTOs. `LegacySortMapper` has been deleted.
  - The fragment base class (`AbsLibraryPagerRecyclerViewFragment`) no longer carries a legacy query DTO generic type parameter.
  - [MainActivity.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/activities/MainActivity.java:48) now tracks `currentLibrary` as an app-owned `QueryUtil.Library` model instead of legacy `BaseItemDto`.
  - `SearchActivity`, `ArtistDetailActivity`, `GenreDetailActivity`, `AlbumDetailActivity`, `AlbumAdapter`, `ArtistAdapter`, `PlaylistAdapter`, and `ShortcutUtil` use app-owned `QueryUtil` methods with no legacy DTO imports.
  - [PlaylistUtil.kt](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/util/PlaylistUtil.kt) replaced `PlaylistUtil.java`; all seven playlist operations (fetch, create, delete, add items, remove items, move item, rename) now use `playlistsApi` and `libraryApi` from the Kotlin SDK. `PlaylistDetailActivity` calls these directly.

### What is still on the legacy Java client

- The app still depends on `com.github.jellyfin.jellyfin-apiclient-java:android:0.7.3` in [app/build.gradle](/mnt/data/source/gelli/app/build.gradle:55).
- `settings.gradle` still contains local dependency substitution support for the Java client in [settings.gradle](/mnt/data/source/gelli/settings.gradle:5).
- `App` still exposes a global legacy `ApiClient` singleton in [App.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/App.java:29) and [App.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/App.java:66).

Verified call sites that still touch the legacy `ApiClient` (as of the Phase 6B commit `6b758a42`):

| File | Sites | Purpose |
|---|---|---|
| [App.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/App.java:66) | constructs `ApiClient` | global singleton |
| [EventListener.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/helper/EventListener.java:19) | extends `ApiEventListener` | remote `PlaystateCommand` dispatch (PlayPause / Pause / Unpause / NextTrack / PreviousTrack / Seek / Stop) — the rest of the callbacks are `Log.i(...)` only |
| [MusicUtil.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/util/MusicUtil.java:35) | 4 sites | `getTranscodeUri` (stream URL — uses `getApiUrl/getCurrentUserId/getDeviceId/getAccessToken`), `getDownloadUri` (download URL — `getApiUrl/getAccessToken`), `toggleFavorite` (`UpdateFavoriteStatusAsync`) |
| [AbsMusicContentActivity.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/activities/base/AbsMusicContentActivity.java:47) | 2 sites | `App.getApiClient() == null` null checks gating service start |
| [LoginService.kt](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/service/LoginService.kt:67) | 1 site | `legacyWebSocketBootstrap` helper — Phase 6 shim |
| [MusicService.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/service/MusicService.java:767) | 1 site | `ensureWebSocket()` in `ProgressHandler.onNext` — Phase 6 shim |
| [JellyfinSdkSession.kt](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/util/JellyfinSdkSession.kt:62) | dead method | `updateSessionFromApiClient(ApiClient?)` — no remaining callers, can be deleted alongside `App.getApiClient()` |
| [LegacySongMapper.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/mapper/LegacySongMapper.java:6), [LegacyMediaMapper.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/mapper/LegacyMediaMapper.java:8) | imports | dead in production; kept solely because the instrumented `PlaylistBackendIntegrationTest` still imports them |

### Main legacy dependency clusters

1. Query, browse, detail, shortcut, and playlist layer
   - Complete. No production source file imports `org.jellyfin.apiclient.*` for any of these flows.

2. App/session bootstrap
   - [App.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/App.java:66) constructs the legacy `ApiClient`.
   - `LoginActivity` — migrated. Authentication now uses `userApi.authenticateUserByName` and `SdkUserMapper.toUserOrNull`; no legacy imports remain.
   - `LoginService` — migrated. Server availability is checked via `systemApi.getSystemInfo()` (authenticated; doubles as token validation) and capabilities are reported via `sessionApi.postCapabilities(...)`. Only the three-call WebSocket bootstrap (`ChangeServerLocation`, `SetAuthenticationInfo`, `ensureWebSocket`) remains against the legacy `ApiClient`, isolated in a `legacyWebSocketBootstrap` helper and explicitly marked as a Phase 6 shim until `EventListener` is migrated.

3. Playback reporting and remote session events
   - `MusicService.java` playback reporting is migrated (Phase 6B complete). The three legacy session model imports (`PlaybackStartInfo`, `PlaybackProgressInfo`, `PlaybackStopInfo`) and their associated `EmptyResponse`/`Response` callbacks have been removed. Only `App.getApiClient().ensureWebSocket()` remains in `ProgressHandler.onNext()` as an explicit Phase 6C shim.
   - [EventListener.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/helper/EventListener.java:18) extends the Java client's event listener for remote session commands and library/session events. This and the `ensureWebSocket` shim are the last legacy surfaces remaining.

4. Model mapping and DTO coupling
   - All app media models now map through dedicated mapper utilities. The only remaining legacy mapper is `LegacySongMapper.java`, which is dead code in production (kept because the backend integration test still imports it directly).

5. Image URL generation
   - Complete. `CustomGlideRequest.java` now delegates to `JellyfinImageUrls.buildPrimaryImageUrl(...)`. The legacy `GetImageUrl` call and its two imports (`ImageOptions`, `ImageType` from `org.jellyfin.apiclient.*`) have been removed.

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

## Regression Testing Strategy

Phase 3 shipped with three behavioral regressions that the legacy Java code did not have (fragment transaction state-loss, sort/order parity gaps, and playlist queries inheriting `currentLibrary` as `parentId`). All three were in pure translation logic that runs without an emulator or a live server. The lesson: **every future phase must land alongside JVM unit tests for the translation/mapping shims it introduces or modifies.**

Conventions for this codebase:

- Tests live in `app/src/test/kotlin/...` and run via `./gradlew :app:testDebugUnitTest` (no emulator required, sub-second once compiled).
- The existing instrumented `PlaylistBackendIntegrationTest` is valuable but requires backend credentials and is not a substitute for fast regression coverage on pure logic.
- The first JVM test class — `QueryUtilTest` — establishes the pattern: take Android/global dependencies as injected parameters in an `internal` overload, keep the public no-arg overload as a thin delegate, and exercise the overload directly. New shims should follow the same shape rather than reaching for Robolectric.

What every phase MUST add unit tests for, before being declared complete:

- Request builders — for each migrated query, assert the resulting SDK request matches the legacy behavior on `parentId` scoping, sort/order, paging, recursive flag, and filters (favorites, artist/genre/album scope).
- Model mappers — for `SdkMediaMapper`, `SdkSongMapper`, and any new mapper, assert ID format (dashless), blurhash selection, favorite state, artist fallback (`artistItems` → `albumArtists`), and media-source field copying against fixed `BaseItemDto` fixtures.
- ID/UUID helpers — round-trip dashed and dashless forms; reject malformed input.
- Any new pure helper introduced by a shim (URL builders, request DTOs, response normalizers).

What MUST NOT be the only line of defense:

- Manual testing against a live server. It catches gross failures but misses parity gaps that only show up under specific filter combinations or library configurations.
- Successful compilation. The Kotlin compiler will not catch a missing `parentId = null` override, an inverted ascending/descending mapping, or a dropped media-source field.

When a regression IS found in production code, the fix PR must add a unit test that fails before the fix and passes after. This converts every reported bug into permanent coverage and is the cheapest way to ratchet regression risk down across the remaining phases.

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

Status update:

- Complete.
- `QueryUtil` is fully SDK-backed with no legacy query DTO types anywhere in its public or internal API.
- All six library browse fragments (`Albums`, `Artists`, `Songs`, `Favorites`, `Genres`, `Playlists`) use app-owned parameters; `LegacySortMapper` deleted; legacy `Q` generic removed from fragment base class.
- Internal request builders (`albumsRequest`, `artistsRequest`, `songsRequest`, `genresRequest`, `playlistsRequest`) are injectable for unit testing; `QueryUtilTest` updated with coverage for sort/order mapping, library scoping, favorites flag, startIndex paging, and UUID helpers.
- A latent bug was also fixed: `getSongsBySort` now correctly honours its explicit `limit` parameter instead of having it silently overwritten by the page-size default.

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
- For each migrated function, add JVM unit tests for the request builder and any model mapping introduced — follow the `QueryUtilTest` pattern (inject globals as parameters, keep the public overload as a thin delegate, assert parity with the legacy behavior).

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

Status update:

- Complete.
- `JellyfinSdkBridge.kt` deleted; `getAlbumSongs` folded into `QueryUtil.kt` with an internal `albumSongsRequest` builder and 4 new JVM unit tests.
- `PlaylistUtil.java` replaced by `PlaylistUtil.kt`; all 7 playlist operations (get, create, delete, add, remove, move, rename) now use the Kotlin SDK (`playlistsApi`, `libraryApi`). `LegacySongMapper.fromPlaylistItem` replaced by `SdkSongMapper.fromPlaylistItem`.
- No production source file imports `org.jellyfin.apiclient.*` for any browse, detail, shortcut, or playlist flow.

### Phase 5: Migrate Authentication and Session Restore

Goal: remove reliance on legacy login/session objects.

Work:

- Replace legacy login calls in `LoginActivity` with SDK-backed authentication.
- Update [User.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/model/User.java:24) so persisted user records are not built from legacy `AuthenticationResult`.
- Refactor [LoginService.java](/mnt/data/source/gelli/app/src/main/java/com/dkanada/gramophone/service/LoginService.java:59) to restore session state, validate connectivity, and report capabilities through SDK-compatible code.
- Decide whether server capability reporting stays as-is, is reimplemented with SDK APIs, or is deferred if not supported yet.

Exit criteria:

- Login and app restart no longer require `ApiClient`.
- Session restoration is owned by the new SDK session layer.

Status update:

- `JellyfinSdkSession.kt` — added `createApiForServer(serverUrl)`: creates an unauthenticated SDK `ApiClient` from a bare server URL, reusing the same `buildJellyfin` helper now shared with `createApiOrNull()`. This factory is required for the login flow, which has no access token yet.
- `LoginActivity.java` replaced by `LoginActivity.kt`. Authentication now calls `userApi.authenticateUserByName`, maps the result with `SdkUserMapper.toUserOrNull`, and checks server version via `systemApi.getPublicSystemInfo()` (public endpoint, no token required). Error handling distinguishes `InvalidStatusException(401)` (wrong credentials) from all other failures (server unreachable). No `org.jellyfin.apiclient.*` imports remain in the file.
- `LegacyUserMapper.java` deleted — `LoginActivity` was its only production caller.
- `User.java` required no changes; `SdkUserMapper` already mapped the SDK `AuthenticationResult` to `User` before this phase.
- `LoginService.java` replaced by `LoginService.kt` (Phase 5C). Session restore now runs `systemApi.getSystemInfo()` on the authenticated SDK `ApiClient` (which validates both server reachability and token validity in a single call) and reports capabilities via `sessionApi.postCapabilities(supportsMediaControl = true, supportsPersistentIdentifier = true)`. The previous `EmptyResponse`/`Response<SystemInfo>` callbacks and the `ClientCapabilities` DTO are gone. A new JVM unit test, `LoginServiceTest`, pins the capability default flags so a silent flip during refactors fails the build instead of degrading remote-control behavior against a live server.
- The three legacy `ApiClient` calls required to keep the WebSocket alive (`ChangeServerLocation`, `SetAuthenticationInfo`, `ensureWebSocket`) are isolated in a `legacyWebSocketBootstrap` helper inside `LoginService.kt` and labeled as an explicit Phase 6 shim. They will be removed when `EventListener` is migrated.

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

Status update:

- Phase 6A complete (image URL generation migrated).
  - `CustomGlideRequest.java` no longer calls `App.getApiClient().GetImageUrl(...)`. The two legacy imports (`org.jellyfin.apiclient.model.dto.ImageOptions`, `org.jellyfin.apiclient.model.entities.ImageType`) have been removed.
  - New file `JellyfinImageUrls.kt` provides a `@JvmStatic` `buildPrimaryImageUrl(itemId, maxHeight)` method backed by the SDK's `UrlBuilder`. The internal overload accepts `baseUrl` for testability.
  - URL shape is identical to the legacy path: `{baseUrl}/Items/{dashed-uuid}/Images/Primary?maxHeight=800`. Existing Glide disk caches are not invalidated.
  - 9 new JVM unit tests in `JellyfinImageUrlsTest` cover: expected URL for dashless and dashed IDs, custom `maxHeight`, default `maxHeight=800`, null/blank `baseUrl` path-only fallback, trailing-slash normalization, expected path segments, and invalid UUID fallback.

- Phase 6B complete (playback reporting migrated).
  - New file `PlaybackReporter.kt` (`object PlaybackReporter`) provides `@JvmStatic` methods `reportStart`, `reportProgress`, and `markPlayed`, each running fire-and-forget on a background thread via `Thread { runBlocking { ... } }.start()`. All three call `api.playStateApi` from the Kotlin SDK.
  - Internal `buildStartInfo` and `buildProgressInfo` builders construct SDK `PlaybackStartInfo` / `PlaybackProgressInfo` DTOs with the same field values as the legacy setter calls in `MusicService.java`.
  - `MusicService.java` `ProgressHandler`: the three legacy session model imports (`PlaybackStartInfo`, `PlaybackProgressInfo`, `PlaybackStopInfo`) and their `EmptyResponse`/`Response` imports have been removed. `onNext()`, `onProgress()`, and `onStop()` now call `PlaybackReporter` methods instead.
  - `onStop()` latent bug preserved for parity: the legacy code constructed a `PlaybackStopInfo` but never sent it to the server (only cancelled the local timer). The migrated `onStop()` simply cancels the timer, matching that behavior exactly. The dead `PlaybackStopInfo` construction was deleted since its import is gone; this is documented here for followup.
  - `App.getApiClient().ensureWebSocket()` in `onNext()` is retained as the Phase 6C shim — identical to the shim in `LoginService.kt`.
  - 14 new JVM unit tests in `PlaybackReporterTest` pin all fields set by `buildStartInfo` and `buildProgressInfo` (itemId UUID parsing, canSeek, isPaused, volumeLevel, positionTicks conversion, playSessionId pass-through, playMethod, repeatMode, playbackOrder).

- Phase 6C is **paused pending scoping decision** (see "Open scoping questions for Phase 6C" below). Audit performed at the end of Phase 6B surfaced legacy `ApiClient` callers in `MusicUtil.java` (stream URL, download URL, favorite toggle) and `AbsMusicContentActivity.java` (null checks gating service start) that were not previously called out in this plan. These prevent the legacy `ApiClient` field on `App.java` from being deleted even after `EventListener` is migrated.

#### Open scoping questions for Phase 6C

1. **Bundle or split the remaining work?** Three credible scopings:
   - (a) Phase 6C = only `EventListener` + the two WebSocket shims (`LoginService.legacyWebSocketBootstrap`, `MusicService.ensureWebSocket`). Defer `MusicUtil` and `AbsMusicContentActivity` to a new Phase 6D, then drop the `App.java` `ApiClient` field in Phase 7. Smallest, most reviewable PRs.
   - (b) One bundled Phase 6C that finishes everything the legacy `ApiClient` does: `EventListener` + `MusicUtil` + `AbsMusicContentActivity` + drop `App.getApiClient()` and the legacy import block from `App.java`. One bigger PR; reaches Phase 7 in one step but mixes WebSocket parity work with URL/favorite work that has nothing to do with sockets.
   - (c) Do `MusicUtil` + `AbsMusicContentActivity` first as the new Phase 6C (mechanical migration; no SDK parity risk), leaving `EventListener` for a later phase. Buys time to confirm the SDK's `SocketApi` / `SubscriptionExtensions` (`org.jellyfin.sdk.api.sockets.*` exists in 1.8.8) has parity with the legacy callbacks before committing to it.

2. **`MusicUtil.toggleFavorite` parity.** The legacy code calls `UpdateFavoriteStatusAsync(itemId, userId, favorite, Response<UserItemDataDto>)`. The SDK equivalent on `userLibraryApi` returns a `UserItemDataDto` synchronously from a `suspend` function. Decide whether to mirror the existing callback shape or move to `Thread { runBlocking { ... } }` like `PlaylistUtil` already does — the latter is more consistent with the rest of the migration.

3. **Streaming URL construction.** `MusicUtil.getTranscodeUri` builds an `/Audio/{id}/universal?...` URL by hand using `apiClient.getApiUrl/getCurrentUserId/getDeviceId/getAccessToken`. Two options:
   - Build the URL the same way, replacing each accessor with a `JellyfinSdkSession.getXxx()` call (e.g. `getBaseUrl()` already exists; would need to add `getAccessToken()` / `getDeviceId()`).
   - Use the SDK's `audioApi.getUniversalAudioStreamUrl(...)` if present in 1.8.8 — needs verification before committing.

   The hand-built URL form is what ExoPlayer hits today, so option 1 is lower-risk for playback regressions. Confirm before choosing.

4. **`onStop()` latent bug followup.** The legacy `ProgressHandler.onStop()` constructed a `PlaybackStopInfo` but never reported it. Phase 6B preserved that behavior. Decide whether Phase 6C (or a separate small PR) should fix this by calling `playStateApi.reportPlaybackStopped(...)`. Fixing it is a behavior change against the server (it will now see stop events), so it warrants a deliberate decision rather than a silent fix.

5. **`JellyfinSdkSession.updateSessionFromApiClient(ApiClient?)`** has no remaining callers in production. Delete it as part of Phase 6C, or hold it until Phase 7 cleanup. Either is fine; deleting it now means the file would no longer import `org.jellyfin.apiclient.interaction.ApiClient`.

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

Required automated checks (must be green before a phase is declared complete):

- `./gradlew :app:testDebugUnitTest` passes, including new tests for any shim introduced by the phase (request builder, mapper, URL helper, etc.).
- For every public migrated function, at least one unit test asserts behavioral parity with the legacy path on the dimensions called out in the Regression Testing Strategy section (parentId, sort/order, paging, filters; ID format, blurhash, favorite, artist fallback).
- Any bug found and fixed during the phase is locked in by a test that fails on the broken code and passes on the fix.

Required manual smoke checks against a real Jellyfin server (after automated checks pass):

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

1. **Remote session WebSocket parity.** `EventListener.java` is the only meaningful behavior left on the legacy `ApiClient` — it dispatches `PlaystateCommand` messages (PlayPause / Pause / Unpause / NextTrack / PreviousTrack / Seek / Stop) into `MusicPlayerRemote`. The SDK 1.8.8 ships `org.jellyfin.sdk.api.sockets.*` (`SocketApi`, `SubscriptionExtensions`, `SocketReconnectPolicy`), so parity is achievable, but the lifecycle (when to subscribe, how reconnects interact with our `MusicService` lifecycle) needs validation against a real server before commit.
2. **Streaming URL construction.** `MusicUtil.getTranscodeUri` builds an `/Audio/{id}/universal?...` URL by hand. Any reshape of this URL risks ExoPlayer regressions that are hard to catch in unit tests. The lowest-risk migration is to keep the same hand-built URL and only swap the four accessor calls (`getApiUrl/getCurrentUserId/getDeviceId/getAccessToken`) onto `JellyfinSdkSession`.
3. **Favorite toggling.** `MusicUtil.toggleFavorite` is the last write call still going through the legacy client. The SDK equivalent (`userLibraryApi.markFavoriteItem` / `unmarkFavoriteItem`) returns a `UserItemDataDto` synchronously from a `suspend` function — different shape from the legacy callback. Behavioral parity is straightforward but UI feedback wiring needs care.
4. **Latent `onStop()` non-report.** Phase 6B preserved a legacy bug where `PlaybackStopInfo` is built but never sent. Server-side "currently playing" state may linger past local stop until the timeout. Fix is a one-line `playStateApi.reportPlaybackStopped(...)` call but it is a behavior change that warrants a deliberate decision.

## Bottom Line

The migration has cleared every read-path surface and most write-path surfaces. Phases 5C, 6A, and 6B have landed, removing legacy code for session restore, capability reporting, image URL generation, and playback reporting (start/progress/markPlayed). JVM unit-test coverage is now 62 tests across 5 files (`QueryUtilTest`, `PlaylistUtilTest`, `LoginServiceTest`, `JellyfinImageUrlsTest`, `PlaybackReporterTest`) and the build is green.

The remaining legacy `ApiClient` surface is now well-bounded and accurately enumerated in the table under "What is still on the legacy Java client". Phase 6C is paused on the open scoping questions documented under "Phase 6 Status update" — the choice between a single bundled Phase 6C and splitting it into smaller 6C/6D PRs is the gating decision. Once that is resolved, the only remaining work to enable Phase 7 dependency removal is: `EventListener` → SDK socket subscriptions, `MusicUtil` 4 sites → SDK + `JellyfinSdkSession` accessors, and the two `AbsMusicContentActivity` null checks → `JellyfinSdkSession.getBaseUrl() != null` or equivalent.
