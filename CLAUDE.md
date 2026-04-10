# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires STORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD env vars)
./gradlew assembleRelease

# Install debug build on connected device
./gradlew installDebug
```

There are no unit or instrumentation tests in this project.

## Architecture Overview

Gelli is an Android music player app (Java + Kotlin, minSdk 23) that streams from a Jellyfin server. The package root is `com.dkanada.gramophone`.

### Global State — `App.java`

`App` is the `Application` subclass and the single source of truth for two global singletons accessed throughout the codebase:
- `App.getApiClient()` — the Jellyfin `ApiClient` (deprecated Java library)
- `App.getDatabase()` — the Room `JellyDatabase`

### Jellyfin API — Two Clients in Parallel

The app uses **two Jellyfin client libraries simultaneously** during an ongoing migration:

1. **`jellyfin-apiclient-java`** (deprecated) — used by `QueryUtil.java` and most existing code. All calls go through `App.getApiClient()` and use callback-style `Response<T>` objects.
2. **`jellyfin-sdk` (Kotlin)** — the replacement. `JellyfinSdkBridge.kt` is the bridging layer that creates a fresh SDK instance from the existing `ApiClient` credentials and exposes `@JvmStatic` methods callable from Java. New API queries should use this path.

`settings.gradle` supports a local source substitution: if `../jellyfin-apiclient-java` exists on disk, it replaces the published artifact automatically (controlled by `enable.dependency.substitution` property).

### Data Flow

```
Fragment / Activity
    → QueryUtil / JellyfinSdkBridge  (API queries, callback: MediaCallback<T>)
    → Model objects (Song, Album, Artist, Genre, Playlist)
    → Adapter (RecyclerView)
```

`QueryUtil.currentLibrary` is a static field holding the currently selected Jellyfin library (`BaseItemDto`). Most queries apply it as `parentId` to scope results to that library.

### Playback

- **`MusicService`** — the central Android `Service` for playback. Manages the queue (`QueueManager`), ExoPlayer, `MediaSession`, and reports playback progress/start/stop back to the Jellyfin server.
- **`LocalPlayer`** — thin wrapper around ExoPlayer 2 that implements the `Playback` interface.
- **`MusicPlayerRemote`** (helper) — static helper that binds to `MusicService` and exposes player controls to UI code.
- **`helper/EventListener`** — handles Jellyfin API lifecycle events (auth expiry, etc.).

### Database

Room database (`JellyDatabase`) with migrations tracked in the class. DAOs:
- `UserDao` — saved server/user credentials
- `CacheDao` — cached songs for offline playback
- `QueueSongDao` — persisted playback queue
- `SongDao` — general song metadata cache

### UI Structure

- `activities/` — `SplashActivity` → `LoginActivity` → `MainActivity` (hosts the `LibraryFragment`). `SettingsActivity`, `SearchActivity`, `SelectActivity`, and detail activities live alongside.
- `fragments/library/` — one fragment per library tab: `AlbumsFragment`, `ArtistsFragment`, `SongsFragment`, `PlaylistsFragment`, `FavoritesFragment`, `GenresFragment`.
- `fragments/player/` — sliding-up player panel rendered inside `MainActivity`.
- `glide/` — `CustomGlideRequest` builds image URLs from Jellyfin item IDs; `BlurTransformation` uses blurhash placeholders.

### Key Utilities

| File | Purpose |
|------|---------|
| `QueryUtil.java` | All Jellyfin item queries (songs, albums, artists, etc.) |
| `JellyfinSdkBridge.kt` | New Kotlin SDK queries (currently album song fetch) |
| `PreferenceUtil.java` | Typed `SharedPreferences` wrapper |
| `ThemeUtil.java` | Runtime theme/color application |
| `MusicUtil.java` | Formatting helpers (duration, track info) |
| `NavigationUtil.java` | Centralized `startActivity` helpers |
