package com.dkanada.gramophone.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;

import com.dkanada.gramophone.model.Song;

@Entity(tableName = "local_songs", primaryKeys = {"accountId", "itemId"})
public class LocalSong {
    public enum State {
        QUEUED,
        DOWNLOADING,
        COMPLETE,
        FAILED,
        MISSING
    }

    public enum Source {
        EXPLICIT,
        PLAYED
    }

    public enum Variant {
        ORIGINAL,
        TRANSCODED
    }

    @NonNull
    public String accountId;
    @NonNull
    public String itemId;

    public String title;
    public int trackNumber;
    public int discNumber;
    public int year;
    public long duration;

    public String albumId;
    public String albumName;

    public String artistId;
    public String artistName;

    public String imageTag;
    public String blurHash;
    public boolean favorite;

    @NonNull
    public State state;
    @NonNull
    public Source source;
    // null until the save worker decides what to store
    public Variant variant;

    // absolute path of the stored file, null until saved
    public String filePath;
    public long fileSize;

    // describes the stored file, which may differ from the server item when transcoded
    public String container;
    public String codec;
    public int bitRate;

    public long serverDateModified;

    public long addedAt;
    public long lastPlayedAt;

    public int failureCount;
    public String lastError;

    public LocalSong(@NonNull String accountId, @NonNull String itemId, @NonNull State state, @NonNull Source source) {
        this.accountId = accountId;
        this.itemId = itemId;
        this.state = state;
        this.source = source;
    }

    @Ignore
    public LocalSong(@NonNull String accountId, @NonNull Song song, @NonNull Source source) {
        this(accountId, song.id, State.QUEUED, source);

        this.title = song.title;
        this.trackNumber = song.trackNumber;
        this.discNumber = song.discNumber;
        this.year = song.year;
        this.duration = song.duration;

        this.albumId = song.albumId;
        this.albumName = song.albumName;

        this.artistId = song.artistId;
        this.artistName = song.artistName;

        this.imageTag = song.primary;
        this.blurHash = song.blurHash;
        this.favorite = song.favorite;

        this.addedAt = System.currentTimeMillis();
    }
}
