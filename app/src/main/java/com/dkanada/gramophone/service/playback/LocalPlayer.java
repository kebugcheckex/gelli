package com.dkanada.gramophone.service.playback;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.util.LocalLibrary;
import com.dkanada.gramophone.util.MusicUtil;
import com.dkanada.gramophone.util.PreferenceUtil;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.extractor.DefaultExtractorsFactory;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        public void onIsPlayingChanged(boolean isPlaying) {
            Log.i(TAG, String.format("onIsPlayingChanged: %b", isPlaying));
            if (listener != null) listener.onPlayingChanged(isPlaying);
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

        // transcoded songs are saved as adts or mp3 without an index, which can only seek by assuming a constant bitrate
        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true);

        exoPlayer = new ExoPlayer.Builder(context)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(context, extractorsFactory))
            .setAudioAttributes(audioAttributes, true)
            .build();

        exoPlayer.addListener(eventListener);
        exoPlayer.prepare();
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
            Map<String, File> files = LocalLibrary.getFiles(queue);
            List<MediaItem> mediaItems = queue.stream()
                    .map(song -> createMediaItem(song, files.get(song.id)))
                    .collect(Collectors.toList());

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

    // Points queued songs at their stored file once saved, and back at the stream once removed.
    // The current song keeps playing from wherever it started.
    @Override
    public void refreshQueue(List<Song> queue) {
        executorService.submit(() -> {
            Map<String, File> files = LocalLibrary.getFiles(queue);

            mainHandler.post(() -> {
                int current = exoPlayer.getCurrentMediaItemIndex();
                int count = Math.min(queue.size(), exoPlayer.getMediaItemCount());

                for (int i = 0; i < count; i++) {
                    Song song = queue.get(i);
                    MediaItem mediaItem = exoPlayer.getMediaItemAt(i);

                    // the queue may have changed since, so only touch items that still hold the same song
                    if (i == current || !mediaItem.mediaId.equals(song.id)) continue;

                    File audio = files.get(song.id);
                    Uri uri = mediaItem.localConfiguration.uri;
                    boolean stale = audio != null ? !Uri.fromFile(audio).equals(uri) : isLocal(uri);

                    if (stale) {
                        Log.i(TAG, String.format("refreshQueue: %s now %s", song.id, audio != null ? "stored" : "streamed"));
                        exoPlayer.replaceMediaItem(i, createMediaItem(song, audio));
                    }
                }
            });
        });
    }

    private static boolean isLocal(Uri uri) {
        return "file".equals(uri.getScheme());
    }

    private MediaItem createMediaItem(Song song, @Nullable File audio) {
        Uri uri = audio != null ? Uri.fromFile(audio) : Uri.parse(MusicUtil.getTranscodeUri(song));

        List<String> containers = PreferenceUtil.getInstance(context).getDirectPlayCodecs().stream()
                .map(codec -> codec.container.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
        List<String> codecs = PreferenceUtil.getInstance(context).getDirectPlayCodecs().stream()
                .map(codec -> codec.codec.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
        String maxBitrate = PreferenceUtil.getInstance(context).getMaximumBitrate();

        if (audio != null || (containers.contains(song.container.toLowerCase(Locale.ROOT)) && codecs.contains(song.codec.toLowerCase(Locale.ROOT)) && song.bitRate <= Integer.parseInt(maxBitrate))) {
            return new MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(song.id)
                    .build();
        }

        return new MediaItem.Builder()
                .setUri(uri)
                .setMediaId(song.id)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build();
    }

    @Override
    public void playSongAt(int position) {
        postToMain(() -> {
            if (exoPlayer.getMediaItemCount() > 0) {
                exoPlayer.seekTo(Math.max(0, position) % exoPlayer.getMediaItemCount(), 0);
            }
        });
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
    public boolean isEnded() {
        return getOnMain(() -> exoPlayer.getPlaybackState() == Player.STATE_ENDED, false);
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
        postToMain(exoPlayer::release);
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
