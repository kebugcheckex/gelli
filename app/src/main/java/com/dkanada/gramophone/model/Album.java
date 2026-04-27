package com.dkanada.gramophone.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class Album implements Parcelable {
    public List<Song> songs;

    public String id;
    public String title;
    public int year;

    public String artistId;
    public String artistName;

    public String primary;
    public String blurHash;

    public Album(String id, String title, int year, String artistId, String artistName, String primary, String blurHash) {
        this.id = id;
        this.title = title;
        this.year = year;
        this.artistId = artistId;
        this.artistName = artistName;
        this.primary = primary;
        this.blurHash = blurHash;
        this.songs = new ArrayList<>();
    }

    public Album(Song song) {
        this.id = song.albumId;
        this.title = song.albumName;
        this.year = song.year;

        this.artistId = song.artistId;
        this.artistName = song.artistName;

        this.primary = song.primary;
        this.blurHash = song.blurHash;

        this.songs = new ArrayList<>();
    }

    public Album() {
        this.songs = new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Album album = (Album) o;
        return id.equals(album.id);
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
        dest.writeTypedList(songs);

        dest.writeString(id);
        dest.writeString(title);
        dest.writeInt(year);

        dest.writeString(artistId);
        dest.writeString(artistName);

        dest.writeString(primary);
        dest.writeString(blurHash);
    }

    protected Album(Parcel in) {
        this.songs = in.createTypedArrayList(Song.CREATOR);

        this.id = in.readString();
        this.title = in.readString();
        this.year = in.readInt();

        this.artistId = in.readString();
        this.artistName = in.readString();

        this.primary = in.readString();
        this.blurHash = in.readString();
    }

    public static final Creator<Album> CREATOR = new Creator<Album>() {
        public Album createFromParcel(Parcel source) {
            return new Album(source);
        }

        public Album[] newArray(int size) {
            return new Album[size];
        }
    };
}
