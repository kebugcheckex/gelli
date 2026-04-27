package com.dkanada.gramophone.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(tableName = "songs")
public class Song implements Parcelable {
    @NonNull
    @PrimaryKey
    public String id;
    public String title;
    public int trackNumber;
    public int discNumber;
    public int year;
    public long duration;

    public String albumId;
    public String albumName;

    public String artistId;
    public String artistName;

    public String primary;
    public String blurHash;
    public boolean favorite;

    public String path;
    public long size;

    public String container;
    public String codec;

    public boolean supportsTranscoding;

    public int sampleRate;
    public int bitRate;
    public int bitDepth;
    public int channels;

    @ColumnInfo(defaultValue = "1")
    public boolean cache;

    public Song() {
        this.id = UUID.randomUUID().toString();
    }

    public Song(Song source) {
        this.id = source.id;
        this.title = source.title;
        this.trackNumber = source.trackNumber;
        this.discNumber = source.discNumber;
        this.year = source.year;
        this.duration = source.duration;

        this.albumId = source.albumId;
        this.albumName = source.albumName;

        this.artistId = source.artistId;
        this.artistName = source.artistName;

        this.primary = source.primary;
        this.blurHash = source.blurHash;
        this.favorite = source.favorite;

        this.path = source.path;
        this.size = source.size;

        this.container = source.container;
        this.codec = source.codec;

        this.supportsTranscoding = source.supportsTranscoding;

        this.sampleRate = source.sampleRate;
        this.bitRate = source.bitRate;
        this.bitDepth = source.bitDepth;
        this.channels = source.channels;

        this.cache = source.cache;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Song song = (Song) o;
        return id.equals(song.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return id;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.id);
        dest.writeString(this.title);
        dest.writeInt(this.trackNumber);
        dest.writeInt(this.discNumber);
        dest.writeInt(this.year);
        dest.writeLong(this.duration);

        dest.writeString(this.albumId);
        dest.writeString(this.albumName);

        dest.writeString(this.artistId);
        dest.writeString(this.artistName);

        dest.writeString(this.primary);
        dest.writeString(Boolean.toString(favorite));
        dest.writeString(this.blurHash);

        dest.writeString(this.path);
        dest.writeLong(this.size);

        dest.writeString(this.container);
        dest.writeString(this.codec);

        dest.writeInt(this.sampleRate);
        dest.writeInt(this.bitRate);
        dest.writeInt(this.bitDepth);
        dest.writeInt(this.channels);
    }

    protected Song(Parcel in) {
        this.id = in.readString();
        this.title = in.readString();
        this.trackNumber = in.readInt();
        this.discNumber = in.readInt();
        this.year = in.readInt();
        this.duration = in.readLong();

        this.albumId = in.readString();
        this.albumName = in.readString();

        this.artistId = in.readString();
        this.artistName = in.readString();

        this.primary = in.readString();
        this.favorite = Boolean.parseBoolean(in.readString());
        this.blurHash = in.readString();

        this.path = in.readString();
        this.size = in.readLong();

        this.container = in.readString();
        this.codec = in.readString();

        this.sampleRate = in.readInt();
        this.bitRate = in.readInt();
        this.bitDepth = in.readInt();
        this.channels = in.readInt();
    }

    public static final Creator<Song> CREATOR = new Creator<Song>() {
        public Song createFromParcel(Parcel source) {
            return new Song(source);
        }

        public Song[] newArray(int size) {
            return new Song[size];
        }
    };
}
