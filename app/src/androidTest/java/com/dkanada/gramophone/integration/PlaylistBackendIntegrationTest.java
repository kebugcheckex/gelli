package com.dkanada.gramophone.integration;

import static org.junit.Assert.assertNotNull;

import android.os.Bundle;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.dkanada.gramophone.App;
import com.dkanada.gramophone.interfaces.MediaCallback;
import com.dkanada.gramophone.model.Playlist;
import com.dkanada.gramophone.model.PlaylistSong;
import com.dkanada.gramophone.util.PlaylistUtil;
import com.dkanada.gramophone.util.QueryUtil;

import org.jellyfin.apiclient.interaction.EmptyResponse;
import org.jellyfin.apiclient.interaction.Response;
import org.jellyfin.apiclient.model.playlists.PlaylistItemQuery;
import org.jellyfin.apiclient.model.users.AuthenticationResult;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class PlaylistBackendIntegrationTest {
    private static final long AUTH_TIMEOUT_MS = 30_000L;
    private static final long QUERY_TIMEOUT_MS = 20_000L;
    private static final long PLAYLIST_APPEAR_TIMEOUT_MS = 60_000L;

    @Test
    public void playlistRoundTrip_realBackend() throws Exception {
        BackendConfig config = BackendConfig.fromInstrumentation();
        Assume.assumeTrue(
                "Missing Jellyfin test config. Provide jellyfin.url, jellyfin.username, jellyfin.password "
                        + "as instrumentation args or JELLYFIN_URL/JELLYFIN_USERNAME/JELLYFIN_PASSWORD env vars.",
                config.isComplete()
        );

        AuthenticationResult auth = authenticate(config);
        assertNotNull("AuthenticationResult should not be null", auth);
        assertNotNull("Access token should not be null", auth.getAccessToken());
        assertNotNull("Authenticated user should not be null", auth.getUser());

        App.getApiClient().SetAuthenticationInfo(auth.getAccessToken(), auth.getUser().getId());

        String playlistName = String.format(Locale.US, "gelli-it-%d", System.currentTimeMillis());
        Playlist createdPlaylist = null;
        try {
            PlaylistUtil.createPlaylist(playlistName, new ArrayList<>());
            createdPlaylist = waitForPlaylistByName(playlistName, PLAYLIST_APPEAR_TIMEOUT_MS);
            assertNotNull("Created playlist not found via QueryUtil.getPlaylists", createdPlaylist);

            PlaylistItemQuery query = new PlaylistItemQuery();
            query.setId(createdPlaylist.id);
            List<PlaylistSong> songs = awaitMediaCallback(
                    callback -> PlaylistUtil.getPlaylist(query, callback),
                    QUERY_TIMEOUT_MS
            );
            assertNotNull("Playlist songs query returned null", songs);
        } finally {
            if (createdPlaylist != null) {
                deletePlaylist(createdPlaylist.id);
            }
        }
    }

    private AuthenticationResult authenticate(BackendConfig config) throws Exception {
        App.getApiClient().ChangeServerLocation(config.url);
        return awaitResponse(
                callback -> App.getApiClient().AuthenticateUserAsync(config.username, config.password, callback),
                AUTH_TIMEOUT_MS
        );
    }

    private Playlist waitForPlaylistByName(String name, long timeoutMs) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        while (SystemClock.elapsedRealtime() < deadline) {
            List<Playlist> playlists = awaitMediaCallback(QueryUtil::getPlaylists, QUERY_TIMEOUT_MS);
            for (Playlist playlist : playlists) {
                if (name.equals(playlist.name)) {
                    return playlist;
                }
            }
            Thread.sleep(1_000L);
        }
        return null;
    }

    private void deletePlaylist(String playlistId) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        App.getApiClient().DeleteItem(playlistId, new EmptyResponse() {
            @Override
            public void onResponse() {
                latch.countDown();
            }

            @Override
            public void onError(Exception ex) {
                latch.countDown();
            }
        });
        latch.await(QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private interface ResponseCall<T> {
        void execute(Response<T> callback);
    }

    private interface MediaCall<T> {
        void execute(MediaCallback<T> callback);
    }

    private <T> T awaitResponse(ResponseCall<T> call, long timeoutMs) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        call.execute(new Response<T>() {
            @Override
            public void onResponse(T response) {
                result.set(response);
                latch.countDown();
            }

            @Override
            public void onError(Exception ex) {
                error.set(ex);
                latch.countDown();
            }
        });

        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new AssertionError("Timed out waiting for API response");
        }
        if (error.get() != null) {
            throw error.get();
        }
        return result.get();
    }

    private <T> List<T> awaitMediaCallback(MediaCall<T> call, long timeoutMs) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<T>> result = new AtomicReference<>();

        call.execute(media -> {
            result.set(media);
            latch.countDown();
        });

        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new AssertionError("Timed out waiting for media callback");
        }
        return result.get();
    }

    private static final class BackendConfig {
        private final String url;
        private final String username;
        private final String password;

        private BackendConfig(String url, String username, String password) {
            this.url = url;
            this.username = username;
            this.password = password;
        }

        static BackendConfig fromInstrumentation() {
            Bundle args = InstrumentationRegistry.getArguments();
            String url = firstNonBlank(args.getString("jellyfin.url"), System.getenv("JELLYFIN_URL"));
            String username = firstNonBlank(args.getString("jellyfin.username"), System.getenv("JELLYFIN_USERNAME"));
            String password = firstNonBlank(args.getString("jellyfin.password"), System.getenv("JELLYFIN_PASSWORD"));
            return new BackendConfig(url, username, password);
        }

        boolean isComplete() {
            return !isBlank(url) && !isBlank(username) && !isBlank(password);
        }

        private static String firstNonBlank(String first, String second) {
            if (!isBlank(first)) return first;
            if (!isBlank(second)) return second;
            return null;
        }

        private static boolean isBlank(String value) {
            return value == null || value.trim().isEmpty();
        }
    }
}
