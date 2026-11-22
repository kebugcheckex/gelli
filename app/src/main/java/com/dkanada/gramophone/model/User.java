package com.dkanada.gramophone.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.dkanada.gramophone.database.AddressConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An account on a Jellyfin server. The id comes from the server's own id and the user id, see
 * Accounts.accountId, so the same server reached through several addresses is one account.
 */
@Entity(tableName = "users")
public class User {
    @NonNull
    @PrimaryKey
    public String id;
    public String name;

    // the address the account last connected through
    public String server;
    // every address the account signed in through, in the order they were added
    @NonNull
    @TypeConverters(AddressConverter.class)
    public List<String> addresses = new ArrayList<>();

    public String token;
    public String jellyfinUserId;
    // null for accounts saved before the server id was recorded, until they connect again
    public String serverId;

    public User() {
        this.id = UUID.randomUUID().toString();
    }

    @Ignore
    public User(@NonNull String id, String serverId, String jellyfinUserId) {
        this.id = id;
        this.serverId = serverId;
        this.jellyfinUserId = jellyfinUserId;
    }
}
