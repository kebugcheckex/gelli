package com.dkanada.gramophone.service.playback;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.util.MusicUtil;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSink;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.MimeTypes;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class LocalPlayer implements Playback {
    public static final String TAG = LocalPlayer.class.getSimpleName();

    private final Context context;
    private final ExoPlayer exoPlayer;
    private final SimpleCache simpleCache;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private PlaybackListener listener;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @SuppressWarnings("FieldCanBeLocal")
    private final Player.Listener eventListener = new Player.Listener() {
        @Override
        public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            Log.i(TAG, String.format("onPlayWhenReadyChanged: %b %d", playWhenReady, reason));
            if (listener != null) listener.onReadyChanged(playWhenReady, reason);
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            Log.i(TAG, String.format("onPlaybackStateChanged: %d", state));
            if (listener != null) listener.onStateChanged(state);
        }

        @Override
        public void onPlaybackSuppressionReasonChanged(@Player.PlaybackSuppressionReason int playbackSuppressionReason) {
            Log.i(TAG, String.format("onPlaybackSuppressionReasonChanged: %d", playbackSuppressionReason));
            if (listener != null) listener.onStateChanged(Player.STATE_READY);
        }

        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            Log.i(TAG, String.format("onMediaItemTransition: %s %d", mediaItem, reason));
            if (listener != null) listener.onTrackChanged(reason);
        }

        @Override
        public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
            Log.i(TAG, String.format("onPositionDiscontinuity: %d", reason));
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            Log.i(TAG, String.format("onPlayerError: %s", error.getMessage()));

            exoPlayer.clearMediaItems();
            exoPlayer.prepare();

            Toast.makeText(context, context.getResources().getString(R.string.unplayable_file), Toast.LENGTH_SHORT).show();
        }
    };

    public LocalPlayer(Context context) {
        this.context = context;

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.CONTENT_TYPE_MUSIC)
            .build();

        exoPlayer = new ExoPlayer.Builder(context)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(buildDataSourceFactory()))
            .setAudioAttributes(audioAttributes, true)
            .build();

        exoPlayer.addListener(eventListener);
        exoPlayer.prepare();

        long cacheSize = PreferenceUtil.getInstance(context).getMediaCacheSize();
        LeastRecentlyUsedCacheEvictor recentlyUsedCache = new LeastRecentlyUsedCacheEvictor(cacheSize);
        StandaloneDatabaseProvider databaseProvider = new StandaloneDatabaseProvider(context);

        File cacheDirectory = new File(PreferenceUtil.getInstance(context).getLocationCache(), "exoplayer");
        simpleCache = new SimpleCache(cacheDirectory, recentlyUsedCache, databaseProvider);
    }

    // Run a runnable on the main thread. If already on main, run synchronously.
    private void postToMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            mainHandler.post(r);
        }
    }

    // Run a callable on the main thread and return its value, blocking if necessary.
    private <T> T getOnMain(Callable<T> c, T fallback) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                return c.call();
            } catch (Exception e) {
                return fallback;
            }
        }
        AtomicReference<T> ref = new AtomicReference<>(fallback);
        CountDownLatch latch = new CountDownLatch(1);
        mainHandler.post(() -> {
            try {
                ref.set(c.call());
            } catch (Exception ignored) {
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ref.get();
    }

    @Override
    public void setQueue(List<Song> queue, int position, int progress, boolean resetCurrentSong) {
        executorService.submit(() -> {
            List<MediaItem> mediaItems = createMediaItems(queue);

            mainHandler.post(() -> {
                if (resetCurrentSong) {
                    exoPlayer.setMediaItems(mediaItems, position, progress);
                    return;
                }

                int currentPosition = exoPlayer.getCurrentMediaItemIndex();
                exoPlayer.removeMediaItems(0, currentPosition);

                if (exoPlayer.getMediaItemCount() > 1) {
                    exoPlayer.removeMediaItems(1, exoPlayer.getMediaItemCount());
                }

                if (position + 1 < mediaItems.size()) {
                    exoPlayer.addMediaItems(1, mediaItems.subList(position + 1, mediaItems.size()));
                }

                exoPlayer.addMediaItems(0, mediaItems.subList(0, position));
            });
        });
    }

    private List<MediaItem> createMediaItems(List<Song> queue) {
        return queue.stream().map(song -> {
            File audio = new File(MusicUtil.getFileUri(song));
            Uri uri = Uri.fromFile(audio);

            if (!audio.exists()) {
                uri = Uri.parse(MusicUtil.getTranscodeUri(song));
            }

            List<String> containers = PreferenceUtil.getInstance(context).getDirectPlayCodecs().stream()
                    .map(codec -> codec.container.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toList());
            List<String> codecs = PreferenceUtil.getInstance(context).getDirectPlayCodecs().stream()
                    .map(codec -> codec.codec.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toList());
            String maxBitrate = PreferenceUtil.getInstance(context).getMaximumBitrate();

            MediaItem mediaItem;

            if (uri.toString().contains("file://") || (containers.contains(song.container.toLowerCase(Locale.ROOT)) && codecs.contains(song.codec.toLowerCase(Locale.ROOT)) && song.bitRate <= Integer.parseInt(maxBitrate))) {
                mediaItem = new MediaItem.Builder()
                        .setUri(uri)
                        .setMediaId(song.id)
                        .build();
            } else {
                mediaItem = new MediaItem.Builder()
                        .setUri(uri)
                        .setMediaId(song.id)
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .build();
            }

            return mediaItem;
        }).collect(Collectors.toList());
    }

    @Override
    public void playSongAt(int position) {
        postToMain(() -> {
            if (exoPlayer.getMediaItemCount() > 0) {
                exoPlayer.seekTo(Math.max(0, position) % exoPlayer.getMediaItemCount(), 0);
            }
        });
    }

    private DataSource.Factory buildDataSourceFactory() {
        return () -> new CacheDataSource(
                simpleCache,
                new DefaultDataSource.Factory(context).createDataSource(),
                new FileDataSource(),
                new CacheDataSink(simpleCache, 10 * 1024 * 1024),
                CacheDataSource.FLAG_BLOCK_ON_CACHE,
                null
        );
    }

    @Override
    public void setListener(PlaybackListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean isReady() {
        return getOnMain(exoPlayer::getPlayWhenReady, false);
    }

    @Override
    public boolean isPlaying() {
        return getOnMain(() -> exoPlayer.getPlayWhenReady() && exoPlayer.getPlaybackSuppressionReason() == Player.PLAYBACK_SUPPRESSION_REASON_NONE, false);
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public boolean isLoading() {
        return getOnMain(() -> {
            MediaItem current = exoPlayer.getCurrentMediaItem();
            if (current != null && current.localConfiguration.uri.toString().contains("file://")) {
                return false;
            }
            return exoPlayer.getPlaybackState() == Player.STATE_BUFFERING;
        }, false);
    }

    @Override
    public void start() {
        postToMain(() -> exoPlayer.setPlayWhenReady(true));
    }

    @Override
    public void pause() {
        postToMain(() -> exoPlayer.setPlayWhenReady(false));
    }

    @Override
    public void stop() {
        postToMain(() -> {
            simpleCache.release();
            exoPlayer.release();
        });
    }

    @Override
    public void previous() {
        postToMain(exoPlayer::seekToPreviousMediaItem);
    }

    @Override
    public void next() {
        postToMain(exoPlayer::seekToNextMediaItem);
    }

    @Override
    public void setRepeatMode(@Player.RepeatMode int repeatMode) {
        postToMain(() -> exoPlayer.setRepeatMode(repeatMode));
    }

    @Override
    public int getProgress() {
        return getOnMain(() -> (int) exoPlayer.getCurrentPosition(), 0);
    }

    @Override
    public int getDuration() {
        return getOnMain(() -> (int) exoPlayer.getDuration(), 0);
    }

    @Override
    public void setProgress(int progress) {
        postToMain(() -> exoPlayer.seekTo(progress));
    }

    @Override
    public void setVolume(int volume) {
        postToMain(() -> exoPlayer.setVolume(volume / 100f));
    }

    @Override
    public int getVolume() {
        return getOnMain(() -> (int) (exoPlayer.getVolume() * 100), 100);
    }
}
