# Playlist Backend Integration Test

This project now includes an instrumentation integration test:

- `app/src/androidTest/java/com/dkanada/gramophone/integration/PlaylistBackendIntegrationTest.java`

It validates playlist round-trip behavior against a real Jellyfin server:

1. authenticate
2. create playlist
3. query playlists through `QueryUtil.getPlaylists(...)`
4. load playlist items via `PlaylistUtil.getPlaylist(...)`
5. cleanup by deleting the created playlist

## Passing Server Config Without Committing Secrets

Do **not** hardcode credentials in source files.

Use either instrumentation args, env vars, or both.

### Option A: CLI instrumentation args

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.jellyfin.url=http://YOUR_HOST:8096 \
  -Pandroid.testInstrumentationRunnerArguments.jellyfin.username=YOUR_TEST_USER \
  -Pandroid.testInstrumentationRunnerArguments.jellyfin.password=YOUR_TEST_PASSWORD
```

### Option B: Environment variables (local shell/CI secrets)

```bash
export JELLYFIN_URL=http://YOUR_HOST:8096
export JELLYFIN_USERNAME=YOUR_TEST_USER
export JELLYFIN_PASSWORD=YOUR_TEST_PASSWORD

./gradlew connectedDebugAndroidTest
```

### Network note for separate host

- Ensure the emulator/device can reach the Jellyfin host and port.
- If running Jellyfin on the same machine as the emulator, use `http://10.0.2.2:8096`.

## Useful command for only this class

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.dkanada.gramophone.integration.PlaylistBackendIntegrationTest
```
