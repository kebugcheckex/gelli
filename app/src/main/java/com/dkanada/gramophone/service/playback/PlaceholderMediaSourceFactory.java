package com.dkanada.gramophone.service.playback;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.SilenceMediaSource;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;

// Songs that can't play right now (not stored, and streaming is not allowed) stay in the player as a moment of
// silence, so it keeps one item per queued song without touching the network. MusicService skips past them.
public class PlaceholderMediaSourceFactory implements MediaSource.Factory {
    private static final String SCHEME = "unavailable";
    private static final long DURATION_US = 100_000;

    private final MediaSource.Factory delegate;

    public PlaceholderMediaSourceFactory(MediaSource.Factory delegate) {
        this.delegate = delegate;
    }

    public static MediaItem createMediaItem(String songId) {
        return new MediaItem.Builder()
                .setUri(Uri.fromParts(SCHEME, songId, null))
                .setMediaId(songId)
                .build();
    }

    public static boolean isPlaceholder(@Nullable Uri uri) {
        return uri != null && SCHEME.equals(uri.getScheme());
    }

    @NonNull
    @Override
    public MediaSource createMediaSource(@NonNull MediaItem mediaItem) {
        if (mediaItem.localConfiguration == null || !isPlaceholder(mediaItem.localConfiguration.uri)) {
            return delegate.createMediaSource(mediaItem);
        }

        SilenceMediaSource source = new SilenceMediaSource.Factory().setDurationUs(DURATION_US).createMediaSource();

        // keep the song's media id, which the queue refresh matches items by
        source.updateMediaItem(mediaItem);
        return source;
    }

    @NonNull
    @Override
    public MediaSource.Factory setDrmSessionManagerProvider(@NonNull DrmSessionManagerProvider drmSessionManagerProvider) {
        delegate.setDrmSessionManagerProvider(drmSessionManagerProvider);
        return this;
    }

    @NonNull
    @Override
    public MediaSource.Factory setLoadErrorHandlingPolicy(@NonNull LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
        delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
        return this;
    }

    @NonNull
    @Override
    public @C.ContentType int[] getSupportedTypes() {
        return delegate.getSupportedTypes();
    }
}
