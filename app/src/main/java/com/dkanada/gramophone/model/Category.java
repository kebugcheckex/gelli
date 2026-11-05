package com.dkanada.gramophone.model;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.dkanada.gramophone.R;

public enum Category {
    SONGS(R.string.songs, R.drawable.ic_music_note_white_24dp),
    ALBUMS(R.string.albums, R.drawable.ic_album_white_24dp),
    ARTISTS(R.string.artists, R.drawable.ic_person_white_24dp),
    ALBUM_ARTISTS(R.string.album_artists, R.drawable.ic_group_white_24dp),
    GENRES(R.string.genres, R.drawable.ic_label_white_24dp),
    PLAYLISTS(R.string.playlists, R.drawable.ic_queue_music_white_24dp),
    FAVORITES(R.string.favorites, R.drawable.ic_favorite_white_24dp);

    @StringRes
    public final int title;

    @DrawableRes
    public final int icon;

    public boolean select;

    Category(int title, int icon) {
        this.title = title;
        this.icon = icon;
    }
}
