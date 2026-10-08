package com.dkanada.gramophone.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertDownload(Download download);

    @Query("SELECT * FROM downloads WHERE userId = :userId"
            + " ORDER BY artistName COLLATE NOCASE, albumName COLLATE NOCASE, discNumber, trackNumber")
    List<Download> getDownloads(String userId);

    @Query("SELECT * FROM downloads WHERE id = :id AND userId = :userId")
    Download getDownload(String id, String userId);

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE id = :id AND userId = :userId)")
    boolean isDownloaded(String id, String userId);
}
