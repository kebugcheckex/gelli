# Server address priority and fallback

## Status

M1 (priority order and the Server addresses screen) and M2 (connection state and the unreachable mode) are done. M3 and M4 are not started. The "Today" section below describes the code before M1. It builds on multi-address accounts (commit `44c24861`), where an account is keyed by the server id and keeps every address it signed in through.

## Today

- An address is only added by signing in again through it (**Switch account** > add). It can't be removed or reordered.
- `ServerConnection.resolve` probes every address at once with `GET /System/Info/Public`, and the **fastest** one that reports the account's server id wins. The order of the list doesn't matter.
- It probes again only at app start (`LoginService.connect`) and when the default network changes (`ServerConnection.reconnect`, and only for identified accounts with at least two addresses).
- Stream and download urls have the base url baked in when they're built (`MusicUtil.getTranscodeUri`, `getSaveUri`, `getDownloadUri`), so a switch doesn't reach urls that are already queued.
- When `LoginService` can't connect, it broadcasts `STATE_OFFLINE`, and `AbsMusicContentActivity` sends the user to the **login screen**.
- "Offline mode" is a user setting (`PreferenceUtil.offlineMode`, read through `NetworkUtil.isOffline`) that blocks all network use.

## Goals

1. The user sets an explicit priority order for the addresses of an account.
2. The app uses the highest-priority address that works on the current network.
3. When the current address stops working, the app falls back to the next working address in the list automatically, down to the last one.
4. When no address works, the app behaves as if offline (stored songs only), without touching the offline mode setting, and recovers on its own.

## Decisions

These are the defaults for the plan. Change them here before starting a milestone if needed.

- **Unreachable is a runtime state, not the setting.** The offline mode setting stays the user's choice. `NetworkUtil.isOffline()` returns `setting || unreachable`, so everything that already respects offline mode follows without changes. Turning the setting on would make "the user chose offline" and "the server went away" indistinguishable, and the app would have to turn it back off behind the user's back.
- **Move back up the list only when the network changes.** Within one network a switch only moves down. Probing higher-priority addresses periodically could make the app switch back and forth over a flaky LAN.
- **Probe timeout of about 3 s per address**, which is the worst-case delay when leaving home, while a LAN address times out on mobile data. It could be shorter for private-range addresses if it turns out to matter.
- **Only connection errors count as a lost address**: refused, timed out, unknown host, TLS failures. An HTTP 4xx or 5xx response means the server is reachable. A 401 still leads to the login screen, as it means the token is no longer valid.

## Milestones

### M1. Priority order and managing addresses

The order of `User.addresses` becomes the priority, index 0 first. No schema change is needed: the column already keeps insertion order.

**Selection by priority.** Replace the fastest-wins rule in `ServerConnection.resolve`. All addresses are still probed at once, but address *i* wins once it has answered with the account's server id **and** every address above it has failed, answered for another server, or timed out. This gives the same result as trying the addresses one by one, at the cost of at most one timeout.

**Manage addresses screen.** Reached from an account card in `SelectActivity`, and from settings for the current account.

- Lists the addresses in priority order, with the one in use marked.
- Drag to reorder.
- Add an address: probe it, and accept it only if it reports the account's server id. No password needed, as the token is the same for every address of the server.
- Remove an address, but never the last one.
- Optionally a test action that probes one address and shows the result.

Signing in through a new address still works, and appends it at the end.

After the order changes, run selection again if the current account was edited, so a new first choice takes effect without restarting the app.

**Tests.** Extend `ServerConnectionTest`: a higher-priority address that answers late still wins, a failure or timeout of a higher one lets a lower one win, and an address that reports another server is skipped. Add the add/remove/reorder rules to `AccountsTest`.

### M2. Connection state and the unreachable mode

Give `ServerConnection` an observable state, the single place that knows how the app reaches the server:

- `Resolving`: selection is running.
- `Online(address)`: the session points at this address.
- `Unreachable`: no address answered.

Changes:

- `LoginService` reports through this state instead of its own broadcasts. Connection failures lead to `Unreachable`, and only an auth failure (401) leads to the login screen. `AbsMusicContentActivity` stops treating `STATE_OFFLINE` as "sign in again".
- `NetworkUtil.isOffline()` also returns true while `Unreachable`. Check its callers (`SaveWorker`, `PlaybackReporter`, `LyricsRepository`, `UserLibraryUtil`) and the library screens, which should show stored content rather than errors.
- A banner in the main screen while unreachable, such as "Server unreachable · playing saved songs", with a retry action.
- **Recovery.** While unreachable, run selection again on every network change, and on a backoff (30 s, 1 min, 2 min, then every 5 min) while the app is in the foreground or playing. Stop retrying when neither is true.
- `NetworkReceiver` broadcasts `STATE_ONLINE`/`STATE_OFFLINE` from the old connectivity API. Fold it into the new state or remove it.

**Tests.** The state transitions with a fake probe and a fake clock: unreachable at start, recovery on a network change, the backoff schedule, and a 401 versus a connection error.

**As built.**

- `ConnectionState` is `Connecting`, `Online(address)`, `Unreachable` or `Rejected` (a 401). `ConnectionLoop` runs one attempt at a time, retries on the backoff while `Unreachable`, and is tested on virtual time (`ConnectionLoopTest`).
- `ServerConnection.start()` replaces `LoginService`: the first attempt for an account checks the token with `GET /System/Info` and identifies the server, and later attempts only pick the address. `NetworkReceiver` is gone.
- Network changes run an attempt for every account, including a lost network, so airplane mode leads to `Unreachable` within a second. Retries need the app in the foreground or playing, and pause in offline mode.
- `AbsMusicContentActivity` follows the state: `Rejected` opens the login screen, `Unreachable` still loads the screen, and coming back `Online` reloads it. `MainActivity` shows the banner with **Retry** above the mini player.

### M3. Switching addresses mid-session

Make a switch reach everything that already holds a url, so that M4 can switch at any time.

- **Streams.** Wrap the ExoPlayer data source in a `ResolvingDataSource` that rewrites the scheme, host, port and path prefix of any url on one of the account's addresses to the current address, when the stream is opened. Queued songs and retries then use the new address without rebuilding the queue. The urls can keep being built the way they are now.
- **Saves.** Do the same for `SaveWorker`, so a save that failed on a lost address retries on the current one.
- **Covers.** `JellyfinImageUrls` builds the url when the request is made, and caches by item, so no change is needed beyond checking that a failed cover loads again after a switch.
- **WebSocket.** `EventListener.restart()` already reconnects after a switch.
- **Playback reporting.** Check that `PlaybackReporter` builds its API client per call (it does today, through `createApiOrNull()`), so reports go to the current address.

**Tests.** Unit test the url rewriting: every saved address maps to the current one, urls for other hosts are untouched, and the query string (including the `ApiKey`) is kept.

### M4. Noticing a lost address and falling back

Add triggers that make `ServerConnection` check the current address:

| Trigger | Where |
|---|---|
| Default network changes | The existing callback. Runs the full selection, so it can also move back up the list. Done in M2. |
| WebSocket drops | `EventListener`. The server sends keep-alives, so the socket is a heartbeat at no extra cost. Check what the SDK's socket exposes for its connection state. |
| Playback fails on a connection error | `LocalPlayer.onPlayerError`, when the cause is an `IOException`. |
| An API call fails on a connection error | A single `ServerConnection.reportFailure(address, error)`, called from the shared SDK call paths. Look for one place to hook it (for example the SDK's HTTP client options) before adding calls at each site. |

Each trigger runs the same steps:

1. Debounce for a moment, so a burst of failures leads to one check.
2. Ignore a failure for an address the session no longer uses.
3. Probe the current address once. If it answers, stay: one dropped request isn't a lost address.
4. Otherwise run selection over the addresses **after** the current one, in priority order. A network change uses the whole list instead.
5. On success, switch the session, restart the socket, and save the address as `user.server`. On failure, go to `Unreachable` (M2).

After a fallback caused by a playback error, retry the current song from the same position.

**Tests.** With a fake probe: a lost current address falls back to the next working one, skipping dead ones in between; the last lost address leads to `Unreachable`; a single failed request with a healthy address doesn't switch; repeated failures lead to one check.

## Verifying on a device

Each milestone ends with a run on the test AVD, and M4 also needs the real phone, since the AVD can't easily move between networks.

- LAN address first and the domain second: at home the LAN address is used. On mobile data the domain is used within about 3 s.
- Switch Wi-Fi off during playback: the song continues on the domain after a short stall, without user action.
- Airplane mode during playback: stored songs keep playing, others are skipped, and the unreachable banner shows. Turn airplane mode off and the app reconnects on its own.
- Stop the server: the app falls back down the list, then shows the unreachable banner, and recovers when the server is back.
- Reorder the addresses while connected: the new first choice takes effect.
