package dev.kebugcheck.gelli.database;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

import dev.kebugcheck.gelli.model.Song;

@Entity(
        tableName = "queueSongs",
        primaryKeys = {
                "index",
                "queue"
        },
        foreignKeys = {
                @ForeignKey(
                        entity = Song.class,
                        parentColumns = {"id"},
                        childColumns = {"songId"},
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index(value = {"songId"})
        }
)
public class QueueSong {
    public int index;

    public int queue;

    public String songId;

    public QueueSong(String songId, int index, int queue) {
        this.songId = songId;

        this.index = index;
        this.queue = queue;
    }
}
