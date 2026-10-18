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
    int updateSong(LocalSong song);

    @Query("SELECT * FROM local_songs WHERE accountId = :accountId AND itemId = :itemId")
    LocalSong getSong(String accountId, String itemId);

    @Query("SELECT * FROM local_songs WHERE accountId = :accountId AND state IN (:states)")
    List<LocalSong> getSongs(String accountId, List<LocalSong.State> states);

    @Query("SELECT * FROM local_songs WHERE accountId = :accountId AND itemId IN (:itemIds)")
    List<LocalSong> getSongsByIds(String accountId, List<String> itemIds);

    // rowid keeps the order songs were requested in when they share a timestamp
    @Query("SELECT * FROM local_songs WHERE accountId = :accountId AND state = :state ORDER BY addedAt, rowid LIMIT 1")
    LocalSong getNextSong(String accountId, LocalSong.State state);

    @Query("SELECT COUNT(*) FROM local_songs WHERE accountId = :accountId AND state IN (:states)")
    int countSongs(String accountId, List<LocalSong.State> states);

    @Query("SELECT COUNT(*) FROM local_songs WHERE accountId = :accountId AND albumId = :albumId")
    int countAlbumSongs(String accountId, String albumId);

    @Query("UPDATE local_songs SET state = :state WHERE accountId = :accountId AND itemId = :itemId")
    void setState(String accountId, String itemId, LocalSong.State state);

    @Query("UPDATE local_songs SET state = :to WHERE accountId = :accountId AND state = :from")
    void replaceState(String accountId, LocalSong.State from, LocalSong.State to);

    @Query("DELETE FROM local_songs WHERE accountId = :accountId AND itemId = :itemId")
    void deleteSong(String accountId, String itemId);

    @Query("DELETE FROM local_songs WHERE accountId = :accountId AND state IN (:states)")
    void deleteSongs(String accountId, List<LocalSong.State> states);
}
