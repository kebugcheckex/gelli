package com.dkanada.gramophone.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.dkanada.gramophone.model.User;

import java.util.List;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUser(User user);

    @Delete
    void deleteUser(User user);

    @Query("SELECT * FROM users")
    List<User> getUsers();

    @Query("SELECT * FROM users WHERE id = :id")
    User getUser(String id);

    @Query("UPDATE users SET server = :server WHERE id = :id")
    void setServer(String id, String server);

    // accounts saved before the server id was recorded
    @Query("SELECT * FROM users WHERE serverId IS NULL AND jellyfinUserId = :jellyfinUserId")
    List<User> getUnidentifiedUsers(String jellyfinUserId);
}
