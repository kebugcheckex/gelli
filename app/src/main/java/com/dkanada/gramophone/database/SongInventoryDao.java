package com.dkanada.gramophone.database;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Dao
public abstract class SongInventoryDao {
    @Query("SELECT * FROM song_inventories WHERE accountId = :accountId")
    @Nullable
    protected abstract SongInventory getInventory(String accountId);

    @Query("SELECT itemId FROM inventory_songs WHERE accountId = :accountId")
    protected abstract List<String> getSongIds(String accountId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void insertInventory(SongInventory inventory);

    @Insert
    protected abstract void insertSongs(List<InventorySong> songs);

    @Nullable
    @Transaction
    public InventorySnapshot read(String accountId) {
        SongInventory inventory = getInventory(accountId);
        return inventory == null ? null : new InventorySnapshot(accountId,
                new HashSet<>(getSongIds(accountId)), inventory.checkedAt);
    }

    /** Replacing the header cascades to old IDs; failures roll back the entire replacement. */
    @Transaction
    public void replace(InventorySnapshot snapshot) {
        insertInventory(new SongInventory(snapshot.getAccountId(), snapshot.getCheckedAt()));
        List<InventorySong> songs = new ArrayList<>();
        for (String id : snapshot.getSongIds()) {
            songs.add(new InventorySong(snapshot.getAccountId(), id));
        }
        insertSongs(songs);
    }
}
