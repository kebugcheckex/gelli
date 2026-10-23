package com.dkanada.gramophone.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;

@Entity(tableName = "inventory_songs", primaryKeys = {"accountId", "itemId"},
        foreignKeys = @ForeignKey(entity = SongInventory.class, parentColumns = "accountId",
                childColumns = "accountId", onDelete = ForeignKey.CASCADE))
public class InventorySong {
    @NonNull
    public String accountId;
    @NonNull
    public String itemId;

    public InventorySong(@NonNull String accountId, @NonNull String itemId) {
        this.accountId = accountId;
        this.itemId = itemId;
    }
}
