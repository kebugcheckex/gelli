package com.dkanada.gramophone.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class Artist implements Parcelable {
    public List<Genre> genres;
    public List<Album> albums;
    public List<Song> songs;

    public String id;
    public String name;

    public String primary;
    public String blurHash;

    public Artist(String id, String name, String primary, String blurHash) {
        this.id = id;
        this.name = name;
        this.primary = primary;
        this.blurHash = blurHash;
        this.genres = new ArrayList<>();
        this.albums = new ArrayList<>();
        this.songs = new ArrayList<>();
    }

    public Artist(Album album) {
        this.id = album.artistId;
        this.name = album.artistName;
        this.primary = this.id;
    }

    public Artist(Song song) {
        this.id = song.artistId;
        this.name = song.artistName;
        this.primary = this.id;
    }

    public Artist() {
        this.genres = new ArrayList<>();
        this.albums = new ArrayList<>();
        this.songs = new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Artist artist = (Artist) o;
        return id.equals(artist.id);
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
        dest.writeString(id);
        dest.writeString(name);

        dest.writeString(primary);
        dest.writeString(blurHash);
    }

    protected Artist(Parcel in) {
        this.genres = new ArrayList<>();
        this.albums = new ArrayList<>();
        this.songs = new ArrayList<>();

        this.id = in.readString();
        this.name = in.readString();

        this.primary = in.readString();
        this.blurHash = in.readString();
    }

    public static final Parcelable.Creator<Artist> CREATOR = new Parcelable.Creator<Artist>() {
        @Override
        public Artist createFromParcel(Parcel source) {
            return new Artist(source);
        }

        @Override
        public Artist[] newArray(int size) {
            return new Artist[size];
        }
    };
}
