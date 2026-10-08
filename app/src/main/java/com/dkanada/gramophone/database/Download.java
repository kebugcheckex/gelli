package com.dkanada.gramophone.database;

import androidx.annotation.NonNull;
import androidx.room.Embedded;
import androidx.room.Entity;

import com.dkanada.gramophone.model.Song;

import java.io.File;

// a song downloaded by a specific account, with enough metadata to browse and play it offline
@Entity(tableName = "downloads", primaryKeys = {"id", "userId"})
public class Download {
    @NonNull
    @Embedded
    public Song song;

    // local user id from the users table, which is unique per server and account
    @NonNull
    public String userId;

    public String filePath;
    public long fileSize;
    public long downloadedAt;

    public Download() {
        this.song = new Song();
        this.userId = "";
    }

    public Download(@NonNull Song song, @NonNull String userId, @NonNull File file) {
        this.song = song;
        this.userId = userId;
        this.filePath = file.getAbsolutePath();
        this.fileSize = file.length();
        this.downloadedAt = System.currentTimeMillis();
    }
}
