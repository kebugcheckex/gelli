package com.dkanada.gramophone.model;

import android.os.Parcel;

public class PlaylistSong extends Song {
    public final String playlistId;
    public final String indexId;

    public PlaylistSong(Song song, String playlistId, String indexId) {
        super(song);
        this.playlistId = playlistId;
        this.indexId = indexId;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);

        dest.writeString(this.playlistId);
        dest.writeString(this.indexId);
    }

    protected PlaylistSong(Parcel in) {
        super(in);

        this.playlistId = in.readString();
        this.indexId = in.readString();
    }

    public static final Creator<PlaylistSong> CREATOR = new Creator<PlaylistSong>() {
        public PlaylistSong createFromParcel(Parcel source) {
            return new PlaylistSong(source);
        }

        public PlaylistSong[] newArray(int size) {
            return new PlaylistSong[size];
        }
    };
}
