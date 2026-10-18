package com.dkanada.gramophone.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface LocalSongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSong(LocalSong song);

    @Update
    void updateSong(LocalSong song);

    @Query("SELECT * FROM local_songs WHERE accountId = :accountId AND itemId = :itemId")
    LocalSong getSong(String accountId, String itemId);

    @Query("SELECT * FROM local_songs WHERE accountId = :accountId AND state IN (:states)")
    List<LocalSong> getSongs(String accountId, List<LocalSong.State> states);

    @Query("UPDATE local_songs SET state = :state WHERE accountId = :accountId AND itemId = :itemId")
    void setState(String accountId, String itemId, LocalSong.State state);

    @Query("DELETE FROM local_songs WHERE accountId = :accountId AND itemId = :itemId")
    void deleteSong(String accountId, String itemId);
}
