package com.dkanada.gramophone.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class Playlist implements Parcelable {
    public final String id;
    public final String name;

    public String primary;
    public String blurHash;

    public Playlist(String id, String name, String primary, String blurHash) {
        this.id = id;
        this.name = name;
        this.primary = primary;
        this.blurHash = blurHash;
    }

    public Playlist(final int id, final String name) {
        this.id = Integer.toString(id);
        this.name = name;
    }

    public Playlist() {
        this.id = "";
        this.name = "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Playlist playlist = (Playlist) o;
        return id.equals(playlist.id);
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
        dest.writeString(this.name);
    }

    protected Playlist(Parcel in) {
        this.id = in.readString();
        this.name = in.readString();
    }

    public static final Creator<Playlist> CREATOR = new Creator<Playlist>() {
        public Playlist createFromParcel(Parcel source) {
            return new Playlist(source);
        }

        public Playlist[] newArray(int size) {
            return new Playlist[size];
        }
    };
}
