package com.dkanada.gramophone.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(tableName = "users")
public class User {
    @NonNull
    @PrimaryKey
    public String id;
    public String name;

    public String server;
    public String token;
    public String jellyfinUserId;

    public User() {
        this.id = UUID.randomUUID().toString();
    }

    @Ignore
    public User(String server, String token, String jellyfinUserId, String name) {
        this.server = server;
        this.token = token;
        this.jellyfinUserId = jellyfinUserId;
        this.name = name;
        this.id = server + jellyfinUserId;
    }
}
