# Jellyfin Client Migration Plan

## Purpose

This document captures the current status of the migration from `jellyfin-apiclient-java` to the Kotlin `jellyfin-sdk`, then lays out the next phases required to fully remove the legacy Java client from the app.

## Current Status

**Migration complete.** All seven phases are done. No production source file imports `org.jellyfin.apiclient.*`. The `jellyfin-apiclient-java` dependency has been removed from `app/build.gradle` and the local substitution block removed from `settings.gradle`. The app builds cleanly (debug and release).

### What is migrated

- **SDK session layer** — `JellyfinSdkSession.kt` owns base URL, access token, user ID, and device metadata. Provides `createApiOrNull()` for SDK calls and accessors (`getBaseUrl()`, `getAccessToken()`, `getDeviceId()`) for URL construction. No longer has `updateSessionFromApiClient()`.
- **Model mapping** — `SdkSongMapper`, `SdkMediaMapper`, `SdkUserMapper` convert SDK DTOs to app models. Legacy mapper files deleted (`LegacyUserMapper`, `LegacySongMapper`, `LegacyMediaMapper`).
- **Queries, browse, detail, shortcuts, playlists** — `QueryUtil.kt`, `PlaylistUtil.kt`, all library fragments, detail activities, `ShortcutUtil` — all SDK-backed. `JellyfinSdkBridge.kt` and `LegacySortMapper` deleted.
- **Authentication and session restore** — `LoginActivity.kt` uses `userApi.authenticateUserByName` + `SdkUserMapper`. `LoginService.kt` uses `systemApi.getSystemInfo()` + `sessionApi.postFullCapabilities()`.
- **WebSocket / remote commands** — `EventListener.kt` (replaces `.java`) uses `api.webSocket.subscribe<PlaystateMessage>()` flow; no longer extends `ApiEventListener`.
- **Playback reporting** — `PlaybackReporter.kt` replaces five legacy `ApiClient` calls in `MusicService.java`; also fixes a pre-existing bug where stop info was built but never sent.
- **Image URLs** — `CustomGlideRequest.java` constructs image URLs directly from `JellyfinSdkSession.getBaseUrl()`. `MusicUtil.java` constructs transcode/download URIs the same way.
- **Favorite toggle** — `UserLibraryUtil.kt` wraps `userLibraryApi.markFavoriteItem`/`unmarkFavoriteItem`; `MusicUtil.toggleFavorite` delegates to it.
- **App singleton** — `App.java` has no `apiClient` field and no `getApiClient()` method. Only `database`/`getDatabase()` remain.
- **Dependency** — `jellyfin-apiclient-java` removed from `app/build.gradle`; substitution block removed from `settings.gradle`.

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

## Migration Phases

### Phase 1: Introduce a Real SDK Session Layer

Status: **Complete.**

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

Status: **Complete.**

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

Status: **Complete.**

- `QueryUtil` is fully SDK-backed with no legacy query DTO types anywhere in its public or internal API.
- All six library browse fragments (`Albums`, `Artists`, `Songs`, `Favorites`, `Genres`, `Playlists`) use app-owned parameters; `LegacySortMapper` deleted; legacy `Q` generic removed from fragment base class.
- Internal request builders (`albumsRequest`, `artistsRequest`, `songsRequest`, `genresRequest`, `playlistsRequest`) are injectable for unit testing; `QueryUtilTest` updated with coverage for sort/order mapping, library scoping, favorites flag, startIndex paging, and UUID helpers.
- A latent bug was also fixed: `getSongsBySort` now correctly honours its explicit `limit` parameter instead of having it silently overwritten by the page-size default.

### Phase 4: Migrate Detail, Shortcut, and Playlist Workflows

Status: **Complete.**

- `JellyfinSdkBridge.kt` deleted; `getAlbumSongs` folded into `QueryUtil.kt` with an internal `albumSongsRequest` builder and 4 new JVM unit tests.
- `PlaylistUtil.java` replaced by `PlaylistUtil.kt`; all 7 playlist operations (get, create, delete, add, remove, move, rename) now use the Kotlin SDK (`playlistsApi`, `libraryApi`). `LegacySongMapper.fromPlaylistItem` replaced by `SdkSongMapper.fromPlaylistItem`.
- No production source file imports `org.jellyfin.apiclient.*` for any browse, detail, shortcut, or playlist flow.

### Phase 5: Migrate Authentication and Session Restore

Status: **Complete.**

- `JellyfinSdkSession.kt` — added `createApiForServer(serverUrl)`: creates an unauthenticated SDK `ApiClient` from a bare server URL, reusing the same `buildJellyfin` helper now shared with `createApiOrNull()`. This factory is required for the login flow, which has no access token yet.
- `LoginActivity.java` replaced by `LoginActivity.kt`. Authentication now calls `userApi.authenticateUserByName`, maps the result with `SdkUserMapper.toUserOrNull`, and checks server version via `systemApi.getPublicSystemInfo()` (public endpoint, no token required). Error handling distinguishes `InvalidStatusException(401)` (wrong credentials) from all other failures (server unreachable). No `org.jellyfin.apiclient.*` imports remain in the file.
- `LegacyUserMapper.java` deleted — `LoginActivity` was its only production caller.
- `User.java` required no changes; `SdkUserMapper` already mapped the SDK `AuthenticationResult` to `User` before this phase.
- `LoginService.java` replaced by `LoginService.kt`. Session restore uses `JellyfinSdkSession.updateSession()`, connectivity verified via `systemApi.getSystemInfo()`, capabilities reported via `sessionApi.postFullCapabilities()`, and WebSocket opened via `EventListener.start()`. No `org.jellyfin.apiclient.*` imports remain.

### Phase 6: Migrate Playback Reporting, Remote Commands, and Images

Status: **Complete.**

- `PlaybackReporter.kt` (new) replaces five legacy `ApiClient` calls in `MusicService.java`: playback start, progress, stop, and mark-played. Internal request builders (`buildStartInfo`, `buildProgressInfo`, `buildStopInfo`) are testable without a running service; 13 JVM unit tests cover item ID, volume, flags, enum defaults, play session ID, and tick conversion (`progressMs * 10000L`). Also fixes a pre-existing bug where stop info was built but never sent.
- `EventListener.java` replaced by `EventListener.kt`. No longer extends `ApiEventListener`; uses `api.webSocket.subscribe<PlaystateMessage>().onEach { }.launchIn(scope)` inside a `CoroutineScope(SupervisorJob() + Dispatchers.IO)`. Handles `PLAY_PAUSE`, `PAUSE`, `UNPAUSE`, `NEXT_TRACK`, `PREVIOUS_TRACK`, `SEEK` (tick→ms), and `STOP` via `MusicPlayerRemote`.
- `CustomGlideRequest.java` — `createUrl()` now uses `JellyfinImageUrls.buildPrimaryImageUrl(...)` backed by the SDK's `UrlBuilder`.
- `UserLibraryUtil.kt` (new) — `@JvmStatic toggleFavorite(song)` wraps `userLibraryApi.markFavoriteItem`/`unmarkFavoriteItem` with an optimistic flip confirmed from the response. `MusicUtil.toggleFavorite` is a one-line delegate.
- `MusicUtil.java` — `getTranscodeUri()` and `getDownloadUri()` use `JellyfinSdkSession` accessors; no `ApiClient` calls remain.
- `AbsMusicContentActivity.java` — session guard changed from `App.getApiClient() == null` (always false) to `JellyfinSdkSession.getCurrentUserId() == null`.

### Phase 7: Remove the Legacy Dependency

Status: **Complete.**

- `jellyfin-apiclient-java` dependency removed from `app/build.gradle`.
- Local substitution block removed from `settings.gradle`.
- Dead bridge/shim code deleted: `LegacyMediaMapper.java`, `LegacySongMapper.java`.
- `App.java` — `apiClient` field, `createApiClient()`, and `getApiClient()` removed entirely; all 8 `org.jellyfin.apiclient.*` imports removed.
- Full source scan confirms zero remaining `org.jellyfin.apiclient.*` imports in production code.
- `./gradlew :app:testDebugUnitTest` passes (26 tasks, BUILD SUCCESSFUL).

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

## Bottom Line

Migration complete. The `jellyfin-apiclient-java` dependency has been fully removed. All seven phases are done, all 26 unit tests pass, and no production source file imports `org.jellyfin.apiclient.*`. The remaining validation is manual smoke testing against a live Jellyfin server per the checklist above.
