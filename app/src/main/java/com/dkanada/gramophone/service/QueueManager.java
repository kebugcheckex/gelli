package com.dkanada.gramophone.service;

import android.content.Context;

import com.dkanada.gramophone.App;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.util.DownloadUtil;
import com.dkanada.gramophone.util.PreferenceUtil;
import androidx.media3.common.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class QueueManager {
    public static final int REPEAT_MODE_NONE = 0;
    public static final int REPEAT_MODE_THIS = 1;
    public static final int REPEAT_MODE_ALL = 2;

    public static final int SHUFFLE_MODE_NONE = 0;
    public static final int SHUFFLE_MODE_SHUFFLE = 1;

    private final Context context;
    private final QueueCallbacks callbacks;

    private List<Song> playingQueue = new ArrayList<>();
    private List<Song> shuffledQueue = new ArrayList<>();

    private int position = 0;
    private int restoredProgress = 0;
    private boolean resetCurrentSong = true;

    private int shuffleMode;
    private @Player.RepeatMode int repeatMode;

    public QueueManager(Context context, QueueCallbacks callbacks) {
        this.context = context;
        this.callbacks = callbacks;
    }

    public void setPlayingQueueAndPosition(List<Song> queue, int position) {
        this.position = position;
        this.playingQueue = new ArrayList<>(queue);
        this.shuffledQueue = new ArrayList<>(queue);
        removeUnplayableSongs();
        shuffleQueue();

        callbacks.onQueueChanged();
    }

    public List<Song> getPlayingQueue() {
        return shuffleMode == SHUFFLE_MODE_SHUFFLE ? shuffledQueue : playingQueue;
    }

    public int getPosition() {
        return position;
    }

    public Song getCurrentSong() {
        return getSongAt(getPosition());
    }

    public Song getSongAt(int position) {
        if (position >= 0 && position < getPlayingQueue().size()) {
            return getPlayingQueue().get(position);
        }

        return null;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public void setNextPosition() {
        switch (getRepeatMode()) {
            case REPEAT_MODE_NONE:
            case REPEAT_MODE_THIS:
                position = Math.min(position + 1, playingQueue.size() - 1);
                break;
            case REPEAT_MODE_ALL:
                position = (position + 1) % playingQueue.size();
                break;
        }
    }

    public void setPreviousPosition() {
        switch (getRepeatMode()) {
            case REPEAT_MODE_NONE:
            case REPEAT_MODE_THIS:
                position = Math.max(position - 1, 0);
                break;
            case REPEAT_MODE_ALL:
                position = (position - 1 + playingQueue.size()) % playingQueue.size();
                break;
        }
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public int getShuffleMode() {
        return shuffleMode;
    }

    public void toggleShuffle() {
        if (getShuffleMode() == SHUFFLE_MODE_NONE) {
            setShuffleMode(SHUFFLE_MODE_SHUFFLE);
        } else {
            setShuffleMode(SHUFFLE_MODE_NONE);
        }
    }

    public void setRepeatMode(final int repeatMode) {
        switch (repeatMode) {
            case REPEAT_MODE_NONE:
            case REPEAT_MODE_ALL:
            case REPEAT_MODE_THIS:
                this.repeatMode = repeatMode;
                PreferenceUtil.getInstance(context).setRepeat(repeatMode);
                callbacks.onRepeatModeChanged();
                break;
        }
    }

    public void setShuffleMode(final int shuffleMode) {
        PreferenceUtil.getInstance(context).setShuffle(shuffleMode);

        switch (shuffleMode) {
            case SHUFFLE_MODE_SHUFFLE:
                this.shuffleMode = shuffleMode;
                shuffleQueue();

                break;
            case SHUFFLE_MODE_NONE:
                String currentSongId = getCurrentSong().id;
                int newPosition = 0;

                Optional<Song> currentSong = playingQueue.stream()
                        .filter(song -> song.id.equals(currentSongId))
                        .findFirst();

                if (currentSong.isPresent()) {
                    newPosition = playingQueue.indexOf(currentSong.get());
                }

                shuffledQueue = new ArrayList<>(playingQueue);

                position = newPosition;
                this.shuffleMode = shuffleMode;
                break;
        }

        resetCurrentSong = false;
        callbacks.onShuffleModeChanged();
        callbacks.onQueueChanged();
    }

    private void shuffleQueue() {
        if (shuffleMode == SHUFFLE_MODE_SHUFFLE) {
            this.shuffledQueue = new ArrayList<>(playingQueue);

            if (!shuffledQueue.isEmpty()) {
                if (getPosition() >= 0) {
                    Song song = shuffledQueue.remove(getPosition());

                    Collections.shuffle(shuffledQueue);
                    shuffledQueue.add(0, song);
                } else {
                    Collections.shuffle(shuffledQueue);
                }
            }

            position = 0;
        }
    }

    public void addSong(int position, Song song) {
        if (!isPlayable(song)) return;

        playingQueue.add(position, song);
        shuffledQueue.add(position, song);

        resetCurrentSong = false;
        callbacks.onQueueChanged();
    }

    public void addSong(Song song) {
        if (!isPlayable(song)) return;

        playingQueue.add(song);
        shuffledQueue.add(song);

        resetCurrentSong = false;
        callbacks.onQueueChanged();
    }

    public void addSongs(int position, List<Song> songs) {
        songs = filterPlayable(songs);

        playingQueue.addAll(position, songs);
        shuffledQueue.addAll(position, songs);

        resetCurrentSong = false;
        callbacks.onQueueChanged();
    }

    public void addSongs(List<Song> songs) {
        songs = filterPlayable(songs);

        playingQueue.addAll(songs);
        shuffledQueue.addAll(songs);

        resetCurrentSong = false;
        callbacks.onQueueChanged();
    }

    public void removeSong(int position) {
        if (getShuffleMode() == SHUFFLE_MODE_NONE) {
            playingQueue.remove(position);
            shuffledQueue.remove(position);
        } else {
            playingQueue.remove(shuffledQueue.remove(position));
        }

        int currentPosition = getPosition();
        if (position != currentPosition) {
            resetCurrentSong = false;
        }

        if (position < currentPosition) {
            this.position = currentPosition - 1;
        }

        callbacks.onQueueChanged();
    }

    public void moveSong(int from, int to) {
        if (from == to) return;

        final int currentPosition = getPosition();
        Song songToMove = getPlayingQueue().remove(from);
        getPlayingQueue().add(to, songToMove);

        if (from > currentPosition && to <= currentPosition) {
            position = currentPosition + 1;
        } else if (from < currentPosition && to >= currentPosition) {
            position = currentPosition - 1;
        } else if (from == currentPosition) {
            position = to;
        }

        resetCurrentSong = false;
        callbacks.onQueueChanged();
    }

    public void clearQueue() {
        playingQueue.clear();
        shuffledQueue.clear();

        position = 0;
        callbacks.onQueueChanged();
    }

    public long getQueueDurationMillis(int position) {
        long duration = 0;
        for (int i = position + 1; i < playingQueue.size(); i++) {
            duration += playingQueue.get(i).duration;
        }

        return duration;
    }

    public void cycleRepeatMode() {
        switch (getRepeatMode()) {
            case REPEAT_MODE_NONE:
                setRepeatMode(REPEAT_MODE_ALL);
                break;
            case REPEAT_MODE_ALL:
                setRepeatMode(REPEAT_MODE_THIS);
                break;
            default:
                setRepeatMode(REPEAT_MODE_NONE);
                break;
        }
    }

    public void restoreQueue() {
        position = PreferenceUtil.getInstance(context).getPosition();
        restoredProgress = PreferenceUtil.getInstance(context).getProgress();

        playingQueue = new ArrayList<>(App.getDatabase().queueSongDao().getQueue(0));
        shuffledQueue = new ArrayList<>(App.getDatabase().queueSongDao().getQueue(1));

        shuffleMode = PreferenceUtil.getInstance(context).getShuffle();
        repeatMode = PreferenceUtil.getInstance(context).getRepeat();

        Song current = getCurrentSong();
        if (removeUnplayableSongs() && (current == null || !current.equals(getCurrentSong()))) {
            restoredProgress = 0;
        }

        callbacks.onQueueChanged();
        callbacks.onRepeatModeChanged();
        callbacks.onShuffleModeChanged();
    }

    public void saveQueue() {
        PreferenceUtil.getInstance(context).setPosition(position);
        App.getDatabase().queueSongDao().updateQueues(playingQueue, shuffledQueue);
    }

    // called when offline mode is turned on while songs are already queued
    public void removeUnavailableSongs() {
        Song current = getCurrentSong();
        if (!removeUnplayableSongs()) return;

        // keep the current song playing without interruption when it is still in the queue
        if (current != null && current.equals(getCurrentSong())) {
            resetCurrentSong = false;
        }

        callbacks.onQueueChanged();
    }

    // offline mode can only play downloaded songs, so drop the rest from both queues
    // and keep the position on the same song, or the next playable one if it was removed
    private boolean removeUnplayableSongs() {
        if (!DownloadUtil.isOfflineMode()) return false;

        List<Song> queue = getPlayingQueue();
        List<Song> playable = new ArrayList<>();
        int newPosition = 0;

        for (int i = 0; i < queue.size(); i++) {
            if (!DownloadUtil.isPlayableOffline(queue.get(i))) continue;
            if (i < position) newPosition++;
            playable.add(queue.get(i));
        }

        int removed = queue.size() - playable.size();
        if (removed == 0) return false;

        // both queues hold the same songs in a different order
        Set<Song> keep = new HashSet<>(playable);
        List<Song> other = shuffleMode == SHUFFLE_MODE_SHUFFLE ? playingQueue : shuffledQueue;
        List<Song> otherPlayable = other.stream().filter(keep::contains).collect(Collectors.toList());

        if (shuffleMode == SHUFFLE_MODE_SHUFFLE) {
            shuffledQueue = playable;
            playingQueue = otherPlayable;
        } else {
            playingQueue = playable;
            shuffledQueue = otherPlayable;
        }

        position = Math.max(0, Math.min(newPosition, playable.size() - 1));
        DownloadUtil.showUnavailableToast(context, removed);

        return true;
    }

    private boolean isPlayable(Song song) {
        if (!DownloadUtil.isOfflineMode() || DownloadUtil.isPlayableOffline(song)) return true;

        DownloadUtil.showUnavailableToast(context, 1);
        return false;
    }

    private List<Song> filterPlayable(List<Song> songs) {
        if (!DownloadUtil.isOfflineMode()) return songs;

        List<Song> playable = songs.stream().filter(DownloadUtil::isPlayableOffline).collect(Collectors.toList());
        DownloadUtil.showUnavailableToast(context, songs.size() - playable.size());

        return playable;
    }

    public int getRestoredProgress() {
        int progress = restoredProgress;
        restoredProgress = 0;

        return progress;
    }

    public boolean isResetCurrentSong() {
        boolean reset = resetCurrentSong;
        resetCurrentSong = true;

        return reset;
    }

    interface QueueCallbacks {
        void onQueueChanged();
        void onRepeatModeChanged();
        void onShuffleModeChanged();
    }
}
