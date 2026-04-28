package com.dkanada.gramophone.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class Genre implements Parcelable {
    public final String id;
    public final String name;
    public final int songCount;

    public String primary;
    public String blurHash;

    public Genre(String id, String name, int songCount, String primary, String blurHash) {
        this.id = id;
        this.name = name;
        this.songCount = songCount;
        this.primary = primary;
        this.blurHash = blurHash;
    }

    public Genre(String id, String name) {
        this(id, name, 0, null, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Genre genre = (Genre) o;
        return id.equals(genre.id);
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
        dest.writeInt(this.songCount);
    }

    protected Genre(Parcel in) {
        this.id = in.readString();
        this.name = in.readString();
        this.songCount = in.readInt();
    }

    public static final Creator<Genre> CREATOR = new Creator<Genre>() {
        public Genre createFromParcel(Parcel source) {
            return new Genre(source);
        }

        public Genre[] newArray(int size) {
            return new Genre[size];
        }
    };
}
