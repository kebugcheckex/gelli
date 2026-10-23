package com.dkanada.gramophone.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Presence of a row distinguishes a successfully checked empty library from an unknown total. */
@Entity(tableName = "song_inventories")
public class SongInventory {
    @PrimaryKey
    @NonNull
    public String accountId;
    public long checkedAt;

    public SongInventory(@NonNull String accountId, long checkedAt) {
        this.accountId = accountId;
        this.checkedAt = checkedAt;
    }
}
